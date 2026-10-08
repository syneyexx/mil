package org.millenaire.catalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.VillagerType;

public final class CatalogSubjectEnumerator {
   private CatalogSubjectEnumerator() {
   }

   public static List<ShotSpec> enumerate(
      Map<ResourceLocation, VillagerType> villagerTypes, Map<ResourceLocation, BuildingPlanSet> planSets, CatalogJob job, Path root
   ) {
      List<ShotSpec> shots = new ArrayList<>();
      int limit = job.limit();
      if (job.target().includesVillagers()) {
         int count = 0;

         for (VillagerType vt : villagerTypes.values()) {
            String culture = vt.culture().getPath();
            if (job.includesCulture(culture) && job.includesVillager(vt.id())) {
               if (limit > 0 && count >= limit) {
                  break;
               }

               count++;
               shots.add(new ShotSpec.VillagerShot(vt.id(), CatalogPaths.villagerOutput(root, culture, vt.id())));
            }
         }
      }

      if (job.target().includesBuildings()) {
         int count = 0;

         for (BuildingPlanSet ps : planSets.values()) {
            String culture = ps.culture().getPath();
            if (job.includesCulture(culture)
               && !ps.isSubBuilding()
               && !ps.isWallSegment()
               && !ps.isBorderBuilding()
               && !ps.tags().contains("worldquest")
               && job.includesBuilding(ps.id())) {
               if (limit > 0 && count >= limit) {
                  break;
               }

               count++;

               for (Entry<String, List<BuildingPlanSet.LevelDef>> entry : ps.variants().entrySet()) {
                  String variant = entry.getKey();
                  int maxLevel = entry.getValue().size() - 1;
                  if (maxLevel >= 0) {
                     Set<Integer> levels = new LinkedHashSet<>();
                     levels.add(maxLevel);

                     for (int lvl = 0; lvl <= maxLevel; lvl++) {
                        levels.add(lvl);
                     }

                     for (int level : levels) {
                        shots.add(
                           new ShotSpec.BuildingShot(ps.id(), variant, level, maxLevel, CatalogPaths.buildingOutput(root, culture, ps.id(), variant, level))
                        );
                     }
                  }
               }
            }
         }
      }

      return shots;
   }
}
