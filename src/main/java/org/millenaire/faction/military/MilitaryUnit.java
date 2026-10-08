package org.millenaire.faction.military;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Logical military unit (soldier counts, not one persistent entity per soldier).
 */
public final class MilitaryUnit {
   private final UnitId id;
   private FactionId factionId;
   private final ResourceLocation unitTypeId;
   private int soldierCount;
   private int maxStrength;
   private int readinessPercent;
   private TrainingLevel trainingLevel;
   private EquipmentLevel equipmentLevel;
   private UnitStatus status;
   @Nullable
   private ArmyId armyId;
   @Nullable
   private VillageId garrisonSettlementId;
   @Nullable
   private VillageId originSettlementId;
   @Nullable
   private CommanderId commanderId;
   private long createdGameTime;
   private long lastUpdatedGameTime;

   public MilitaryUnit(
      UnitId id,
      FactionId factionId,
      ResourceLocation unitTypeId,
      int soldierCount,
      int maxStrength,
      long gameTime,
      @Nullable VillageId originSettlementId
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.unitTypeId = Objects.requireNonNull(unitTypeId, "unitTypeId");
      this.soldierCount = MilitaryConfig.clampNonNegative(soldierCount);
      this.maxStrength = Math.max(1, maxStrength);
      this.readinessPercent = MilitaryConfig.READINESS_PARTIAL;
      this.trainingLevel = TrainingLevel.PARTIAL;
      UnitTypeDefinition definition = UnitTypeRegistry.get(unitTypeId);
      this.equipmentLevel = definition == null ? EquipmentLevel.BASIC : definition.defaultEquipmentLevel();
      this.status = UnitStatus.RESERVE;
      this.originSettlementId = originSettlementId;
      this.createdGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   public UnitId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   void reassignFaction(FactionId newFactionId) {
      this.factionId = Objects.requireNonNull(newFactionId, "newFactionId");
   }

   public ResourceLocation getUnitTypeId() {
      return this.unitTypeId;
   }

   public int getSoldierCount() {
      return this.soldierCount;
   }

   public int getMaxStrength() {
      return this.maxStrength;
   }

   public int getReadinessPercent() {
      return this.readinessPercent;
   }

   public TrainingLevel getTrainingLevel() {
      return this.trainingLevel;
   }

   public EquipmentLevel getEquipmentLevel() {
      return this.equipmentLevel;
   }

   public UnitStatus getStatus() {
      return this.status;
   }

   @Nullable
   public ArmyId getArmyId() {
      return this.armyId;
   }

   @Nullable
   public VillageId getGarrisonSettlementId() {
      return this.garrisonSettlementId;
   }

   @Nullable
   public VillageId getOriginSettlementId() {
      return this.originSettlementId;
   }

   @Nullable
   public CommanderId getCommanderId() {
      return this.commanderId;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   void setSoldierCount(int count, long gameTime) {
      this.soldierCount = MilitaryConfig.clampNonNegative(count);
      this.lastUpdatedGameTime = gameTime;
   }

   void setReadinessPercent(int readinessPercent, long gameTime) {
      this.readinessPercent = MilitaryConfig.clampPercent(readinessPercent);
      this.trainingLevel = TrainingLevel.fromReadiness(this.readinessPercent);
      this.lastUpdatedGameTime = gameTime;
   }

   void setEquipmentLevel(EquipmentLevel level, long gameTime) {
      this.equipmentLevel = level == null ? EquipmentLevel.NONE : level;
      this.lastUpdatedGameTime = gameTime;
   }

   void assignToArmy(ArmyId armyId, long gameTime) {
      this.armyId = armyId;
      this.garrisonSettlementId = null;
      this.status = UnitStatus.IN_ARMY;
      this.lastUpdatedGameTime = gameTime;
   }

   void assignToReserve(long gameTime) {
      this.armyId = null;
      this.garrisonSettlementId = null;
      this.status = UnitStatus.RESERVE;
      this.lastUpdatedGameTime = gameTime;
   }

   void assignToGarrison(VillageId settlementId, long gameTime) {
      Objects.requireNonNull(settlementId, "settlementId");
      this.armyId = null;
      this.garrisonSettlementId = settlementId;
      this.status = UnitStatus.GARRISONED;
      this.lastUpdatedGameTime = gameTime;
   }

   void setCommanderId(@Nullable CommanderId commanderId, long gameTime) {
      this.commanderId = commanderId;
      this.lastUpdatedGameTime = gameTime;
   }

   void clearOriginIfMatches(VillageId settlementId) {
      if (settlementId != null && settlementId.equals(this.originSettlementId)) {
         this.originSettlementId = null;
      }

      if (settlementId != null && settlementId.equals(this.garrisonSettlementId)) {
         this.garrisonSettlementId = null;
         if (this.status == UnitStatus.GARRISONED) {
            this.status = UnitStatus.RESERVE;
         }
      }
   }

   void loadState(
      int soldierCount,
      int maxStrength,
      int readinessPercent,
      TrainingLevel trainingLevel,
      EquipmentLevel equipmentLevel,
      UnitStatus status,
      @Nullable ArmyId armyId,
      @Nullable VillageId garrisonSettlementId,
      @Nullable VillageId originSettlementId,
      @Nullable CommanderId commanderId,
      long createdGameTime,
      long lastUpdatedGameTime
   ) {
      this.soldierCount = MilitaryConfig.clampNonNegative(soldierCount);
      this.maxStrength = Math.max(1, maxStrength);
      this.readinessPercent = MilitaryConfig.clampPercent(readinessPercent);
      this.trainingLevel = trainingLevel == null ? TrainingLevel.fromReadiness(this.readinessPercent) : trainingLevel;
      this.equipmentLevel = equipmentLevel == null ? EquipmentLevel.BASIC : equipmentLevel;
      this.status = status == null ? UnitStatus.RESERVE : status;
      this.armyId = armyId;
      this.garrisonSettlementId = garrisonSettlementId;
      this.originSettlementId = originSettlementId;
      this.commanderId = commanderId;
      this.createdGameTime = Math.max(0L, createdGameTime);
      this.lastUpdatedGameTime = Math.max(0L, lastUpdatedGameTime);
   }
}
