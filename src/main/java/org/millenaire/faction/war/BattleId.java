package org.millenaire.faction.war;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record BattleId(UUID uuid) {
   public BattleId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static BattleId random() {
      return new BattleId(UUID.randomUUID());
   }

   @Nullable
   public static BattleId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new BattleId(UUID.fromString(raw.trim()));
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
