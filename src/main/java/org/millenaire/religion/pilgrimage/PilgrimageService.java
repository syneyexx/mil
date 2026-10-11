package org.millenaire.religion.pilgrimage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Temporary pilgrimage tracking — distinct from permanent migration.
 * Does not create duplicate records or assign permanent housing.
 */
public final class PilgrimageService {
   private final Map<UUID, PilgrimageTrip> activeTrips = new HashMap<>();

   public PilgrimageService() {
   }

   public record PilgrimageTrip(
      UUID villagerId,
      UUID originVillageId,
      @Nullable UUID destinationVillageId,
      ResourceLocation ritualId,
      long startGameTime,
      long returnByGameTime,
      boolean completed
   ) {
   }

   @Nullable
   public PilgrimageTrip startTrip(
      ServerLevel level,
      Village origin,
      VillagerRecord pilgrim,
      @Nullable Village destination,
      RitualDefinition ritual
   ) {
      if (pilgrim == null || pilgrim.isKilled() || activeTrips.containsKey(pilgrim.getUuid())) {
         return null;
      }
      NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, origin, pilgrim);
      if (!profile.hasTradition(ritual.traditionId())) {
         return null;
      }
      BuildingInstance site = null;
      Village dest = destination != null ? destination : origin;
      for (String tag : ritual.locationTags()) {
         var buildings = dest.getOperationalBuildingsWithTag(tag);
         if (!buildings.isEmpty()) {
            site = buildings.get(0);
            break;
         }
      }
      if (site == null) {
         return null;
      }
      PilgrimageTrip trip = new PilgrimageTrip(
         pilgrim.getUuid(),
         origin.getId().uuid(),
         dest.getId().uuid(),
         ritual.id(),
         level.getGameTime(),
         level.getGameTime() + 24000L,
         false
      );
      activeTrips.put(pilgrim.getUuid(), trip);
      ReligionSavedData.get(level).communityFor(origin.getId().uuid())
         .addHistory("pilgrimage_start:" + pilgrim.getUuid() + ":" + ritual.id());
      ReligionSavedData.get(level).setDirty();
      return trip;
   }

   public void tick(ServerLevel level, Village village) {
      if (activeTrips.isEmpty()) {
         return;
      }
      activeTrips.entrySet().removeIf(e -> {
         PilgrimageTrip trip = e.getValue();
         if (!trip.originVillageId().equals(village.getId().uuid())
            && (trip.destinationVillageId() == null || !trip.destinationVillageId().equals(village.getId().uuid()))) {
            return false;
         }
         if (level.getGameTime() >= trip.returnByGameTime()) {
            ReligionSavedData data = ReligionSavedData.get(level);
            data.communityFor(village.getId().uuid()).addHistory("pilgrimage_return:" + trip.villagerId());
            NpcFaithProfile profile = data.getProfile(trip.villagerId());
            if (profile != null && profile.primaryAffiliation() != null) {
               profile.primaryAffiliation().adjustDevotion(3);
            }
            data.setDirty();
            return true;
         }
         return false;
      });
   }

   public boolean isOnPilgrimage(UUID villagerId) {
      return activeTrips.containsKey(villagerId);
   }

   @Nullable
   public PilgrimageTrip getTrip(UUID villagerId) {
      return activeTrips.get(villagerId);
   }

   @Nullable
   public BlockPos destinationPos(Village village, ResourceLocation ritualId) {
      RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(ritualId);
      if (ritual == null) {
         return null;
      }
      for (String tag : ritual.locationTags()) {
         for (BuildingInstance b : village.getOperationalBuildingsWithTag(tag)) {
            BlockPos pos = b.getFirstPointPos("craftingPos");
            return pos != null ? pos : b.getOrigin();
         }
      }
      return null;
   }
}
