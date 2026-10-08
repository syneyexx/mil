package org.millenaire.faction.conflict;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.BattleId;
import org.millenaire.faction.war.WarId;
import org.millenaire.village.VillageId;

public final class ConflictHistoryEntry {
   private final long gameTime;
   private final ConflictEventType eventType;
   private final ConflictId conflictId;
   private final FactionId factionId;
   @Nullable
   private final ConflictSideId sideId;
   @Nullable
   private final WarId warId;
   @Nullable
   private final BattleId battleId;
   @Nullable
   private final VillageId settlementId;
   private final int quantity;
   @Nullable
   private final String detail;

   public ConflictHistoryEntry(
      long gameTime,
      ConflictEventType eventType,
      ConflictId conflictId,
      FactionId factionId,
      @Nullable ConflictSideId sideId,
      @Nullable WarId warId,
      @Nullable BattleId battleId,
      @Nullable VillageId settlementId,
      int quantity,
      @Nullable String detail
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.sideId = sideId;
      this.warId = warId;
      this.battleId = battleId;
      this.settlementId = settlementId;
      this.quantity = Math.max(0, quantity);
      this.detail = detail;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public ConflictEventType getEventType() {
      return this.eventType;
   }

   public ConflictId getConflictId() {
      return this.conflictId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public ConflictSideId getSideId() {
      return this.sideId;
   }

   @Nullable
   public WarId getWarId() {
      return this.warId;
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
