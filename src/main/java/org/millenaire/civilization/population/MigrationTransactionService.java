package org.millenaire.civilization.population;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Transactional resident migration between villages.
 * Single mutation path: selects destination housing, then uses {@link Village#transferVillagerPermanently}.
 * Never force-loads chunks; unloaded entities rehydrate into the destination later.
 */
public final class MigrationTransactionService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private MigrationTransactionService() {
   }

   /**
    * @return operation id when reserved/completed/failed after reservation; null if rejected before reservation
    */
   @Nullable
   public static UUID migrate(ServerLevel level, VillageId originId, VillageId destinationId, UUID villagerRecordId) {
      if (level == null || originId == null || destinationId == null || villagerRecordId == null || originId.equals(destinationId)) {
         return null;
      }

      VillageSavedData data = VillageSavedData.get(level);
      Village origin = data.getVillageManager().getVillage(originId);
      Village destination = data.getVillageManager().getVillage(destinationId);
      if (origin == null || destination == null) {
         return null;
      }
      VillagerRecord record = origin.getVillagerRecords().get(villagerRecordId);
      if (record == null || record.isKilled()) {
         return null;
      }
      if (!isEligibleForPermanentMigration(level, origin, record)) {
         LOGGER.debug("[Millenaire] Migration rejected: villager {} not eligible", villagerRecordId);
         return null;
      }

      PopulationSnapshot destPop = PopulationAggregator.aggregateVillage(destination, level.getGameTime());
      if (destPop.residents() >= destPop.housingCapacity() && destPop.housingCapacity() > 0) {
         LOGGER.debug("[Millenaire] Migration rejected: destination {} at housing capacity", destinationId.uuid());
         return null;
      }
      if (destPop.housingCapacity() <= 0) {
         LOGGER.debug("[Millenaire] Migration rejected: destination {} has no housing", destinationId.uuid());
         return null;
      }

      BuildingId destBuilding = selectDestinationHome(destination, record);
      if (destBuilding == null) {
         LOGGER.debug("[Millenaire] Migration rejected: no free home in {}", destinationId.uuid());
         return null;
      }

      long day = level.getGameTime() / 24000L;
      UUID opId = UUID.nameUUIDFromBytes(
         ("mig:" + villagerRecordId + ":" + originId.uuid() + ":" + destinationId.uuid() + ":" + day).getBytes(StandardCharsets.UTF_8)
      );
      CivilizationSavedData civ = CivilizationSavedData.get(level);
      MigrationOperation prior = civ.getMigration(opId);
      if (prior != null) {
         return opId;
      }

      MigrationOperation op = new MigrationOperation(opId, villagerRecordId, originId.uuid(), destinationId.uuid(), level.getGameTime());
      op.setDestinationBuilding(destBuilding.uuid());
      civ.putMigration(op);

      try {
         // Verify still present before transfer (idempotent if already moved by a prior crash recovery).
         if (!origin.getVillagerRecords().containsKey(villagerRecordId)) {
            if (destination.getVillagerRecords().containsKey(villagerRecordId)) {
               op.trySetStatus(MigrationOperation.Status.COMPLETE);
               civ.putMigration(op);
               return opId;
            }
            op.fail("missing_at_remove");
            civ.putMigration(op);
            return opId;
         }

         origin.transferVillagerPermanently(level, villagerRecordId, destination, destBuilding);
         op.trySetStatus(MigrationOperation.Status.REMOVED_FROM_ORIGIN);
         op.trySetStatus(MigrationOperation.Status.ADDED_TO_DESTINATION);

         VillagerRecord moved = destination.getVillagerRecords().get(villagerRecordId);
         if (moved == null) {
            op.fail("missing_after_transfer");
            op.markNeedsRecovery(true);
            civ.putMigration(op);
            data.setDirty();
            return opId;
         }
         if (origin.getVillagerRecords().containsKey(villagerRecordId)) {
            op.fail("still_in_origin");
            op.markNeedsRecovery(true);
            civ.putMigration(op);
            data.setDirty();
            return opId;
         }

         // Ensure entity affiliation matches record when loaded.
         if (level.getEntity(villagerRecordId) instanceof MillVillager villager) {
            villager.setVillageId(destination.getId());
            villager.setHomeBuilding(destBuilding);
         }

         origin.markDirty();
         destination.markDirty();
         data.setDirty();

         op.trySetStatus(MigrationOperation.Status.COMPLETE);
         civ.putMigration(op);
         CivilizationMetrics.MIGRATIONS.incrementAndGet();

         LivingEventService.emit(
            level,
            LivingEventType.MIGRATION,
            LivingEventSource.SYSTEM,
            destination,
            villagerRecordId,
            null,
            "origin=" + originId.uuid() + " home=" + destBuilding.uuid(),
            "public",
            "mig:" + opId
         );
         return opId;
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Migration failed {}", opId, e);
         op.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
         op.markNeedsRecovery(true);
         civ.putMigration(op);
         data.setDirty();
         return opId;
      }
   }

   /**
    * Eligibility rules from existing gameplay:
    * - permanent residents only (record present, not killed)
    * - not currently hired
    * - not a temporary visitor/merchant tagged as visiting
    * - raiders are excluded when villager type tags indicate raid roles
    */
   public static boolean isEligibleForPermanentMigration(ServerLevel level, Village origin, VillagerRecord record) {
      if (record == null || record.isKilled()) {
         return false;
      }
      if (record.getHiredBy() != null && record.getHiredUntil() > level.getGameTime()) {
         return false;
      }
      if (record.isAwayHired()) {
         return false;
      }
      if (record.getRaiderSpawn() > 0L) {
         return false;
      }
      var type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      if (type != null) {
         String path = type.id() != null ? type.id().getPath() : "";
         if (path.contains("visitor") || path.contains("merchant_foreign") || path.contains("raid")) {
            return false;
         }
      }
      return true;
   }

   @Nullable
   public static BuildingId selectDestinationHome(Village destination, VillagerRecord record) {
      Map<UUID, VillagerRecord> residents = destination.getVillagerRecords();
      List<BuildingInstance> homes = new ArrayList<>();
      for (BuildingInstance building : destination.getBuildings()) {
         if (!building.isOperational()) {
            continue;
         }
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet == null || !planSet.isResidential()) {
            continue;
         }
         int capacity = planSet.maleResidentsAt(building.getVariant(), building.getLevel()).size()
            + planSet.femaleResidentsAt(building.getVariant(), building.getLevel()).size();
         if (capacity <= 0) {
            continue;
         }
         int occupied = 0;
         for (VillagerRecord r : residents.values()) {
            if (r != null && !r.isKilled() && building.getId().equals(r.getHomeBuilding())) {
               occupied++;
            }
         }
         if (occupied < capacity) {
            homes.add(building);
         }
      }
      if (homes.isEmpty()) {
         return null;
      }
      homes.sort(Comparator.comparing(b -> b.getId().uuid()));
      return homes.getFirst().getId();
   }
}
