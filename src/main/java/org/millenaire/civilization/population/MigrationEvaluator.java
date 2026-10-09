package org.millenaire.civilization.population;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Bounded migration evaluator: moves residents under housing pressure to villages with spare homes.
 * Does not shuffle randomly. AI dialogue may describe outcomes; it never authorizes migrations.
 */
public final class MigrationEvaluator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private MigrationEvaluator() {
   }

   public static void evaluate(ServerLevel level, CivilizationSavedData state) {
      int maxOps = Math.min(
         CivilizationConfig.MAX_MIGRATIONS_PER_CYCLE,
         MillenaireServerConfig.SERVER.civilizationMigrationBudget.getAsInt()
      );
      if (maxOps <= 0) {
         return;
      }
      int maxScans = Math.min(CivilizationConfig.MAX_MIGRATION_SCANS_PER_CYCLE, maxOps * 16);
      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      if (villages.isEmpty()) {
         return;
      }
      villages.sort(Comparator.comparing(v -> v.getId().uuid()));
      int n = villages.size();
      int start = Math.floorMod(state.migrationCursor(), n);
      int scanned = 0;
      int ops = 0;

      for (int i = 0; i < n && scanned < maxScans && ops < maxOps; i++) {
         scanned++;
         Village origin = villages.get((start + i) % n);
         PopulationSnapshot originPop = PopulationAggregator.aggregateVillage(origin, level.getGameTime());
         if (originPop.housingCapacity() <= 0) {
            continue;
         }
         if (originPop.residents() <= originPop.housingCapacity()) {
            continue;
         }
         // Overcrowded — find same-faction destination with spare housing.
         Village destination = findDestination(level, origin);
         if (destination == null) {
            continue;
         }
         UUID migrant = selectMigrant(level, origin);
         if (migrant == null) {
            continue;
         }
         // Cooldown: same villager cannot bounce every cycle.
         String cdKey = "mig-cd:" + migrant;
         if (!state.tryMarkPublished(cdKey, level.getGameTime(), 24000L * 3L)) {
            continue;
         }
         UUID op = MigrationTransactionService.migrate(level, origin.getId(), destination.getId(), migrant);
         if (op != null) {
            ops++;
            state.updateStateMarker(
               "mig-reason:" + op,
               "housing_pressure:" + origin.getId().uuid() + "->" + destination.getId().uuid()
            );
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationMetricsLogging.get())) {
               LOGGER.debug(
                  "[Millenaire] Migration {} overcrowding {} -> {}",
                  migrant,
                  origin.getId().uuid(),
                  destination.getId().uuid()
               );
            }
         }
      }
      state.setMigrationCursor(start + Math.max(1, scanned));
   }

   private static Village findDestination(ServerLevel level, Village origin) {
      FactionIdHolder faction = factionOf(origin);
      List<Village> candidates = new ArrayList<>();
      for (Village other : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (other.getId().equals(origin.getId())) {
            continue;
         }
         if (faction != null) {
            FactionIdHolder otherFaction = factionOf(other);
            if (otherFaction == null || !otherFaction.id().equals(faction.id())) {
               continue;
            }
         }
         PopulationSnapshot pop = PopulationAggregator.aggregateVillage(other, level.getGameTime());
         if (pop.residents() < pop.housingCapacity()) {
            candidates.add(other);
         }
      }
      if (candidates.isEmpty()) {
         return null;
      }
      candidates.sort(Comparator
         .comparingLong((Village v) -> distSq(origin, v))
         .thenComparing(v -> v.getId().uuid()));
      return candidates.getFirst();
   }

   private static UUID selectMigrant(ServerLevel level, Village origin) {
      List<UUID> eligible = new ArrayList<>();
      for (VillagerRecord record : origin.getVillagerRecords().values()) {
         if (MigrationTransactionService.isEligibleForPermanentMigration(level, origin, record)) {
            eligible.add(record.getUuid());
         }
      }
      if (eligible.isEmpty()) {
         return null;
      }
      eligible.sort(Comparator.naturalOrder());
      return eligible.getFirst();
   }

   private static long distSq(Village a, Village b) {
      long dx = (long)a.getCenter().getX() - b.getCenter().getX();
      long dz = (long)a.getCenter().getZ() - b.getCenter().getZ();
      return dx * dx + dz * dz;
   }

   private static FactionIdHolder factionOf(Village village) {
      if (village.getFactionId() == null) {
         return null;
      }
      return new FactionIdHolder(village.getFactionId().asString());
   }

   private record FactionIdHolder(String id) {
   }
}
