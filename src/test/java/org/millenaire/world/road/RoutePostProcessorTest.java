package org.millenaire.world.road;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoutePostProcessorTest {
   @Test
   void douglasPeuckerReducesColinearPoints() {
      int[] path = new int[]{
         0, 64, 0,
         10, 64, 0,
         20, 64, 0,
         30, 64, 0,
         40, 64, 0
      };
      int[] simplified = RoutePostProcessor.douglasPeucker(path, 1.0);
      assertTrue(simplified.length / 3 <= 3, "expected endpoints (+maybe one), got " + (simplified.length / 3));
      assertTrue(simplified[0] == 0 && simplified[simplified.length - 3] == 40);
   }

   @Test
   void chaikinKeepsEndpoints() {
      int[] path = new int[]{0, 64, 0, 40, 64, 40};
      int[] smooth = RoutePostProcessor.chaikin(path, 1, 12345L);
      assertTrue(smooth[0] == 0 && smooth[2] == 0);
      assertTrue(smooth[smooth.length - 3] == 40 && smooth[smooth.length - 1] == 40);
   }
}
