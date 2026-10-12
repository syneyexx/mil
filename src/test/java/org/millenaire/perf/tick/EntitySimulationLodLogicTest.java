package org.millenaire.perf.tick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EntitySimulationLodLogicTest {
   @AfterEach
   void tearDown() {
      ServerTickGovernor.resetForTest();
   }

   @Test
   void nearAlwaysAllowsGoalAndAmbient() {
      assertTrue(EntitySimulationLod.NEAR.allowGoalPick(0L, 1));
      assertTrue(EntitySimulationLod.NEAR.allowAmbientWork());
   }

   @Test
   void nearCombatScanStaggersUnlessForced() {
      int interval = EntitySimulationLod.nearCombatInterval();
      int entityId = 1;
      int stagger = Math.floorMod(entityId, interval);
      assertTrue(EntitySimulationLod.NEAR.allowCombatScan(stagger, entityId));
      if (interval > 1) {
         assertFalse(EntitySimulationLod.NEAR.allowCombatScan(stagger + 1L, entityId));
      }
   }

   @Test
   void farSleepBlocksGoalCombatAmbient() {
      // farSleepEnabled defaults true when config unloaded
      assertFalse(EntitySimulationLod.FAR.allowGoalPick(0L, 1));
      assertFalse(EntitySimulationLod.FAR.allowCombatScan(0L, 1));
      assertFalse(EntitySimulationLod.FAR.allowAmbientWork());
      assertFalse(EntitySimulationLod.FAR.allowIdleNavigation());
   }

   @Test
   void midCombatStaggers() {
      int interval = EntitySimulationLod.midCombatInterval();
      int entityId = 3;
      int stagger = Math.floorMod(entityId, interval);
      assertTrue(EntitySimulationLod.MID.allowCombatScan(stagger, entityId));
      if (interval > 1) {
         assertFalse(EntitySimulationLod.MID.allowCombatScan(stagger + 1L, entityId));
      }
   }

   @Test
   void farSleepPausesOnlyNonUrgentPausableTasks() {
      assertTrue(EntitySimulationLod.shouldPauseTask(true, false, false, true));
      assertFalse(EntitySimulationLod.shouldPauseTask(false, false, false, true), "not far sleeping");
      assertFalse(EntitySimulationLod.shouldPauseTask(true, true, false, true), "urgent villager context");
      assertFalse(EntitySimulationLod.shouldPauseTask(true, false, true, true), "combat-urgent goal");
      assertFalse(EntitySimulationLod.shouldPauseTask(true, false, false, false), "task not pausable (build/transfer/trade)");
   }
}
