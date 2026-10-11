package org.millenaire.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.millenaire.worldboss.spawn.WorldBossSpawnStrategy;

class WorldBossDefinitionSelectionTest {
   @Test
   void weightedPickRespectsWeights() {
      WorldBossDefinition heavy = stub("heavy", 90);
      WorldBossDefinition light = stub("light", 10);
      int heavyCount = 0;
      RandomSource random = RandomSource.create(7L);
      for (int i = 0; i < 300; i++) {
         WorldBossDefinition picked = WorldBossDefinitionRegistry.weightedPick(List.of(heavy, light), random);
         if (picked.id().getPath().equals("heavy")) {
            heavyCount++;
         }
      }
      assertTrue(heavyCount > 200);
   }

   @Test
   void singleDefinitionAlwaysWins() {
      WorldBossDefinition only = stub("only", 1);
      assertEquals(
         only.id(),
         WorldBossDefinitionRegistry.weightedPick(List.of(only), RandomSource.create(1L)).id()
      );
   }

   private static WorldBossDefinition stub(String path, int weight) {
      WorldBossSpawnStrategy noop = (level, player, random) -> java.util.Optional.empty();
      return WorldBossDefinition.builder(ResourceLocation.fromNamespaceAndPath("millenaire", path))
         .entityType(() -> {
            throw new UnsupportedOperationException("stub");
         })
         .weight(weight)
         .spawnStrategy(noop)
         .build();
   }
}
