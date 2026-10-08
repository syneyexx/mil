package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record CrisisId(UUID uuid) {
   public CrisisId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static CrisisId random() {
      return new CrisisId(UUID.randomUUID());
   }

   @Nullable
   public static CrisisId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new CrisisId(UUID.fromString(raw.trim()));
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
