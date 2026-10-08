package org.millenaire.faction.territory;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.village.VillageId;

/**
 * Stable territory identity. Settlement-linked territories reuse the settlement UUID.
 */
public record TerritoryId(UUID uuid) {
   public TerritoryId {
      Objects.requireNonNull(uuid, "uuid");
   }

   public static TerritoryId random() {
      return new TerritoryId(UUID.randomUUID());
   }

   public static TerritoryId ofSettlement(VillageId settlementId) {
      Objects.requireNonNull(settlementId, "settlementId");
      return new TerritoryId(settlementId.uuid());
   }

   @Nullable
   public static TerritoryId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return new TerritoryId(UUID.fromString(raw.trim()));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public VillageId asSettlementId() {
      return new VillageId(this.uuid);
   }

   public String asString() {
      return this.uuid.toString();
   }

   @Override
   public String toString() {
      return this.uuid.toString();
   }
}
