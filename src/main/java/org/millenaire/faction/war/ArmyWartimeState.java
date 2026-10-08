package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;

/**
 * Wartime runtime overlay for a Phase 5 army. Does not replace Army membership/strength.
 */
public final class ArmyWartimeState {
   private final ArmyId armyId;
   private final FactionId factionId;
   @Nullable
   private WarId warId;
   @Nullable
   private BattleId battleId;
   private ResourceLocation dimensionId;
   private BlockPos position;
   @Nullable
   private BlockPos destination;
   private ArmyOrder order;
   @Nullable
   private MilitaryObjective objective;
   private int morale = WarConfig.MORALE_BASE;
   private int supplyPercent = WarConfig.SUPPLY_BASE;
   private long lastMovedGameTime;
   private long lastAiEvaluationGameTime;
   private long lastReinforcementGameTime;
   private long retreatImmunityUntil;
   private boolean destroyed;

   public ArmyWartimeState(ArmyId armyId, FactionId factionId, ResourceLocation dimensionId, BlockPos position, long gameTime) {
      this.armyId = Objects.requireNonNull(armyId, "armyId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
      this.position = Objects.requireNonNull(position, "position").immutable();
      this.order = ArmyOrder.none(gameTime);
      this.lastMovedGameTime = gameTime;
   }

   public ArmyId getArmyId() {
      return this.armyId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public WarId getWarId() {
      return this.warId;
   }

   @Nullable
   public BattleId getBattleId() {
      return this.battleId;
   }

   public ResourceLocation getDimensionId() {
      return this.dimensionId;
   }

   public BlockPos getPosition() {
      return this.position;
   }

   @Nullable
   public BlockPos getDestination() {
      return this.destination;
   }

   public ArmyOrder getOrder() {
      return this.order;
   }

   @Nullable
   public MilitaryObjective getObjective() {
      return this.objective;
   }

   public int getMorale() {
      return this.morale;
   }

   public int getSupplyPercent() {
      return this.supplyPercent;
   }

   public long getLastMovedGameTime() {
      return this.lastMovedGameTime;
   }

   public long getLastAiEvaluationGameTime() {
      return this.lastAiEvaluationGameTime;
   }

   public long getLastReinforcementGameTime() {
      return this.lastReinforcementGameTime;
   }

   public long getRetreatImmunityUntil() {
      return this.retreatImmunityUntil;
   }

   public boolean isDestroyed() {
      return this.destroyed;
   }

   public boolean isInBattle() {
      return this.battleId != null;
   }

   void assignWar(@Nullable WarId warId) {
      this.warId = warId;
   }

   void assignBattle(@Nullable BattleId battleId) {
      this.battleId = battleId;
   }

   void setPosition(BlockPos position, ResourceLocation dimensionId, long gameTime) {
      this.position = Objects.requireNonNull(position, "position").immutable();
      this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
      this.lastMovedGameTime = gameTime;
   }

   void setDestination(@Nullable BlockPos destination) {
      this.destination = destination == null ? null : destination.immutable();
   }

   void setOrder(ArmyOrder order) {
      this.order = order == null ? ArmyOrder.none(0L) : order;
      if (order != null && order.getTargetPos() != null) {
         this.destination = order.getTargetPos();
      }
   }

   void setObjective(@Nullable MilitaryObjective objective) {
      this.objective = objective;
   }

   void setMorale(int morale) {
      this.morale = WarConfig.clampMorale(morale);
   }

   void setSupplyPercent(int supplyPercent) {
      this.supplyPercent = WarConfig.clampSupply(supplyPercent);
   }

   void setLastAiEvaluationGameTime(long gameTime) {
      this.lastAiEvaluationGameTime = gameTime;
   }

   void setLastReinforcementGameTime(long gameTime) {
      this.lastReinforcementGameTime = gameTime;
   }

   void setRetreatImmunityUntil(long gameTime) {
      this.retreatImmunityUntil = gameTime;
   }

   void markDestroyed() {
      this.destroyed = true;
      this.battleId = null;
      this.order = ArmyOrder.none(0L);
      this.objective = null;
      this.destination = null;
   }

   void loadState(
      @Nullable WarId warId,
      @Nullable BattleId battleId,
      ResourceLocation dimensionId,
      BlockPos position,
      @Nullable BlockPos destination,
      ArmyOrder order,
      @Nullable MilitaryObjective objective,
      int morale,
      int supplyPercent,
      long lastMovedGameTime,
      long lastAiEvaluationGameTime,
      long lastReinforcementGameTime,
      long retreatImmunityUntil,
      boolean destroyed
   ) {
      this.warId = warId;
      this.battleId = battleId;
      this.dimensionId = dimensionId == null ? this.dimensionId : dimensionId;
      this.position = position == null ? this.position : position.immutable();
      this.destination = destination == null ? null : destination.immutable();
      this.order = order == null ? ArmyOrder.none(0L) : order;
      this.objective = objective;
      this.morale = WarConfig.clampMorale(morale);
      this.supplyPercent = WarConfig.clampSupply(supplyPercent);
      this.lastMovedGameTime = Math.max(0L, lastMovedGameTime);
      this.lastAiEvaluationGameTime = Math.max(0L, lastAiEvaluationGameTime);
      this.lastReinforcementGameTime = Math.max(0L, lastReinforcementGameTime);
      this.retreatImmunityUntil = Math.max(0L, retreatImmunityUntil);
      this.destroyed = destroyed;
   }
}
