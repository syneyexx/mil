package org.millenaire.faction.war;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.military.MilitarySavedData;

/**
 * Query layer for Phase 6 warfare and military AI. Not a player-facing GUI.
 * Safe for Phase 7 conquest systems to consume without mutating internals.
 */
public final class WarQueries {
   private WarQueries() {
   }

   public static WarManager manager(ServerLevel level) {
      return WarSavedData.get(level).getWarManager();
   }

   public static boolean isAtWar(ServerLevel level, FactionId factionId) {
      return manager(level).isAtWar(factionId);
   }

   public static boolean areAtWar(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).areAtWar(a, b);
   }

   public static Collection<FactionId> enemies(ServerLevel level, FactionId factionId) {
      return manager(level).getEnemies(factionId);
   }

   public static Collection<War> warsInvolving(ServerLevel level, FactionId factionId) {
      return manager(level).getWarsInvolving(factionId);
   }

   public static Collection<War> activeWars(ServerLevel level) {
      return manager(level).getActiveWars();
   }

   @Nullable
   public static War war(ServerLevel level, WarId warId) {
      return manager(level).getWar(warId);
   }

   @Nullable
   public static Battle battle(ServerLevel level, BattleId battleId) {
      return manager(level).getBattle(battleId);
   }

   public static int warScore(ServerLevel level, WarId warId) {
      War war = manager(level).getWar(warId);
      return war == null ? 0 : war.getWarScore();
   }

   public static Collection<WarGoal> warGoals(ServerLevel level, WarId warId) {
      War war = manager(level).getWar(warId);
      return war == null ? List.of() : List.copyOf(war.getGoals().values());
   }

   public static List<WarHistoryEntry> warHistory(ServerLevel level, WarId warId) {
      War war = manager(level).getWar(warId);
      return war == null ? List.of() : war.getHistory();
   }

   @Nullable
   public static Battle lastBattle(ServerLevel level, WarId warId) {
      War war = manager(level).getWar(warId);
      if (war == null || war.getBattleIds().isEmpty()) {
         return null;
      }

      Battle latest = null;
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = manager(level).getBattle(battleId);
         if (battle == null) {
            continue;
         }

         if (latest == null || battle.getStartedGameTime() > latest.getStartedGameTime()) {
            latest = battle;
         }
      }

      return latest;
   }

   public static int totalCasualties(ServerLevel level, WarId warId, FactionId factionId) {
      War war = manager(level).getWar(warId);
      if (war == null) {
         return 0;
      }

      WarParticipant participant = war.getParticipant(factionId);
      return participant == null ? 0 : participant.getCasualtiesSuffered();
   }

   public static int peacePressure(ServerLevel level, WarId warId, WarSideId side) {
      return manager(level).peacePressure(warId, side);
   }

   @Nullable
   public static ArmyWartimeState armyState(ServerLevel level, ArmyId armyId) {
      return manager(level).getArmyWartimeState(armyId);
   }

   @Nullable
   public static MilitaryAiEvaluator.NearbyEnemyArmy strongestEnemyArmy(ServerLevel level, WarId warId, FactionId factionId) {
      War war = manager(level).getWar(warId);
      if (war == null) {
         return null;
      }

      WarSideId side = war.getSide(factionId);
      if (side == null) {
         return null;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      return MilitaryAiEvaluator.strongestEnemyArmy(war, side, manager(level), military);
   }

   @Nullable
   public static BlockPos safestRetreat(ServerLevel level, ArmyId armyId) {
      ArmyWartimeState state = manager(level).getArmyWartimeState(armyId);
      if (state == null) {
         return null;
      }

      return ArmyMovementController.retreatPosition(state.getPosition(), state.getPosition().offset(16, 0, 0));
   }
}
