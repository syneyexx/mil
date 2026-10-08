package org.millenaire.faction.diplomacy;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Stable unique identifier for a diplomatic agreement. Survives server restart.
 */
public record AgreementId(UUID uuid) {
   public AgreementId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static AgreementId random() {
      return new AgreementId(UUID.randomUUID());
   }

   public static AgreementId of(UUID uuid) {
      return new AgreementId(uuid);
   }

   @Nullable
   public static AgreementId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new AgreementId(UUID.fromString(raw.trim()));
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
