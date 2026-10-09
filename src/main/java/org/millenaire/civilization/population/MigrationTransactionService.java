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
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.faction.FactionId;
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
 * Success is an explicit {@link MigrationResult} property — never inferred from a non-null operation id.
 */
public final class MigrationTransactionService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_RECOVERY_PER_TICK = 4;

   private MigrationTransactionService() {
   }

   public static MigrationResult migrate(ServerLevel level, VillageId originId, VillageId destinationId, UUID villagerRecordId) {
      if (level == null || originId == null || destinationId == null || villagerRecordId == null || originId.equals(destinationId)) {
         return MigrationResult.rejected("invalid_args");
      }

      VillageSavedData data = VillageSavedData.get(level);
      Village origin = data.getVillageManager().getVillage(originId);
      Village destination = data.getVillageManager().getVillage(destinationId);
      if (origin == null || destination == null) {
         return MigrationResult.rejected("village_missing", originId.uuid(), destinationId == null ? null : destinationId.uuid());
      }
      VillagerRecord record = origin.getVillagerRecords().get(villagerRecordId);
      if (record == null || record.isKilled()) {
         // Idempotent: already at destination counts as already complete.
         if (destination.getVillagerRecords().containsKey(villagerRecordId)) {
            BuildingId home = destination.getVillagerRecords().get(villagerRecordId).getHomeBuilding();
            return MigrationResult.of(
               null,
               MigrationResult.Outcome.ALREADY_COMPLETE,
               true,
               false,
               "already_at_destination",
               originId.uuid(),
               destinationId.uuid(),
               home == null ? null : home.uuid()
            );
         }
         return MigrationResult.rejected("record_missing", originId.uuid(), destinationId.uuid());
      }
      if (!isEligibleForPermanentMigration(level, origin, record)) {
         return MigrationResult.rejected("not_eligible", originId.uuid(), destinationId.uuid());
      }

      if (!factionAllowsMigration(origin, destination)) {
         return MigrationResult.rejected("faction_blocked", originId.uuid(), destinationId.uuid());
      }

      PopulationSnapshot destPop = PopulationAggregator.aggregateVillage(destination, level.getGameTime());
      if (destPop.residents() >= destPop.housingCapacity() && destPop.housingCapacity() > 0) {
         return MigrationResult.rejected("destination_full", originId.uuid(), destinationId.uuid());
      }
      if (destPop.housingCapacity() <= 0) {
         return MigrationResult.rejected("no_housing", originId.uuid(), destinationId.uuid());
      }

      BuildingId destBuilding = selectDestinationHome(destination, record);
      if (destBuilding == null) {
         return MigrationResult.rejected("no_matching_home", originId.uuid(), destinationId.uuid());
      }

      long day = level.getGameTime() / 24000L;
      UUID opId = UUID.nameUUIDFromBytes(
         ("mig:" + villagerRecordId + ":" + originId.uuid() + ":" + destinationId.uuid() + ":" + day).getBytes(StandardCharsets.UTF_8)
      );
      CivilizationSavedData civ = CivilizationSavedData.get(level);
      MigrationOperation prior = civ.getMigration(opId);
      if (prior != null) {
         if (prior.status() == MigrationOperation.Status.COMPLETE) {
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.ALREADY_COMPLETE,
               true,
               false,
               "already_complete",
               originId.uuid(),
               destinationId.uuid(),
               prior.destinationBuildingId()
            );
         }
         if (prior.needsRecovery()) {
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.RECOVERY_REQUIRED,
               false,
               true,
               "needs_recovery",
               originId.uuid(),
               destinationId.uuid(),
               prior.destinationBuildingId()
            );
         }
         if (prior.status() == MigrationOperation.Status.FAILED || prior.status() == MigrationOperation.Status.ROLLED_BACK) {
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.FAILED,
               false,
               true,
               prior.failureReason().isEmpty() ? "prior_failed" : prior.failureReason(),
               originId.uuid(),
               destinationId.uuid(),
               prior.destinationBuildingId()
            );
         }
         return MigrationResult.of(
            opId,
            MigrationResult.Outcome.PENDING,
            false,
            true,
            "prior_pending",
            originId.uuid(),
            destinationId.uuid(),
            prior.destinationBuildingId()
         );
      }

      MigrationOperation op = new MigrationOperation(opId, villagerRecordId, originId.uuid(), destinationId.uuid(), level.getGameTime());
      op.setDestinationBuilding(destBuilding.uuid());
      civ.putMigration(op);

      try {
         if (!origin.getVillagerRecords().containsKey(villagerRecordId)) {
            if (destination.getVillagerRecords().containsKey(villagerRecordId)) {
               op.trySetStatus(MigrationOperation.Status.COMPLETE);
               civ.putMigration(op);
               return MigrationResult.of(
                  opId,
                  MigrationResult.Outcome.ALREADY_COMPLETE,
                  true,
                  false,
                  "already_moved",
                  originId.uuid(),
                  destinationId.uuid(),
                  destBuilding.uuid()
               );
            }
            op.fail("missing_at_remove");
            civ.putMigration(op);
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.FAILED,
               false,
               false,
               "missing_at_remove",
               originId.uuid(),
               destinationId.uuid(),
               destBuilding.uuid()
            );
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
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.RECOVERY_REQUIRED,
               false,
               true,
               "missing_after_transfer",
               originId.uuid(),
               destinationId.uuid(),
               destBuilding.uuid()
            );
         }
         if (origin.getVillagerRecords().containsKey(villagerRecordId)) {
            op.fail("still_in_origin");
            op.markNeedsRecovery(true);
            civ.putMigration(op);
            data.setDirty();
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.RECOVERY_REQUIRED,
               false,
               true,
               "still_in_origin",
               originId.uuid(),
               destinationId.uuid(),
               destBuilding.uuid()
            );
         }

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
         return MigrationResult.of(
            opId,
            MigrationResult.Outcome.COMPLETE,
            true,
            false,
            "complete",
            originId.uuid(),
            destinationId.uuid(),
            destBuilding.uuid()
         );
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Migration failed {}", opId, e);
         op.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
         op.markNeedsRecovery(true);
         civ.putMigration(op);
         data.setDirty();
         return MigrationResult.of(
            opId,
            MigrationResult.Outcome.RECOVERY_REQUIRED,
            false,
            true,
            "exception",
            originId.uuid(),
            destinationId.uuid(),
            destBuilding.uuid()
         );
      }
   }

   /**
    * Bounded recovery for half-finished migration operations.
    */
   public static int recoverPending(ServerLevel level, CivilizationSavedData civ) {
      if (level == null || civ == null) {
         return 0;
      }
      VillageSavedData data = VillageSavedData.get(level);
      int fixed = 0;
      for (MigrationOperation op : List.copyOf(civ.migrationOpsView())) {
         if (fixed >= MAX_RECOVERY_PER_TICK) {
            break;
         }
         if (!op.needsRecovery() && op.status() != MigrationOperation.Status.REMOVED_FROM_ORIGIN
            && op.status() != MigrationOperation.Status.ADDED_TO_DESTINATION) {
            continue;
         }
         Village origin = data.getVillageManager().getVillage(new VillageId(op.originVillageId()));
         Village destination = data.getVillageManager().getVillage(new VillageId(op.destinationVillageId()));
         UUID villagerId = op.villagerId();
         boolean inOrigin = origin != null && origin.getVillagerRecords().containsKey(villagerId);
         boolean inDest = destination != null && destination.getVillagerRecords().containsKey(villagerId);

         if (inDest && !inOrigin) {
            op.trySetStatus(MigrationOperation.Status.COMPLETE);
            op.markNeedsRecovery(false);
            civ.putMigration(op);
            fixed++;
            continue;
         }
         if (inOrigin && !inDest) {
            // Transfer never finished — clear recovery and mark failed so a new day op can retry.
            op.fail("recovery_still_in_origin");
            op.markNeedsRecovery(false);
            civ.putMigration(op);
            fixed++;
            continue;
         }
         if (inOrigin && inDest) {
            // Duplicate — authoritative location is destination; remove origin copy.
            origin.getVillagerRecords().remove(villagerId);
            origin.markDirty();
            data.setDirty();
            op.trySetStatus(MigrationOperation.Status.COMPLETE);
            op.markNeedsRecovery(false);
            civ.putMigration(op);
            fixed++;
            continue;
         }
         op.fail("recovery_unresolvable");
         op.markNeedsRecovery(false);
         civ.putMigration(op);
         fixed++;
      }
      return fixed;
   }

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
      if (record.isAwayRaiding() || record.isRaidingVillage()) {
         return false;
      }
      if (record.getRaiderSpawn() > 0L) {
         return false;
      }
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      if (type != null) {
         String path = type.id() != null ? type.id().getPath() : "";
         if (path.contains("visitor") || path.contains("merchant_foreign") || path.contains("raid")) {
            return false;
         }
         // Children are not independent migrants — keep family households intact.
         if (type.isChild()) {
            return false;
         }
      }
      return true;
   }

   /**
    * Factionless origin may only migrate to another factionless destination.
    * Same-faction required otherwise. War is checked by callers that choose destinations.
    */
   public static boolean factionAllowsMigration(Village origin, Village destination) {
      FactionId o = origin.getFactionId();
      FactionId d = destination.getFactionId();
      if (o == null && d == null) {
         return true;
      }
      if (o == null || d == null) {
         return false;
      }
      return o.equals(d);
   }

   @Nullable
   public static BuildingId selectDestinationHome(Village destination, VillagerRecord record) {
      Map<UUID, VillagerRecord> residents = destination.getVillagerRecords();
      VillagerType type = record != null && record.getVillagerTypeId() != null
         ? ModCultures.getVillagerType(record.getVillagerTypeId())
         : null;
      Gender gender = type != null ? type.gender() : null;
      String typePath = type != null && type.id() != null ? type.id().getPath() : null;

      List<BuildingInstance> homes = new ArrayList<>();
      for (BuildingInstance building : destination.getBuildings()) {
         if (!building.isOperational()) {
            continue;
         }
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet == null || !planSet.isResidential()) {
            continue;
         }
         List<String> maleSlots = planSet.maleResidentsAt(building.getVariant(), building.getLevel());
         List<String> femaleSlots = planSet.femaleResidentsAt(building.getVariant(), building.getLevel());
         int capacity = maleSlots.size() + femaleSlots.size();
         if (capacity <= 0) {
            continue;
         }

         // Gender / declaration match when we know the migrant type.
         if (gender == Gender.MALE && maleSlots.isEmpty()) {
            continue;
         }
         if (gender == Gender.FEMALE && femaleSlots.isEmpty()) {
            continue;
         }
         if (typePath != null) {
            List<String> relevant = gender == Gender.FEMALE ? femaleSlots : maleSlots;
            if (gender == null) {
               relevant = new ArrayList<>();
               relevant.addAll(maleSlots);
               relevant.addAll(femaleSlots);
            }
            boolean typeFits = relevant.isEmpty();
            for (String slot : relevant) {
               if (slot == null || slot.isBlank() || typePath.equals(slot) || typePath.endsWith(slot) || slot.endsWith(typePath)) {
                  typeFits = true;
                  break;
               }
            }
            if (!typeFits) {
               continue;
            }
         }

         int occupied = 0;
         int occupiedMale = 0;
         int occupiedFemale = 0;
         for (VillagerRecord r : residents.values()) {
            if (r != null && !r.isKilled() && building.getId().equals(r.getHomeBuilding())) {
               occupied++;
               VillagerType rt = r.getVillagerTypeId() != null ? ModCultures.getVillagerType(r.getVillagerTypeId()) : null;
               if (rt != null && rt.gender() == Gender.FEMALE) {
                  occupiedFemale++;
               } else {
                  occupiedMale++;
               }
            }
         }
         if (occupied >= capacity) {
            continue;
         }
         if (gender == Gender.MALE && occupiedMale >= maleSlots.size() && !maleSlots.isEmpty()) {
            continue;
         }
         if (gender == Gender.FEMALE && occupiedFemale >= femaleSlots.size() && !femaleSlots.isEmpty()) {
            continue;
         }
         homes.add(building);
      }
      if (homes.isEmpty()) {
         return null;
      }
      homes.sort(Comparator.comparing(b -> b.getId().uuid()));
      return homes.getFirst().getId();
   }
}
