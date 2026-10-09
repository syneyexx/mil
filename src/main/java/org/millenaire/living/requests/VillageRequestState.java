package org.millenaire.living.requests;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum VillageRequestState {
   AVAILABLE,
   OFFERED,
   DECLINED,
   ACCEPTED,
   IN_PROGRESS,
   EXPIRED,
   CANCELLED,
   READY_TO_COMPLETE,
   COMPLETED;

   public String i18nKey() {
      return "living.millenaire.request.state." + this.name().toLowerCase(Locale.ROOT);
   }

   public boolean isTerminal() {
      return this == DECLINED || this == EXPIRED || this == CANCELLED || this == COMPLETED;
   }

   public boolean canTransitionTo(VillageRequestState next) {
      if (next == null || this == next) {
         return false;
      }
      return switch (this) {
         case AVAILABLE -> next == OFFERED || next == CANCELLED || next == EXPIRED;
         case OFFERED -> next == ACCEPTED || next == DECLINED || next == CANCELLED || next == EXPIRED || next == AVAILABLE;
         case ACCEPTED -> next == IN_PROGRESS || next == READY_TO_COMPLETE || next == CANCELLED || next == EXPIRED;
         case IN_PROGRESS -> next == READY_TO_COMPLETE || next == CANCELLED || next == EXPIRED || next == IN_PROGRESS;
         case READY_TO_COMPLETE -> next == COMPLETED || next == CANCELLED || next == EXPIRED;
         case DECLINED, EXPIRED, CANCELLED, COMPLETED -> false;
      };
   }

   public static Set<VillageRequestState> activeStates() {
      return EnumSet.of(AVAILABLE, OFFERED, ACCEPTED, IN_PROGRESS, READY_TO_COMPLETE);
   }
}
