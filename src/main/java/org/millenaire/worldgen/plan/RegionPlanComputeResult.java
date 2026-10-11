package org.millenaire.worldgen.plan;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.perf.compute.ComputeResult;
import org.millenaire.worldgen.WorldgenMetrics;

/**
 * Persists a settlement plan produced by a worker finalize job.
 */
public record RegionPlanComputeResult(SettlementPlan plan, long regionPacked, boolean created) implements ComputeResult {
   @Override
   public boolean apply(ServerLevel level) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      SettlementPlan existing = store.getByRegion(this.plan.region());
      WorldgenPlanningQueue.get().endRegion(this.regionPacked);
      if (existing != null) {
         WorldgenPlanningQueue.get().clearRegionWaiting(this.regionPacked);
         return false;
      }
      store.putPlan(this.plan);
      WorldgenPlanningQueue.get().clearRegionWaiting(this.regionPacked);
      if (this.created) {
         WorldgenMetrics.PLANS_CREATED.incrementAndGet();
      }
      if (!this.plan.status().isTerminalFailure()
         && (this.plan.status() == SettlementPlanStatus.PLANNED || this.plan.buildings().isEmpty())) {
         WorldgenPlanningQueue.get().offerLayout(this.plan.region(), new ChunkPos(this.plan.center()));
      }
      return true;
   }
}
