package org.millenaire.activity;

public enum ActivityKind {
   HUNT,
   PATROL,
   BANDIT_ROAM,
   AMBUSH,
   SKIRMISH,
   VILLAGE_RAID,
   RESPONSE;

   public boolean isViolent() {
      return this == AMBUSH || this == SKIRMISH || this == VILLAGE_RAID || this == RESPONSE || this == BANDIT_ROAM;
   }

   public boolean isVillageRaid() {
      return this == VILLAGE_RAID;
   }

   public static ActivityKind fromId(String id) {
      if (id == null || id.isEmpty()) {
         return HUNT;
      }
      try {
         return ActivityKind.valueOf(id.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return HUNT;
      }
   }
}
