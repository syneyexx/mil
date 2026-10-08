package org.millenaire.faction.war;

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
import org.millenaire.faction.military.ArmyId;
import org.millenaire.village.VillageId;

public final class Battle {
   private final BattleId id;
   private final WarId warId;
   private final ResourceLocation dimensionId;
   private final BlockPos location;
   @Nullable
   private final VillageId settlementId;
   private BattleState state;
   private final Set<ArmyId> attackerArmyIds = new LinkedHashSet<>();
   private final Set<ArmyId> defenderArmyIds = new LinkedHashSet<>();
   private final Set<FactionId> attackerFactionIds = new LinkedHashSet<>();
   private final Set<FactionId> defenderFactionIds = new LinkedHashSet<>();
   private int attackerCasualties;
   private int defenderCasualties;
   private int attackerMorale = WarConfig.MORALE_BASE;
   private int defenderMorale = WarConfig.MORALE_BASE;
   private int roundIndex;
   private TerrainType terrain = TerrainType.PLAINS;
   @Nullable
   private WarSideId victorSide;
   private final long startedGameTime;
   private long endedGameTime;
   private long lastRoundGameTime;
   private final Deque<WarHistoryEntry> history = new ArrayDeque<>();

   public Battle(
      BattleId id,
      WarId warId,
      ResourceLocation dimensionId,
      BlockPos location,
      long gameTime,
      @Nullable VillageId settlementId
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.warId = Objects.requireNonNull(warId, "warId");
      this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
      this.location = Objects.requireNonNull(location, "location").immutable();
      this.settlementId = settlementId;
      this.state = BattleState.PREPARING;
      this.startedGameTime = gameTime;
      this.lastRoundGameTime = gameTime;
   }

   public BattleId getId() {
      return this.id;
   }

   public WarId getWarId() {
      return this.warId;
   }

   public ResourceLocation getDimensionId() {
      return this.dimensionId;
   }

   public BlockPos getLocation() {
      return this.location;
   }

   @Nullable
   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public BattleState getState() {
      return this.state;
   }

   public Set<ArmyId> getAttackerArmyIds() {
      return Collections.unmodifiableSet(this.attackerArmyIds);
   }

   public Set<ArmyId> getDefenderArmyIds() {
      return Collections.unmodifiableSet(this.defenderArmyIds);
   }

   public Set<FactionId> getAttackerFactionIds() {
      return Collections.unmodifiableSet(this.attackerFactionIds);
   }

   public Set<FactionId> getDefenderFactionIds() {
      return Collections.unmodifiableSet(this.defenderFactionIds);
   }

   public int getAttackerCasualties() {
      return this.attackerCasualties;
   }

   public int getDefenderCasualties() {
      return this.defenderCasualties;
   }

   public int getAttackerMorale() {
      return this.attackerMorale;
   }

   public int getDefenderMorale() {
      return this.defenderMorale;
   }

   public int getRoundIndex() {
      return this.roundIndex;
   }

   public TerrainType getTerrain() {
      return this.terrain;
   }

   @Nullable
   public WarSideId getVictorSide() {
      return this.victorSide;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   public long getLastRoundGameTime() {
      return this.lastRoundGameTime;
   }

   public List<WarHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public boolean containsArmy(ArmyId armyId) {
      return armyId != null && (this.attackerArmyIds.contains(armyId) || this.defenderArmyIds.contains(armyId));
   }

   void setState(BattleState state) {
      this.state = state == null ? BattleState.PREPARING : state;
   }

   void setTerrain(TerrainType terrain) {
      this.terrain = terrain == null ? TerrainType.PLAINS : terrain;
   }

   boolean addArmy(ArmyId armyId, FactionId factionId, WarSideId side) {
      Objects.requireNonNull(armyId, "armyId");
      Objects.requireNonNull(factionId, "factionId");
      Objects.requireNonNull(side, "side");
      if (side == WarSideId.ATTACKER) {
         this.attackerFactionIds.add(factionId);
         return this.attackerArmyIds.add(armyId);
      }

      this.defenderFactionIds.add(factionId);
      return this.defenderArmyIds.add(armyId);
   }

   boolean removeArmy(ArmyId armyId) {
      return this.attackerArmyIds.remove(armyId) | this.defenderArmyIds.remove(armyId);
   }

   void addCasualties(WarSideId side, int amount) {
      if (amount <= 0) {
         return;
      }

      if (side == WarSideId.ATTACKER) {
         this.attackerCasualties = WarConfig.clampNonNegative(this.attackerCasualties + amount);
      } else {
         this.defenderCasualties = WarConfig.clampNonNegative(this.defenderCasualties + amount);
      }
   }

   void setMorale(WarSideId side, int morale) {
      if (side == WarSideId.ATTACKER) {
         this.attackerMorale = WarConfig.clampMorale(morale);
      } else {
         this.defenderMorale = WarConfig.clampMorale(morale);
      }
   }

   void advanceRound(long gameTime) {
      this.roundIndex++;
      this.lastRoundGameTime = gameTime;
   }

   void decide(WarSideId victor, long gameTime) {
      this.victorSide = victor;
      this.state = BattleState.DECIDED;
      this.endedGameTime = gameTime;
   }

   void finish(long gameTime) {
      this.state = BattleState.FINISHED;
      if (this.endedGameTime <= 0L) {
         this.endedGameTime = gameTime;
      }
   }

   void recordHistory(WarHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);
      while (this.history.size() > WarConfig.MAX_BATTLE_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadState(
      BattleState state,
      Set<ArmyId> attackerArmies,
      Set<ArmyId> defenderArmies,
      Set<FactionId> attackerFactions,
      Set<FactionId> defenderFactions,
      int attackerCasualties,
      int defenderCasualties,
      int attackerMorale,
      int defenderMorale,
      int roundIndex,
      TerrainType terrain,
      @Nullable WarSideId victorSide,
      long endedGameTime,
      long lastRoundGameTime,
      List<WarHistoryEntry> history
   ) {
      this.state = state == null ? BattleState.PREPARING : state;
      this.attackerArmyIds.clear();
      if (attackerArmies != null) {
         this.attackerArmyIds.addAll(attackerArmies);
      }

      this.defenderArmyIds.clear();
      if (defenderArmies != null) {
         this.defenderArmyIds.addAll(defenderArmies);
      }

      this.attackerFactionIds.clear();
      if (attackerFactions != null) {
         this.attackerFactionIds.addAll(attackerFactions);
      }

      this.defenderFactionIds.clear();
      if (defenderFactions != null) {
         this.defenderFactionIds.addAll(defenderFactions);
      }

      this.attackerCasualties = WarConfig.clampNonNegative(attackerCasualties);
      this.defenderCasualties = WarConfig.clampNonNegative(defenderCasualties);
      this.attackerMorale = WarConfig.clampMorale(attackerMorale);
      this.defenderMorale = WarConfig.clampMorale(defenderMorale);
      this.roundIndex = WarConfig.clampNonNegative(roundIndex);
      this.terrain = terrain == null ? TerrainType.PLAINS : terrain;
      this.victorSide = victorSide;
      this.endedGameTime = Math.max(0L, endedGameTime);
      this.lastRoundGameTime = Math.max(0L, lastRoundGameTime);
      this.history.clear();
      if (history != null) {
         for (WarHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }
}
