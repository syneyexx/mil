package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum PeaceTermType {
   WHITE_PEACE,
   TERRITORIAL_TRANSFER,
   RETURN_TERRITORY,
   RELEASE_TERRITORY,
   REPARATIONS,
   DEMILITARIZATION;

   @Nullable
   public static PeaceTermType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PeaceTermType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
