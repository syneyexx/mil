package org.millenaire.faction.territory;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record PeaceTreatyId(UUID uuid) {
   public PeaceTreatyId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static PeaceTreatyId random() {
      return new PeaceTreatyId(UUID.randomUUID());
   }

   @Nullable
   public static PeaceTreatyId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new PeaceTreatyId(UUID.fromString(raw.trim()));
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
