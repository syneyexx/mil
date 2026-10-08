package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum EquipmentLevel {
   NONE(0),
   BASIC(1),
   STANDARD(2),
   GOOD(3),
   EXCELLENT(4);

   private final int rank;

   EquipmentLevel(int rank) {
      this.rank = rank;
   }

   public int getRank() {
      return this.rank;
   }

   @Nullable
   public static EquipmentLevel tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return EquipmentLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
