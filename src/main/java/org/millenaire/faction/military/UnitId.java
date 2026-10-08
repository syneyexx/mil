package org.millenaire.faction.military;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record UnitId(UUID uuid) {
   public UnitId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static UnitId random() {
      return new UnitId(UUID.randomUUID());
   }

   @Nullable
   public static UnitId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new UnitId(UUID.fromString(raw.trim()));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public String asString() {
      return this.uuid.toString();
   }

   @Override
   public String toString() {
      return this.uuid.toString();
   }
}
