package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.village.VillageId;

public final class WarGoal {
   private final WarGoalId id;
   private final WarId warId;
   private final WarGoalType type;
   private final FactionId initiatingFactionId;
   @Nullable
   private final FactionId targetFactionId;
   @Nullable
   private final ArmyId targetArmyId;
   @Nullable
   private final VillageId targetSettlementId;
   private int progressPercent;
   private WarGoalStatus status;
   private final long createdGameTime;
   private long completedGameTime;

   public WarGoal(
      WarGoalId id,
      WarId warId,
      WarGoalType type,
      FactionId initiatingFactionId,
      long gameTime,
      @Nullable FactionId targetFactionId,
      @Nullable ArmyId targetArmyId,
      @Nullable VillageId targetSettlementId
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.warId = Objects.requireNonNull(warId, "warId");
      this.type = Objects.requireNonNull(type, "type");
      this.initiatingFactionId = Objects.requireNonNull(initiatingFactionId, "initiatingFactionId");
      this.targetFactionId = targetFactionId;
      this.targetArmyId = targetArmyId;
      this.targetSettlementId = targetSettlementId;
      this.progressPercent = 0;
      this.status = WarGoalStatus.ACTIVE;
      this.createdGameTime = gameTime;
   }

   public WarGoalId getId() {
      return this.id;
   }

   public WarId getWarId() {
      return this.warId;
   }

   public WarGoalType getType() {
      return this.type;
   }

   public FactionId getInitiatingFactionId() {
      return this.initiatingFactionId;
   }

   @Nullable
   public FactionId getTargetFactionId() {
      return this.targetFactionId;
   }

   @Nullable
   public ArmyId getTargetArmyId() {
      return this.targetArmyId;
   }

   @Nullable
   public VillageId getTargetSettlementId() {
      return this.targetSettlementId;
   }

   public int getProgressPercent() {
      return this.progressPercent;
   }

   public WarGoalStatus getStatus() {
      return this.status;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getCompletedGameTime() {
      return this.completedGameTime;
   }

   void setProgress(int progressPercent, long gameTime) {
      this.progressPercent = WarConfig.clampPercent(progressPercent);
      if (this.progressPercent > 0 && this.status == WarGoalStatus.ACTIVE) {
         this.status = WarGoalStatus.PROGRESSING;
      }

      if (this.progressPercent >= 100 && !this.status.isTerminal()) {
         this.status = WarGoalStatus.COMPLETED;
         this.completedGameTime = gameTime;
      }
   }

   void markFailed(long gameTime) {
      this.status = WarGoalStatus.FAILED;
      this.completedGameTime = gameTime;
   }

   void markCancelled(long gameTime) {
      this.status = WarGoalStatus.CANCELLED;
      this.completedGameTime = gameTime;
   }

   void loadState(int progressPercent, WarGoalStatus status, long completedGameTime) {
      this.progressPercent = WarConfig.clampPercent(progressPercent);
      this.status = status == null ? WarGoalStatus.ACTIVE : status;
      this.completedGameTime = Math.max(0L, completedGameTime);
   }
}
