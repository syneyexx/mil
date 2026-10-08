package org.millenaire.faction.territory;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PeaceTerm {
   private final PeaceTermType type;
   @Nullable
   private final TerritoryId territoryId;
   @Nullable
   private final FactionId fromFactionId;
   @Nullable
   private final FactionId toFactionId;
   private final int quantity;
   @Nullable
   private final String note;

   public PeaceTerm(
      PeaceTermType type,
      @Nullable TerritoryId territoryId,
      @Nullable FactionId fromFactionId,
      @Nullable FactionId toFactionId,
      int quantity,
      @Nullable String note
   ) {
      this.type = Objects.requireNonNull(type, "type");
      this.territoryId = territoryId;
      this.fromFactionId = fromFactionId;
      this.toFactionId = toFactionId;
      this.quantity = Math.max(0, quantity);
      this.note = note;
   }

   public PeaceTermType getType() {
      return this.type;
   }

   @Nullable
   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   @Nullable
   public FactionId getFromFactionId() {
      return this.fromFactionId;
   }

   @Nullable
   public FactionId getToFactionId() {
      return this.toFactionId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   @Nullable
   public String getNote() {
      return this.note;
   }
}
