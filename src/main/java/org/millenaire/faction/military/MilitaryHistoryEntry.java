package org.millenaire.faction.military;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

public final class MilitaryHistoryEntry {
   private final long gameTime;
   private final MilitaryEventType eventType;
   private final FactionId factionId;
   @Nullable
   private final ArmyId armyId;
   @Nullable
   private final UnitId unitId;
   @Nullable
   private final CommanderId commanderId;
   @Nullable
   private final VillageId settlementId;
   private final int quantity;
   @Nullable
   private final String detail;

   public MilitaryHistoryEntry(
      long gameTime,
      MilitaryEventType eventType,
      FactionId factionId,
      @Nullable ArmyId armyId,
      @Nullable UnitId unitId,
      @Nullable CommanderId commanderId,
      @Nullable VillageId settlementId,
      int quantity,
      @Nullable String detail
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.armyId = armyId;
      this.unitId = unitId;
      this.commanderId = commanderId;
      this.settlementId = settlementId;
      this.quantity = Math.max(0, quantity);
      this.detail = detail;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public MilitaryEventType getEventType() {
      return this.eventType;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public ArmyId getArmyId() {
      return this.armyId;
   }

   @Nullable
   public UnitId getUnitId() {
      return this.unitId;
   }

   @Nullable
   public CommanderId getCommanderId() {
      return this.commanderId;
   }

   @Nullable
   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   @Nullable
   public String getDetail() {
      return this.detail;
   }
}
