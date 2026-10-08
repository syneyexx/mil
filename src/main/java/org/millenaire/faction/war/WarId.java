package org.millenaire.faction.war;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record WarId(UUID uuid) {
   public WarId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static WarId random() {
      return new WarId(UUID.randomUUID());
   }

   @Nullable
   public static WarId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new WarId(UUID.fromString(raw.trim()));
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
