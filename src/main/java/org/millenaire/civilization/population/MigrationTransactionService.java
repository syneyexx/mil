package org.millenaire.civilization.population;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Transactional resident migration between villages. Never duplicates VillagerRecord identities.
 * Does not force-load chunks or teleport unloaded entities; entity presence uses existing rehydration.
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

      PopulationSnapshot destPop = PopulationAggregator.aggregateVillage(destination, level.getGameTime());
      if (destPop.residents() >= Math.max(1, destPop.housingCapacity())) {
         LOGGER.debug("[Millenaire] Migration rejected: destination {} at housing capacity", destinationId.uuid());
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
      civ.putMigration(op);

      VillagerRecord removed = origin.getVillagerRecords().remove(villagerRecordId);
      if (removed == null) {
         op.fail("missing_at_remove");
         civ.putMigration(op);
         return opId;
      }
      op.trySetStatus(MigrationOperation.Status.REMOVED_FROM_ORIGIN);

      if (destination.getVillagerRecords().containsKey(villagerRecordId)) {
         origin.getVillagerRecords().put(villagerRecordId, removed);
         op.fail("duplicate_at_destination");
         civ.putMigration(op);
         data.setDirty();
         return opId;
      }

      destination.getVillagerRecords().put(villagerRecordId, removed);
      op.trySetStatus(MigrationOperation.Status.ADDED_TO_DESTINATION);
      op.trySetStatus(MigrationOperation.Status.COMPLETE);
      civ.putMigration(op);
      data.setDirty();
      CivilizationMetrics.MIGRATIONS.incrementAndGet();

      LivingEventService.emit(
         level,
         LivingEventType.MIGRATION,
         LivingEventSource.SYSTEM,
         destination,
         villagerRecordId,
         null,
         "origin=" + originId.uuid(),
         "public",
         "mig:" + opId
      );
      return opId;
   }
}
