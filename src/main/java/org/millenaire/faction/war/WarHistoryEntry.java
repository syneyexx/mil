package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.village.VillageId;

public final class WarHistoryEntry {
   private final long gameTime;
   private final WarEventType eventType;
   @Nullable
   private final FactionId factionA;
   @Nullable
   private final FactionId factionB;
   @Nullable
   private final ArmyId armyId;
   @Nullable
   private final BattleId battleId;
   @Nullable
   private final VillageId settlementId;
   private final int quantity;
   @Nullable
   private final String detail;

   public WarHistoryEntry(
      long gameTime,
      WarEventType eventType,
      @Nullable FactionId factionA,
      @Nullable FactionId factionB,
      @Nullable ArmyId armyId,
      @Nullable BattleId battleId,
      @Nullable VillageId settlementId,
      int quantity,
      @Nullable String detail
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.factionA = factionA;
      this.factionB = factionB;
      this.armyId = armyId;
      this.battleId = battleId;
      this.settlementId = settlementId;
      this.quantity = Math.max(0, quantity);
      this.detail = detail;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public WarEventType getEventType() {
      return this.eventType;
   }

   @Nullable
   public FactionId getFactionA() {
      return this.factionA;
   }

   @Nullable
   public FactionId getFactionB() {
      return this.factionB;
   }

   @Nullable
   public ArmyId getArmyId() {
      return this.armyId;
   }

   @Nullable
   public BattleId getBattleId() {
      return this.battleId;
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
