package org.millenaire.worldgen.highway;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.materialize.RoadPlacementGuard;
import org.millenaire.worldgen.plan.SettlementPlan;

/**
 * Procedural mountain tunnel: hold grade Y, carve width x 3-high passage, place path floor.
 */
public final class TunnelPlacementHelper {
   private static final int HEADROOM = 3;

   private TunnelPlacementHelper() {
   }

   public enum WorkResult {
      COMPLETE,
      PARTIAL,
      DEFERRED_BUDGET,
      BLOCKED
   }

   public record Outcome(WorkResult result, int ops, String reason, int carveCursor) {
      public static Outcome complete(int ops) {
         return new Outcome(WorkResult.COMPLETE, ops, "", 0);
      }

      public static Outcome deferred(int ops, int cursor) {
         return new Outcome(WorkResult.DEFERRED_BUDGET, ops, "budget_exhausted", cursor);
      }

      public static Outcome blocked(String reason, int cursor) {
         return new Outcome(WorkResult.BLOCKED, 0, reason == null ? "" : reason, cursor);
      }

      public boolean finished() {
         return this.result == WorkResult.COMPLETE;
      }

      public boolean budgetExhausted() {
         return this.result == WorkResult.DEFERRED_BUDGET;
      }
   }

   /** For width 4: offsets -1,0,1,2. */
   public static int[] laneOffsets(int width) {
      int w = Math.max(1, width);
      if (w == 4) {
         return new int[]{-1, 0, 1, 2};
      }
      int[] offsets = new int[w];
      int start = -(w / 2);
      for (int i = 0; i < w; i++) {
         offsets[i] = start + i;
      }
      return offsets;
   }

   /**
    * Place one tunnel column band at {@code floorCenter} (already at grade Y).
    * {@code carveCursor} indexes into flattened (lane * HEADROOM) carve units for resume.
    */
   public static Outcome placeTunnelColumn(
      ServerLevel level,
      BlockPos floorCenter,
      int latX,
      int latZ,
      int width,
      BlockState path,
      int carveCursor,
      @Nullable SettlementPlan owningPlan
   ) {
      if (level == null || floorCenter == null || path == null || width < 1) {
         return Outcome.blocked("invalid", 0);
      }
      int[] offsets = laneOffsets(width);
      int ops = 0;
      int cursor = Math.max(0, carveCursor);
      int total = offsets.length * HEADROOM;

      for (int offset : offsets) {
         BlockPos floor = floorCenter.offset(latX * offset, 0, latZ * offset);
         if (!level.hasChunk(floor.getX() >> 4, floor.getZ() >> 4)) {
            return Outcome.blocked("chunk_not_loaded", cursor);
         }
         RoadPlacementGuard.Result floorPermit = RoadPlacementGuard.evaluate(
            level, floor, RoadPlacementGuard.Intent.TUNNEL_FLOOR, owningPlan, path
         );
         if (floorPermit.deferred()) {
            return Outcome.deferred(ops, cursor);
         }
         if (!floorPermit.allowed()) {
            return Outcome.blocked(floorPermit.reason().isEmpty() ? "tunnel_floor_blocked" : floorPermit.reason(), cursor);
         }
         if (!floorPermit.alreadyPath()) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               return Outcome.deferred(ops, cursor);
            }
            level.setBlock(floor, path, 2);
            ops++;
         }
      }

      while (cursor < total) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            return Outcome.deferred(ops, cursor);
         }
         int lane = cursor / HEADROOM;
         int dy = 1 + (cursor % HEADROOM);
         int offset = offsets[lane];
         BlockPos floor = floorCenter.offset(latX * offset, 0, latZ * offset);
         BlockPos carve = floor.above(dy);
         if (!level.hasChunk(carve.getX() >> 4, carve.getZ() >> 4)) {
            return Outcome.blocked("chunk_not_loaded", cursor);
         }
         RoadPlacementGuard.Result permit = RoadPlacementGuard.evaluate(
            level, carve, RoadPlacementGuard.Intent.TUNNEL_CARVE, owningPlan, path
         );
         if (permit.deferred()) {
            return Outcome.deferred(ops, cursor);
         }
         if (!permit.allowed()) {
            return Outcome.blocked(permit.reason().isEmpty() ? "tunnel_carve_blocked" : permit.reason(), cursor);
         }
         BlockState cur = level.getBlockState(carve);
         if (!cur.isAir()) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               return Outcome.deferred(ops, cursor);
            }
            level.setBlock(carve, Blocks.AIR.defaultBlockState(), 2);
            ops++;
         }
         cursor++;
      }
      return Outcome.complete(ops);
   }
}
