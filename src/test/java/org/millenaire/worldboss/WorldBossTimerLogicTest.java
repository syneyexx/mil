package org.millenaire.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class WorldBossTimerLogicTest {
   @Test
   void rollIntervalRespectsBounds() {
      RandomSource random = RandomSource.create(1L);
      for (int i = 0; i < 50; i++) {
         int v = WorldBossTimerLogic.rollInterval(72000, 108000, random);
         assertTrue(v >= 72000 && v <= 108000);
      }
   }

   @Test
   void timerPausesWithoutEligiblePlayers() {
      assertEquals(10, WorldBossTimerLogic.advance(10, 100, false));
   }

   @Test
   void timerAdvancesWithEligiblePlayers() {
      assertEquals(11, WorldBossTimerLogic.advance(10, 100, true));
   }

   @Test
   void timerDoesNotBacklogPastInterval() {
      assertEquals(100, WorldBossTimerLogic.advance(100, 100, true));
      assertEquals(100, WorldBossTimerLogic.advance(150, 100, true));
   }

   @Test
   void dueAndRemaining() {
      assertFalse(WorldBossTimerLogic.isDue(10, 100));
      assertTrue(WorldBossTimerLogic.isDue(100, 100));
      assertEquals(40, WorldBossTimerLogic.remaining(60, 100));
   }
}
