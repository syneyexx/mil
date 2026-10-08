package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.village.VillageId;

public final class MilitaryObjective {
   private final MilitaryObjectiveType type;
   private final int priority;
   @Nullable
   private final BlockPos targetPos;
   @Nullable
   private final ArmyId targetArmyId;
   @Nullable
   private final VillageId targetSettlementId;
   @Nullable
   private final BattleId targetBattleId;
   private final long assignedGameTime;

   public MilitaryObjective(
      MilitaryObjectiveType type,
      int priority,
      long assignedGameTime,
      @Nullable BlockPos targetPos,
      @Nullable ArmyId targetArmyId,
      @Nullable VillageId targetSettlementId,
      @Nullable BattleId targetBattleId
   ) {
      this.type = Objects.requireNonNull(type, "type");
      this.priority = priority;
      this.assignedGameTime = assignedGameTime;
      this.targetPos = targetPos == null ? null : targetPos.immutable();
      this.targetArmyId = targetArmyId;
      this.targetSettlementId = targetSettlementId;
      this.targetBattleId = targetBattleId;
   }

   public MilitaryObjectiveType getType() {
      return this.type;
   }

   public int getPriority() {
      return this.priority;
   }

   @Nullable
   public BlockPos getTargetPos() {
      return this.targetPos;
   }

   @Nullable
   public ArmyId getTargetArmyId() {
      return this.targetArmyId;
   }

   @Nullable
   public VillageId getTargetSettlementId() {
      return this.targetSettlementId;
   }

   @Nullable
   public BattleId getTargetBattleId() {
      return this.targetBattleId;
   }

   public long getAssignedGameTime() {
      return this.assignedGameTime;
   }
}
