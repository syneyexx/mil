package org.millenaire.faction.politics;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

public record LeaderId(UUID uuid) {
   public LeaderId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static LeaderId random() {
      return new LeaderId(UUID.randomUUID());
   }

   public static LeaderId ofNpc(UUID npcUuid) {
      return new LeaderId(Objects.requireNonNull(npcUuid, "npcUuid"));
   }

   @Nullable
   public static LeaderId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new LeaderId(UUID.fromString(raw.trim()));
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
