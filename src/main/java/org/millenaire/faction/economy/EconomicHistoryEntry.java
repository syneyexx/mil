package org.millenaire.faction.economy;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

/** Bounded faction economic history entry. */
public final class EconomicHistoryEntry {
   private final long gameTime;
   private final EconomicEventType type;
   @Nullable
   private final FactionId otherFactionId;
   @Nullable
   private final EconomicResourceId resourceId;
   private final int quantity;
   private final int value;

   public EconomicHistoryEntry(
      long gameTime,
      EconomicEventType type,
      @Nullable FactionId otherFactionId,
      @Nullable EconomicResourceId resourceId,
      int quantity,
      int value
   ) {
      this.gameTime = gameTime;
      this.type = Objects.requireNonNull(type, "type");
      this.otherFactionId = otherFactionId;
      this.resourceId = resourceId;
      this.quantity = Math.max(0, quantity);
      this.value = Math.max(0, value);
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public EconomicEventType getType() {
      return this.type;
   }

   @Nullable
   public FactionId getOtherFactionId() {
      return this.otherFactionId;
   }

   @Nullable
   public EconomicResourceId getResourceId() {
      return this.resourceId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   public int getValue() {
      return this.value;
   }
}
