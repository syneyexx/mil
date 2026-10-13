package org.millenaire.village;

import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.entity.MillVillager;

/**
 * UUID household edges for villagers. Keeps string names for UI; UUIDs are authoritative.
 * Costs stay O(1) per link — never all-pairs.
 */
public final class HouseholdLinks {
   private HouseholdLinks() {
   }

   public static void linkSpouses(Village village, MillVillager a, MillVillager b) {
      if (village == null || a == null || b == null) {
         return;
      }
      VillagerRecord ra = village.getVillagerRecord(a.getUUID());
      VillagerRecord rb = village.getVillagerRecord(b.getUUID());
      if (ra == null || rb == null) {
         return;
      }
      UUID household = ra.getHouseholdId() != null ? ra.getHouseholdId() : rb.getHouseholdId();
      if (household == null) {
         household = UUID.randomUUID();
      }
      ra.setHouseholdId(household);
      rb.setHouseholdId(household);
      ra.setSpouseUuid(rb.getUuid());
      rb.setSpouseUuid(ra.getUuid());
      a.setSpousesName(b.getIdentity().getFirstName());
      b.setSpousesName(a.getIdentity().getFirstName());
      ra.updateFromEntity(a);
      rb.updateFromEntity(b);
      village.markDirty();
   }

   public static void linkChildToParents(
      Village village,
      MillVillager child,
      @Nullable MillVillager mother,
      @Nullable MillVillager father
   ) {
      if (village == null || child == null) {
         return;
      }
      VillagerRecord childRec = village.getVillagerRecord(child.getUUID());
      if (childRec == null) {
         return;
      }
      UUID household = null;
      if (mother != null) {
         VillagerRecord mr = village.getVillagerRecord(mother.getUUID());
         if (mr != null) {
            household = mr.getHouseholdId();
            childRec.setMotherUuid(mr.getUuid());
         }
      }
      if (father != null) {
         VillagerRecord fr = village.getVillagerRecord(father.getUUID());
         if (fr != null) {
            if (household == null) {
               household = fr.getHouseholdId();
            }
            childRec.setFatherUuid(fr.getUuid());
         }
      }
      if (household == null) {
         household = UUID.randomUUID();
      }
      childRec.setHouseholdId(household);
      childRec.updateFromEntity(child);
      village.markDirty();
   }

   public static void linkMentor(Village village, VillagerRecord mentor, VillagerRecord apprentice) {
      if (village == null || mentor == null || apprentice == null) {
         return;
      }
      if (mentor.addApprentice(apprentice.getUuid())) {
         apprentice.setMentorUuid(mentor.getUuid());
         apprentice.addMemory("apprenticed_to:" + mentor.getUuid());
         village.markDirty();
      }
   }

   /**
    * When a skilled villager is lost, transfer a fraction of XP to their apprentice and raise a production need signal.
    */
   public static void onKeyVillagerLost(Village village, VillagerRecord lost) {
      if (village == null || lost == null) {
         return;
      }
      for (UUID apprenticeId : lost.getApprenticeUuids()) {
         VillagerRecord apprentice = village.getVillagerRecord(apprenticeId);
         if (apprentice == null || apprentice.isKilled()) {
            continue;
         }
         for (var e : lost.getCraftXp().entrySet()) {
            apprentice.addCraftXp(e.getKey(), Math.max(1, e.getValue() / 4));
         }
         apprentice.addMemory("succeeded:" + lost.getUuid());
         apprentice.setMentorUuid(null);
      }
      lost.addMemory("lost_role");
      village.markDirty();
   }

}
