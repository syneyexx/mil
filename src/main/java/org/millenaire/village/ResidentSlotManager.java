package org.millenaire.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;

public class ResidentSlotManager {
   private final Village village;
   private final Map<ResidentSlotManager.SlotKey, UUID> reservedSlots = new HashMap<>();

   public ResidentSlotManager(Village village) {
      this.village = village;
   }

   public int countAdultsInBuilding(BuildingId buildingId, Gender gender) {
      int count = 0;

      for (VillagerRecord record : this.village.getVillagerRecords().values()) {
         BuildingId home = record.getHomeBuilding();
         if (home != null && home.equals(buildingId)) {
            VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vType != null && !vType.isChild() && vType.gender() == gender) {
               count++;
            }
         }
      }

      return count;
   }

   public int countResidents(Gender gender) {
      int count = 0;

      for (VillagerRecord record : this.village.getVillagerRecords().values()) {
         if (!record.isKilled() && !record.isRaidingVillage()) {
            VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vType != null && !vType.hasTag("foreignmerchant") && !vType.hasTag("localmerchant") && vType.gender() == gender) {
               count++;
            }
         }
      }

      return count;
   }

   public List<String> getFreeSlots(BuildingId buildingId, Gender gender) {
      BuildingInstance building = this.village.getBuilding(buildingId);
      if (building != null && building.getPlanSetId() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet == null) {
            return List.of();
         }

         List<String> expectedResidents = expectedResidents(planSet, building, gender);
         int currentAdults = this.countAdultsInBuilding(buildingId, gender);
         int reservedCount = 0;

         for (Entry<ResidentSlotManager.SlotKey, UUID> entry : this.reservedSlots.entrySet()) {
            ResidentSlotManager.SlotKey key = entry.getKey();
            if (key.buildingId().equals(buildingId) && key.gender() == gender) {
               reservedCount++;
            }
         }

         int occupied = currentAdults + reservedCount;
         if (occupied >= expectedResidents.size()) {
            return List.of();
         }

         List<String> freeSlots = new ArrayList<>();

         for (int i = occupied; i < expectedResidents.size(); i++) {
            freeSlots.add(expectedResidents.get(i));
         }

         return freeSlots;
      } else {
         return List.of();
      }
   }

   private static List<String> expectedResidents(BuildingPlanSet planSet, BuildingInstance building, Gender gender) {
      return gender == Gender.MALE
         ? planSet.maleResidentsAt(building.getVariant(), building.getLevel())
         : planSet.femaleResidentsAt(building.getVariant(), building.getLevel());
   }

   public boolean hasFreeSlot(BuildingId buildingId, Gender gender) {
      return !this.getFreeSlots(buildingId, gender).isEmpty();
   }

   @Nullable
   public String reserveSlot(BuildingId buildingId, Gender gender, UUID teenagerId) {
      return this.reserveSlot(buildingId, gender, teenagerId, null);
   }

   /**
    * Reserves a free resident slot. When {@code preferredTypeSuffix} is set (e.g. parent job),
    * that role is chosen if still free so generations inherit work.
    */
   @Nullable
   public String reserveSlot(BuildingId buildingId, Gender gender, UUID teenagerId, @Nullable String preferredTypeSuffix) {
      List<String> freeSlots = this.getFreeSlots(buildingId, gender);
      if (freeSlots.isEmpty()) {
         return null;
      }

      BuildingInstance building = this.village.getBuilding(buildingId);
      if (building != null && building.getPlanSetId() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet == null) {
            return null;
         }

         List<String> expectedResidents = expectedResidents(planSet, building, gender);
         String chosen = freeSlots.get(0);
         if (preferredTypeSuffix != null && !preferredTypeSuffix.isBlank()) {
            for (String free : freeSlots) {
               if (preferredTypeSuffix.equals(free) || free.endsWith("_" + preferredTypeSuffix) || preferredTypeSuffix.endsWith("_" + free)) {
                  chosen = free;
                  break;
               }
            }
         }

         int slotIndex = expectedResidents.indexOf(chosen);
         if (slotIndex < 0) {
            slotIndex = expectedResidents.size() - freeSlots.size();
         }
         ResidentSlotManager.SlotKey key = new ResidentSlotManager.SlotKey(building.getId(), gender, slotIndex);
         this.reservedSlots.put(key, teenagerId);
         this.village.markDirty();
         return chosen;
      } else {
         return null;
      }
   }

   public void releaseSlot(ResidentSlotManager.SlotKey key) {
      this.reservedSlots.remove(key);
      this.village.markDirty();
   }

   public void releaseAllSlots(UUID teenagerId) {
      this.reservedSlots.entrySet().removeIf(entry -> entry.getValue().equals(teenagerId));
      this.village.markDirty();
   }

   public void cleanupStaleReservations(ServerLevel level) {
      this.reservedSlots.entrySet().removeIf(entry -> {
         Entity entity = level.getEntity(entry.getValue());
         return entity == null || !entity.isAlive();
      });
   }

   public int countChildren() {
      int count = 0;

      for (VillagerRecord record : this.village.getVillagerRecords().values()) {
         VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
         if (vType != null && vType.isChild()) {
            count++;
         }
      }

      return count;
   }

   public int countChildrenInBuilding(BuildingId buildingId) {
      int count = 0;

      for (VillagerRecord record : this.village.getVillagerRecords().values()) {
         BuildingId home = record.getHomeBuilding();
         if (home != null && home.equals(buildingId)) {
            VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vType != null && vType.isChild()) {
               count++;
            }
         }
      }

      return count;
   }

   public boolean hasAnyFreeSlot(Gender gender) {
      for (BuildingInstance b : this.village.getBuildings()) {
         if (b.isOperational() && this.hasFreeSlot(b.getId(), gender)) {
            return true;
         }
      }

      return false;
   }

   public record SlotKey(BuildingId buildingId, Gender gender, int slotIndex) {
   }
}
