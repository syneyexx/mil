package org.millenaire.faction.conflict;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record ConflictSideId(UUID uuid) {
   public ConflictSideId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static ConflictSideId random() {
      return new ConflictSideId(UUID.randomUUID());
   }

   @Nullable
   public static ConflictSideId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new ConflictSideId(UUID.fromString(raw.trim()));
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
