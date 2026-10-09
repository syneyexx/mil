package org.millenaire.worldgen.materialize;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.worldgen.plan.SettlementPlan;

/**
 * Shared resumable bridge placement for local and regional roads.
 * Deck height is stored so retries never climb on already-placed pillars.
 * Placement uses {@link RoadPlacementGuard} — ordinary building blocks are never treated as free terrain.
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
      return placeBridge(level, approxSurface, deck, progress, bridgeKey, bridgesEnabled, null);
   }

   public static Outcome placeBridge(
      ServerLevel level,
      BlockPos approxSurface,
      BlockState deck,
      @Nullable ChunkMaterializationProgress progress,
      UUID bridgeKey,
      boolean bridgesEnabled,
      @Nullable SettlementPlan owningPlan
   ) {
      if (!bridgesEnabled) {
         return Outcome.blocked("water_no_bridges");
      }
      if (level == null || approxSurface == null) {
         return Outcome.blocked("invalid_position");
      }

      int deckY = resolveDeckHeight(level, approxSurface, progress, bridgeKey);
      BlockPos deckPos = new BlockPos(approxSurface.getX(), deckY, approxSurface.getZ());
      RoadPlacementGuard.Result deckPermit = RoadPlacementGuard.evaluate(
         level, deckPos, RoadPlacementGuard.Intent.BRIDGE_DECK, owningPlan, deck
      );
      if (!deckPermit.allowed()) {
         return Outcome.blocked(deckPermit.reason().isEmpty() ? "deck_blocked" : deckPermit.reason());
      }

      int ops = 0;
      int supportCursor = progress == null ? 0 : progress.bridgeSupportCursor(bridgeKey);
      int minY = Math.max(level.getMinBuildHeight() + 1, deckY - MAX_SUPPORT_DEPTH);
      int y = deckY - supportCursor;

      while (y > minY) {
         BlockPos support = new BlockPos(deckPos.getX(), y, deckPos.getZ());
         BlockState state = level.getBlockState(support);
         if (state.canOcclude() && state.getFluidState().isEmpty() && RoadPlacementGuard.isNaturalTerrain(state)) {
            break;
         }
         RoadPlacementGuard.Result supportPermit = RoadPlacementGuard.evaluate(
            level, support, RoadPlacementGuard.Intent.BRIDGE_SUPPORT, owningPlan, deck
         );
         if (supportPermit.alreadyPath()) {
            break;
         }
         if (!supportPermit.allowed()) {
            if (progress != null) {
               progress.setBridgeSupportCursor(bridgeKey, deckY - y);
            }
            return Outcome.blocked(supportPermit.reason().isEmpty() ? "support_blocked" : supportPermit.reason());
         }
         if (!MaterializationTickBudget.tryConsumeOp()) {
            if (progress != null) {
               progress.setBridgeSupportCursor(bridgeKey, deckY - y);
               progress.setBridgeDeckHeight(bridgeKey, deckY);
            }
            return Outcome.deferred(ops);
         }
         // Re-check immediately before mutation.
         supportPermit = RoadPlacementGuard.evaluate(
            level, support, RoadPlacementGuard.Intent.BRIDGE_SUPPORT, owningPlan, deck
         );
         if (!supportPermit.allowed()) {
            return Outcome.blocked(supportPermit.reason());
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
         RoadPlacementGuard.Result recheck = RoadPlacementGuard.evaluate(
            level, deckPos, RoadPlacementGuard.Intent.BRIDGE_DECK, owningPlan, desiredDeck
         );
         if (!recheck.allowed()) {
            return Outcome.blocked(recheck.reason().isEmpty() ? "deck_blocked" : recheck.reason());
         }
         if (!recheck.alreadyPath()) {
            level.setBlock(deckPos, desiredDeck, 2);
            ops++;
         }
      }

      BlockPos above = deckPos.above();
      RoadPlacementGuard.Result passage = RoadPlacementGuard.evaluate(
         level, above, RoadPlacementGuard.Intent.CLEAR_PASSAGE, owningPlan, desiredDeck
      );
      if (!passage.allowed()) {
         // Deck exists but passage is blocked by a solid non-replaceable block — not complete.
         return Outcome.blocked(passage.reason().isEmpty() ? "passage_blocked" : passage.reason());
      }
      BlockState aboveState = level.getBlockState(above);
      if (!aboveState.isAir() && (aboveState.canBeReplaced() || RoadPlacementGuard.isClearableVegetation(aboveState))) {
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

   /** @deprecated use {@link RoadPlacementGuard#evaluate} — kept for callers that only need a boolean. */
   public static boolean isSafeToOverwrite(ServerLevel level, BlockPos pos) {
      RoadPlacementGuard.Result r = RoadPlacementGuard.evaluate(
         level, pos, RoadPlacementGuard.Intent.PATH_SURFACE, null, null
      );
      return r.allowed();
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
      int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, approxSurface.getX(), approxSurface.getZ()) - 1;
      int y = Math.max(approxSurface.getY(), surface);
      for (int probe = 0; probe < 8; probe++) {
         BlockPos p = new BlockPos(approxSurface.getX(), y - probe, approxSurface.getZ());
         BlockState s = level.getBlockState(p);
         if (!s.getFluidState().isEmpty()) {
            continue;
         }
         if (RoadPlacementGuard.isRegisteredPath(s) || s.is(Blocks.OAK_PLANKS) || s.is(Blocks.STONE)) {
            return p.getY();
         }
         if (RoadPlacementGuard.isNaturalTerrain(s) && s.canOcclude()) {
            return Math.max(p.getY(), approxSurface.getY());
         }
      }
      int deckY = Math.max(approxSurface.getY(), surface);
      if (progress != null && bridgeKey != null) {
         progress.setBridgeDeckHeight(bridgeKey, deckY);
      }
      return deckY;
   }

   public static boolean statesMatch(BlockState current, BlockState desired) {
      return current != null && desired != null && current.getBlock() == desired.getBlock();
   }
}
