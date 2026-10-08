package org.millenaire.faction.diplomacy;

import java.util.Locale;
import javax.annotation.Nullable;

public enum AgreementStatus {
   ACTIVE,
   TERMINATED;

   @Nullable
   public static AgreementStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return AgreementStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
