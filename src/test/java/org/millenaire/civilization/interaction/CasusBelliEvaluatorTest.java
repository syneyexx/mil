package org.millenaire.civilization.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CasusBelliEvaluatorTest {
   @Test
   void bountyUnpaidGivesJustification() {
      Map<CasusBelliType, Integer> scores = CasusBelliEvaluator.evaluatePure(
         true, false, 0, 0, false, false
      );
      assertEquals(40, scores.get(CasusBelliType.BOUNTY_UNPAID));
      assertTrue(CasusBelliEvaluator.totalStrength(scores) >= 40);
   }

   @Test
   void corridorDisruptionAndClaimHeatStack() {
      Map<CasusBelliType, Integer> scores = CasusBelliEvaluator.evaluatePure(
         false, true, 80, -50, true, false
      );
      assertTrue(scores.get(CasusBelliType.CORRIDOR_DISRUPTION) > 0);
      assertTrue(scores.get(CasusBelliType.CLAIM_HEAT) > 0);
      assertTrue(scores.get(CasusBelliType.BROKEN_TREATY) > 0);
      assertTrue(CasusBelliEvaluator.totalStrength(scores) > 50);
   }
}
