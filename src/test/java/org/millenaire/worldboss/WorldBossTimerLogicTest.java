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

   @Test
   void dayIndexFromDayTime() {
      assertEquals(0L, WorldBossTimerLogic.dayIndex(0L));
      assertEquals(0L, WorldBossTimerLogic.dayIndex(23999L));
      assertEquals(1L, WorldBossTimerLogic.dayIndex(24000L));
      assertEquals(5L, WorldBossTimerLogic.dayIndex(5L * 24000L + 100L));
   }

   @Test
   void dayDueAndRemaining() {
      assertFalse(WorldBossTimerLogic.isDayDue(3L, 5L));
      assertTrue(WorldBossTimerLogic.isDayDue(5L, 5L));
      assertTrue(WorldBossTimerLogic.isDayDue(8L, 5L));
      assertEquals(2L, WorldBossTimerLogic.daysRemaining(3L, 5L));
      assertEquals(0L, WorldBossTimerLogic.daysRemaining(5L, 5L));
   }

   @Test
   void nextDueDayAddsInterval() {
      assertEquals(8L, WorldBossTimerLogic.nextDueDay(3L, 5));
      assertEquals(4L, WorldBossTimerLogic.nextDueDay(3L, 1));
   }

   @Test
   void migrateNearlyDueBecomesToday() {
      assertEquals(10L, WorldBossTimerLogic.migrateTickTimerToDueDay(10L, 95, 100, 5));
   }

   @Test
   void migrateEarlyProgressSchedulesIntervalOut() {
      assertEquals(15L, WorldBossTimerLogic.migrateTickTimerToDueDay(10L, 10, 100, 5));
   }
}
