package org.millenaire.faction.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.Army;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryManager;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Deterministic wartime AI evaluation. Uses war participants, army wartime positions, and Phase 7 territory.
 */
public final class MilitaryAiEvaluator {
   private MilitaryAiEvaluator() {
   }

   public record EvaluatedObjective(MilitaryObjective objective, ArmyOrder order) {
   }

   public static EvaluatedObjective evaluate(
      ServerLevel level,
      War war,
      Army army,
      ArmyWartimeState state,
      MilitaryManager military,
      WarManager wars,
      long gameTime
   ) {
      if (war == null || army == null || state == null || state.isDestroyed()) {
         return new EvaluatedObjective(
            new MilitaryObjective(MilitaryObjectiveType.HOLD_POSITION, 100, gameTime, state == null ? null : state.getPosition(), null, null, null),
            ArmyOrder.hold(gameTime, state == null ? null : state.getPosition())
         );
      }

      WarSideId side = war.getSide(army.getFactionId());
      if (side == null) {
         return new EvaluatedObjective(
            new MilitaryObjective(MilitaryObjectiveType.HOLD_POSITION, 100, gameTime, state.getPosition(), null, null, null),
            ArmyOrder.hold(gameTime, state.getPosition())
         );
      }

      if (MoraleCalculator.shouldRetreat(state.getMorale()) || army.getCachedStrength() <= 0) {
         BlockPos safe = findSafeRetreat(level, war, state, side, wars);
         return new EvaluatedObjective(
            new MilitaryObjective(MilitaryObjectiveType.RETREAT, MilitaryObjectiveType.RETREAT.basePriority(), gameTime, safe, null, null, null),
            ArmyOrder.retreat(gameTime, safe)
         );
      }

      ThreatenedSettlement threat = nearestThreatenedSettlement(level, war, army.getFactionId(), side, state, wars);
      if (threat != null) {
         return new EvaluatedObjective(
            new MilitaryObjective(
               MilitaryObjectiveType.DEFEND_SETTLEMENT,
               MilitaryObjectiveType.DEFEND_SETTLEMENT.basePriority() + threat.priorityBonus(),
               gameTime,
               threat.pos(),
               null,
               threat.settlementId(),
               null
            ),
            ArmyOrder.defend(gameTime, threat.settlementId())
         );
      }

      TerritoryTarget liberation = highestValueLiberationTarget(level, war, army.getFactionId(), side, state);
      if (liberation != null) {
         return new EvaluatedObjective(
            new MilitaryObjective(
               MilitaryObjectiveType.ATTACK_SETTLEMENT,
               MilitaryObjectiveType.ATTACK_SETTLEMENT.basePriority() + liberation.valueBonus(),
               gameTime,
               liberation.pos(),
               null,
               liberation.settlementId(),
               null
            ),
            ArmyOrder.attackSettlement(gameTime, liberation.settlementId())
         );
      }

      TerritoryTarget conquest = highestValueConquestTarget(level, war, side, state);
      if (conquest != null) {
         return new EvaluatedObjective(
            new MilitaryObjective(
               MilitaryObjectiveType.ATTACK_SETTLEMENT,
               MilitaryObjectiveType.ATTACK_SETTLEMENT.basePriority() + conquest.valueBonus() - 5,
               gameTime,
               conquest.pos(),
               null,
               conquest.settlementId(),
               null
            ),
            ArmyOrder.attackSettlement(gameTime, conquest.settlementId())
         );
      }

      NearbyEnemyBattle joinable = nearestJoinableBattle(war, state, side, wars);
      if (joinable != null) {
         return new EvaluatedObjective(
            new MilitaryObjective(
               MilitaryObjectiveType.REINFORCE,
               MilitaryObjectiveType.REINFORCE.basePriority(),
               gameTime,
               joinable.pos(),
               null,
               null,
               joinable.battleId()
            ),
            ArmyOrder.reinforce(gameTime, joinable.battleId(), joinable.pos())
         );
      }

      NearbyEnemyArmy enemy = nearestEnemyArmy(war, state, side, wars, military);
      if (enemy != null) {
         int ownStrength = Math.max(1, army.getCachedStrength());
         if (enemy.strength() > ownStrength * 2 && state.getMorale() < 50) {
            BlockPos safe = findSafeRetreat(level, war, state, side, wars);
            return new EvaluatedObjective(
               new MilitaryObjective(MilitaryObjectiveType.RETREAT, MilitaryObjectiveType.RETREAT.basePriority() + 5, gameTime, safe, null, null, null),
               ArmyOrder.retreat(gameTime, safe)
            );
         }

         return new EvaluatedObjective(
            new MilitaryObjective(
               MilitaryObjectiveType.ATTACK_ARMY,
               MilitaryObjectiveType.ATTACK_ARMY.basePriority(),
               gameTime,
               enemy.pos(),
               enemy.armyId(),
               null,
               null
            ),
            ArmyOrder.attack(gameTime, enemy.armyId())
         );
      }

      BlockPos gather = state.getPosition();
      VillageId home = army.getOriginSettlementId() != null ? army.getOriginSettlementId() : army.getStationedSettlementId();
      if (home != null) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(home);
         if (village != null) {
            gather = village.getCenter();
            return new EvaluatedObjective(
               new MilitaryObjective(MilitaryObjectiveType.HOLD_POSITION, MilitaryObjectiveType.HOLD_POSITION.basePriority(), gameTime, gather, null, home, null),
               ArmyOrder.defend(gameTime, home)
            );
         }
      }

      return new EvaluatedObjective(
         new MilitaryObjective(MilitaryObjectiveType.PATROL, MilitaryObjectiveType.PATROL.basePriority(), gameTime, gather, null, null, null),
         ArmyOrder.hold(gameTime, gather)
      );
   }

   @Nullable
   public static NearbyEnemyArmy strongestEnemyArmy(War war, WarSideId side, WarManager wars, MilitaryManager military) {
      NearbyEnemyArmy best = null;
      for (FactionId enemyFaction : war.getActiveFactionIds(side.opposite())) {
         for (Army army : military.getArmies(enemyFaction)) {
            ArmyWartimeState enemyState = wars.getArmyWartimeState(army.getId());
            if (enemyState == null || enemyState.isDestroyed()) {
               continue;
            }

            int strength = army.getCachedStrength();
            if (best == null || strength > best.strength()) {
               best = new NearbyEnemyArmy(army.getId(), enemyState.getPosition(), strength);
            }
         }
      }

      return best;
   }

   @Nullable
   private static NearbyEnemyArmy nearestEnemyArmy(War war, ArmyWartimeState state, WarSideId side, WarManager wars, MilitaryManager military) {
      NearbyEnemyArmy best = null;
      double bestDist = Double.MAX_VALUE;
      for (FactionId enemyFaction : war.getActiveFactionIds(side.opposite())) {
         for (Army army : military.getArmies(enemyFaction)) {
            ArmyWartimeState enemyState = wars.getArmyWartimeState(army.getId());
            if (enemyState == null || enemyState.isDestroyed()) {
               continue;
            }

            if (!ArmyMovementController.sameDimension(state.getDimensionId(), enemyState.getDimensionId())) {
               continue;
            }

            double dist = ArmyMovementController.distance(state.getPosition(), enemyState.getPosition());
            if (dist < bestDist) {
               bestDist = dist;
               best = new NearbyEnemyArmy(army.getId(), enemyState.getPosition(), army.getCachedStrength());
            }
         }
      }

      return best;
   }

   @Nullable
   private static ThreatenedSettlement nearestThreatenedSettlement(
      ServerLevel level,
      War war,
      FactionId factionId,
      WarSideId side,
      ArmyWartimeState state,
      WarManager wars
   ) {
      List<ThreatenedSettlement> threats = new ArrayList<>();
      TerritoryManager territories = TerritorySavedData.get(level).getTerritoryManager();
      for (FactionId friendly : war.getActiveFactionIds(side)) {
         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            Territory territory = territories.getTerritoryForSettlement(village.getId());
            FactionId controller = territory != null && territory.getControllerFactionId() != null
               ? territory.getControllerFactionId()
               : village.getFactionId();
            if (controller == null || !controller.equals(friendly)) {
               continue;
            }

            int valueBonus = territory == null ? 0 : Math.min(20, territory.getStrategicValue() / 5);
            for (FactionId enemyFaction : war.getActiveFactionIds(side.opposite())) {
               for (ArmyWartimeState enemy : wars.getArmyStatesForFaction(enemyFaction)) {
                  if (enemy.isDestroyed()) {
                     continue;
                  }

                  if (!ArmyMovementController.sameDimension(state.getDimensionId(), enemy.getDimensionId())) {
                     continue;
                  }

                  double dist = ArmyMovementController.distance(village.getCenter(), enemy.getPosition());
                  if (dist <= WarConfig.SETTLEMENT_THREAT_RANGE_BLOCKS) {
                     threats.add(new ThreatenedSettlement(village.getId(), village.getCenter(), dist, valueBonus));
                  }
               }
            }
         }
      }

      return threats.stream().min(Comparator.comparingDouble(ThreatenedSettlement::distance)).orElse(null);
   }

   @Nullable
   private static TerritoryTarget highestValueLiberationTarget(
      ServerLevel level,
      War war,
      FactionId factionId,
      WarSideId side,
      ArmyWartimeState state
   ) {
      TerritoryManager territories = TerritorySavedData.get(level).getTerritoryManager();
      TerritoryTarget best = null;
      int bestScore = Integer.MIN_VALUE;
      for (Territory territory : territories.getFactionOwnedTerritories(factionId)) {
         if (!territory.isOccupied()) {
            continue;
         }

         FactionId occupier = territory.getOccupierFactionId();
         if (occupier == null || !war.getActiveFactionIds(side.opposite()).contains(occupier)) {
            continue;
         }

         if (territory.getCenter() == null || territory.getDimensionId() == null) {
            continue;
         }

         if (!ArmyMovementController.sameDimension(state.getDimensionId(), territory.getDimensionId())) {
            continue;
         }

         int score = territory.getStrategicValue() + territory.getEconomicValue() + 30;
         if (score > bestScore) {
            bestScore = score;
            best = new TerritoryTarget(territory.getSettlementId(), territory.getCenter(), Math.min(25, score / 10));
         }
      }

      return best;
   }

   @Nullable
   private static TerritoryTarget highestValueConquestTarget(ServerLevel level, War war, WarSideId side, ArmyWartimeState state) {
      TerritoryManager territories = TerritorySavedData.get(level).getTerritoryManager();
      TerritoryTarget best = null;
      int bestScore = Integer.MIN_VALUE;
      for (FactionId enemy : war.getActiveFactionIds(side.opposite())) {
         for (Territory territory : territories.getFactionOwnedTerritories(enemy)) {
            FactionId controller = territory.getControllerFactionId();
            if (controller == null || !war.getActiveFactionIds(side.opposite()).contains(controller)) {
               continue;
            }

            if (territory.getCenter() == null || territory.getDimensionId() == null) {
               continue;
            }

            if (!ArmyMovementController.sameDimension(state.getDimensionId(), territory.getDimensionId())) {
               continue;
            }

            // Skip settlements already under our occupation; prefer high-value targets.
            if (territory.isOccupied() && war.getActiveFactionIds(side).contains(territory.getOccupierFactionId())) {
               continue;
            }

            int score = territory.getStrategicValue() * 2 + territory.getEconomicValue();
            if (!territories.getFactionClaims(war.getInitiatorFactionId()).isEmpty()) {
               score += 5;
            }

            if (score > bestScore) {
               bestScore = score;
               best = new TerritoryTarget(territory.getSettlementId(), territory.getCenter(), Math.min(20, score / 12));
            }
         }
      }

      return best;
   }

   @Nullable
   private static NearbyEnemyBattle nearestJoinableBattle(War war, ArmyWartimeState state, WarSideId side, WarManager wars) {
      NearbyEnemyBattle best = null;
      double bestDist = Double.MAX_VALUE;
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = wars.getBattle(battleId);
         if (battle == null || !battle.getState().isOpen()) {
            continue;
         }

         if (!ArmyMovementController.sameDimension(state.getDimensionId(), battle.getDimensionId())) {
            continue;
         }

         double dist = ArmyMovementController.distance(state.getPosition(), battle.getLocation());
         if (dist <= WarConfig.BATTLE_JOIN_RANGE_BLOCKS && dist < bestDist) {
            bestDist = dist;
            best = new NearbyEnemyBattle(battleId, battle.getLocation());
         }
      }

      return best;
   }

   private static BlockPos findSafeRetreat(ServerLevel level, War war, ArmyWartimeState state, WarSideId side, WarManager wars) {
      BlockPos awayFrom = state.getPosition();
      double best = Double.MAX_VALUE;
      for (FactionId enemyFaction : war.getActiveFactionIds(side.opposite())) {
         for (ArmyWartimeState other : wars.getArmyStatesForFaction(enemyFaction)) {
            if (other.isDestroyed()) {
               continue;
            }

            double dist = ArmyMovementController.distance(state.getPosition(), other.getPosition());
            if (dist < best) {
               best = dist;
               awayFrom = other.getPosition();
            }
         }
      }

      BlockPos retreat = ArmyMovementController.retreatPosition(state.getPosition(), awayFrom);
      return ArmyMovementController.snapToSurface(level, retreat);
   }

   public record NearbyEnemyArmy(ArmyId armyId, BlockPos pos, int strength) {
   }

   public record NearbyEnemyBattle(BattleId battleId, BlockPos pos) {
   }

   public record ThreatenedSettlement(VillageId settlementId, BlockPos pos, double distance, int priorityBonus) {
   }

   public record TerritoryTarget(VillageId settlementId, BlockPos pos, int valueBonus) {
   }
}
