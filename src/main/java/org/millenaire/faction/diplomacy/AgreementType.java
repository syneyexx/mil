package org.millenaire.faction.diplomacy;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Formal diplomatic agreement kinds.
 * <p>
 * Bilateral cooperative types treat participants unordered. {@link #EMBARGO} is directional
 * (imposer → target). This is not a war system and does not spawn military forces.
 */
public enum AgreementType {
   PEACE,
   NON_AGGRESSION,
   TRADE_AGREEMENT,
   DEFENSIVE_PACT,
   ALLIANCE,
   EMBARGO;

   public boolean isDirectional() {
      return this == EMBARGO;
   }

   public boolean isCooperative() {
      return this != EMBARGO;
   }

   @Nullable
   public static AgreementType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return AgreementType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
