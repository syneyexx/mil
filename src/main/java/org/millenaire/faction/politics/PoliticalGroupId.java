package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record PoliticalGroupId(UUID uuid) {
   public PoliticalGroupId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static PoliticalGroupId random() {
      return new PoliticalGroupId(UUID.randomUUID());
   }

   @Nullable
   public static PoliticalGroupId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new PoliticalGroupId(UUID.fromString(raw.trim()));
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
