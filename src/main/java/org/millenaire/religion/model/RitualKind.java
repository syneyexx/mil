package org.millenaire.religion.model;

import java.util.Locale;
import javax.annotation.Nullable;

public enum RitualKind {
   DAILY,
   SERVICE,
   FEAST,
   MEMORIAL,
   FAMILY,
   CONSECRATION,
   PILGRIMAGE;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static RitualKind fromId(@Nullable String id) {
      if (id == null || id.isEmpty()) {
         return null;
      }
      try {
         return valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
