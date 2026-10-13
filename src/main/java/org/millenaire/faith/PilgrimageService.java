package org.millenaire.faith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * Player pilgrimages to existing shrines (v1) and abstract NPC travel records (v2+).
 * No physical travel through hundreds of chunks for NPCs.
 */
public final class PilgrimageService {
   private static final int MAX_TRAVEL_PER_WORLD = 128;
   private static final double PLAYER_ARRIVE_RANGE_SQ = 64.0;

   private PilgrimageService() {
   }

   /** Lists operational pilgrim sites in loaded/registered villages (bounded). */
   public static List<PilgrimSite> findPilgrimSites(ServerLevel level, int max) {
      List<PilgrimSite> out = new ArrayList<>();
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (village.isLoneBuilding()) {
            continue;
         }
         for (BuildingInstance b : village.getOperationalBuildingsWithTag("pilgrimsite")) {
            out.add(new PilgrimSite(village.getId(), village.getVillageName(), b.getId().uuid(), b.getOrigin()));
            if (out.size() >= max) {
               return out;
            }
         }
         for (BuildingInstance b : village.getOperationalBuildingsWithTag("praying")) {
            out.add(new PilgrimSite(village.getId(), village.getVillageName(), b.getId().uuid(), b.getOrigin()));
            if (out.size() >= max) {
               return out;
            }
         }
      }
      return out;
   }

   public static boolean completePlayerPilgrimage(ServerPlayer player, VillageId targetVillageId) {
      ServerLevel level = player.serverLevel();
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(targetVillageId);
      if (village == null) {
         return false;
      }
      BlockPos shrine = FaithCalendarService.resolveChurchCenter(village);
      if (player.blockPosition().distSqr(shrine) > PLAYER_ARRIVE_RANGE_SQ) {
         player.sendSystemMessage(Component.translatable("message.millenaire.religion.pilgrimage_too_far"));
         return false;
      }
      player.sendSystemMessage(Component.translatable(
         "message.millenaire.religion.pilgrimage_complete",
         village.getVillageName() != null ? village.getVillageName() : "Village"
      ));
      // Bounded order spread: contacting a chapter may mark player as aware.
      ReligionSavedData data = ReligionSavedData.get(level);
      Chapter chapter = data.getChapterForVillage(village.getId());
      if (chapter != null && chapter.stage() != Chapter.Stage.DISBANDED) {
         Membership existing = data.getMembership(Membership.SubjectType.PLAYER, player.getUUID());
         if (existing == null) {
            // Discovery only — not auto membership
            player.getPersistentData().putBoolean("millenaire_lastflame_pilgrim", true);
         }
         OrderSpreadService.onSuccessfulContact(level, village, null);
      }
      return true;
   }

   /**
    * Schedules an abstract NPC pilgrimage (no chunk loading). Caps total travel records.
    */
   @Nullable
   public static TravelRecord scheduleNpcTravel(
      ServerLevel level, Village from, Village to, VillagerRecord record, String purpose, int durationDays
   ) {
      ReligionSavedData data = ReligionSavedData.get(level);
      if (data.allTravel().size() >= MAX_TRAVEL_PER_WORLD) {
         return null;
      }
      long day = level.getDayTime() / 24000L;
      TravelRecord travel = new TravelRecord(
         UUID.randomUUID(), record.getUuid(), from.getId(), to.getId(), day
      );
      travel.setCompleteByDay(day + Math.max(1, durationDays));
      travel.setPurpose(purpose);
      travel.setStatus(TravelRecord.Status.IN_PROGRESS);
      data.putTravel(travel);
      ReligionService.deadlines().schedule(
         level.getGameTime() + durationDays * 24000L,
         ReligionDeadlineQueue.Kind.TRAVEL_COMPLETE,
         travel.id()
      );
      return travel;
   }

   public record PilgrimSite(VillageId villageId, @Nullable String villageName, UUID buildingUuid, BlockPos pos) {
   }
}
