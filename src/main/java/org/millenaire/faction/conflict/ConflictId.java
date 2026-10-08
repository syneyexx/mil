package org.millenaire.faction.conflict;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record ConflictId(UUID uuid) {
   public ConflictId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static ConflictId random() {
      return new ConflictId(UUID.randomUUID());
   }

   @Nullable
   public static ConflictId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new ConflictId(UUID.fromString(raw.trim()));
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
