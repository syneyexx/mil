package org.millenaire.religion;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.impl.VisitBuildingGoal;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.RitualKind;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Server lifecycle hooks for religion. Does not replace VillageManager ticking.
 */
public final class ReligionLifecycle {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static boolean listenersRegistered;
   private static int villageCursor;

   private ReligionLifecycle() {
   }

   public static void bootstrap() {
      if (listenersRegistered) {
         return;
      }
      listenersRegistered = true;
      LivingEventService.addListener(ReligionLifecycle::onLivingEvent);
      LOGGER.info("[Religion] Religion lifecycle bootstrapped");
   }

   public static void onVillageManagerTick(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      if (villages.isEmpty()) {
         return;
      }
      int interval = MillenaireServerConfig.SERVER.religionEvaluationInterval.getAsInt();
      int budget = MillenaireServerConfig.SERVER.religionGlobalBudget.getAsInt();
      int ceremonyBudget = MillenaireServerConfig.SERVER.religionCeremonyBudget.getAsInt();
      long now = level.getGameTime();
      int processed = 0;
      int ceremonyAdvances = 0;
      int start = Math.floorMod(villageCursor, villages.size());
      for (int i = 0; i < villages.size() && processed < budget; i++) {
         Village village = villages.get((start + i) % villages.size());
         // Stagger by village hash + interval.
         if (Math.floorMod(village.getId().uuid().hashCode() + now, interval) != 0) {
            continue;
         }
         if (!village.isActive() || !level.isLoaded(village.getCenter())) {
            continue;
         }
         ReligionService.get().ceremonies().tickVillage(level, village);
         ReligionService.get().pilgrimages().tick(level, village);
         for (LivingEvent event : LivingEventService.recentPublic(level, village.getId(), 4)) {
            if (level.getGameTime() - event.gameTime() <= 400L) {
               handleLivingEventWithLevel(level, village, event);
            }
         }
         processed++;
         ceremonyAdvances++;
         if (ceremonyAdvances >= ceremonyBudget) {
            break;
         }
      }
      villageCursor = start + processed;
      if (now % 12000L == 0L) {
         ReligionSavedData.get(level).pruneCeremonies(
            MillenaireServerConfig.SERVER.religionMaxCeremoniesTracked.getAsInt(),
            now
         );
      }
   }

   public static void onVisitCompleted(
      ServerLevel level,
      Village village,
      VillagerRecord record,
      VisitBuildingGoal goal,
      StopReason reason
   ) {
      if (!enabled()) {
         return;
      }
      ReligionService.get().daily().onReligiousVisitCompleted(level, village, record, goal, reason);
   }

   public static void onVillageRemoved(ServerLevel level, Village village) {
      if (village == null) {
         return;
      }
      ReligionSavedData.get(level).removeVillage(village.getId().uuid());
   }

   private static void onLivingEvent(LivingEvent event) {
      if (!enabled() || event == null) {
         return;
      }
      // Event-driven ceremonies are planned from VillageManager path where we have ServerLevel.
      // Store pending hooks on community history for the next village tick via details.
   }

   public static void onResidentPermanentlyLost(ServerLevel level, Village village, VillagerRecord record) {
      if (!enabled() || record == null || !record.isKilled()) {
         return;
      }
      ReligionService.get().ceremonies().planEventCeremony(level, village, RitualKind.MEMORIAL, null, null);
   }

   public static void onReligiousBuildingCompleted(ServerLevel level, Village village, BuildingInstance building) {
      if (!enabled() || building == null) {
         return;
      }
      boolean religious = building.getPurpose() == org.millenaire.building.BuildingPurpose.SHRINE;
      if (!religious) {
         for (String tag : new String[]{"praying", "pujas", "sacrifices", "mosque", "pilgrimsite"}) {
            for (BuildingInstance b : village.getOperationalBuildingsWithTag(tag)) {
               if (b.getId().equals(building.getId())) {
                  religious = true;
                  break;
               }
            }
         }
      }
      if (religious) {
         ReligionService.get().ceremonies().planEventCeremony(
            level, village, RitualKind.CONSECRATION, null, building.getId()
         );
      }
   }

   public static void onFamilyEvent(ServerLevel level, Village village, VillagerRecord subject) {
      if (!enabled() || subject == null) {
         return;
      }
      String spouse = subject.getSpousesName();
      if (spouse == null || spouse.isEmpty()) {
         return;
      }
      ReligionService.get().ceremonies().planEventCeremony(level, village, RitualKind.FAMILY, null, null);
   }

   public static void handleLivingEventWithLevel(ServerLevel level, Village village, LivingEvent event) {
      if (!enabled() || event == null || village == null) {
         return;
      }
      if (event.eventType() == LivingEventType.RESIDENT_DIED && event.targetId() != null) {
         VillagerRecord record = village.getVillagerRecords().get(event.targetId());
         if (record != null && record.isKilled()) {
            onResidentPermanentlyLost(level, village, record);
         }
      } else if (event.eventType() == LivingEventType.BUILDING_COMPLETED && event.details() != null) {
         // details may contain building id; consecration also hooked from growth path when available.
      } else if (event.eventType() == LivingEventType.ATTACK_STARTED) {
         var community = ReligionSavedData.get(level).getCommunity(village.getId().uuid());
         if (community != null && community.activeCeremonyId() != null) {
            var ceremony = ReligionSavedData.get(level).getCeremony(community.activeCeremonyId());
            if (ceremony != null && ceremony.state().isActivePhase()) {
               ceremony.setState(org.millenaire.religion.model.CeremonyState.INTERRUPTED);
               ceremony.setInterruptReason("attack");
               ReligionSavedData.get(level).setDirty();
            }
         }
      }
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.religionEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
