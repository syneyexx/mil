package org.millenaire.religion.diplomacy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.religion.model.CeremonyState;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;

/**
 * Records religious incidents (e.g. shrine damage). Does not auto-declare war.
 */
public final class ReligionIncidentService {
   public ReligionIncidentService() {
   }

   public void onReligiousBuildingDamaged(
      ServerLevel level,
      Village village,
      BuildingInstance building,
      @Nullable UUID actorPlayerId
   ) {
      if (level == null || village == null || building == null) {
         return;
      }
      if (!isReligiousBuilding(village, building)) {
         return;
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      community.addHistory("incident:shrine_damage:" + building.getId().uuid());
      data.setDirty();
      LivingEventService.emit(
         level,
         LivingEventType.RELIGIOUS_INCIDENT,
         LivingEventSource.SYSTEM,
         village,
         actorPlayerId,
         null,
         "shrine_damage:" + building.getId().uuid(),
         "public",
         "shrine_dmg:" + village.getId().uuid() + ":" + building.getId().uuid() + ":" + (level.getGameTime() / 1200L)
      );
      if (community.activeCeremonyId() != null) {
         var ceremony = data.getCeremony(community.activeCeremonyId());
         if (ceremony != null
            && ceremony.buildingId() != null
            && ceremony.buildingId().equals(building.getId())
            && ceremony.state().isActivePhase()) {
            ceremony.setState(CeremonyState.INTERRUPTED);
            ceremony.setInterruptReason("shrine_damage");
            data.setDirty();
         }
      }
   }

   private boolean isReligiousBuilding(Village village, BuildingInstance building) {
      if (building.getPurpose() == BuildingPurpose.SHRINE) {
         return true;
      }
      for (String tag : new String[]{"praying", "pujas", "sacrifices", "mosque", "mosquefemaleside", "pilgrimsite"}) {
         for (BuildingInstance b : village.getOperationalBuildingsWithTag(tag)) {
            if (b.getId().equals(building.getId())) {
               return true;
            }
         }
      }
      return false;
   }
}
