package org.millenaire.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class WorldBossPlayerSelectionTest {
   @Test
   void prefersLessRecentlyChosenPlayers() {
      UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
      List<WorldBossPlayerSelection.Candidate> candidates = List.of(
         new WorldBossPlayerSelection.Candidate(a, 70),
         new WorldBossPlayerSelection.Candidate(b, 0)
      );
      Map<UUID, Integer> counts = new HashMap<>();
      RandomSource random = RandomSource.create(42L);
      for (int i = 0; i < 200; i++) {
         UUID picked = WorldBossPlayerSelection.select(candidates, random);
         assertNotNull(picked);
         counts.merge(picked, 1, Integer::sum);
      }
      assertTrue(counts.getOrDefault(b, 0) > counts.getOrDefault(a, 0));
   }

   @Test
   void singleCandidateAlwaysSelected() {
      UUID only = UUID.randomUUID();
      UUID picked = WorldBossPlayerSelection.select(
         List.of(new WorldBossPlayerSelection.Candidate(only, 99)),
         RandomSource.create(1L)
      );
      assertEquals(only, picked);
   }

   @Test
   void emptyReturnsNull() {
      assertEquals(null, WorldBossPlayerSelection.select(new ArrayList<>(), RandomSource.create(1L)));
   }
}
