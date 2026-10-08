package org.millenaire.faction.conflict;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.politics.LeaderId;
import org.millenaire.faction.politics.PoliticalGroupId;
import org.millenaire.faction.territory.TerritoryId;
import org.millenaire.village.VillageId;

public final class ConflictSide {
   private final ConflictSideId id;
   private final ConflictId conflictId;
   private FactionId factionId;
   private boolean governmentSide;
   @Nullable
   private LeaderId leaderId;
   @Nullable
   private String displayName;
   private int conflictScore;
   private int casualties;
   private int exhaustion;
   private int support;
   private int legitimacy;
   private RebelObjective primaryObjective = RebelObjective.CHANGE_POLICY;
   private final Set<PoliticalGroupId> supporterGroupIds = new LinkedHashSet<>();
   private final Set<ArmyId> armyIds = new LinkedHashSet<>();
   private final Set<TerritoryId> controlledTerritoryIds = new LinkedHashSet<>();
   private final Set<VillageId> controlledSettlementIds = new LinkedHashSet<>();
   private boolean active = true;

   public ConflictSide(ConflictSideId id, ConflictId conflictId, FactionId factionId, boolean governmentSide) {
      this.id = Objects.requireNonNull(id, "id");
      this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.governmentSide = governmentSide;
      this.primaryObjective = governmentSide ? RebelObjective.LIBERATION : RebelObjective.OVERTHROW_GOVERNMENT;
   }

   public ConflictSideId getId() {
      return this.id;
   }

   public ConflictId getConflictId() {
      return this.conflictId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public boolean isGovernmentSide() {
      return this.governmentSide;
   }

   @Nullable
   public LeaderId getLeaderId() {
      return this.leaderId;
   }

   @Nullable
   public String getDisplayName() {
      return this.displayName;
   }

   public int getConflictScore() {
      return this.conflictScore;
   }

   public int getCasualties() {
      return this.casualties;
   }

   public int getExhaustion() {
      return this.exhaustion;
   }

   public int getSupport() {
      return this.support;
   }

   public int getLegitimacy() {
      return this.legitimacy;
   }

   public RebelObjective getPrimaryObjective() {
      return this.primaryObjective;
   }

   public Set<PoliticalGroupId> getSupporterGroupIds() {
      return Collections.unmodifiableSet(this.supporterGroupIds);
   }

   public Set<ArmyId> getArmyIds() {
      return Collections.unmodifiableSet(this.armyIds);
   }

   public Set<TerritoryId> getControlledTerritoryIds() {
      return Collections.unmodifiableSet(this.controlledTerritoryIds);
   }

   public Set<VillageId> getControlledSettlementIds() {
      return Collections.unmodifiableSet(this.controlledSettlementIds);
   }

   public boolean isActive() {
      return this.active;
   }

   void reassignFaction(FactionId newFactionId) {
      this.factionId = Objects.requireNonNull(newFactionId, "newFactionId");
   }

   void setLeaderId(@Nullable LeaderId leaderId) {
      this.leaderId = leaderId;
   }

   void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   void setConflictScore(int score) {
      this.conflictScore = Math.max(-100, Math.min(100, score));
   }

   void addCasualties(int amount) {
      this.casualties = ConflictConfig.clampNonNegative(this.casualties + Math.max(0, amount));
   }

   void setExhaustion(int exhaustion) {
      this.exhaustion = ConflictConfig.clamp(exhaustion);
   }

   void setSupport(int support) {
      this.support = ConflictConfig.clamp(support);
   }

   void setLegitimacy(int legitimacy) {
      this.legitimacy = ConflictConfig.clamp(legitimacy);
   }

   void setPrimaryObjective(RebelObjective objective) {
      this.primaryObjective = objective == null ? RebelObjective.CHANGE_POLICY : objective;
   }

   void addSupporter(PoliticalGroupId groupId) {
      if (groupId != null) {
         this.supporterGroupIds.add(groupId);
      }
   }

   void addArmy(ArmyId armyId) {
      if (armyId != null) {
         this.armyIds.add(armyId);
      }
   }

   void removeArmy(ArmyId armyId) {
      this.armyIds.remove(armyId);
   }

   void addTerritory(TerritoryId territoryId) {
      if (territoryId != null) {
         this.controlledTerritoryIds.add(territoryId);
      }
   }

   void addSettlement(VillageId settlementId) {
      if (settlementId != null) {
         this.controlledSettlementIds.add(settlementId);
      }
   }

   void removeSettlement(VillageId settlementId) {
      this.controlledSettlementIds.remove(settlementId);
   }

   void removeTerritory(TerritoryId territoryId) {
      this.controlledTerritoryIds.remove(territoryId);
   }

   void deactivate() {
      this.active = false;
   }

   void loadState(
      boolean governmentSide,
      @Nullable LeaderId leaderId,
      @Nullable String displayName,
      int conflictScore,
      int casualties,
      int exhaustion,
      int support,
      int legitimacy,
      RebelObjective objective,
      Set<PoliticalGroupId> supporters,
      Set<ArmyId> armies,
      Set<TerritoryId> territories,
      Set<VillageId> settlements,
      boolean active
   ) {
      this.governmentSide = governmentSide;
      this.leaderId = leaderId;
      this.displayName = displayName;
      this.conflictScore = Math.max(-100, Math.min(100, conflictScore));
      this.casualties = ConflictConfig.clampNonNegative(casualties);
      this.exhaustion = ConflictConfig.clamp(exhaustion);
      this.support = ConflictConfig.clamp(support);
      this.legitimacy = ConflictConfig.clamp(legitimacy);
      this.primaryObjective = objective == null ? RebelObjective.CHANGE_POLICY : objective;
      this.supporterGroupIds.clear();
      if (supporters != null) {
         this.supporterGroupIds.addAll(supporters);
      }

      this.armyIds.clear();
      if (armies != null) {
         this.armyIds.addAll(armies);
      }

      this.controlledTerritoryIds.clear();
      if (territories != null) {
         this.controlledTerritoryIds.addAll(territories);
      }

      this.controlledSettlementIds.clear();
      if (settlements != null) {
         this.controlledSettlementIds.addAll(settlements);
      }

      this.active = active;
   }
}
