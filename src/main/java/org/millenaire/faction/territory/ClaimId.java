package org.millenaire.faction.territory;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record ClaimId(UUID uuid) {
   public ClaimId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static ClaimId random() {
      return new ClaimId(UUID.randomUUID());
   }

   @Nullable
   public static ClaimId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new ClaimId(UUID.fromString(raw.trim()));
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
