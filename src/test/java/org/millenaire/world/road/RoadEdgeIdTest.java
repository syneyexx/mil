package org.millenaire.world.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class RoadEdgeIdTest {
   @Test
   void stableIdIsOrderIndependent() {
      long ab = RoadEdge.stableId("a", "b");
      long ba = RoadEdge.stableId("b", "a");
      assertEquals(ab, ba);
   }

   @Test
   void differentPairsDiffer() {
      assertNotEquals(RoadEdge.stableId("a", "b"), RoadEdge.stableId("a", "c"));
   }
}
