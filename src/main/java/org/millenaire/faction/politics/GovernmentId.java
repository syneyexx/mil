package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record GovernmentId(UUID uuid) {
   public GovernmentId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static GovernmentId random() {
      return new GovernmentId(UUID.randomUUID());
   }

   @Nullable
   public static GovernmentId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new GovernmentId(UUID.fromString(raw.trim()));
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
