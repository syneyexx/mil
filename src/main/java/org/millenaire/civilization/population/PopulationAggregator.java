package org.millenaire.civilization.population;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * Derives population summaries from authoritative VillagerRecord maps. Never invents phantom residents.
 */
public final class PopulationAggregator {
   private PopulationAggregator() {
   }

   public static void refreshDirtyFactions(ServerLevel level, CivilizationSavedData state) {
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      if (factions.isEmpty()) {
         return;
      }
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt());
      int cursor = state.membershipCursor() % factions.size();
      VillageSavedData villages = VillageSavedData.get(level);
      for (int i = 0; i < budget && i < factions.size(); i++) {
         Faction faction = factions.get((cursor + i) % factions.size());
         PopulationSnapshot snap = aggregateFaction(villages, faction, level.getGameTime());
         state.putPopulationSnapshot(faction.getId().asString(), snap.save());
      }
   }

   public static PopulationSnapshot aggregateFaction(VillageSavedData villages, Faction faction, long gameTime) {
      int residents = 0;
      int adults = 0;
      int children = 0;
      int workers = 0;
      int housing = 0;
      int military = 0;
      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = villages.getVillageManager().getVillage(settlementId);
         if (village == null) {
            continue;
         }
         PopulationSnapshot local = aggregateVillage(village, gameTime);
         residents += local.residents();
         adults += local.adults();
         children += local.children();
         workers += local.workers();
         housing += local.housingCapacity();
         military += local.militaryEligible();
      }
      return new PopulationSnapshot(residents, adults, children, housing, workers, military, gameTime);
   }

   public static PopulationSnapshot aggregateVillage(Village village, long gameTime) {
      int residents = 0;
      int adults = 0;
      int children = 0;
      int workers = 0;
      int military = 0;
      Map<UUID, VillagerRecord> records = village.getVillagerRecords();
      for (VillagerRecord record : records.values()) {
         if (record == null || record.isKilled()) {
            continue;
         }
         residents++;
         VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
         boolean child = type != null && type.isChild();
         if (child) {
            children++;
         } else {
            adults++;
            workers++;
            if (type == null || type.gender() != Gender.FEMALE || type.maxHealth() > 10.0F) {
               military++;
            }
         }
      }
      int housing = Math.max(residents, Math.max(1, village.getBuildings().size()) * 2);
      return new PopulationSnapshot(residents, adults, children, housing, workers, military, gameTime);
   }
}
