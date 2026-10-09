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

      // Exclusive founder reservation: only the matching colony destination may migrate this villager.
      if (!founderReservationAllows(level, record.getUuid(), destinationId)) {
         return MigrationResult.rejected("founder_reserved", originId.uuid(), destinationId.uuid());
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
         boolean removed = op.trySetStatus(MigrationOperation.Status.REMOVED_FROM_ORIGIN);
         boolean added = op.trySetStatus(MigrationOperation.Status.ADDED_TO_DESTINATION);
         if (!removed || !added) {
            // Physical transfer may already have succeeded — do not invent rollback; mark for reconcile.
            op.markNeedsRecovery(true);
            civ.putMigration(op);
            data.setDirty();
         }

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

         // Prefer normal COMPLETE; fall back to reconcile when intermediate status write lagged.
         if (!op.trySetStatus(MigrationOperation.Status.COMPLETE) && !op.reconcileStatus(MigrationOperation.Status.COMPLETE)) {
            op.markNeedsRecovery(true);
            civ.putMigration(op);
            return MigrationResult.of(
               opId,
               MigrationResult.Outcome.RECOVERY_REQUIRED,
               false,
               true,
               "status_complete_failed",
               originId.uuid(),
               destinationId.uuid(),
               destBuilding.uuid()
            );
         }
         op.markNeedsRecovery(false);
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
    * Outcomes follow physical village records — never invent a resident.
    * {@code needsRecovery} clears only after postconditions and status transition succeed.
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
         if (!isRecoveryCandidate(op)) {
            continue;
         }
         // Terminal COMPLETE without recovery flag is a no-op.
         if (op.status() == MigrationOperation.Status.COMPLETE && !op.needsRecovery()) {
            continue;
         }

         op.incrementRecoveryAttempts();
         Village origin = data.getVillageManager().getVillage(new VillageId(op.originVillageId()));
         Village destination = data.getVillageManager().getVillage(new VillageId(op.destinationVillageId()));
         UUID villagerId = op.villagerId();
         boolean originPresent = origin != null;
         boolean destPresent = destination != null;
         boolean inOrigin = originPresent && origin.getVillagerRecords().containsKey(villagerId);
         boolean inDest = destPresent && destination.getVillagerRecords().containsKey(villagerId);

         if (!originPresent || !destPresent) {
            // Missing village — keep unresolved evidence; do not invent records or clear recovery.
            if (!op.needsRecovery()) {
               op.markNeedsRecovery(true);
            }
            if (op.failureReason().isEmpty()) {
               op.fail(originPresent ? "recovery_destination_village_missing" : "recovery_origin_village_missing");
               // fail() sets FAILED; restore recovery flag intentionally.
               op.markNeedsRecovery(true);
            }
            civ.putMigration(op);
            fixed++;
            continue;
         }

         if (inDest && !inOrigin) {
            if (!ensureDestinationHomeAndEntity(level, destination, villagerId, op)) {
               op.markNeedsRecovery(true);
               civ.putMigration(op);
               fixed++;
               continue;
            }
            if (!op.reconcileStatus(MigrationOperation.Status.COMPLETE)) {
               op.markNeedsRecovery(true);
               civ.putMigration(op);
               fixed++;
               continue;
            }
            op.markNeedsRecovery(false);
            destination.markDirty();
            data.setDirty();
            civ.putMigration(op);
            fixed++;
            continue;
         }

         if (inOrigin && !inDest) {
            // Transfer never finished — safe failed/rollback so a later day may retry.
            if (!op.reconcileStatus(MigrationOperation.Status.FAILED)) {
               op.markNeedsRecovery(true);
               civ.putMigration(op);
               fixed++;
               continue;
            }
            op.fail("recovery_still_in_origin");
            op.markNeedsRecovery(false);
            civ.putMigration(op);
            fixed++;
            continue;
         }

         if (inOrigin && inDest) {
            // Duplicate — destination wins; clean origin via village helper (not raw map only).
            if (!ensureDestinationHomeAndEntity(level, destination, villagerId, op)) {
               op.markNeedsRecovery(true);
               civ.putMigration(op);
               fixed++;
               continue;
            }
            origin.removeVillagerRecord(villagerId);
            origin.markDirty();
            destination.markDirty();
            data.setDirty();
            if (!op.reconcileStatus(MigrationOperation.Status.COMPLETE)) {
               op.markNeedsRecovery(true);
               civ.putMigration(op);
               fixed++;
               continue;
            }
            op.markNeedsRecovery(false);
            civ.putMigration(op);
            fixed++;
            continue;
         }

         // Neither village has the record — keep evidence; do not invent a resident.
         op.fail("recovery_unresolvable_no_record");
         op.markNeedsRecovery(true);
         civ.putMigration(op);
         fixed++;
      }
      return fixed;
   }

   private static boolean isRecoveryCandidate(MigrationOperation op) {
      if (op == null) {
         return false;
      }
      if (op.needsRecovery()) {
         return true;
      }
      return switch (op.status()) {
         case RESERVED, REMOVED_FROM_ORIGIN, ADDED_TO_DESTINATION -> true;
         case FAILED -> false; // only when needsRecovery (handled above)
         case COMPLETE, ROLLED_BACK -> false;
      };
   }

   /**
    * Ensure destination home building is still suitable and the live entity (if any) matches.
    * An old destinationBuildingId is not automatic proof the building still exists/fits.
    */
   private static boolean ensureDestinationHomeAndEntity(
      ServerLevel level,
      Village destination,
      UUID villagerId,
      MigrationOperation op
   ) {
      VillagerRecord record = destination.getVillagerRecords().get(villagerId);
      if (record == null || record.isKilled()) {
         return false;
      }
      BuildingId home = record.getHomeBuilding();
      BuildingId desired = op.destinationBuildingId() != null ? new BuildingId(op.destinationBuildingId()) : null;
      BuildingInstance homeBuilding = home != null ? destination.getBuilding(home) : null;
      if (homeBuilding == null || !homeBuilding.isOperational()) {
         BuildingId selected = selectDestinationHome(destination, record);
         if (selected == null) {
            op.fail("recovery_no_matching_home");
            op.markNeedsRecovery(true);
            return false;
         }
         record.setHomeBuilding(selected);
         op.setDestinationBuilding(selected.uuid());
         home = selected;
      } else if (desired != null && !desired.equals(home)) {
         BuildingInstance desiredBuilding = destination.getBuilding(desired);
         if (desiredBuilding != null && desiredBuilding.isOperational()) {
            // Prefer persisted destination when still valid.
            record.setHomeBuilding(desired);
            home = desired;
         } else {
            op.setDestinationBuilding(home.uuid());
         }
      } else if (home != null) {
         op.setDestinationBuilding(home.uuid());
      }

      if (level.getEntity(villagerId) instanceof MillVillager villager) {
         villager.setVillageId(destination.getId());
         if (home != null) {
            villager.setHomeBuilding(home);
         }
      }
      return true;
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
    * Reserved founders may only migrate into their colonization destination village.
    * Unreserved villagers always pass this check.
    */
   public static boolean founderReservationAllows(ServerLevel level, UUID villagerId, VillageId destinationId) {
      if (level == null || villagerId == null || destinationId == null) {
         return true;
      }
      UUID holder = org.millenaire.civilization.settlement.ColonizationSavedData.get(level).reservationHolder(villagerId);
      if (holder == null) {
         return true;
      }
      org.millenaire.civilization.settlement.ColonizationOperation op =
         org.millenaire.civilization.settlement.ColonizationSavedData.get(level).get(holder);
      if (op == null || !op.isActive()) {
         return true;
      }
      org.millenaire.worldgen.plan.SettlementPlan plan =
         org.millenaire.worldgen.plan.SettlementPlanSavedData.get(level).getPlan(op.planId());
      if (plan == null || plan.villageUuid() == null) {
         // Colony village not activated yet — block ordinary migration away.
         return false;
      }
      return destinationId.uuid().equals(plan.villageUuid());
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
