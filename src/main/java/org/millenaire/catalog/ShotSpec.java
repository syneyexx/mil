package org.millenaire.catalog;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;

public sealed interface ShotSpec permits ShotSpec.VillagerShot, ShotSpec.BuildingShot {
   Path output();

   record BuildingShot(ResourceLocation planSet, String variant, int level, int maxLevel, Path output) implements ShotSpec {
      public boolean isMaxLevel() {
         return this.level == this.maxLevel;
      }
   }

   record VillagerShot(ResourceLocation villagerType, Path output) implements ShotSpec {
   }
}
