package org.millenaire.activity;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Sole server coordinator for regional group activities. One global admission pass per server tick;
 * work is shared fairly across dimensions/regions.
 */
public final class RegionalActivityService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final RegionalActivityService INSTANCE = new RegionalActivityService();

   private final ActivityAdmissionController admission = new ActivityAdmissionController();
   private final Map<ResourceLocation, ActivitySpatialIndex> indexes = new HashMap<>();
   private boolean disableDraining;
   private int dimFairness;

   private RegionalActivityService() {}

   public static RegionalActivityService get() {
      return INSTANCE;
   }

   public ActivityAdmissionController admission() {
      return this.admission;
   }

   public ActivitySpatialIndex indexFor(ServerLevel level) {
      return this.indexes.computeIfAbsent(level.dimension().location(), ActivitySpatialIndex::new);
   }

   public void removeGroupFromIndexes(UUID groupId) {
      for (ActivitySpatialIndex idx : this.indexes.values()) {
         idx.removeGroup(groupId);
      }
   }

   public void onServerTick(MinecraftServer server) {
      long start = System.nanoTime();
      ActivityPressureMonitor.get().onServerTick(server);
      ActivityNavigationService.get().resetTick();
      this.admission.resetTickCounters();

      boolean configured = MillenaireServerConfig.SERVER.regionalActivityEnabled.getAsBoolean();
      boolean enabled = configured && ActivityDiagnostics.get().runtimeEnabled();

      List<ServerLevel> levels = new java.util.ArrayList<>();
      for (ServerLevel level : server.getAllLevels()) {
         levels.add(level);
      }
      if (levels.isEmpty()) {
         ActivityDiagnostics.get().addCoordinatorNanos(System.nanoTime() - start);
         return;
      }
      this.dimFairness = Math.floorMod(this.dimFairness + 1, levels.size());

      double budgetMs = ActivityPressureMonitor.get().state().targetCoordinatorMs();
      long budgetNanos = (long) (budgetMs * 1_000_000L);

      // Global admission sync from overworld/first level ledger counts aggregated lightly
      for (int i = 0; i < levels.size(); i++) {
         ServerLevel level = levels.get((this.dimFairness + i) % levels.size());
         tickDimension(level, enabled, start, budgetNanos);
         if (System.nanoTime() - start >= budgetNanos) {
            this.admission.setLastDefer(ActivityReasonCode.TIME_BUDGET);
            break;
         }
      }
      ActivityDiagnostics.get().addCoordinatorNanos(System.nanoTime() - start);
   }

   private void tickDimension(ServerLevel level, boolean enabled, long start, long budgetNanos) {
      ActivitySavedData data = ActivitySavedData.get(level);
      ActivitySpatialIndex index = indexFor(level);
      this.admission.syncFromSaved(data);

      // Warmup + grace for existing saves
      long now = level.getGameTime();
      if (data.warmupUntilGameTime() == 0L) {
         data.setWarmupUntilGameTime(now + MillenaireServerConfig.SERVER.regionalActivityWarmupTicks.getAsInt());
      }
      if (!data.featureGraceAssigned()) {
         long grace = now + MillenaireServerConfig.SERVER.regionalActivityExistingSaveGraceTicks.getAsInt();
         for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            data.assignGraceIfNeeded(v.getId().uuid(), grace);
            index.upsertSettlement(v);
         }
         data.setFeatureGraceAssigned(true);
      } else if (!index.isBootstrapped()) {
         index.bootstrapStep(level, 8);
      }

      data.compactHistory(
         MillenaireServerConfig.SERVER.regionalActivityMaxPersistentOps.getAsInt(),
         MillenaireServerConfig.SERVER.regionalActivityMaxOutcomeHistory.getAsInt()
      );

      // Due ops first (running work has priority)
      int commitBudget = MillenaireServerConfig.SERVER.regionalActivityMaxCommitStepsPerTick.getAsInt();
      List<ActivityOperation> due = data.pollDue(now, commitBudget);
      for (ActivityOperation op : due) {
         if (System.nanoTime() - start >= budgetNanos) {
            data.reschedule(op);
            this.admission.setLastDefer(ActivityReasonCode.TIME_BUDGET);
            break;
         }
         if (!enabled && !op.state().isTerminal()) {
            // Disable flow: bounded return/cleanup
            if (op.state() != ActivityState.RETURNING
               && op.state() != ActivityState.RETREATING
               && op.state() != ActivityState.SETTLING
               && op.state() != ActivityState.RECOVERY_REQUIRED
               && op.state() != ActivityState.COMPLETED
               && op.state() != ActivityState.CANCELLED) {
               op.enterState(ActivityState.RETURNING, now);
            }
         }
         ActivityLifecycleService.advance(level, data, this.admission, index, op);
         if (op.state() == ActivityState.COMPLETED || op.state() == ActivityState.CANCELLED) {
            ActivityLifecycleService.releaseParticipantsOnLevel(level, op);
         }
      }

      if (!enabled || now < data.warmupUntilGameTime()) {
         return;
      }
      if (ActivityPressureMonitor.get().state() == ActivityPressureState.HARD) {
         return;
      }
      if (System.nanoTime() - start >= budgetNanos) {
         return;
      }

      int candCap = MillenaireServerConfig.SERVER.regionalActivityMaxCandidatesPerTick.getAsInt()
         * ActivityPressureMonitor.get().state().candidateScalePercent()
         / 100;
      if (candCap <= 0) {
         return;
      }
      List<ActivityPlanner.Proposal> proposals = ActivityPlanner.planSlice(
         level, data, index, this.admission, data.fairnessCursor(), candCap
      );
      data.setFairnessCursor(data.fairnessCursor() + Math.max(1, candCap));
      for (ActivityPlanner.Proposal proposal : proposals) {
         if (System.nanoTime() - start >= budgetNanos) break;
         if (!this.admission.trySpendCommit()) break;
         ActivityOperation op = ActivityLifecycleService.createFromProposal(level, proposal);
         data.putOperation(op);
         ActivityLifecycleService.advance(level, data, this.admission, index, op);
      }
   }

   public void onVillageAdded(ServerLevel level, Village village) {
      indexFor(level).upsertSettlement(village);
      ActivitySavedData data = ActivitySavedData.get(level);
      long grace = level.getGameTime() + MillenaireServerConfig.SERVER.regionalActivityNewSettlementGraceTicks.getAsInt();
      data.assignGraceIfNeeded(village.getId().uuid(), grace);
   }

   public void onVillageRemoved(ServerLevel level, UUID villageId) {
      indexFor(level).removeSettlement(villageId);
   }

   public void onServerStopping() {
      this.indexes.clear();
      this.admission.resetTickCounters();
      ActivityNavigationService.get().clearRuntime();
      ActivityPressureMonitor.get().resetRuntime();
      ActivityDiagnostics.get().resetRuntime();
      LOGGER.debug("[Activity] Cleared runtime leases on server stop");
   }

   public void setDisableDraining(boolean v) {
      this.disableDraining = v;
   }

   public boolean isDisableDraining() {
      return this.disableDraining;
   }
}
