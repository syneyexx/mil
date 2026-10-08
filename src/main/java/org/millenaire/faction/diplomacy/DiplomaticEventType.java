package org.millenaire.faction.diplomacy;

import java.util.Locale;
import javax.annotation.Nullable;

public enum DiplomaticEventType {
   PEACE_ESTABLISHED,
   NON_AGGRESSION_SIGNED,
   NON_AGGRESSION_EXPIRED,
   TRADE_AGREEMENT_SIGNED,
   TRADE_AGREEMENT_EXPIRED,
   DEFENSIVE_PACT_SIGNED,
   DEFENSIVE_PACT_EXPIRED,
   ALLIANCE_FORMED,
   ALLIANCE_TERMINATED,
   EMBARGO_IMPOSED,
   EMBARGO_LIFTED,
   AGREEMENT_EXPIRED,
   AGREEMENT_TERMINATED;

   @Nullable
   public static DiplomaticEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return DiplomaticEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public static DiplomaticEventType forEstablishment(AgreementType type) {
      return switch (type) {
         case PEACE -> PEACE_ESTABLISHED;
         case NON_AGGRESSION -> NON_AGGRESSION_SIGNED;
         case TRADE_AGREEMENT -> TRADE_AGREEMENT_SIGNED;
         case DEFENSIVE_PACT -> DEFENSIVE_PACT_SIGNED;
         case ALLIANCE -> ALLIANCE_FORMED;
         case EMBARGO -> EMBARGO_IMPOSED;
      };
   }

   public static DiplomaticEventType forExpiration(AgreementType type) {
      return switch (type) {
         case NON_AGGRESSION -> NON_AGGRESSION_EXPIRED;
         case TRADE_AGREEMENT -> TRADE_AGREEMENT_EXPIRED;
         case DEFENSIVE_PACT -> DEFENSIVE_PACT_EXPIRED;
         case ALLIANCE -> ALLIANCE_TERMINATED;
         case EMBARGO -> EMBARGO_LIFTED;
         case PEACE -> AGREEMENT_EXPIRED;
      };
   }

   public static DiplomaticEventType forTermination(AgreementType type) {
      return switch (type) {
         case ALLIANCE -> ALLIANCE_TERMINATED;
         case EMBARGO -> EMBARGO_LIFTED;
         default -> AGREEMENT_TERMINATED;
      };
   }
}
