package org.millenaire.perf.plan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackgroundWorkGateTest {
   @AfterEach
   void tearDown() {
      BackgroundWorkGate.get().shutdown();
   }

   @Test
   void enforcesTotalCapAndReleases() {
      BackgroundWorkGate gate = BackgroundWorkGate.get();
      gate.onServerStarted(42L, 2, 2);

      assertTrue(gate.tryAcquire(PlanDomain.RELIGION, PlanPriority.SIM, 0L));
      assertTrue(gate.tryAcquire(PlanDomain.WAR, PlanPriority.SIM, 0L));
      assertFalse(gate.tryAcquire(PlanDomain.ECONOMY, PlanPriority.SIM, 0L));

      gate.release(PlanDomain.RELIGION);
      assertTrue(gate.tryAcquire(PlanDomain.ECONOMY, PlanPriority.SIM, 0L));
      gate.release(PlanDomain.WAR);
      gate.release(PlanDomain.ECONOMY);
   }
}
