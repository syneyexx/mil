package org.millenaire.perf.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure cadence math mirroring {@link VillagerAiCadence} stagger without a live level.
 */
class VillagerAiCadenceLogicTest {
   @Test
   void staggerCoversAllOffsets() {
      int interval = 3;
      int[] hits = new int[interval];
      for (long gameTime = 0; gameTime < 30; gameTime++) {
         for (int entityId = 0; entityId < interval; entityId++) {
            int stagger = Math.floorMod(entityId, interval);
            if (Math.floorMod(gameTime, interval) == stagger) {
               hits[entityId]++;
            }
         }
      }
      for (int h : hits) {
         assertEquals(10, h);
      }
   }

   @Test
   void defaultDistancesAreSane() {
      assertTrue(VillagerAiCadence.midDistance() >= 16);
      assertTrue(VillagerAiCadence.midInterval() >= 1);
   }
}
