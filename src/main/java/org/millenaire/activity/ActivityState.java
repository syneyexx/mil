package org.millenaire.activity;

public enum ActivityState {
   PROPOSED,
   RESERVED,
   ASSEMBLING,
   TRAVELLING,
   EXECUTING,
   RETURNING,
   SETTLING,
   COMPLETED,
   RETREATING,
   WAITING_CHUNKS,
   RECOVERY_REQUIRED,
   CANCELLED;

   public boolean isTerminal() {
      return this == COMPLETED || this == CANCELLED;
   }

   public boolean isActivePhysical() {
      return this == ASSEMBLING
         || this == TRAVELLING
         || this == EXECUTING
         || this == RETURNING
         || this == RETREATING
         || this == SETTLING
         || this == WAITING_CHUNKS;
   }

   public boolean allowsDematerialize() {
      return this == TRAVELLING || this == WAITING_CHUNKS || this == RETURNING;
   }

   public static ActivityState fromId(String id) {
      if (id == null || id.isEmpty()) {
         return PROPOSED;
      }
      try {
         return ActivityState.valueOf(id.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return PROPOSED;
      }
   }
}
