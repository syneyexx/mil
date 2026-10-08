package org.millenaire.faction.territory;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record TransferId(UUID uuid) {
   public TransferId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static TransferId random() {
      return new TransferId(UUID.randomUUID());
   }

   @Nullable
   public static TransferId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new TransferId(UUID.fromString(raw.trim()));
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
