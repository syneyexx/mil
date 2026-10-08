package org.millenaire.faction.conflict;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record UnrestId(UUID uuid) {
   public UnrestId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static UnrestId random() {
      return new UnrestId(UUID.randomUUID());
   }

   @Nullable
   public static UnrestId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new UnrestId(UUID.fromString(raw.trim()));
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
