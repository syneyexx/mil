package org.millenaire.activity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * Materialize / park participants while preserving identity. Never creates a second copy of an existing NPC.
 */
public final class ActivityProjectionService {
   private ActivityProjectionService() {}

   @Nullable
   public static MillVillager ensurePresent(
      ServerLevel level, ActivityAdmissionController admission, Village village, VillagerRecord record, BlockPos near, ActivityOperation op
   ) {
      if (record == null || record.isKilled()) {
         return null;
      }
      if (level.getEntity(record.getUuid()) instanceof MillVillager existing) {
         return existing;
      }
      // Missing entity in unloaded chunk => UNKNOWN, do not spawn
      BlockPos spawnAt = near != null ? near : record.getLastKnownPos() != null ? record.getLastKnownPos() : village.getCenter();
      if (spawnAt == null || !level.isPositionEntityTicking(spawnAt)) {
         op.setDeferReason(ActivityReasonCode.CHUNKS_UNAVAILABLE);
         op.enterState(ActivityState.WAITING_CHUNKS, level.getGameTime());
         return null;
      }
      if (isTrackedOrVisibleNear(level, spawnAt)) {
         // Safe to materialize at source/saved location only when chunk ticking
      }
      if (!admission.tryMaterialize()) {
         return null;
      }
      if (!isSafeSpawn(level, spawnAt)) {
         op.setDeferReason(ActivityReasonCode.UNSAFE_SPAWN);
         return null;
      }
      MillVillager spawned = VillagerSpawnFactory.spawnInVillage(level, village, record.getVillagerTypeId(), spawnAt, null, record);
      if (spawned == null) {
         op.setDeferReason(ActivityReasonCode.UNSAFE_SPAWN);
         return null;
      }
      return spawned;
   }

   public static boolean canDematerialize(ServerLevel level, MillVillager villager, ActivityOperation op) {
      if (villager == null) return false;
      if (!op.state().allowsDematerialize()) return false;
      if (villager.getTarget() != null && villager.getTarget().isAlive()) return false;
      if (villager.isPassenger() || villager.isVehicle()) return false;
      int park = MillenaireServerConfig.SERVER.regionalActivityParkRadius.getAsInt();
      double parkSq = (double) park * park;
      // Distance alone is not proof of invisibility; refuse park while any player is within
      // tracking-relevant range (park radius) so we never despawn a tracked entity.
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(villager) <= parkSq) {
            return false;
         }
      }
      return true;
   }

   public static void parkIfAllowed(ServerLevel level, MillVillager villager, ActivityOperation op, VillagerRecord record) {
      if (!canDematerialize(level, villager, op)) {
         return;
      }
      record.setLastKnownPos(villager.blockPosition());
      op.setLastValidPos(villager.blockPosition());
      villager.discard();
      op.setPhysicalActive(false);
   }

   private static boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
      if (!level.hasChunkAt(pos)) return false;
      return level.getBlockState(pos).isAir() || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   private static boolean isTrackedOrVisibleNear(ServerLevel level, BlockPos pos) {
      int r = MillenaireServerConfig.SERVER.regionalActivityInterestRadius.getAsInt();
      double rSq = (double) r * r;
      for (ServerPlayer p : level.players()) {
         if (p.blockPosition().distSqr(pos) <= rSq) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   public static Village resolveVillage(ServerLevel level, UUID id) {
      if (id == null) return null;
      return VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(id));
   }
}
