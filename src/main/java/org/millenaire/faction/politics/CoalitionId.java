package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record CoalitionId(UUID uuid) {
   public CoalitionId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static CoalitionId random() {
      return new CoalitionId(UUID.randomUUID());
   }

   @Nullable
   public static CoalitionId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new CoalitionId(UUID.fromString(raw.trim()));
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
