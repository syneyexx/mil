package org.millenaire.activity;

public enum ActivityReasonCode {
   NONE,
   NO_SOURCE_CAMP,
   NO_ELIGIBLE_MEMBERS,
   HOME_DEFENSE_RESERVE,
   NO_TARGET,
   TARGET_PROTECTED,
   TARGET_RESERVED,
   COOLDOWN,
   NO_PREY,
   CHUNKS_UNAVAILABLE,
   UNSAFE_SPAWN,
   PATH_FAILED,
   ENTITY_CAP,
   GROUP_CAP,
   RAID_CAP,
   TIME_BUDGET,
   GLOBAL_BUDGET,
   SERVER_PRESSURE,
   STALE_RESULT,
   RECOVERY_REQUIRED,
   FEATURE_DISABLED,
   QUEUE_FULL,
   REGION_CAP,
   ACTIVATION_RATE,
   LEGACY_LOAD,
   TREATY_BLOCKS,
   OUTCOME_APPLIED;

   public static ActivityReasonCode fromId(String id) {
      if (id == null || id.isEmpty()) {
         return NONE;
      }
      try {
         return ActivityReasonCode.valueOf(id.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return NONE;
      }
   }
}
