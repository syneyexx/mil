package org.millenaire.faction.territory;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record DisputeId(UUID uuid) {
   public DisputeId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static DisputeId random() {
      return new DisputeId(UUID.randomUUID());
   }

   @Nullable
   public static DisputeId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new DisputeId(UUID.fromString(raw.trim()));
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
