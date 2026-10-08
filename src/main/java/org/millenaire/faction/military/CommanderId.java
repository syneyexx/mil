package org.millenaire.faction.military;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record CommanderId(UUID uuid) {
   public CommanderId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static CommanderId random() {
      return new CommanderId(UUID.randomUUID());
   }

   @Nullable
   public static CommanderId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new CommanderId(UUID.fromString(raw.trim()));
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
