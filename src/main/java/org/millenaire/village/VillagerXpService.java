package org.millenaire.village;

import javax.annotation.Nullable;
import org.millenaire.entity.MillVillager;

/**
 * Awards craft/build XP from completed real tasks only (event-driven, not per-tick).
 */
public final class VillagerXpService {
   private VillagerXpService() {
   }

   public static void award(@Nullable MillVillager villager, @Nullable Village village, String skill, int amount) {
      if (villager == null || village == null || skill == null || amount <= 0) {
         return;
      }
      VillagerRecord record = village.getVillagerRecord(villager.getUUID());
      if (record == null || record.isKilled()) {
         return;
      }
      record.addCraftXp(skill, amount);
      if (villager.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
         org.millenaire.knowledge.KnowledgeService.get().onSkillEvent(serverLevel, village, skill, amount);
      }
      // Mentorship: high skill may take an apprentice in same home (cheap, event-only).
      if (record.getCraftXp(skill) >= 50 && record.getApprenticeUuids().isEmpty() && record.getHomeBuilding() != null) {
         for (VillagerRecord other : village.getVillagerRecords().values()) {
            if (other.getUuid().equals(record.getUuid()) || other.isKilled()) {
               continue;
            }
            if (other.getHomeBuilding() != null
               && other.getHomeBuilding().equals(record.getHomeBuilding())
               && other.getMentorUuid() == null
               && other.getCraftXp(skill) < record.getCraftXp(skill) / 2) {
               HouseholdLinks.linkMentor(village, record, other);
               break;
            }
         }
      }
      village.markDirty();
   }

   public static void awardCraft(@Nullable MillVillager villager, @Nullable Village village) {
      award(villager, village, "craft", 2);
   }

   public static void awardBuild(@Nullable MillVillager villager, @Nullable Village village) {
      award(villager, village, "build", 5);
   }

   public static void awardGather(@Nullable MillVillager villager, @Nullable Village village, String skill) {
      award(villager, village, skill == null ? "gather" : skill, 1);
   }
}
