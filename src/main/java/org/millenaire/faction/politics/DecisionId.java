package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record DecisionId(UUID uuid) {
   public DecisionId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static DecisionId random() {
      return new DecisionId(UUID.randomUUID());
   }

   @Nullable
   public static DecisionId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new DecisionId(UUID.fromString(raw.trim()));
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
