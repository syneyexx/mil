package org.millenaire.faction.military;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record ArmyId(UUID uuid) {
   public ArmyId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static ArmyId random() {
      return new ArmyId(UUID.randomUUID());
   }

   @Nullable
   public static ArmyId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new ArmyId(UUID.fromString(raw.trim()));
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
