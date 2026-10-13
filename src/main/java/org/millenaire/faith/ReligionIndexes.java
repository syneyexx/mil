package org.millenaire.faith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;

/**
 * Event-driven indexes for religious buildings and members. Rebuild is chunked, not full-scan per tick.
 */
public final class ReligionIndexes {
   private final Map<UUID, List<BuildingId>> religiousBuildingsByVillage = new HashMap<>();
   private final Map<UUID, Integer> rebuildCursor = new HashMap<>();

   public void invalidateVillage(UUID villageId) {
      this.religiousBuildingsByVillage.remove(villageId);
      this.rebuildCursor.remove(villageId);
   }

   public void onBuildingChanged(Village village) {
      invalidateVillage(village.getId().uuid());
      FaithCalendarService.invalidateChurchCenter(village);
   }

   /**
    * Rebuilds up to {@code budget} buildings into the religious index for a village.
    * Returns true when the index is complete.
    */
   public boolean rebuildChunk(Village village, int budget) {
      UUID vid = village.getId().uuid();
      List<BuildingId> acc = this.religiousBuildingsByVillage.computeIfAbsent(vid, k -> new ArrayList<>());
      int cursor = this.rebuildCursor.getOrDefault(vid, 0);
      List<BuildingInstance> buildings = village.getBuildings();
      int end = Math.min(buildings.size(), cursor + Math.max(1, budget));
      FaithDefinition def = FaithService.resolve(village);
      for (int i = cursor; i < end; i++) {
         BuildingInstance b = buildings.get(i);
         if (!b.isOperational() || b.getPlanSetId() == null) {
            continue;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
         if (planSet == null) {
            continue;
         }
         if (isReligious(planSet, def)) {
            acc.add(b.getId());
         }
      }
      if (end >= buildings.size()) {
         this.rebuildCursor.remove(vid);
         return true;
      }
      this.rebuildCursor.put(vid, end);
      return false;
   }

   public List<BuildingId> getReligiousBuildings(UUID villageId) {
      List<BuildingId> list = this.religiousBuildingsByVillage.get(villageId);
      return list == null ? List.of() : Collections.unmodifiableList(list);
   }

   public boolean hasIndex(UUID villageId) {
      return this.religiousBuildingsByVillage.containsKey(villageId) && !this.rebuildCursor.containsKey(villageId);
   }

   private static boolean isReligious(BuildingPlanSet planSet, @Nullable FaithDefinition def) {
      for (String tag : planSet.tags()) {
         if (def != null && def.churchBuildingTags().contains(tag)) {
            return true;
         }
         if ("praying".equals(tag) || "mosque".equals(tag) || "pujas".equals(tag)
            || "sacrifices".equals(tag) || "religious".equals(tag) || "pilgrimsite".equals(tag)) {
            return true;
         }
      }
      return false;
   }
}
