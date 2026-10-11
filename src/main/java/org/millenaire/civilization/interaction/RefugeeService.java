package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.MigrationResult;
import org.millenaire.civilization.population.MigrationTransactionService;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * On war/occupation, try migrating civilians to OPEN player allies (capped).
 */
public final class RefugeeService {
   private RefugeeService() {
   }

   public static void tick(ServerLevel level, CivilizationSavedData civState) {
      if (level == null) {
         return;
      }
      List<War> wars = new ArrayList<>(WarSavedData.get(level).getWarManager().getAllWars());
      if (wars.isEmpty()) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      int budget = 2;
      for (War war : wars) {
         if (budget <= 0) {
            break;
         }
         if (war == null) {
            continue;
         }
         String warKey = war.getId() == null ? war.toString() : war.getId().asString();
         if (data.getRefugeeAccepted(warKey) >= InteractionConfig.REFUGEE_ACCEPT_CAP_PER_WAR) {
            continue;
         }
         Faction victim = FactionSavedData.get(level).getFactionManager().getFaction(war.getPrimaryTargetFactionId());
         if (victim == null || victim.getType() != FactionType.CULTURE) {
            continue;
         }
         Village origin = pickCrowdedVillage(level, victim);
         if (origin == null) {
            continue;
         }
         Village haven = findOpenAllyHaven(level, war.getPrimaryTargetFactionId());
         if (haven == null) {
            continue;
         }
         UUID migrant = pickCivilian(level, origin, civState);
         if (migrant == null) {
            continue;
         }
         MigrationResult result = MigrationTransactionService.migrateForImmigration(
            level, origin.getId(), haven.getId(), migrant
         );
         if (result.completed()) {
            data.setRefugeeAccepted(warKey, data.getRefugeeAccepted(warKey) + 1);
            data.addTheaterStory(new TheaterStory(
               "refugee",
               "civilization.millenaire.theater.refugee_accepted",
               victim.getId().asString(),
               haven.getFactionId() == null ? "" : haven.getFactionId().asString(),
               2,
               level.getGameTime()
            ));
            budget--;
         }
      }
   }

   private static Village pickCrowdedVillage(ServerLevel level, Faction faction) {
      List<Village> candidates = new ArrayList<>();
      for (var vid : faction.getSettlementIds()) {
         Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
         if (v == null) {
            continue;
         }
         var pop = PopulationAggregator.aggregateVillage(v, level.getGameTime());
         if (pop.residents() > pop.housingCapacity() && pop.housingCapacity() > 0) {
            candidates.add(v);
         }
      }
      if (candidates.isEmpty()) {
         for (var vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v != null) {
               candidates.add(v);
            }
         }
      }
      candidates.sort(Comparator.comparing(v -> v.getId().uuid()));
      return candidates.isEmpty() ? null : candidates.getFirst();
   }

   private static Village findOpenAllyHaven(ServerLevel level, org.millenaire.faction.FactionId victimFaction) {
      List<Village> havens = new ArrayList<>();
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() != FactionType.PLAYER || faction.getImmigrationPolicy() != ImmigrationPolicy.OPEN) {
            continue;
         }
         if (!diplomacy.isAllied(victimFaction, faction.getId()) && !diplomacy.hasTradeAgreement(victimFaction, faction.getId())) {
            continue;
         }
         for (var vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v != null && PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, v)) {
               havens.add(v);
            }
         }
      }
      havens.sort(Comparator.comparing(v -> v.getId().uuid()));
      return havens.isEmpty() ? null : havens.getFirst();
   }

   private static UUID pickCivilian(ServerLevel level, Village origin, CivilizationSavedData state) {
      List<UUID> eligible = new ArrayList<>();
      for (VillagerRecord record : origin.getVillagerRecords().values()) {
         if (!MigrationTransactionService.isEligibleForPermanentMigration(level, origin, record)) {
            continue;
         }
         eligible.add(record.getUuid());
      }
      eligible.sort(Comparator.naturalOrder());
      return eligible.isEmpty() ? null : eligible.getFirst();
   }
}
