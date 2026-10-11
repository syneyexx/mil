package org.millenaire.perf.tick;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
   }

   @Test
   void deferCountsAccumulate() {
      ServerTickGovernor.beginTick(2L);
      ServerTickGovernor.noteDefer(TickPhase.WORLDGEN);
      ServerTickGovernor.noteDefer(TickPhase.WORLDGEN);
      String diag = ServerTickGovernor.diagnostic();
      assertTrue(diag.contains("worldgen=") && diag.contains("/d2"), diag);
   }
}
