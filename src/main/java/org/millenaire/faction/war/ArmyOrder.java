package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.village.VillageId;

public final class ArmyOrder {
   private final ArmyOrderType type;
   @Nullable
   private final BlockPos targetPos;
   @Nullable
   private final ArmyId targetArmyId;
   @Nullable
   private final VillageId targetSettlementId;
   @Nullable
   private final BattleId targetBattleId;
   private final long issuedGameTime;
   @Nullable
   private final String note;

   public ArmyOrder(
      ArmyOrderType type,
      long issuedGameTime,
      @Nullable BlockPos targetPos,
      @Nullable ArmyId targetArmyId,
      @Nullable VillageId targetSettlementId,
      @Nullable BattleId targetBattleId,
      @Nullable String note
   ) {
      this.type = type == null ? ArmyOrderType.NONE : type;
      this.issuedGameTime = issuedGameTime;
      this.targetPos = targetPos == null ? null : targetPos.immutable();
      this.targetArmyId = targetArmyId;
      this.targetSettlementId = targetSettlementId;
      this.targetBattleId = targetBattleId;
      this.note = note;
   }

   public static ArmyOrder none(long gameTime) {
      return new ArmyOrder(ArmyOrderType.NONE, gameTime, null, null, null, null, null);
   }

   public static ArmyOrder hold(long gameTime, @Nullable BlockPos pos) {
      return new ArmyOrder(ArmyOrderType.HOLD, gameTime, pos, null, null, null, null);
   }

   public static ArmyOrder move(long gameTime, BlockPos pos) {
      return new ArmyOrder(ArmyOrderType.MOVE, gameTime, Objects.requireNonNull(pos, "pos"), null, null, null, null);
   }

   public static ArmyOrder attack(long gameTime, ArmyId targetArmyId) {
      return new ArmyOrder(ArmyOrderType.ATTACK, gameTime, null, Objects.requireNonNull(targetArmyId, "targetArmyId"), null, null, null);
   }

   public static ArmyOrder defend(long gameTime, VillageId settlementId) {
      return new ArmyOrder(ArmyOrderType.DEFEND, gameTime, null, null, Objects.requireNonNull(settlementId, "settlementId"), null, null);
   }

   public static ArmyOrder attackSettlement(long gameTime, VillageId settlementId) {
      return new ArmyOrder(ArmyOrderType.ATTACK, gameTime, null, null, Objects.requireNonNull(settlementId, "settlementId"), null, "attack_settlement");
   }

   public static ArmyOrder retreat(long gameTime, BlockPos safePos) {
      return new ArmyOrder(ArmyOrderType.RETREAT, gameTime, Objects.requireNonNull(safePos, "safePos"), null, null, null, "retreat");
   }

   public static ArmyOrder reinforce(long gameTime, BattleId battleId, @Nullable BlockPos battlePos) {
      return new ArmyOrder(ArmyOrderType.FOLLOW, gameTime, battlePos, null, null, battleId, "reinforce");
   }

   public ArmyOrderType getType() {
      return this.type;
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

   public long getIssuedGameTime() {
      return this.issuedGameTime;
   }

   @Nullable
   public String getNote() {
      return this.note;
   }
}
