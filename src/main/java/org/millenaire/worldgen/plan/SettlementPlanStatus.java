package org.millenaire.worldgen.plan;

public enum SettlementPlanStatus {
   CANDIDATE,
   PLANNED,
   RESERVED,
   MATERIALIZING,
   PHYSICAL_COMPLETE,
   ACTIVATING,
   ACTIVE,
   INVALID,
   FAILED;

   public boolean canTransitionTo(SettlementPlanStatus next) {
      if (next == null || next == this) {
         return false;
      }
      return switch (this) {
         case CANDIDATE -> next == PLANNED || next == INVALID || next == FAILED;
         case PLANNED -> next == RESERVED || next == MATERIALIZING || next == INVALID || next == FAILED;
         case RESERVED -> next == MATERIALIZING || next == INVALID || next == FAILED;
         case MATERIALIZING -> next == PHYSICAL_COMPLETE || next == FAILED;
         case PHYSICAL_COMPLETE -> next == ACTIVATING || next == ACTIVE || next == FAILED;
         case ACTIVATING -> next == ACTIVE || next == FAILED;
         case ACTIVE, INVALID, FAILED -> false;
      };
   }

   public boolean isTerminalFailure() {
      return this == INVALID || this == FAILED;
   }

   public boolean allowsMaterialization() {
      return this == PLANNED || this == RESERVED || this == MATERIALIZING;
   }
}
