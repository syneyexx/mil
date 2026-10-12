package org.millenaire.perf.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;

class ServerTickGovernorTest {
   @AfterEach
   void tearDown() {
      ServerTickGovernor.resetForTest();
   }

   @Test
   void diagnosticIncludesPhasesAndLod() {
      ServerTickGovernor.beginTick(1L);
      ServerTickGovernor.enter(TickPhase.VILLAGE_NEAR);
      ServerTickGovernor.noteLod(VillageSimulationLod.NEAR);
      ServerTickGovernor.noteLod(VillageSimulationLod.MID);
      ServerTickGovernor.noteLod(VillageSimulationLod.FAR);
      ServerTickGovernor.noteDefer(TickPhase.VILLAGE_MID);
      ServerTickGovernor.noteAiCadenceSkipped();
      ServerTickGovernor.noteActiveVillage();
      ServerTickGovernor.noteDataOnlyVillage();
      ServerTickGovernor.noteBackgroundTickSkipped();
      ServerTickGovernor.endTick();

      String diag = ServerTickGovernor.diagnostic();
      assertTrue(diag.contains("lod={near=1,mid=1,far=1"), diag);
      assertTrue(diag.contains("aiSkip=1"), diag);
      assertTrue(diag.contains("village_near="), diag);
      assertTrue(diag.contains("village_mid="), diag);
      assertTrue(diag.contains("villages={active=1,dataOnly=1"), diag);
      assertTrue(diag.contains("pathQ="), diag);
      assertTrue(diag.contains("globalMs="), diag);
      assertTrue(diag.contains("effMs="), diag);
      assertTrue(diag.contains("pressure="), diag);
      assertTrue(diag.contains("/ema"), diag);
      assertTrue(diag.contains("entityLod="), diag);
      assertTrue(diag.contains("entityAiMs="), diag);
   }

   @Test
   void deferCountsAccumulate() {
      ServerTickGovernor.beginTick(2L);
      ServerTickGovernor.noteDefer(TickPhase.WORLDGEN);
      ServerTickGovernor.noteDefer(TickPhase.WORLDGEN);
      String diag = ServerTickGovernor.diagnostic();
      assertTrue(diag.contains("worldgen=") && diag.contains("/d2"), diag);
   }

   @Test
   void entityAiAttributionSnapshotsAcrossBeginTick() {
      ServerTickGovernor.noteEntityLod(EntitySimulationLod.NEAR);
      ServerTickGovernor.noteEntityLod(EntitySimulationLod.FAR);
      ServerTickGovernor.noteEntityFarSleep();
      ServerTickGovernor.noteEntityAiNanos(2_500_000L);
      ServerTickGovernor.beginTick(10L);

      assertEquals(1, ServerTickGovernor.entityNearReported());
      assertTrue(ServerTickGovernor.entityAiNanosReported() >= 2_500_000L);
      String diag = ServerTickGovernor.diagnostic();
      assertTrue(diag.contains("entityLod={near=1"), diag);
      assertTrue(diag.contains("entityAiMs=2") || diag.contains("entityAiMs=3"), diag);
   }

   @Test
   void entityGoalPickBudgetDefersNonUrgent() {
      long gameTime = 42L;
      int budget = ServerTickGovernor.entityGoalPickBudgetPerTick();
      for (int i = 0; i < budget; i++) {
         assertTrue(ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, false));
      }
      assertFalse(ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, false));
      assertTrue(ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, true), "urgent always allowed");
   }

   @Test
   void reservedGoalPickSliceFavoursStarvedVillagers() {
      long gameTime = 77L;
      int budget = ServerTickGovernor.entityGoalPickBudgetPerTick();
      int reserve = budget / 4;
      int fresh = budget - reserve;
      for (int i = 0; i < fresh; i++) {
         assertTrue(ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, false, 0));
      }
      if (reserve > 0) {
         assertFalse(ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, false, 0), "fresh villager cannot use reserve");
         assertTrue(
            ServerTickGovernor.tryConsumeEntityGoalPick(gameTime, false, ServerTickGovernor.STARVED_GOAL_PICK_STREAK),
            "starved villager uses reserved slice"
         );
      }
   }

   @Test
   void adaptiveBudgetShrinksWhenPreTickAlreadyHot() {
      long now = System.nanoTime();
      // Pretend ~45 ms already elapsed before Post.
      ServerTickGovernor.setServerTickStartForTest(now - 45_000_000L);
      ServerTickGovernor.beginTick(100L);
      assertTrue(
         ServerTickGovernor.effectiveGlobalBudgetMs() <= ServerTickGovernor.globalBudgetMs(),
         "effective=" + ServerTickGovernor.effectiveGlobalBudgetMs()
      );
      assertTrue(ServerTickGovernor.effectiveGlobalBudgetMs() <= 8, "effective=" + ServerTickGovernor.effectiveGlobalBudgetMs());
   }

   @Test
   void pressureEscalatesAfterSustainedOverspend() {
      // Drive HARD via endTick totals with a fake pre-spend.
      for (int i = 0; i < 3; i++) {
         long t = 200L + i;
         long start = System.nanoTime();
         ServerTickGovernor.setServerTickStartForTest(start - 49_000_000L);
         ServerTickGovernor.beginTick(t);
         // Spin a little so postSpent is non-trivial.
         long spinUntil = System.nanoTime() + 2_000_000L;
         while (System.nanoTime() < spinUntil) {
            // busy wait
         }
         ServerTickGovernor.endTick();
      }
      ServerTickGovernor.beginTick(300L);
      assertTrue(
         ServerTickGovernor.pressure() == TickPressure.SOFT || ServerTickGovernor.pressure() == TickPressure.HARD,
         "pressure=" + ServerTickGovernor.pressure()
      );
   }

   @Test
   void entityAdmissionOpensAtPreAndClosesAtPostEnd() {
      assertFalse(ServerTickGovernor.hasEntityAdmissionTime(), "no Pre seen yet");
      ServerTickGovernor.markServerTickStart();
      assertEquals(1L, ServerTickGovernor.entityTickId());
      assertTrue(ServerTickGovernor.hasEntityAdmissionTime());
      ServerTickGovernor.beginTick(400L);
      ServerTickGovernor.endTick();
      assertFalse(ServerTickGovernor.hasEntityAdmissionTime(), "window closed after Post");
      ServerTickGovernor.markServerTickStart();
      assertEquals(2L, ServerTickGovernor.entityTickId());
      assertTrue(ServerTickGovernor.hasEntityAdmissionTime());
   }

   @Test
   void entityAdmissionIgnoresPreviousPostDeadline() {
      ServerTickGovernor.beginTick(410L);
      // Post deadlines from this tick are long gone by the time the next entity loop runs.
      long spinUntil = System.nanoTime() + 14_000_000L;
      while (System.nanoTime() < spinUntil) {
         // busy wait
      }
      assertFalse(ServerTickGovernor.hasTime());
      ServerTickGovernor.endTick();
      ServerTickGovernor.markServerTickStart();
      assertTrue(ServerTickGovernor.hasEntityAdmissionTime());
   }

   @Test
   void captureBudgetExhaustsAndCounts() {
      ServerTickGovernor.markServerTickStart();
      assertTrue(ServerTickGovernor.tryConsumeCaptureBudget(0L));
      assertTrue(ServerTickGovernor.tryConsumeCaptureBudget(Long.MAX_VALUE / 4));
      assertFalse(ServerTickGovernor.tryConsumeCaptureBudget(1L));
      assertTrue(ServerTickGovernor.diagnostic().contains("capEx=1"), ServerTickGovernor.diagnostic());
      ServerTickGovernor.markServerTickStart();
      assertTrue(ServerTickGovernor.tryConsumeCaptureBudget(1L), "budget refills at next Pre");
   }

   @Test
   void entityCountersAppearInDiagnostic() {
      ServerTickGovernor.noteEntityAsyncAccepted();
      ServerTickGovernor.noteEntityAsyncRejectedOldDeadline();
      ServerTickGovernor.noteEntityAsyncQueueFull();
      ServerTickGovernor.noteEntitySyncFallback();
      String diag = ServerTickGovernor.diagnostic();
      assertTrue(diag.contains("async=1,oldDl=1,qFull=1,capEx=0,syncFb=1"), diag);
   }

   @Test
   void adaptiveFloorDropsToRecoveryWhenIdealTickSpent() {
      assertEquals(2, ServerTickGovernor.computeEffectiveGlobalMsForTest(12, 60_000_000L, TickPressure.HEALTHY));
      assertEquals(2, ServerTickGovernor.computeEffectiveGlobalMsForTest(12, 50_000_000L, TickPressure.HARD));
      assertTrue(ServerTickGovernor.computeEffectiveGlobalMsForTest(12, 45_000_000L, TickPressure.HEALTHY) >= 6);
      assertEquals(12, ServerTickGovernor.computeEffectiveGlobalMsForTest(12, 0L, TickPressure.HEALTHY));
   }

   @Test
   void worldgenAndHighwaySetHierarchicalMaterializationDeadline() {
      ServerTickGovernor.beginTick(500L);
      ServerTickGovernor.enter(TickPhase.WORLDGEN);
      long afterWorldgenEnter = System.nanoTime();
      long worldgenDeadline = MaterializationTickBudget.deadlineNanos();
      assertTrue(worldgenDeadline <= afterWorldgenEnter + 2_000_000L, "worldgen quota caps materialization");

      ServerTickGovernor.enter(TickPhase.HIGHWAY);
      long afterHighwayEnter = System.nanoTime();
      long highwayDeadline = MaterializationTickBudget.deadlineNanos();
      assertTrue(highwayDeadline >= worldgenDeadline, "unused worldgen slice carries to highway");
      assertTrue(highwayDeadline <= afterHighwayEnter + 3_000_000L);

      ServerTickGovernor.enter(TickPhase.BOSS_QUEST_MAP);
      assertTrue(MaterializationTickBudget.deadlineNanos() >= highwayDeadline, "global deadline restored after highway");
   }
}
