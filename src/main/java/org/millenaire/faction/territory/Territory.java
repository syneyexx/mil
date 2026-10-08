package org.millenaire.faction.territory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Authoritative territorial record for one settlement. Culture stays on the settlement; ownership is political.
 */
public final class Territory {
   private final TerritoryId id;
   private final VillageId settlementId;
   private FactionId ownerFactionId;
   @Nullable
   private FactionId previousOwnerFactionId;
   @Nullable
   private FactionId controllerFactionId;
   @Nullable
   private FactionId occupierFactionId;
   private TerritoryState state = TerritoryState.OWNED;
   private int resistance;
   private int stability = 70;
   private int integrationProgress = TerritoryConfig.INTEGRATION_COMPLETE;
   private int strategicValue = 10;
   private int economicValue = 10;
   private long ownedSinceGameTime;
   private long lastStateChangeGameTime;
   @Nullable
   private OccupationAdministration occupation;
   @Nullable
   private ResourceLocation dimensionId;
   @Nullable
   private BlockPos center;
   private final Set<TerritoryId> neighborIds = new LinkedHashSet<>();
   private final Deque<TerritoryHistoryEntry> history = new ArrayDeque<>();

   public Territory(TerritoryId id, VillageId settlementId, FactionId ownerFactionId, long gameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
      this.ownerFactionId = Objects.requireNonNull(ownerFactionId, "ownerFactionId");
      this.controllerFactionId = ownerFactionId;
      this.ownedSinceGameTime = gameTime;
      this.lastStateChangeGameTime = gameTime;
   }

   public TerritoryId getId() {
      return this.id;
   }

   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public FactionId getOwnerFactionId() {
      return this.ownerFactionId;
   }

   @Nullable
   public FactionId getPreviousOwnerFactionId() {
      return this.previousOwnerFactionId;
   }

   @Nullable
   public FactionId getControllerFactionId() {
      return this.controllerFactionId;
   }

   @Nullable
   public FactionId getOccupierFactionId() {
      return this.occupierFactionId;
   }

   public TerritoryState getState() {
      return this.state;
   }

   public int getResistance() {
      return this.resistance;
   }

   public int getStability() {
      return this.stability;
   }

   public int getIntegrationProgress() {
      return this.integrationProgress;
   }

   public int getStrategicValue() {
      return this.strategicValue;
   }

   public int getEconomicValue() {
      return this.economicValue;
   }

   public long getOwnedSinceGameTime() {
      return this.ownedSinceGameTime;
   }

   public long getLastStateChangeGameTime() {
      return this.lastStateChangeGameTime;
   }

   @Nullable
   public OccupationAdministration getOccupation() {
      return this.occupation;
   }

   @Nullable
   public ResourceLocation getDimensionId() {
      return this.dimensionId;
   }

   @Nullable
   public BlockPos getCenter() {
      return this.center;
   }

   public Set<TerritoryId> getNeighborIds() {
      return Collections.unmodifiableSet(this.neighborIds);
   }

   public List<TerritoryHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public boolean isOccupied() {
      return this.occupierFactionId != null && this.state.isOccupiedControl();
   }

   public int getEconomicEfficiencyPermille() {
      if (this.state == TerritoryState.OCCUPIED && this.occupation != null && this.occupation.isActive()) {
         return Math.max(0, 1000 - this.occupation.getPolicy().economicPenaltyPermille());
      }

      if (this.state == TerritoryState.INTEGRATING || this.state == TerritoryState.TRANSFERRED) {
         return Math.max(0, 1000 - TerritoryConfig.ECONOMIC_INTEGRATING_PENALTY_PERMILLE);
      }

      return 1000;
   }

   void setLocation(@Nullable ResourceLocation dimensionId, @Nullable BlockPos center) {
      this.dimensionId = dimensionId;
      this.center = center == null ? null : center.immutable();
   }

   void setValues(int strategicValue, int economicValue) {
      this.strategicValue = TerritoryConfig.clampNonNegative(strategicValue);
      this.economicValue = TerritoryConfig.clampNonNegative(economicValue);
   }

   void setResistance(int resistance) {
      this.resistance = TerritoryConfig.clampResistance(resistance);
   }

   void setStability(int stability) {
      this.stability = TerritoryConfig.clampStability(stability);
   }

   void setIntegrationProgress(int progress) {
      this.integrationProgress = TerritoryConfig.clampIntegration(progress);
   }

   void setState(TerritoryState state, long gameTime) {
      this.state = state == null ? TerritoryState.OWNED : state;
      this.lastStateChangeGameTime = gameTime;
   }

   void setController(@Nullable FactionId controller) {
      this.controllerFactionId = controller;
   }

   void setOccupier(@Nullable FactionId occupier) {
      this.occupierFactionId = occupier;
   }

   void beginOccupation(OccupationAdministration occupation, long gameTime) {
      this.occupation = Objects.requireNonNull(occupation, "occupation");
      this.occupierFactionId = occupation.getOccupierFactionId();
      this.controllerFactionId = occupation.getOccupierFactionId();
      this.state = TerritoryState.OCCUPIED;
      this.lastStateChangeGameTime = gameTime;
   }

   void endOccupation(long gameTime) {
      if (this.occupation != null) {
         this.occupation.end(gameTime);
      }

      this.occupierFactionId = null;
      this.controllerFactionId = this.ownerFactionId;
      if (this.state == TerritoryState.OCCUPIED || this.state == TerritoryState.CONTESTED) {
         this.state = TerritoryState.OWNED;
      }

      this.lastStateChangeGameTime = gameTime;
   }

   void transferOwnership(FactionId newOwner, long gameTime) {
      Objects.requireNonNull(newOwner, "newOwner");
      this.previousOwnerFactionId = this.ownerFactionId;
      this.ownerFactionId = newOwner;
      this.controllerFactionId = newOwner;
      this.occupierFactionId = null;
      if (this.occupation != null && this.occupation.isActive()) {
         this.occupation.end(gameTime);
      }

      this.occupation = null;
      this.state = TerritoryState.TRANSFERRED;
      this.integrationProgress = TerritoryConfig.INTEGRATION_START;
      this.ownedSinceGameTime = gameTime;
      this.lastStateChangeGameTime = gameTime;
   }

   void markIntegrating(long gameTime) {
      this.state = TerritoryState.INTEGRATING;
      this.lastStateChangeGameTime = gameTime;
   }

   void markIntegrated(long gameTime) {
      this.state = TerritoryState.INTEGRATED;
      this.integrationProgress = TerritoryConfig.INTEGRATION_COMPLETE;
      this.resistance = Math.min(this.resistance, 20);
      this.stability = Math.max(this.stability, 70);
      this.lastStateChangeGameTime = gameTime;
   }

   void setContested(long gameTime) {
      this.state = TerritoryState.CONTESTED;
      this.lastStateChangeGameTime = gameTime;
   }

   void replaceNeighbors(Set<TerritoryId> neighbors) {
      this.neighborIds.clear();
      if (neighbors != null) {
         this.neighborIds.addAll(neighbors);
      }
   }

   void addNeighbor(TerritoryId neighborId) {
      if (neighborId != null && !neighborId.equals(this.id)) {
         this.neighborIds.add(neighborId);
      }
   }

   void removeNeighbor(TerritoryId neighborId) {
      this.neighborIds.remove(neighborId);
   }

   void recordHistory(TerritoryHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);
      while (this.history.size() > TerritoryConfig.MAX_TERRITORY_HISTORY) {
         this.history.removeFirst();
      }
   }

   void loadState(
      FactionId owner,
      @Nullable FactionId previousOwner,
      @Nullable FactionId controller,
      @Nullable FactionId occupier,
      TerritoryState state,
      int resistance,
      int stability,
      int integrationProgress,
      int strategicValue,
      int economicValue,
      long ownedSince,
      long lastChange,
      @Nullable OccupationAdministration occupation,
      @Nullable ResourceLocation dimensionId,
      @Nullable BlockPos center,
      Set<TerritoryId> neighbors,
      List<TerritoryHistoryEntry> history
   ) {
      this.ownerFactionId = Objects.requireNonNull(owner, "owner");
      this.previousOwnerFactionId = previousOwner;
      this.controllerFactionId = controller;
      this.occupierFactionId = occupier;
      this.state = state == null ? TerritoryState.OWNED : state;
      this.resistance = TerritoryConfig.clampResistance(resistance);
      this.stability = TerritoryConfig.clampStability(stability);
      this.integrationProgress = TerritoryConfig.clampIntegration(integrationProgress);
      this.strategicValue = TerritoryConfig.clampNonNegative(strategicValue);
      this.economicValue = TerritoryConfig.clampNonNegative(economicValue);
      this.ownedSinceGameTime = Math.max(0L, ownedSince);
      this.lastStateChangeGameTime = Math.max(0L, lastChange);
      this.occupation = occupation;
      this.dimensionId = dimensionId;
      this.center = center == null ? null : center.immutable();
      this.neighborIds.clear();
      if (neighbors != null) {
         this.neighborIds.addAll(neighbors);
      }

      this.history.clear();
      if (history != null) {
         for (TerritoryHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }
}
