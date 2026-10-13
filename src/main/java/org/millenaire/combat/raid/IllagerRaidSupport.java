package org.millenaire.combat.raid;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.perf.StaggeredVillageJob;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Hybrid Illager support: vanilla Illagers tagged to Millenaire raids/camps.
 */
public final class IllagerRaidSupport {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String TAG_RAID_VILLAGE = "MillenaireRaidVillage";
   public static final String TAG_CAMP_VILLAGE = "MillenaireCampVillage";

   private IllagerRaidSupport() {
   }

   public static boolean isIllagerCamp(Village village) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      return vt != null && "illager_camp".equals(vt.specialType());
   }

   public static void onRaidStarted(Village attacker, Village target, ServerLevel level) {
      if (!isIllagerCamp(attacker)) {
         return;
      }
      int pack = MillenaireServerConfig.SERVER.illagerRaidPackSize.getAsInt();
      if (pack <= 0) {
         return;
      }
      BlockPos attackerCenter = attacker.getCenter();
      int spawned = 0;
      for (int i = 0; i < pack; i++) {
         BlockPos spawnPos = RaidSpawnLocator.findSpawnPoint(level, target, attackerCenter);
         if (!level.isPositionEntityTicking(spawnPos)) {
            continue;
         }
         AbstractIllager illager = createIllager(level, i, pack);
         if (illager == null) {
            continue;
         }
         illager.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
         tagForRaid(illager, target.getId().uuid());
         if (level.addFreshEntity(illager)) {
            target.trackRaidIllager(illager.getUUID());
            spawned++;
            TickPerfCounters.ILLAGER_SPAWNS.incrementAndGet();
         }
      }
      if (spawned > 0) {
         LOGGER.info("[Millenaire] Hybrid raid: spawned {} vanilla Illagers at {}", spawned, target.getVillageName());
         target.markDirty();
      }
   }

   public static void cleanupRaidIllagers(Village target, ServerLevel level) {
      List<UUID> tracked = new ArrayList<>(target.getRaidIllagerUuids());
      for (UUID uuid : tracked) {
         if (level.getEntity(uuid) instanceof AbstractIllager illager) {
            illager.discard();
         }
      }
      target.clearRaidIllagers();
   }

   public static void tickCampGarrison(ServerLevel level, Village camp) {
      if (!isIllagerCamp(camp) || !camp.isActive()) {
         return;
      }
      if (!StaggeredVillageJob.shouldRun(level, camp, 200)) {
         return;
      }
      int cap = MillenaireServerConfig.SERVER.illagerCampGarrisonCap.getAsInt();
      if (cap <= 0) {
         return;
      }
      pruneDead(camp, level);
      int living = 0;
      for (UUID uuid : camp.getCampIllagerUuids()) {
         if (level.getEntity(uuid) instanceof AbstractIllager) {
            living++;
         }
      }
      int toSpawn = Math.min(2, cap - living);
      for (int i = 0; i < toSpawn; i++) {
         BlockPos spawn = camp.getCenter().offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
         AbstractIllager illager = createIllager(level, i, cap);
         if (illager == null) {
            continue;
         }
         illager.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
         tagForCamp(illager, camp.getId().uuid());
         if (level.addFreshEntity(illager)) {
            camp.trackCampIllager(illager.getUUID());
            TickPerfCounters.ILLAGER_SPAWNS.incrementAndGet();
         }
      }
   }

   public static void discardCampIllagers(Village camp, ServerLevel level) {
      for (UUID uuid : new ArrayList<>(camp.getCampIllagerUuids())) {
         if (level.getEntity(uuid) instanceof AbstractIllager illager) {
            illager.discard();
         }
      }
      camp.clearCampIllagers();
   }

   private static void pruneDead(Village camp, ServerLevel level) {
      Iterator<UUID> it = camp.getCampIllagerUuids().iterator();
      while (it.hasNext()) {
         UUID uuid = it.next();
         if (!(level.getEntity(uuid) instanceof AbstractIllager)) {
            it.remove();
         }
      }
   }

   private static AbstractIllager createIllager(ServerLevel level, int index, int pack) {
      if (pack >= 8 && index == 0) {
         Evoker evoker = EntityType.EVOKER.create(level);
         return evoker;
      }
      if (index % 3 == 0) {
         Vindicator vindicator = EntityType.VINDICATOR.create(level);
         return vindicator;
      }
      Pillager pillager = EntityType.PILLAGER.create(level);
      return pillager;
   }

   private static void tagForRaid(AbstractIllager illager, UUID targetVillage) {
      CompoundTag tag = illager.getPersistentData();
      tag.putUUID(TAG_RAID_VILLAGE, targetVillage);
      illager.setPersistenceRequired();
   }

   private static void tagForCamp(AbstractIllager illager, UUID campVillage) {
      CompoundTag tag = illager.getPersistentData();
      tag.putUUID(TAG_CAMP_VILLAGE, campVillage);
      illager.setPersistenceRequired();
   }
}
