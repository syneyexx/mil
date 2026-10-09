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

   public record Outcome(WorkResult result, int ops, String reason, int supportCursor, int deckY) {
      public static Outcome complete(int ops, int deckY) {
         return new Outcome(WorkResult.COMPLETE, ops, "", 0, deckY);
      }

      public static Outcome deferred(int ops, int supportCursor, int deckY) {
         return new Outcome(WorkResult.DEFERRED_BUDGET, ops, "budget_exhausted", supportCursor, deckY);
      }

      public static Outcome blocked(String reason, int supportCursor, int deckY) {
         return new Outcome(WorkResult.BLOCKED, 0, reason == null ? "" : reason, supportCursor, deckY);
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
      return placeBridge(level, approxSurface, deck, BridgeColumnProgress.fromChunk(progress), bridgeKey, bridgesEnabled, null);
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
      return placeBridge(level, approxSurface, deck, BridgeColumnProgress.fromChunk(progress), bridgeKey, bridgesEnabled, owningPlan);
   }

   public static Outcome placeBridge(
      ServerLevel level,
      BlockPos approxSurface,
      BlockState deck,
      @Nullable BridgeColumnProgress progress,
      UUID bridgeKey,
      boolean bridgesEnabled
   ) {
      return placeBridge(level, approxSurface, deck, progress, bridgeKey, bridgesEnabled, null);
   }

   public static Outcome placeBridge(
      ServerLevel level,
      BlockPos approxSurface,
      BlockState deck,
      @Nullable BridgeColumnProgress progress,
      UUID bridgeKey,
      boolean bridgesEnabled,
      @Nullable SettlementPlan owningPlan
   ) {
      if (!bridgesEnabled) {
         return Outcome.blocked("water_no_bridges", 0, Integer.MIN_VALUE);
      }
      if (level == null || approxSurface == null) {
         return Outcome.blocked("invalid_position", 0, Integer.MIN_VALUE);
      }

      int deckY = resolveDeckHeight(level, approxSurface, progress, bridgeKey);
      BlockPos deckPos = new BlockPos(approxSurface.getX(), deckY, approxSurface.getZ());
      int supportCursor = progress == null ? 0 : progress.bridgeSupportCursor(bridgeKey);
      if (progress != null && bridgeKey != null) {
         progress.setBridgeDeckHeight(bridgeKey, deckY);
      }

      RoadPlacementGuard.Result deckPermit = RoadPlacementGuard.evaluate(
         level, deckPos, RoadPlacementGuard.Intent.BRIDGE_DECK, owningPlan, deck
      );
      if (deckPermit.deferred()) {
         return Outcome.deferred(0, supportCursor, deckY);
      }
      if (!deckPermit.allowed()) {
         return Outcome.blocked(deckPermit.reason().isEmpty() ? "deck_blocked" : deckPermit.reason(), supportCursor, deckY);
      }

      int ops = 0;
      BlockState desiredDeck = deck == null || deck.isAir() ? Blocks.OAK_PLANKS.defaultBlockState() : deck;

      boolean supportComplete = progress instanceof BridgeColumnProgress.SingleColumn sc && sc.supportComplete();
      if (!supportComplete) {
         int minY = Math.max(level.getMinBuildHeight() + 1, deckY - MAX_SUPPORT_DEPTH);
         // Supports start under the deck — never at deckY.
         int y = deckY - 1 - supportCursor;

         while (y >= minY) {
            BlockPos support = new BlockPos(deckPos.getX(), y, deckPos.getZ());
            BlockState state = level.getBlockState(support);

            // Own-placed STONE below deck is already-placed support — continue downward.
            if (state.is(Blocks.STONE) && state.getFluidState().isEmpty()) {
               supportCursor = deckY - y;
               if (progress != null) {
                  progress.setBridgeSupportCursor(bridgeKey, supportCursor);
                  progress.setBridgeDeckHeight(bridgeKey, deckY);
               }
               y--;
               continue;
            }

            RoadPlacementGuard.Result supportPermit = RoadPlacementGuard.evaluate(
               level, support, RoadPlacementGuard.Intent.BRIDGE_SUPPORT, owningPlan, desiredDeck
            );
            // Natural terrain end-ground (non-stone) ends the support phase.
            if (supportPermit.alreadyPath()) {
               supportComplete = true;
               break;
            }
            if (supportPermit.deferred()) {
               if (progress != null) {
                  progress.setBridgeSupportCursor(bridgeKey, supportCursor);
                  progress.setBridgeDeckHeight(bridgeKey, deckY);
               }
               return Outcome.deferred(ops, supportCursor, deckY);
            }
            if (!supportPermit.allowed()) {
               if (progress != null) {
                  progress.setBridgeSupportCursor(bridgeKey, supportCursor);
                  progress.setBridgeDeckHeight(bridgeKey, deckY);
               }
               return Outcome.blocked(
                  supportPermit.reason().isEmpty() ? "support_blocked" : supportPermit.reason(),
                  supportCursor,
                  deckY
               );
            }
            if (!MaterializationTickBudget.tryConsumeOp()) {
               if (progress != null) {
                  progress.setBridgeSupportCursor(bridgeKey, supportCursor);
                  progress.setBridgeDeckHeight(bridgeKey, deckY);
               }
               return Outcome.deferred(ops, supportCursor, deckY);
            }
            // Re-check immediately before mutation.
            supportPermit = RoadPlacementGuard.evaluate(
               level, support, RoadPlacementGuard.Intent.BRIDGE_SUPPORT, owningPlan, desiredDeck
            );
            if (supportPermit.alreadyPath()) {
               supportComplete = true;
               break;
            }
            if (!supportPermit.allowed()) {
               return Outcome.blocked(supportPermit.reason(), supportCursor, deckY);
            }
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
            ops++;
            supportCursor = deckY - y;
            y--;
            if (progress != null) {
               progress.setBridgeSupportCursor(bridgeKey, supportCursor);
               progress.setBridgeDeckHeight(bridgeKey, deckY);
            }
         }

         if (!supportComplete) {
            // Max depth reached — support phase ends.
            supportComplete = true;
         }
      }

      if (progress instanceof BridgeColumnProgress.SingleColumn single) {
         single.setSupportComplete(true);
      }

      boolean deckComplete = progress instanceof BridgeColumnProgress.SingleColumn sc2 && sc2.deckComplete();
      BlockState currentDeck = level.getBlockState(deckPos);
      if (!deckComplete) {
         // Deck is complete only when the desired deck material is present (not generic stone).
         if (statesMatch(currentDeck, desiredDeck)) {
            deckComplete = true;
         } else {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               if (progress != null) {
                  progress.setBridgeSupportCursor(bridgeKey, supportCursor);
                  progress.setBridgeDeckHeight(bridgeKey, deckY);
               }
               return Outcome.deferred(ops, supportCursor, deckY);
            }
            RoadPlacementGuard.Result recheck = RoadPlacementGuard.evaluate(
               level, deckPos, RoadPlacementGuard.Intent.BRIDGE_DECK, owningPlan, desiredDeck
            );
            if (recheck.deferred()) {
               return Outcome.deferred(ops, supportCursor, deckY);
            }
            if (!recheck.allowed()) {
               return Outcome.blocked(recheck.reason().isEmpty() ? "deck_blocked" : recheck.reason(), supportCursor, deckY);
            }
            if (!recheck.alreadyPath()) {
               level.setBlock(deckPos, desiredDeck, 2);
               ops++;
            }
            deckComplete = true;
         }
      }

      if (progress instanceof BridgeColumnProgress.SingleColumn singleDeck) {
         singleDeck.setDeckComplete(true);
      }

      boolean passageComplete = progress instanceof BridgeColumnProgress.SingleColumn sc3 && sc3.passageComplete();
      if (!passageComplete) {
         // Entity-height passage: clear Y+1 and Y+2 above the deck.
         for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = deckPos.above(dy);
            RoadPlacementGuard.Result passage = RoadPlacementGuard.evaluate(
               level, above, RoadPlacementGuard.Intent.CLEAR_PASSAGE, owningPlan, desiredDeck
            );
            if (passage.deferred()) {
               return Outcome.deferred(ops, supportCursor, deckY);
            }
            if (!passage.allowed()) {
               return Outcome.blocked(passage.reason().isEmpty() ? "passage_blocked" : passage.reason(), supportCursor, deckY);
            }
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && (aboveState.canBeReplaced() || RoadPlacementGuard.isClearableVegetation(aboveState))) {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  return Outcome.deferred(ops, supportCursor, deckY);
               }
               level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
               ops++;
            }
         }
         passageComplete = true;
      }

      if (progress instanceof BridgeColumnProgress.SingleColumn singlePassage) {
         singlePassage.setPassageComplete(true);
      }

      if (!(supportComplete && deckComplete && passageComplete)) {
         return Outcome.deferred(ops, supportCursor, deckY);
      }

      if (progress != null) {
         progress.clearBridgeSupportCursor(bridgeKey);
      }
      return Outcome.complete(ops, deckY);
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
      @Nullable BridgeColumnProgress progress,
      UUID bridgeKey
   ) {
      if (progress != null && bridgeKey != null && progress.hasBridgeDeckHeight(bridgeKey)) {
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
         // Do not treat stone (support pillars) as a completed deck height for resume.
         if (RoadPlacementGuard.isRegisteredPath(s) || s.is(Blocks.OAK_PLANKS)) {
            if (!s.is(Blocks.STONE)) {
               return p.getY();
            }
         }
         if (RoadPlacementGuard.isNaturalTerrain(s) && s.canOcclude() && !s.is(Blocks.STONE)) {
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
