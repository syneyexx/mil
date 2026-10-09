package org.millenaire.civilization.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

/**
 * Labels settlement development from real buildings/population. Does not spawn buildings.
 */
public final class SettlementDevelopmentClassifier {
   private SettlementDevelopmentClassifier() {
   }

   public static void refresh(ServerLevel level, CivilizationSavedData state) {
      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      villages.sort(Comparator.comparing(v -> v.getId().uuid()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE * 4, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt() * 4);
      int n = 0;
      for (Village village : villages) {
         if (n++ >= budget) {
            break;
         }
         String label = classify(village, level.getGameTime());
         String previous = state.getDevelopmentLabel(village.getId().uuid());
         state.putDevelopmentLabel(village.getId().uuid(), label);
         if (previous != null && !previous.equals(label) && state.consumeEventBudget()) {
            String key = "dev:" + village.getId().uuid() + ":" + label;
            if (state.tryMarkPublished(key, level.getGameTime(), 24000L * 3L)) {
               LivingEventService.emit(
                  level,
                  LivingEventType.SETTLEMENT_DEVELOPMENT,
                  LivingEventSource.SYSTEM,
                  village,
                  null,
                  null,
                  "from=" + previous + " to=" + label,
                  "public",
                  key
               );
            }
         }
      }
   }

   public static String classify(Village village, long gameTime) {
      PopulationSnapshot pop = PopulationAggregator.aggregateVillage(village, gameTime);
      int buildings = village.getBuildings().size();
      boolean hamlet = false;
      try {
         var type = org.millenaire.culture.ModCultures.getVillageType(village.getVillageTypeId());
         hamlet = type != null && type.isHamlet();
      } catch (Exception ignored) {
      }
      if (hamlet || buildings <= 3 && pop.residents() <= 8) {
         return "hamlet";
      }
      if (buildings >= 18 || pop.residents() >= 40) {
         return "regional_hub";
      }
      if (buildings >= 10 || pop.residents() >= 20) {
         return "town";
      }
      return "village";
   }
}
