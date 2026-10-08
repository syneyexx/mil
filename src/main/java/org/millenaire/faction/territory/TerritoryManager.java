package org.millenaire.faction.territory;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.economy.EconomyQueries;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.military.Army;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.ArmyState;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.military.MilitaryUnit;
import org.millenaire.faction.military.UnitId;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.ArmyWartimeState;
import org.millenaire.faction.war.Battle;
import org.millenaire.faction.war.BattleId;
import org.millenaire.faction.war.PeaceOutcome;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarGoal;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarId;
import org.millenaire.faction.war.WarManager;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.faction.war.WarSideId;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Authoritative Phase 7 territory, occupation, conquest, and peace-treaty API.
 */
public final class TerritoryManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<TerritoryId, Territory> territories = new LinkedHashMap<>();
   private final Map<VillageId, TerritoryId> bySettlement = new LinkedHashMap<>();
   private final Map<ClaimId, TerritoryClaim> claims = new LinkedHashMap<>();
   private final Map<DisputeId, TerritorialDispute> disputes = new LinkedHashMap<>();
   private final Map<TransferId, OwnershipTransfer> transfers = new LinkedHashMap<>();
   private final Map<PeaceTreatyId, PeaceTreaty> treaties = new LinkedHashMap<>();
   private final Map<FactionId, FactionTerritorialStatusHolder> factionStatus = new LinkedHashMap<>();
   private final List<TerritoryHistoryEntry> globalHistory = new ArrayList<>();
   private final Set<TransferId> pendingTransferIds = new LinkedHashSet<>();
   private final Runnable markDirty;
   private long lastUpdateGameTime;

   TerritoryManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   @Nullable
   public Territory getTerritory(TerritoryId id) {
      return id == null ? null : this.territories.get(id);
   }

   @Nullable
   public Territory getTerritoryForSettlement(VillageId settlementId) {
      if (settlementId == null) {
         return null;
      }

      TerritoryId id = this.bySettlement.get(settlementId);
      return id == null ? null : this.territories.get(id);
   }

   public Collection<Territory> getAllTerritories() {
      return Collections.unmodifiableCollection(this.territories.values());
   }

   public Collection<TerritoryClaim> getAllClaims() {
      return Collections.unmodifiableCollection(this.claims.values());
   }

   public Collection<TerritorialDispute> getAllDisputes() {
      return Collections.unmodifiableCollection(this.disputes.values());
   }

   public Collection<OwnershipTransfer> getAllTransfers() {
      return Collections.unmodifiableCollection(this.transfers.values());
   }

   public Collection<PeaceTreaty> getAllTreaties() {
      return Collections.unmodifiableCollection(this.treaties.values());
   }

   public List<TerritoryHistoryEntry> getGlobalHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.globalHistory));
   }

   @Nullable
   public FactionId getOwner(TerritoryId id) {
      Territory territory = this.getTerritory(id);
      return territory == null ? null : territory.getOwnerFactionId();
   }

   @Nullable
   public FactionId getController(TerritoryId id) {
      Territory territory = this.getTerritory(id);
      return territory == null ? null : territory.getControllerFactionId();
   }

   @Nullable
   public FactionId getOccupier(TerritoryId id) {
      Territory territory = this.getTerritory(id);
      return territory == null ? null : territory.getOccupierFactionId();
   }

   @Nullable
   public FactionId getPreviousOwner(TerritoryId id) {
      Territory territory = this.getTerritory(id);
      return territory == null ? null : territory.getPreviousOwnerFactionId();
   }

   public Collection<Territory> getFactionOwnedTerritories(FactionId factionId) {
      List<Territory> result = new ArrayList<>();
      for (Territory territory : this.territories.values()) {
         if (factionId.equals(territory.getOwnerFactionId())) {
            result.add(territory);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<Territory> getFactionOccupiedTerritories(FactionId factionId) {
      List<Territory> result = new ArrayList<>();
      for (Territory territory : this.territories.values()) {
         if (factionId.equals(territory.getOccupierFactionId())) {
            result.add(territory);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<Territory> getFactionIntegratingTerritories(FactionId factionId) {
      List<Territory> result = new ArrayList<>();
      for (Territory territory : this.territories.values()) {
         if (factionId.equals(territory.getOwnerFactionId()) && territory.getState().isIntegrating()) {
            result.add(territory);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<TerritoryClaim> getFactionClaims(FactionId factionId) {
      List<TerritoryClaim> result = new ArrayList<>();
      for (TerritoryClaim claim : this.claims.values()) {
         if (claim.isActive() && factionId.equals(claim.getClaimantFactionId())) {
            result.add(claim);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<TerritorialDispute> getDisputesForTerritory(TerritoryId territoryId) {
      List<TerritorialDispute> result = new ArrayList<>();
      for (TerritorialDispute dispute : this.disputes.values()) {
         if (dispute.getStatus().isActive() && territoryId.equals(dispute.getTerritoryId())) {
            result.add(dispute);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<Territory> getNeighborTerritories(TerritoryId territoryId) {
      Territory territory = this.getTerritory(territoryId);
      if (territory == null) {
         return List.of();
      }

      List<Territory> result = new ArrayList<>();
      for (TerritoryId neighborId : territory.getNeighborIds()) {
         Territory neighbor = this.territories.get(neighborId);
         if (neighbor != null) {
            result.add(neighbor);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public FactionTerritorialStatus getFactionTerritorialStatus(FactionId factionId) {
      FactionTerritorialStatusHolder holder = this.factionStatus.get(factionId);
      return holder == null ? FactionTerritorialStatus.SETTLED : holder.getStatus();
   }

   public int getOverextension(FactionId factionId) {
      FactionTerritorialStatusHolder holder = this.factionStatus.get(factionId);
      return holder == null ? 0 : holder.getOverextension();
   }

   public int getTerritorialValue(TerritoryId territoryId) {
      Territory territory = this.getTerritory(territoryId);
      return territory == null ? 0 : territory.getStrategicValue() + territory.getEconomicValue();
   }

   public Territory ensureTerritory(ServerLevel level, Village village) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(village, "village");
      Territory existing = this.getTerritoryForSettlement(village.getId());
      if (existing != null) {
         this.refreshTerritoryMeta(level, existing, village);
         return existing;
      }

      FactionId owner = village.getFactionId();
      if (owner == null) {
         Faction faction = FactionSavedData.get(level).getFactionManager().ensureVillageFaction(level, village);
         owner = faction.getId();
      }

      long gameTime = level.getGameTime();
      TerritoryId id = TerritoryId.ofSettlement(village.getId());
      Territory territory = new Territory(id, village.getId(), owner, gameTime);
      this.refreshTerritoryMeta(level, territory, village);
      this.territories.put(id, territory);
      this.bySettlement.put(village.getId(), id);
      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.TERRITORY_CREATED, id, null, owner, owner, null, null, null, 0, null
         ),
         territory
      );
      this.rebuildBordersFor(territory);
      this.updateFactionStatus(level, owner);
      this.markDirty.run();
      return territory;
   }

   public void ensureAllTerritories(ServerLevel level) {
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         this.ensureTerritory(level, village);
      }

      this.rebuildAllBorders(level);
   }

   /**
    * Military capture after a settlement battle victory. Does not permanently transfer ownership.
    */
   public boolean captureSettlement(
      ServerLevel level,
      VillageId settlementId,
      FactionId capturerFactionId,
      @Nullable WarId warId,
      @Nullable BattleId battleId
   ) {
      Objects.requireNonNull(level, "level");
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
      if (village == null || capturerFactionId == null) {
         return false;
      }

      Territory territory = this.ensureTerritory(level, village);
      FactionId owner = territory.getOwnerFactionId();
      if (owner.equals(capturerFactionId)) {
         // Liberation of own occupied territory
         if (territory.isOccupied()) {
            return this.liberateTerritory(level, territory.getId(), capturerFactionId, warId);
         }

         return false;
      }

      long gameTime = level.getGameTime();
      int garrisonNeed = TerritoryValueCalculator.garrisonRequirement(territory);
      OccupationAdministration occupation = new OccupationAdministration(capturerFactionId, gameTime, garrisonNeed);
      territory.beginOccupation(occupation, gameTime);
      territory.setResistance(TerritoryConfig.CAPTURE_RESISTANCE_BASE);
      territory.setStability(TerritoryConfig.CAPTURE_STABILITY_BASE);
      this.assignOccupationGarrison(level, territory, capturerFactionId, gameTime);
      this.createOrStrengthenClaim(level, capturerFactionId, territory.getId(), ClaimType.WAR_GOAL, 40, warId, "capture");
      this.createOrStrengthenClaim(level, owner, territory.getId(), ClaimType.HISTORICAL, 70, warId, "dispossessed");
      this.openDispute(territory.getId(), owner, capturerFactionId, "occupation", warId, gameTime);
      this.record(
         new TerritoryHistoryEntry(
            gameTime,
            TerritoryEventType.TERRITORY_CAPTURED,
            territory.getId(),
            owner,
            owner,
            capturerFactionId,
            capturerFactionId,
            warId,
            null,
            0,
            battleId == null ? null : battleId.asString()
         ),
         territory
      );
      this.record(
         new TerritoryHistoryEntry(
            gameTime,
            TerritoryEventType.OCCUPATION_STARTED,
            territory.getId(),
            owner,
            owner,
            capturerFactionId,
            capturerFactionId,
            warId,
            null,
            garrisonNeed,
            null
         ),
         territory
      );
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, owner, capturerFactionId, TerritoryConfig.RELATIONSHIP_CONQUEST / 2, RelationshipChangeReason.TERRITORY);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, capturerFactionId, owner, TerritoryConfig.RELATIONSHIP_CONQUEST / 2, RelationshipChangeReason.TERRITORY);
      this.updateFactionStatus(level, owner);
      this.updateFactionStatus(level, capturerFactionId);
      PoliticsSavedData.get(level).getPoliticsManager().onTerritoryCaptured(level, capturerFactionId, owner);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Territory {} captured by {} (owner remains {})", territory.getId(), capturerFactionId, owner);
      return true;
   }

   public boolean liberateTerritory(ServerLevel level, TerritoryId territoryId, FactionId liberatorFactionId, @Nullable WarId warId) {
      Objects.requireNonNull(level, "level");
      Territory territory = this.territories.get(territoryId);
      if (territory == null || liberatorFactionId == null || !territory.isOccupied()) {
         return false;
      }

      FactionId owner = territory.getOwnerFactionId();
      FactionId occupier = territory.getOccupierFactionId();
      // Liberation restores the permanent owner's control; only the owner (or an ally acting for them via owner id) may liberate.
      if (!liberatorFactionId.equals(owner)) {
         return false;
      }

      long gameTime = level.getGameTime();
      this.clearOccupationGarrison(level, territory);
      territory.endOccupation(gameTime);
      territory.setResistance(Math.max(0, territory.getResistance() - 20));
      territory.setStability(Math.min(100, territory.getStability() + 15));
      this.resolveDisputesForOwnership(territoryId, owner, gameTime, null, "liberation");
      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.LIBERATION, territoryId, owner, owner, owner, null, warId, null, 0, null
         ),
         territory
      );
      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.OCCUPATION_ENDED, territoryId, owner, owner, owner, null, warId, null, 0, "liberation"
         ),
         territory
      );
      if (occupier != null) {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .increaseRelationship(level, owner, liberatorFactionId, TerritoryConfig.RELATIONSHIP_LIBERATION, RelationshipChangeReason.TERRITORY);
      }

      this.updateFactionStatus(level, owner);
      if (occupier != null) {
         this.updateFactionStatus(level, occupier);
      }

      this.markDirty.run();
      return true;
   }

   public boolean setOccupationPolicy(ServerLevel level, TerritoryId territoryId, OccupationPolicy policy) {
      Territory territory = this.territories.get(territoryId);
      if (territory == null || !territory.isOccupied() || territory.getOccupation() == null || policy == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      territory.getOccupation().setPolicy(policy);
      territory.getOccupation().setRequiredGarrison(TerritoryValueCalculator.garrisonRequirement(territory));
      this.record(
         new TerritoryHistoryEntry(
            gameTime,
            TerritoryEventType.OCCUPATION_POLICY_CHANGED,
            territoryId,
            territory.getOwnerFactionId(),
            territory.getOwnerFactionId(),
            territory.getControllerFactionId(),
            territory.getOccupierFactionId(),
            null,
            null,
            0,
            policy.name()
         ),
         territory
      );
      this.markDirty.run();
      return true;
   }

   /**
    * Permanent ownership transfer. Atomic: validates fully before mutating.
    */
   public boolean transferOwnership(
      ServerLevel level,
      TerritoryId territoryId,
      FactionId newOwner,
      ConquestType type,
      @Nullable WarId warId,
      @Nullable PeaceTreatyId treatyId,
      @Nullable String reason
   ) {
      Objects.requireNonNull(level, "level");
      Territory territory = this.territories.get(territoryId);
      if (territory == null || newOwner == null) {
         return false;
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(newOwner) || !factions.exists(territory.getOwnerFactionId())) {
         return false;
      }

      if (newOwner.equals(territory.getOwnerFactionId()) && !territory.isOccupied()) {
         return false;
      }

      if (warId != null) {
         War war = WarSavedData.get(level).getWarManager().getWar(warId);
         if (war == null) {
            LOGGER.warn("[Millenaire] Rejecting transfer for {} — unknown war {}", territoryId, warId);
            return false;
         }
      }

      // Peace treaty ids may be supplied while finalizePeaceFromWar is still assembling the treaty;
      // validate only when the treaty registry already contains a conflicting finalized entry for this territory.
      if (treatyId != null) {
         for (OwnershipTransfer existing : this.transfers.values()) {
            if (treatyId.equals(existing.getPeaceTreatyId()) && territoryId.equals(existing.getTerritoryId())) {
               LOGGER.warn("[Millenaire] Rejecting duplicate treaty transfer for {} via {}", territoryId, treatyId);
               return false;
            }
         }
      }

      TransferId pendingKey = TransferId.random();
      // Duplicate guard: same settlement already pending
      for (TransferId pending : this.pendingTransferIds) {
         OwnershipTransfer existing = this.transfers.get(pending);
         if (existing != null && existing.getTerritoryId().equals(territoryId)) {
            LOGGER.warn("[Millenaire] Rejecting duplicate pending transfer for {}", territoryId);
            return false;
         }
      }

      this.pendingTransferIds.add(pendingKey);
      try {
         long gameTime = level.getGameTime();
         FactionId oldOwner = territory.getOwnerFactionId();
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(territory.getSettlementId());
         if (village == null) {
            this.pendingTransferIds.remove(pendingKey);
            return false;
         }

         this.clearOccupationGarrison(level, territory);
         territory.transferOwnership(newOwner, gameTime);
         territory.markIntegrating(gameTime);
         territory.setResistance(Math.max(territory.getResistance(), 40));
         territory.setStability(Math.min(territory.getStability(), 50));

         // Authoritative settlement faction ownership aligns with permanent owner only after transfer.
         factions.assignVillage(village, newOwner);

         OwnershipTransfer transfer = new OwnershipTransfer(
            pendingKey,
            territoryId,
            territory.getSettlementId(),
            oldOwner,
            newOwner,
            type,
            gameTime,
            warId,
            treatyId,
            reason
         );
         this.transfers.put(pendingKey, transfer);
         this.pendingTransferIds.remove(pendingKey);

         this.createOrStrengthenClaim(level, oldOwner, territoryId, ClaimType.HISTORICAL, 60, warId, "former_owner");
         this.weakenOrResolveClaim(level, newOwner, territoryId, gameTime);
         this.resolveDisputesForOwnership(territoryId, newOwner, gameTime, treatyId, type.name());
         this.rebuildBordersFor(territory);

         this.record(
            new TerritoryHistoryEntry(
               gameTime, TerritoryEventType.PERMANENT_TRANSFER, territoryId, oldOwner, newOwner, newOwner, null, warId, treatyId, 0, type.name()
            ),
            territory
         );
         this.record(
            new TerritoryHistoryEntry(
               gameTime, TerritoryEventType.INTEGRATION_STARTED, territoryId, oldOwner, newOwner, newOwner, null, warId, treatyId, 0, null
            ),
            territory
         );

         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, oldOwner, newOwner, TerritoryConfig.RELATIONSHIP_CONQUEST, RelationshipChangeReason.TERRITORY);
         this.applyConquestDiplomaticCascade(level, oldOwner, newOwner, gameTime);
         this.notifyNeighbors(level, territory, oldOwner, newOwner, gameTime);
         EconomySavedData.get(level).getTradeManager().refreshAllProfiles(level);
         this.updateFactionStatus(level, oldOwner);
         this.updateFactionStatus(level, newOwner);
         this.trimPeaceTreatyHistory();
         PoliticsSavedData.get(level).getPoliticsManager().onTerritoryCaptured(level, newOwner, oldOwner);
         this.markDirty.run();
         LOGGER.info("[Millenaire] Permanent transfer of {} from {} to {} ({})", territoryId, oldOwner, newOwner, type);
         return true;
      } catch (RuntimeException e) {
         this.pendingTransferIds.remove(pendingKey);
         LOGGER.error("[Millenaire] Transfer aborted for {} — no partial ownership change applied", territoryId, e);
         return false;
      }
   }

   public boolean returnTerritory(ServerLevel level, TerritoryId territoryId, FactionId rightfulOwner, @Nullable WarId warId, @Nullable PeaceTreatyId treatyId) {
      Territory territory = this.territories.get(territoryId);
      if (territory == null || rightfulOwner == null) {
         return false;
      }

      if (territory.isOccupied() && rightfulOwner.equals(territory.getOwnerFactionId())) {
         return this.liberateTerritory(level, territoryId, rightfulOwner, warId);
      }

      return this.transferOwnership(level, territoryId, rightfulOwner, ConquestType.RETURN, warId, treatyId, "return");
   }

   @Nullable
   public TerritoryClaim createClaim(
      ServerLevel level,
      FactionId claimant,
      TerritoryId territoryId,
      ClaimType type,
      int strength,
      @Nullable String reason
   ) {
      if (claimant == null || territoryId == null || this.territories.get(territoryId) == null) {
         return null;
      }

      for (TerritoryClaim existing : this.claims.values()) {
         if (existing.isActive() && existing.getClaimantFactionId().equals(claimant) && existing.getTerritoryId().equals(territoryId)) {
            existing.setStrength(Math.max(existing.getStrength(), strength));
            this.markDirty.run();
            return existing;
         }
      }

      long gameTime = level.getGameTime();
      TerritoryClaim claim = new TerritoryClaim(ClaimId.random(), claimant, territoryId, type, strength, gameTime, reason);
      this.claims.put(claim.getId(), claim);
      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.CLAIM_CREATED, territoryId, null, null, null, null, null, null, strength, type.name()
         ),
         this.territories.get(territoryId)
      );
      this.markDirty.run();
      return claim;
   }

   /**
    * Builds a Phase 7 peace treaty from an ended war and applies territorial terms from occupations/war goals.
    */
   public PeaceTreaty finalizePeaceFromWar(ServerLevel level, War war, PeaceOutcome outcome, @Nullable String reason) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(war, "war");
      long gameTime = level.getGameTime();
      PeaceTreaty treaty = new PeaceTreaty(
         PeaceTreatyId.random(), war.getId(), outcome, gameTime, war.getWarScore(), reason
      );
      for (var participant : war.getParticipants().values()) {
         treaty.addParticipant(participant.getFactionId());
      }

      boolean attackerWins = outcome == PeaceOutcome.ATTACKER_VICTORY || outcome == PeaceOutcome.CAPITULATION_DEFENDER;
      boolean defenderWins = outcome == PeaceOutcome.DEFENDER_VICTORY || outcome == PeaceOutcome.CAPITULATION_ATTACKER;
      boolean white = outcome == PeaceOutcome.WHITE_PEACE || outcome == PeaceOutcome.STATUS_QUO || outcome == PeaceOutcome.EXHAUSTION;

      if (white) {
         treaty.addTerm(new PeaceTerm(PeaceTermType.WHITE_PEACE, null, null, null, 0, "white_peace"));
         // Occupations return to owners on white peace.
         for (Territory territory : this.territories.values()) {
            if (territory.isOccupied()
               && war.isParticipant(territory.getOwnerFactionId())
               && war.isParticipant(territory.getOccupierFactionId())) {
               FactionId previousOccupier = territory.getOccupierFactionId();
               FactionId owner = territory.getOwnerFactionId();
               this.liberateTerritory(level, territory.getId(), owner, war.getId());
               treaty.addTerm(
                  new PeaceTerm(
                     PeaceTermType.RETURN_TERRITORY,
                     territory.getId(),
                     previousOccupier,
                     owner,
                     0,
                     "white_peace_return"
                  )
               );
            }
         }
      } else if (attackerWins) {
         this.applyVictoryTransfers(level, war, treaty, WarSideId.ATTACKER, gameTime);
      } else if (defenderWins) {
         this.applyVictoryTransfers(level, war, treaty, WarSideId.DEFENDER, gameTime);
      }

      this.treaties.put(treaty.getId(), treaty);
      this.trimPeaceTreatyHistory();
      this.recordGlobal(
         new TerritoryHistoryEntry(
            gameTime,
            TerritoryEventType.PEACE_SETTLEMENT,
            TerritoryId.random(),
            war.getInitiatorFactionId(),
            war.getPrimaryTargetFactionId(),
            null,
            null,
            war.getId(),
            treaty.getId(),
            war.getWarScore(),
            outcome.name()
         )
      );
      this.markDirty.run();
      return treaty;
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastUpdateGameTime > 0L && gameTime - this.lastUpdateGameTime < TerritoryConfig.TERRITORY_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastUpdateGameTime = gameTime;
      for (Territory territory : new ArrayList<>(this.territories.values())) {
         this.tickTerritory(level, territory, gameTime);
      }

      for (FactionId factionId : new ArrayList<>(this.factionStatus.keySet())) {
         this.updateFactionStatus(level, factionId);
      }

      this.markDirty.run();
   }

   public void handleSettlementRemoved(ServerLevel level, VillageId settlementId) {
      TerritoryId territoryId = this.bySettlement.remove(settlementId);
      if (territoryId == null) {
         return;
      }

      Territory territory = this.territories.get(territoryId);
      if (territory != null) {
         this.clearOccupationGarrison(level, territory);
         for (TerritoryId neighborId : new ArrayList<>(territory.getNeighborIds())) {
            Territory neighbor = this.territories.get(neighborId);
            if (neighbor != null) {
               neighbor.removeNeighbor(territoryId);
            }
         }
      }

      // Keep territory record for history but mark contested/invalid settlement link via state.
      if (territory != null) {
         long gameTime = level.getGameTime();
         this.record(
            new TerritoryHistoryEntry(
               gameTime,
               TerritoryEventType.OCCUPATION_ENDED,
               territoryId,
               territory.getOwnerFactionId(),
               territory.getOwnerFactionId(),
               null,
               null,
               null,
               null,
               0,
               "settlement_removed"
            ),
            territory
         );
      }

      this.markDirty.run();
   }

   public void handleFactionRemoved(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      for (Territory territory : new ArrayList<>(this.territories.values())) {
         if (factionId.equals(territory.getOccupierFactionId())) {
            this.clearOccupationGarrison(level, territory);
            territory.endOccupation(gameTime);
         }

         if (factionId.equals(territory.getOwnerFactionId())) {
            // Leave historical owner reference; controller cleared if same.
            if (factionId.equals(territory.getControllerFactionId())) {
               territory.setController(null);
            }
         }
      }

      for (TerritoryClaim claim : this.claims.values()) {
         if (claim.isActive() && factionId.equals(claim.getClaimantFactionId())) {
            claim.resolve(gameTime);
         }
      }

      for (TerritorialDispute dispute : this.disputes.values()) {
         if (dispute.getStatus().isActive() && dispute.getClaimantFactionIds().contains(factionId)) {
            dispute.invalidate(gameTime, "faction_removed");
         }
      }

      this.factionStatus.remove(factionId);
      this.markDirty.run();
   }

   public int purgeInvalid(FactionManager factionManager) {
      int removed = 0;
      List<TerritoryId> orphan = new ArrayList<>();
      for (Territory territory : this.territories.values()) {
         if (!factionManager.exists(territory.getOwnerFactionId())) {
            orphan.add(territory.getId());
         }
      }

      // Do not delete historical territories; only clear invalid controllers/occupiers.
      for (Territory territory : this.territories.values()) {
         if (territory.getControllerFactionId() != null && !factionManager.exists(territory.getControllerFactionId())) {
            territory.setController(territory.getOwnerFactionId());
            removed++;
         }

         if (territory.getOccupierFactionId() != null && !factionManager.exists(territory.getOccupierFactionId())) {
            territory.setOccupier(null);
            removed++;
         }
      }

      if (removed > 0 || !orphan.isEmpty()) {
         this.markDirty.run();
      }

      return removed + orphan.size();
   }

   public void validateLoadedConsistency(FactionManager factionManager) {
      this.bySettlement.clear();
      for (Territory territory : this.territories.values()) {
         this.bySettlement.put(territory.getSettlementId(), territory.getId());
         if (territory.getControllerFactionId() != null && !factionManager.exists(territory.getControllerFactionId())) {
            territory.setController(territory.getOwnerFactionId());
         }

         if (territory.getOccupierFactionId() != null && !factionManager.exists(territory.getOccupierFactionId())) {
            territory.setOccupier(null);
         }
      }

      this.claims.entrySet().removeIf(entry -> this.territories.get(entry.getValue().getTerritoryId()) == null);
      this.disputes.entrySet().removeIf(entry -> this.territories.get(entry.getValue().getTerritoryId()) == null);
      this.markDirty.run();
   }

   void loadTerritory(Territory territory) {
      this.territories.put(territory.getId(), territory);
      this.bySettlement.put(territory.getSettlementId(), territory.getId());
   }

   void loadClaim(TerritoryClaim claim) {
      this.claims.put(claim.getId(), claim);
   }

   void loadDispute(TerritorialDispute dispute) {
      this.disputes.put(dispute.getId(), dispute);
   }

   void loadTransfer(OwnershipTransfer transfer) {
      this.transfers.put(transfer.getId(), transfer);
   }

   void loadTreaty(PeaceTreaty treaty) {
      this.treaties.put(treaty.getId(), treaty);
   }

   void loadFactionStatus(FactionId factionId, FactionTerritorialStatusHolder holder) {
      this.factionStatus.put(factionId, holder);
   }

   void loadGlobalHistory(List<TerritoryHistoryEntry> history) {
      this.globalHistory.clear();
      if (history != null) {
         this.globalHistory.addAll(history);
      }
   }

   Map<FactionId, FactionTerritorialStatusHolder> getFactionStatusMap() {
      return Collections.unmodifiableMap(this.factionStatus);
   }

   long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   void setLastUpdateGameTime(long gameTime) {
      this.lastUpdateGameTime = gameTime;
   }

   private void applyVictoryTransfers(ServerLevel level, War war, PeaceTreaty treaty, WarSideId winnerSide, long gameTime) {
      Set<FactionId> winners = war.getActiveFactionIds(winnerSide);
      Set<FactionId> losers = war.getActiveFactionIds(winnerSide.opposite());

      // Transfer occupied territories held by winners against loser owners.
      for (Territory territory : new ArrayList<>(this.territories.values())) {
         if (!territory.isOccupied()) {
            continue;
         }

         FactionId occupier = territory.getOccupierFactionId();
         FactionId owner = territory.getOwnerFactionId();
         if (occupier == null || !winners.contains(occupier) || !losers.contains(owner)) {
            continue;
         }

         boolean goalMatch = false;
         for (WarGoal goal : war.getGoals().values()) {
            if (goal.getType() == WarGoalType.CONQUER_SETTLEMENT
               && winners.contains(goal.getInitiatingFactionId())
               && (goal.getTargetSettlementId() == null || goal.getTargetSettlementId().equals(territory.getSettlementId()))) {
               goalMatch = true;
               break;
            }
         }

         // Also transfer when war score is decisive or settlement was occupied regardless of explicit goal.
         if (!goalMatch && Math.abs(war.getWarScore()) < 40) {
            continue;
         }

         treaty.addTerm(
            new PeaceTerm(PeaceTermType.TERRITORIAL_TRANSFER, territory.getId(), owner, occupier, territory.getStrategicValue(), "war_victory")
         );
         if (this.transferOwnership(level, territory.getId(), occupier, ConquestType.CONQUEST, war.getId(), treaty.getId(), "peace_cession")) {
            OwnershipTransfer last = this.findLatestTransfer(territory.getId());
            if (last != null) {
               treaty.addTransferId(last.getId());
            }
         }
      }

      // Liberation goals: occupied friendly territory restored.
      for (WarGoal goal : war.getGoals().values()) {
         if (goal.getType() != WarGoalType.LIBERATE_SETTLEMENT || goal.getTargetSettlementId() == null) {
            continue;
         }

         Territory territory = this.getTerritoryForSettlement(goal.getTargetSettlementId());
         if (territory != null && territory.isOccupied() && winners.contains(goal.getInitiatingFactionId())) {
            FactionId previousOccupier = territory.getOccupierFactionId();
            FactionId rightfulOwner = territory.getOwnerFactionId();
            this.liberateTerritory(level, territory.getId(), rightfulOwner, war.getId());
            treaty.addTerm(
               new PeaceTerm(
                  PeaceTermType.RETURN_TERRITORY,
                  territory.getId(),
                  previousOccupier,
                  rightfulOwner,
                  0,
                  "liberation_goal"
               )
            );
         }
      }

      if (treaty.getTerms().isEmpty()) {
         treaty.addTerm(new PeaceTerm(PeaceTermType.WHITE_PEACE, null, null, null, 0, "no_transfers"));
      }
   }

   @Nullable
   private OwnershipTransfer findLatestTransfer(TerritoryId territoryId) {
      OwnershipTransfer latest = null;
      for (OwnershipTransfer transfer : this.transfers.values()) {
         if (!transfer.getTerritoryId().equals(territoryId)) {
            continue;
         }

         if (latest == null || transfer.getTransferredGameTime() > latest.getTransferredGameTime()) {
            latest = transfer;
         }
      }

      return latest;
   }

   private void tickTerritory(ServerLevel level, Territory territory, long gameTime) {
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(territory.getSettlementId());
      if (village != null) {
         this.refreshTerritoryMeta(level, territory, village);
      }

      if (territory.isOccupied() && territory.getOccupation() != null && territory.getOccupation().isActive()) {
         OccupationAdministration occupation = territory.getOccupation();
         OccupationPolicy policy = occupation.getPolicy();
         int previousResistance = territory.getResistance();
         int previousStability = territory.getStability();
         territory.setResistance(territory.getResistance() + policy.resistanceDeltaPerUpdate());
         territory.setStability(territory.getStability() + policy.stabilityDeltaPerUpdate());
         this.refreshOccupationGarrisonCount(level, territory);
         if (!occupation.hasAdequateGarrison()) {
            territory.setResistance(territory.getResistance() + 3);
            territory.setStability(territory.getStability() - 2);
         }

         if (this.hasCulturalMismatch(level, territory.getOccupierFactionId(), village)) {
            territory.setResistance(territory.getResistance() + 1);
            territory.setStability(Math.max(0, territory.getStability() - 1));
         }

         int pressure = territory.getResistance() / 2 + (100 - territory.getStability()) / 2;
         occupation.setRebellionPressure(pressure);
         if (previousResistance != territory.getResistance()) {
            this.record(
               new TerritoryHistoryEntry(
                  gameTime,
                  TerritoryEventType.RESISTANCE_CHANGED,
                  territory.getId(),
                  territory.getOwnerFactionId(),
                  territory.getOwnerFactionId(),
                  territory.getControllerFactionId(),
                  territory.getOccupierFactionId(),
                  null,
                  null,
                  territory.getResistance(),
                  null
               ),
               territory
            );
         }

         if (previousStability != territory.getStability()) {
            this.record(
               new TerritoryHistoryEntry(
                  gameTime,
                  TerritoryEventType.STABILITY_CHANGED,
                  territory.getId(),
                  territory.getOwnerFactionId(),
                  territory.getOwnerFactionId(),
                  territory.getControllerFactionId(),
                  territory.getOccupierFactionId(),
                  null,
                  null,
                  territory.getStability(),
                  null
               ),
               territory
            );
         }

         if (territory.getResistance() >= TerritoryConfig.RESISTANCE_COLLAPSE_CONTROL && !occupation.hasAdequateGarrison()) {
            FactionId occupier = territory.getOccupierFactionId();
            this.clearOccupationGarrison(level, territory);
            territory.endOccupation(gameTime);
            territory.setContested(gameTime);
            this.record(
               new TerritoryHistoryEntry(
                  gameTime,
                  TerritoryEventType.CONTROL_COLLAPSED,
                  territory.getId(),
                  territory.getOwnerFactionId(),
                  territory.getOwnerFactionId(),
                  territory.getOwnerFactionId(),
                  null,
                  null,
                  null,
                  territory.getResistance(),
                  "garrison_failure"
               ),
               territory
            );
            if (occupier != null) {
               this.updateFactionStatus(level, occupier);
            }
         }
      }

      if (territory.getState() == TerritoryState.INTEGRATING || territory.getState() == TerritoryState.TRANSFERRED) {
         if (gameTime % TerritoryConfig.INTEGRATION_TICK_INTERVAL < TerritoryConfig.TERRITORY_UPDATE_INTERVAL_TICKS) {
            int delta = 2;
            if (territory.getOccupation() != null) {
               delta = territory.getOccupation().getPolicy().integrationDeltaPerUpdate();
            }

            if (territory.getStability() > 60) {
               delta += 1;
            }

            if (territory.getResistance() > 50) {
               delta = Math.max(0, delta - 1);
            }

            if (this.hasCulturalMismatch(level, territory.getOwnerFactionId(), village)) {
               delta = Math.max(0, delta - 1);
               territory.setResistance(Math.min(100, territory.getResistance() + 1));
            }

            FactionTerritorialStatusHolder holder = this.factionStatus.computeIfAbsent(territory.getOwnerFactionId(), id -> new FactionTerritorialStatusHolder());
            if (holder.getOverextension() > 50) {
               delta = Math.max(0, delta - 1);
            }

            territory.setIntegrationProgress(territory.getIntegrationProgress() + delta);
            territory.setResistance(Math.max(0, territory.getResistance() - 1));
            territory.setStability(Math.min(100, territory.getStability() + 1));
            if (territory.getState() == TerritoryState.TRANSFERRED) {
               territory.markIntegrating(gameTime);
            }

            if (territory.getIntegrationProgress() >= TerritoryConfig.INTEGRATION_COMPLETE) {
               territory.markIntegrated(gameTime);
               this.record(
                  new TerritoryHistoryEntry(
                     gameTime,
                     TerritoryEventType.INTEGRATION_COMPLETED,
                     territory.getId(),
                     territory.getPreviousOwnerFactionId(),
                     territory.getOwnerFactionId(),
                     territory.getOwnerFactionId(),
                     null,
                     null,
                     null,
                     100,
                     null
                  ),
                  territory
               );
            }
         }
      }
   }

   private boolean hasCulturalMismatch(ServerLevel level, @Nullable FactionId rulingFactionId, @Nullable Village village) {
      if (village == null || village.getCultureId() == null || rulingFactionId == null) {
         return false;
      }

      Faction ruling = FactionSavedData.get(level).getFactionManager().getFaction(rulingFactionId);
      if (ruling == null || ruling.getCultureId() == null) {
         return false;
      }

      return !village.getCultureId().equals(ruling.getCultureId());
   }

   private void refreshTerritoryMeta(ServerLevel level, Territory territory, Village village) {
      territory.setLocation(level.dimension().location(), village.getCenter());
      territory.setValues(
         TerritoryValueCalculator.strategicValue(level, village),
         TerritoryValueCalculator.economicValue(level, village)
      );
   }

   private void assignOccupationGarrison(ServerLevel level, Territory territory, FactionId occupier, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      int need = TerritoryValueCalculator.garrisonRequirement(territory);
      Army best = null;
      for (Army army : military.getArmies(occupier)) {
         if (army.getState() == ArmyState.DISBANDED || army.getState() == ArmyState.DISABLED) {
            continue;
         }

         if (best == null || army.getCachedStrength() > best.getCachedStrength()) {
            best = army;
         }
      }

      OccupationAdministration occupation = territory.getOccupation();
      if (occupation == null) {
         return;
      }

      occupation.setRequiredGarrison(need);
      if (best != null) {
         occupation.setGarrisonArmyId(best.getId());
         military.stationArmy(level, best.getId(), territory.getSettlementId());
         military.setArmyOrganizationalState(level, best.getId(), ArmyState.GARRISONED);
         this.refreshOccupationGarrisonCount(level, territory);
      } else {
         occupation.setPresentGarrison(0);
      }
   }

   private void refreshOccupationGarrisonCount(ServerLevel level, Territory territory) {
      OccupationAdministration occupation = territory.getOccupation();
      if (occupation == null) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      int soldiers = 0;
      if (occupation.getGarrisonArmyId() != null) {
         Army army = military.getArmy(occupation.getGarrisonArmyId());
         if (army != null) {
            for (UnitId unitId : army.getUnitIds()) {
               MilitaryUnit unit = military.getUnit(unitId);
               if (unit != null) {
                  soldiers += unit.getSoldierCount();
               }
            }
         }
      }

      // Also count garrisoned units at settlement.
      var org = military.getOrganization(occupation.getOccupierFactionId());
      if (org != null) {
         var garrison = org.getGarrisons().get(territory.getSettlementId());
         if (garrison != null) {
            soldiers = Math.max(soldiers, garrison.getCurrentSoldierCount(military::getUnit));
         }
      }

      occupation.setPresentGarrison(soldiers);
   }

   private void clearOccupationGarrison(ServerLevel level, Territory territory) {
      OccupationAdministration occupation = territory.getOccupation();
      if (occupation == null || occupation.getGarrisonArmyId() == null) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      ArmyId armyId = occupation.getGarrisonArmyId();
      if (military.getArmy(armyId) != null) {
         military.setArmyOrganizationalState(level, armyId, ArmyState.RESERVE);
      }

      occupation.setGarrisonArmyId(null);
      occupation.setPresentGarrison(0);
   }

   private void createOrStrengthenClaim(
      ServerLevel level,
      FactionId claimant,
      TerritoryId territoryId,
      ClaimType type,
      int strength,
      @Nullable WarId warId,
      @Nullable String reason
   ) {
      TerritoryClaim claim = this.createClaim(level, claimant, territoryId, type, strength, reason);
      if (claim != null && warId != null) {
         claim.setOriginWarId(warId);
      }
   }

   private void weakenOrResolveClaim(ServerLevel level, FactionId newOwner, TerritoryId territoryId, long gameTime) {
      for (TerritoryClaim claim : this.claims.values()) {
         if (!claim.isActive() || !claim.getTerritoryId().equals(territoryId)) {
            continue;
         }

         if (claim.getClaimantFactionId().equals(newOwner)) {
            claim.resolve(gameTime);
            this.record(
               new TerritoryHistoryEntry(
                  gameTime, TerritoryEventType.CLAIM_RESOLVED, territoryId, null, newOwner, null, null, null, null, 0, "satisfied"
               ),
               this.territories.get(territoryId)
            );
         }
      }
   }

   private void openDispute(
      TerritoryId territoryId,
      FactionId a,
      FactionId b,
      String reason,
      @Nullable WarId warId,
      long gameTime
   ) {
      for (TerritorialDispute existing : this.disputes.values()) {
         if (existing.getStatus().isActive() && existing.getTerritoryId().equals(territoryId)) {
            existing.addClaimant(a);
            existing.addClaimant(b);
            existing.setRelatedWarId(warId);
            return;
         }
      }

      TerritorialDispute dispute = new TerritorialDispute(DisputeId.random(), territoryId, reason, gameTime);
      dispute.addClaimant(a);
      dispute.addClaimant(b);
      dispute.setRelatedWarId(warId);
      this.disputes.put(dispute.getId(), dispute);
      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.DISPUTE_CREATED, territoryId, a, b, null, null, warId, null, 0, reason
         ),
         this.territories.get(territoryId)
      );
   }

   private void resolveDisputesForOwnership(
      TerritoryId territoryId,
      FactionId newOwner,
      long gameTime,
      @Nullable PeaceTreatyId treatyId,
      @Nullable String reason
   ) {
      for (TerritorialDispute dispute : this.disputes.values()) {
         if (dispute.getStatus().isActive() && dispute.getTerritoryId().equals(territoryId)) {
            dispute.resolve(gameTime, treatyId, reason);
            this.record(
               new TerritoryHistoryEntry(
                  gameTime, TerritoryEventType.DISPUTE_RESOLVED, territoryId, null, newOwner, null, null, null, treatyId, 0, reason
               ),
               this.territories.get(territoryId)
            );
         }
      }
   }

   private void rebuildAllBorders(ServerLevel level) {
      for (Territory territory : this.territories.values()) {
         territory.replaceNeighbors(Set.of());
      }

      List<Territory> list = new ArrayList<>(this.territories.values());
      for (int i = 0; i < list.size(); i++) {
         Territory a = list.get(i);
         if (a.getCenter() == null || a.getDimensionId() == null) {
            continue;
         }

         for (int j = i + 1; j < list.size(); j++) {
            Territory b = list.get(j);
            if (b.getCenter() == null || b.getDimensionId() == null) {
               continue;
            }

            if (!a.getDimensionId().equals(b.getDimensionId())) {
               continue;
            }

            double dist = Math.sqrt(a.getCenter().distToCenterSqr(b.getCenter().getX() + 0.5, b.getCenter().getY() + 0.5, b.getCenter().getZ() + 0.5));
            if (dist <= TerritoryConfig.NEIGHBOR_DISTANCE_BLOCKS) {
               a.addNeighbor(b.getId());
               b.addNeighbor(a.getId());
            }
         }
      }
   }

   private void rebuildBordersFor(Territory territory) {
      if (territory == null || territory.getCenter() == null || territory.getDimensionId() == null) {
         return;
      }

      for (TerritoryId oldNeighborId : new ArrayList<>(territory.getNeighborIds())) {
         Territory oldNeighbor = this.territories.get(oldNeighborId);
         if (oldNeighbor != null) {
            oldNeighbor.removeNeighbor(territory.getId());
         }
      }

      territory.replaceNeighbors(Set.of());
      for (Territory other : this.territories.values()) {
         if (other.getId().equals(territory.getId()) || other.getCenter() == null || other.getDimensionId() == null) {
            continue;
         }

         if (!territory.getDimensionId().equals(other.getDimensionId())) {
            continue;
         }

         double dist = Math.sqrt(
            territory.getCenter().distToCenterSqr(other.getCenter().getX() + 0.5, other.getCenter().getY() + 0.5, other.getCenter().getZ() + 0.5)
         );
         if (dist <= TerritoryConfig.NEIGHBOR_DISTANCE_BLOCKS) {
            territory.addNeighbor(other.getId());
            other.addNeighbor(territory.getId());
         }
      }
   }

   private void applyConquestDiplomaticCascade(ServerLevel level, FactionId oldOwner, FactionId newOwner, long ignoredGameTime) {
      for (FactionId ally : DiplomacySavedData.get(level).getDiplomaticManager().getAllies(oldOwner)) {
         if (ally.equals(newOwner)) {
            continue;
         }

         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, ally, newOwner, Math.abs(TerritoryConfig.RELATIONSHIP_ANNEX_NEIGHBOR), RelationshipChangeReason.TERRITORY);
      }
   }

   private void trimPeaceTreatyHistory() {
      if (this.treaties.size() <= TerritoryConfig.MAX_PEACE_TREATY_HISTORY) {
         return;
      }

      List<PeaceTreaty> ordered = new ArrayList<>(this.treaties.values());
      ordered.sort((a, b) -> Long.compare(a.getSettledGameTime(), b.getSettledGameTime()));
      int removeCount = ordered.size() - TerritoryConfig.MAX_PEACE_TREATY_HISTORY;
      for (int i = 0; i < removeCount; i++) {
         this.treaties.remove(ordered.get(i).getId());
      }
   }

   private void notifyNeighbors(ServerLevel level, Territory territory, FactionId oldOwner, FactionId newOwner, long gameTime) {
      for (TerritoryId neighborId : territory.getNeighborIds()) {
         Territory neighbor = this.territories.get(neighborId);
         if (neighbor == null) {
            continue;
         }

         FactionId neighborOwner = neighbor.getOwnerFactionId();
         if (!neighborOwner.equals(oldOwner) && !neighborOwner.equals(newOwner)) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .decreaseRelationship(level, neighborOwner, newOwner, Math.abs(TerritoryConfig.RELATIONSHIP_ANNEX_NEIGHBOR), RelationshipChangeReason.TERRITORY);
         }
      }

      this.record(
         new TerritoryHistoryEntry(
            gameTime, TerritoryEventType.BORDER_UPDATED, territory.getId(), oldOwner, newOwner, newOwner, null, null, null, territory.getNeighborIds().size(), null
         ),
         territory
      );
      this.rebuildAllBorders(level);
   }

   private void updateFactionStatus(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return;
      }

      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      FactionTerritorialStatusHolder holder = this.factionStatus.computeIfAbsent(factionId, id -> new FactionTerritorialStatusHolder());
      int owned = this.getFactionOwnedTerritories(factionId).size();
      int occupied = this.getFactionOccupiedTerritories(factionId).size();
      int integrating = this.getFactionIntegratingTerritories(factionId).size();
      FactionEconomicProfile profile = EconomyQueries.profile(level, factionId);
      int stock = profile == null ? 0 : profile.getLastStockValue();
      int capacity = TerritoryConfig.ADMIN_CAPACITY_BASE + stock / 1000 * TerritoryConfig.ADMIN_CAPACITY_PER_STOCK_1000;
      holder.setAdministrativeCapacity(capacity);
      int over = occupied * TerritoryConfig.OVEREXTENSION_PER_OCCUPIED + integrating * TerritoryConfig.OVEREXTENSION_PER_INTEGRATING;
      if (occupied + integrating > capacity) {
         over += (occupied + integrating - capacity) * 5;
      }

      holder.setOverextension(Math.min(TerritoryConfig.OVEREXTENSION_MAX, over));
      if (owned <= 0 && (faction == null || faction.getSettlementCount() <= 0)) {
         FactionTerritorialStatus previous = holder.getStatus();
         holder.setStatus(FactionTerritorialStatus.LANDLESS);
         if (previous != FactionTerritorialStatus.LANDLESS) {
            this.recordGlobal(
               new TerritoryHistoryEntry(
                  level.getGameTime(),
                  TerritoryEventType.FACTION_LANDLESS,
                  TerritoryId.random(),
                  factionId,
                  null,
                  null,
                  null,
                  null,
                  null,
                  0,
                  null
               )
            );
         }
      } else if (owned <= 0) {
         holder.setStatus(FactionTerritorialStatus.DISPLACED);
      } else {
         holder.setStatus(FactionTerritorialStatus.SETTLED);
      }
   }

   private void record(TerritoryHistoryEntry entry, @Nullable Territory territory) {
      if (territory != null) {
         territory.recordHistory(entry);
      }

      this.recordGlobal(entry);
   }

   private void recordGlobal(TerritoryHistoryEntry entry) {
      this.globalHistory.add(entry);
      while (this.globalHistory.size() > TerritoryConfig.MAX_GLOBAL_HISTORY) {
         this.globalHistory.remove(0);
      }
   }
}
