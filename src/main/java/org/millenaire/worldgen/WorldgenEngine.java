package org.millenaire.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.activate.SettlementActivator;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.materialize.ChunkSettlementMaterializer;
import org.millenaire.worldgen.plan.SettlementLayoutPlanner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.slf4j.Logger;

/**
 * Orchestrates Phase 4 plan → materialize → activate on new overworld chunks.
 * Exploration order cannot change settlement identity: region plans are seed-stable.
 */
public final class WorldgenEngine {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static int activationBudgetRemaining;

   private WorldgenEngine() {
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenEngineEnabled.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static void onContentLoaded() {
      WorldgenContentSnapshot.rebuildFromLoadedCultures();
   }

   public static void onServerTick(ServerLevel overworld) {
      if (!isEnabled() || overworld == null) {
         return;
      }
      activationBudgetRemaining = MillenaireServerConfig.SERVER.worldgenActivationBudget.getAsInt();
      SettlementPlanSavedData store = SettlementPlanSavedData.get(overworld);
      for (SettlementPlan plan : store.allPlans()) {
         if (activationBudgetRemaining <= 0) {
            break;
         }
         if (plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE || plan.status() == SettlementPlanStatus.ACTIVATING) {
            if (SettlementActivator.tryActivate(overworld, plan)) {
               activationBudgetRemaining--;
            }
         }
      }
   }

   /**
    * Called for newly generated overworld chunks only. Must run on the server thread.
    */
   public static void onNewChunk(ServerLevel level, ChunkPos chunk) {
      if (!isEnabled()) {
         return;
      }
      if (level.dimension() != Level.OVERWORLD) {
         return;
      }
      if (WorldgenContentSnapshot.current() == null) {
         WorldgenContentSnapshot.rebuildFromLoadedCultures();
      }

      SettlementPlan plan = SettlementPlanner.ensureRegionPlan(level, chunk);
      if (plan == null || plan.status().isTerminalFailure()) {
         return;
      }

      if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
         if (!plan.status().isTerminalFailure()) {
            boolean laidOut = SettlementLayoutPlanner.ensureLayout(level, plan);
            if (!laidOut && plan.status().isTerminalFailure()) {
               SettlementPlanSavedData.get(level).markDirtyPlan();
               return;
            }
         }
      }

      if (!plan.intersectsChunk(chunk) && !plan.reservedChunks().contains(chunk.toLong())) {
         return;
      }

      ChunkSettlementMaterializer.materializeChunk(level, chunk, plan);
      ChunkSettlementMaterializer.refreshBuildingCompletion(plan);

      if (plan.allBuildingsMaterialized()
         && (plan.status() == SettlementPlanStatus.MATERIALIZING || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE)) {
         if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
            plan.forceStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
            SettlementPlanSavedData.get(level).markDirtyPlan();
         }
         SettlementActivator.tryActivate(level, plan);
      }

      // Expand hamlet plans as child region candidates when parent is reserved/active
      if (!plan.hamlets().isEmpty()
         && (plan.status() == SettlementPlanStatus.RESERVED
            || plan.status() == SettlementPlanStatus.MATERIALIZING
            || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.ACTIVE)) {
         expandHamlets(level, plan);
      }
   }

   private static void expandHamlets(ServerLevel level, SettlementPlan parent) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (var hamlet : parent.hamlets()) {
         if (hamlet.status().isTerminalFailure() || hamlet.status() == SettlementPlanStatus.ACTIVE) {
            continue;
         }
         SettlementPlan existing = store.getPlan(hamlet.hamletPlanId());
         if (existing != null) {
            continue;
         }
         int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
         var region = org.millenaire.worldgen.plan.SettlementRegionKey.fromBlock(
            hamlet.center().getX(), hamlet.center().getZ(), regionSize
         );
         if (store.getByRegion(region) != null) {
            continue;
         }
         SettlementPlan child = new SettlementPlan(
            hamlet.hamletPlanId(),
            hamlet.hamletPlanId(),
            region,
            parent.worldSeed(),
            parent.cultureId(),
            hamlet.villageTypeId(),
            hamlet.center(),
            parent.orientation(),
            40,
            Math.max(0, parent.initialCompletionPercent() / 2),
            false,
            parent.planId(),
            parent.plannedName()
         );
         child.reserveFootprintChunks();
         child.trySetStatus(SettlementPlanStatus.PLANNED);
         store.putPlan(child);
         hamlet.setStatus(SettlementPlanStatus.PLANNED);
         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
            LOGGER.info("[Millenaire] Queued hamlet plan {} for parent {}", child.planId(), parent.planId());
         }
      }
   }
}
