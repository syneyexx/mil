package org.millenaire.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.millenaire.worldgen.plan.SettlementRegionKey;
import org.millenaire.worldgen.plan.WorldgenPlanningQueue;

class WorldgenPlanningQueueLogicTest {
   @AfterEach
   void reset() {
      WorldgenPlanningQueue.get().clear();
   }

   @Test
   void planRegionDedupesByRegionNotTriggerChunk() {
      WorldgenPlanningQueue queue = WorldgenPlanningQueue.get();
      SettlementRegionKey region = new SettlementRegionKey(1, 2);
      assertTrue(queue.offerPlanRegion(region, new ChunkPos(10, 20)));
      assertFalse(queue.offerPlanRegion(region, new ChunkPos(11, 21)));
      assertEquals(1, queue.size());
   }

   @Test
   void priorityDrainsLayoutBeforePlan() {
      WorldgenPlanningQueue queue = WorldgenPlanningQueue.get();
      SettlementRegionKey region = new SettlementRegionKey(0, 0);
      queue.offerPlanRegion(region, new ChunkPos(0, 0));
      queue.offerLayout(region, new ChunkPos(0, 0));
      var drained = queue.drainPriority(1, 0, 1);
      assertEquals(WorldgenPlanningQueue.Kind.LAYOUT, drained.getFirst().kind());
   }
}
