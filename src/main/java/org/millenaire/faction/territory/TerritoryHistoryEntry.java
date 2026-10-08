package org.millenaire.faction.territory;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.WarId;

public final class TerritoryHistoryEntry {
   private final long gameTime;
   private final TerritoryEventType eventType;
   private final TerritoryId territoryId;
   @Nullable
   private final FactionId oldOwner;
   @Nullable
   private final FactionId newOwner;
   @Nullable
   private final FactionId controller;
   @Nullable
   private final FactionId occupier;
   @Nullable
   private final WarId warId;
   @Nullable
   private final PeaceTreatyId peaceTreatyId;
   private final int quantity;
   @Nullable
   private final String detail;

   public TerritoryHistoryEntry(
      long gameTime,
      TerritoryEventType eventType,
      TerritoryId territoryId,
      @Nullable FactionId oldOwner,
      @Nullable FactionId newOwner,
      @Nullable FactionId controller,
      @Nullable FactionId occupier,
      @Nullable WarId warId,
      @Nullable PeaceTreatyId peaceTreatyId,
      int quantity,
      @Nullable String detail
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.territoryId = Objects.requireNonNull(territoryId, "territoryId");
      this.oldOwner = oldOwner;
      this.newOwner = newOwner;
      this.controller = controller;
      this.occupier = occupier;
      this.warId = warId;
      this.peaceTreatyId = peaceTreatyId;
      this.quantity = Math.max(0, quantity);
      this.detail = detail;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public TerritoryEventType getEventType() {
      return this.eventType;
   }

   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   @Nullable
   public FactionId getOldOwner() {
      return this.oldOwner;
   }

   @Nullable
   public FactionId getNewOwner() {
      return this.newOwner;
   }

   @Nullable
   public FactionId getController() {
      return this.controller;
   }

   @Nullable
   public FactionId getOccupier() {
      return this.occupier;
   }

   @Nullable
   public WarId getWarId() {
      return this.warId;
   }

   @Nullable
   public PeaceTreatyId getPeaceTreatyId() {
      return this.peaceTreatyId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   @Nullable
   public String getDetail() {
      return this.detail;
   }
}
