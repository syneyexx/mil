package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record CandidateId(UUID uuid) {
   public CandidateId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static CandidateId random() {
      return new CandidateId(UUID.randomUUID());
   }

   @Nullable
   public static CandidateId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new CandidateId(UUID.fromString(raw.trim()));
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
