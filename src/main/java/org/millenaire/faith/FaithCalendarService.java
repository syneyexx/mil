package org.millenaire.faith;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;

/**
 * Day-batched festival calendar. Called once per village on the night edge — not from entity ticks.
 */
public final class FaithCalendarService {
   private static final int MAX_PARTICLES = 12;
   private static final double NOTIFY_RANGE_SQ = 64.0 * 64.0;

   private FaithCalendarService() {
   }

   public static void onNightEdge(ServerLevel level, Village village, long currentDay) {
      if (village.isLoneBuilding()) {
         return;
      }
      if (!village.isActive()) {
         return;
      }

      int interval = Math.max(1, MillenaireServerConfig.SERVER.faithFestivalIntervalDays.getAsInt());
      long last = village.getLastFestivalDay();
      if (last >= 0L && currentDay - last < interval) {
         return;
      }
      // Align to interval boundaries after first festival
      if (last < 0L && currentDay % interval != 0L) {
         return;
      }

      startFestival(level, village, currentDay);
   }

   public static void startFestival(ServerLevel level, Village village, long currentDay) {
      FaithDefinition def = FaithService.resolve(village);
      int duration = MillenaireServerConfig.SERVER.faithFestivalDurationTicks.getAsInt();
      if (def != null && def.festivalDurationTicks() > 0) {
         duration = def.festivalDurationTicks();
      }
      village.setLastFestivalDay(currentDay);
      village.setFestivalEndsAtTick(level.getGameTime() + duration);
      village.markDirty();

      String faithName = FaithService.resolveDisplayName(village);
      String festivalName = def != null ? def.festivalName() : "Festival";
      village.recordChronicleEvent(level, VillageEventType.FESTIVAL, festivalName, faithName);

      BlockPos center = findChurchCenter(village);
      spawnBudgetedVfx(level, center);
      notifyNearbyPlayers(level, village, center, festivalName, faithName);
   }

   public static boolean isReligiousVisitGoal(String goalKey) {
      if (goalKey == null) {
         return false;
      }
      return switch (goalKey) {
         case "pray", "hold_service", "preach_on_pulpit", "perform_pujas", "be_puja_performer",
            "tend_sacrifices", "meditate", "pray_male_muslim", "pray_female_muslim", "pilgrimage" -> true;
         default -> false;
      };
   }

   public static int festivalPriorityBoost(Village village, long gameTime, String goalKey) {
      if (!village.isFestivalActive(gameTime) || !isReligiousVisitGoal(goalKey)) {
         return 0;
      }
      return 80;
   }

   private static BlockPos findChurchCenter(Village village) {
      FaithDefinition def = FaithService.resolve(village);
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational() || building.getPlanSetId() == null) {
            continue;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet == null) {
            continue;
         }
         boolean match = false;
         for (String tag : planSet.tags()) {
            if (def != null && def.churchBuildingTags().contains(tag)) {
               match = true;
               break;
            }
            if ("praying".equals(tag) || "mosque".equals(tag) || "pujas".equals(tag) || "sacrifices".equals(tag)) {
               match = true;
               break;
            }
         }
         if (match) {
            return building.getOrigin();
         }
      }
      return village.getCenter();
   }

   private static void spawnBudgetedVfx(ServerLevel level, BlockPos center) {
      for (int i = 0; i < MAX_PARTICLES; i++) {
         double ox = (level.random.nextDouble() - 0.5) * 6.0;
         double oz = (level.random.nextDouble() - 0.5) * 6.0;
         level.sendParticles(
            ParticleTypes.HAPPY_VILLAGER,
            center.getX() + 0.5 + ox,
            center.getY() + 1.5,
            center.getZ() + 0.5 + oz,
            1,
            0.0,
            0.2,
            0.0,
            0.0
         );
      }
      level.playSound(null, center, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.AMBIENT, 0.6F, 1.0F);
   }

   private static void notifyNearbyPlayers(
      ServerLevel level, Village village, BlockPos center, String festivalName, String faithName
   ) {
      Component msg = Component.translatable(
         "message.millenaire.faith.festival_start",
         village.getVillageName() != null ? village.getVillageName() : "Village",
         festivalName,
         faithName
      );
      for (ServerPlayer player : level.players()) {
         if (player.blockPosition().distSqr(center) <= NOTIFY_RANGE_SQ) {
            player.sendSystemMessage(msg);
         }
      }
   }
}
