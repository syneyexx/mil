package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum PoliticalGroupType {
   RULING_ELITE,
   MERCHANTS,
   FARMERS,
   MILITARY,
   CLERGY,
   NOBILITY,
   COMMONERS,
   TRADITIONALISTS,
   REFORMERS;

   @Nullable
   public static PoliticalGroupType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PoliticalGroupType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
