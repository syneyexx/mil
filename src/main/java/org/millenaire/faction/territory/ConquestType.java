package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ConquestType {
   CONQUEST,
   LIBERATION,
   RETURN,
   CESSION,
   RESTORATION,
   TRANSFER;

   @Nullable
   public static ConquestType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ConquestType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
