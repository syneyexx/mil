package org.millenaire.worldgen.materialize;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shared resumable bridge placement for local and regional roads.
 * Deck height is stored so retries never climb on already-placed pillars.
 * Returns an explicit {@link WorkResult} — never silently completes a half-built crossing.
 */
public final class BridgePlacementHelper {
   private static final int MAX_SUPPORT_DEPTH = 24;

   private BridgePlacementHelper() {
   }

   public enum WorkResult {
      COMPLETE,
      PARTIAL,
      DEFERRED_BUDGET,
      BLOCKED
   }

   public record Outcome(WorkResult result, int ops, String reason) {
      public static Outcome complete(int ops) {
         return new Outcome(WorkResult.COMPLETE, ops, "");
      }

      public static Outcome deferred(int ops) {
         return new Outcome(WorkResult.DEFERRED_BUDGET, ops, "budget_exhausted");
      }

      public static Outcome partial(int ops, String reason) {
         return new Outcome(WorkResult.PARTIAL, ops, reason == null ? "" : reason);
      }

      public static Outcome blocked(String reason) {
         return new Outcome(WorkResult.BLOCKED, 0, reason == null ? "" : reason);
      }

      public boolean finished() {
         return this.result == WorkResult.COMPLETE;
      }

      public boolean budgetExhausted() {
         return this.result == WorkResult.DEFERRED_BUDGET;
      }
   }

   /**
    * Stable key for a bridge column so progress survives across ticks.
    */
   public static UUID columnKey(UUID roadId, BlockPos column) {
      String raw = (roadId == null ? "anon" : roadId.toString())
         + ":" + column.getX() + ":" + column.getZ();
      return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
   }

   public static Outcome placeBridge(
      ServerLevel level,
      BlockPos approxSurface,
      BlockState deck,
      @Nullable ChunkMaterializationProgress progress,
      UUID bridgeKey,
      boolean bridgesEnabled
   ) {
      if (!bridgesEnabled) {
         return Outcome.blocked("water_no_bridges");
      }
      if (level == null || approxSurface == null) {
         return Outcome.blocked("invalid_position");
      }

      int deckY = resolveDeckHeight(level, approxSurface, progress, bridgeKey);
      BlockPos deckPos = new BlockPos(approxSurface.getX(), deckY, approxSurface.getZ());
      if (!isSafeToOverwrite(level, deckPos)) {
         return Outcome.blocked("protected_or_occupied");
      }

      int ops = 0;
      int supportCursor = progress == null ? 0 : progress.bridgeSupportCursor(bridgeKey);
      int minY = Math.max(level.getMinBuildHeight() + 1, deckY - MAX_SUPPORT_DEPTH);
      int y = deckY - supportCursor;

      while (y > minY) {
         BlockPos support = new BlockPos(deckPos.getX(), y, deckPos.getZ());
         BlockState state = level.getBlockState(support);
         if (state.canOcclude() && state.getFluidState().isEmpty()) {
            break;
         }
         if (!isSafeToOverwrite(level, support)) {
            if (progress != null) {
               progress.setBridgeSupportCursor(bridgeKey, deckY - y);
            }
            return Outcome.blocked("support_blocked");
         }
         if (!MaterializationTickBudget.tryConsumeOp()) {
            if (progress != null) {
               progress.setBridgeSupportCursor(bridgeKey, deckY - y);
               progress.setBridgeDeckHeight(bridgeKey, deckY);
            }
            return Outcome.deferred(ops);
         }
         level.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
         ops++;
         y--;
         if (progress != null) {
            progress.setBridgeSupportCursor(bridgeKey, deckY - y);
            progress.setBridgeDeckHeight(bridgeKey, deckY);
         }
      }

      BlockState desiredDeck = deck == null || deck.isAir() ? Blocks.OAK_PLANKS.defaultBlockState() : deck;
      BlockState currentDeck = level.getBlockState(deckPos);
      if (!statesMatch(currentDeck, desiredDeck)) {
         if (!MaterializationTickBudget.tryConsumeOp()) {
            if (progress != null) {
               progress.setBridgeDeckHeight(bridgeKey, deckY);
            }
            return Outcome.deferred(ops);
         }
         if (!isSafeToOverwrite(level, deckPos)) {
            return Outcome.blocked("deck_blocked");
         }
         level.setBlock(deckPos, desiredDeck, 2);
         ops++;
      }

      BlockPos above = deckPos.above();
      BlockState aboveState = level.getBlockState(above);
      if (!aboveState.isAir() && aboveState.canBeReplaced()) {
         if (!MaterializationTickBudget.tryConsumeOp()) {
            return Outcome.deferred(ops);
         }
         level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
         ops++;
      }

      if (progress != null) {
         progress.clearBridgeSupportCursor(bridgeKey);
      }
      return Outcome.complete(ops);
   }

   private static int resolveDeckHeight(
      ServerLevel level,
      BlockPos approxSurface,
      @Nullable ChunkMaterializationProgress progress,
      UUID bridgeKey
   ) {
      if (progress != null && bridgeKey != null && progress.bridgeDeckHeight(bridgeKey, Integer.MIN_VALUE) != Integer.MIN_VALUE) {
         return progress.bridgeDeckHeight(bridgeKey, approxSurface.getY());
      }
      // Prefer the first non-fluid surface reference so retries do not climb on pillars.
      int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, approxSurface.getX(), approxSurface.getZ()) - 1;
      int y = Math.max(approxSurface.getY(), surface);
      for (int probe = 0; probe < 8; probe++) {
         BlockPos p = new BlockPos(approxSurface.getX(), y - probe, approxSurface.getZ());
         BlockState s = level.getBlockState(p);
         if (!s.getFluidState().isEmpty()) {
            continue;
         }
         if (s.is(Blocks.OAK_PLANKS) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE)) {
            return p.getY();
         }
         if (s.canOcclude()) {
            return Math.max(p.getY(), approxSurface.getY());
         }
      }
      int deckY = Math.max(approxSurface.getY(), surface);
      if (progress != null && bridgeKey != null) {
         progress.setBridgeDeckHeight(bridgeKey, deckY);
      }
      return deckY;
   }

   public static boolean isSafeToOverwrite(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)) {
         return false;
      }
      BlockEntity be = level.getBlockEntity(pos);
      if (be != null) {
         return false;
      }
      return true;
   }

   public static boolean statesMatch(BlockState current, BlockState desired) {
      return current != null && desired != null && current.getBlock() == desired.getBlock();
   }
}
