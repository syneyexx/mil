package org.millenaire.faction.war;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record WarGoalId(UUID uuid) {
   public WarGoalId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static WarGoalId random() {
      return new WarGoalId(UUID.randomUUID());
   }

   @Nullable
   public static WarGoalId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new WarGoalId(UUID.fromString(raw.trim()));
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
