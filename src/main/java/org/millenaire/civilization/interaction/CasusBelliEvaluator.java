package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.diplomacy.AgreementType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;

/**
 * Scores available war justifications. Pure strength scoring is testable without a server.
 */
public final class CasusBelliEvaluator {
   private CasusBelliEvaluator() {
   }

   public static List<CasusBelliType> availableJustifications(ServerLevel level, FactionId attacker, FactionId defender) {
      Map<CasusBelliType, Integer> scores = evaluate(level, attacker, defender);
      List<CasusBelliType> out = new ArrayList<>();
      for (Map.Entry<CasusBelliType, Integer> e : scores.entrySet()) {
         if (e.getValue() > 0) {
            out.add(e.getKey());
         }
      }
      return out;
   }

   public static int scoreStrength(ServerLevel level, FactionId attacker, FactionId defender) {
      int total = 0;
      for (int v : evaluate(level, attacker, defender).values()) {
         total += v;
      }
      return total;
   }

   public static Map<CasusBelliType, Integer> evaluate(
      @Nullable ServerLevel level,
      @Nullable FactionId attacker,
      @Nullable FactionId defender
   ) {
      Map<CasusBelliType, Integer> scores = new EnumMap<>(CasusBelliType.class);
      for (CasusBelliType type : CasusBelliType.values()) {
         scores.put(type, 0);
      }
      if (attacker == null || defender == null) {
         return scores;
      }

      // Pure / offline signals can still be supplied via evaluatePure for tests.
      if (level == null) {
         return scores;
      }

      InteractionSavedData data = InteractionSavedData.get(level);
      int score = RelationshipSavedData.get(level).getRelationshipManager().getRelationshipScore(attacker, defender);

      boolean unpaidBounty = false;
      for (TheaterStory story : data.theaterStories()) {
         if ("bounty".equals(story.type())
            && defender.asString().equals(story.factionA())
            && (story.factionB() == null || attacker.asString().equals(story.factionB()))) {
            unpaidBounty = true;
            break;
         }
      }
      // Also treat culture bounty theater / claim from disrupted corridor + hostile score as bounty unpaid proxy.
      if (unpaidBounty || score <= -50) {
         scores.put(CasusBelliType.BOUNTY_UNPAID, unpaidBounty ? 40 : (score <= -70 ? 25 : 0));
      }

      for (TradeCorridorRecord c : data.corridors().values()) {
         boolean involves = (attacker.asString().equals(c.sourceFaction()) && defender.asString().equals(c.targetFaction()))
            || (defender.asString().equals(c.sourceFaction()) && attacker.asString().equals(c.targetFaction()));
         if (involves && c.disrupted() && c.recognized()) {
            scores.merge(CasusBelliType.CORRIDOR_DISRUPTION, 30, Integer::sum);
         }
      }

      ClaimHeatEntry heat = data.claimHeat().get(pairKey(attacker.asString(), defender.asString()));
      if (heat != null && heat.heat() >= 60) {
         scores.put(CasusBelliType.CLAIM_HEAT, Math.min(40, heat.heat() / 2));
      }

      if (score <= -40) {
         scores.put(CasusBelliType.HONOR_INSULT, Math.min(35, (-score) / 3));
      }

      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      if (diplomacy.hasActiveAgreement(AgreementType.PEACE, attacker, defender)
         || diplomacy.hasActiveAgreement(AgreementType.NON_AGGRESSION, attacker, defender)) {
         // Broken treaty only if currently hostile despite prior peaceful standing historically —
         // approximate: embargo while still having soft treaties terminated recently via theater.
         for (TheaterStory story : data.theaterStories()) {
            if ("treaty_broken".equals(story.type()) && involves(story, attacker, defender)) {
               scores.put(CasusBelliType.BROKEN_TREATY, 45);
               break;
            }
         }
      }

      if (WarSavedData.get(level).getWarManager().areAtWar(attacker, defender)) {
         scores.put(CasusBelliType.RAID_REPRISAL, 20);
      }

      for (TheaterStory story : data.theaterStories()) {
         if ("famine".equals(story.type()) && involves(story, attacker, defender)) {
            scores.put(CasusBelliType.FAMINE_BLOCKADE, 35);
         }
      }

      if (heat != null && heat.heat() >= 40) {
         scores.merge(CasusBelliType.EXPANSION_PRESSURE, heat.heat() / 4, Integer::sum);
      }

      return scores;
   }

   /**
    * Pure scoring for unit tests without a ServerLevel.
    */
   public static Map<CasusBelliType, Integer> evaluatePure(
      boolean bountyUnpaid,
      boolean corridorDisrupted,
      int claimHeat,
      int relationshipScore,
      boolean brokenTreaty,
      boolean famineBlockade
   ) {
      Map<CasusBelliType, Integer> scores = new EnumMap<>(CasusBelliType.class);
      for (CasusBelliType type : CasusBelliType.values()) {
         scores.put(type, 0);
      }
      if (bountyUnpaid) {
         scores.put(CasusBelliType.BOUNTY_UNPAID, 40);
      }
      if (corridorDisrupted) {
         scores.put(CasusBelliType.CORRIDOR_DISRUPTION, 30);
      }
      if (claimHeat >= 60) {
         scores.put(CasusBelliType.CLAIM_HEAT, Math.min(40, claimHeat / 2));
      }
      if (relationshipScore <= -40) {
         scores.put(CasusBelliType.HONOR_INSULT, Math.min(35, (-relationshipScore) / 3));
      }
      if (brokenTreaty) {
         scores.put(CasusBelliType.BROKEN_TREATY, 45);
      }
      if (famineBlockade) {
         scores.put(CasusBelliType.FAMINE_BLOCKADE, 35);
      }
      if (claimHeat >= 40) {
         scores.put(CasusBelliType.EXPANSION_PRESSURE, claimHeat / 4);
      }
      return scores;
   }

   public static int totalStrength(Map<CasusBelliType, Integer> scores) {
      int total = 0;
      for (int v : scores.values()) {
         total += v;
      }
      return total;
   }

   /** Pick a war goal from the strongest available justification. */
   public static WarGoalType suggestWarGoal(ServerLevel level, FactionId attacker, FactionId defender) {
      Map<CasusBelliType, Integer> scores = evaluate(level, attacker, defender);
      CasusBelliType best = null;
      int bestScore = 0;
      for (Map.Entry<CasusBelliType, Integer> e : scores.entrySet()) {
         if (e.getValue() > bestScore) {
            bestScore = e.getValue();
            best = e.getKey();
         }
      }
      if (best == null || bestScore <= 0) {
         return WarGoalType.MILITARY_SUPERIORITY;
      }
      return switch (best) {
         case BOUNTY_UNPAID, HONOR_INSULT, RAID_REPRISAL -> WarGoalType.HUMILIATION;
         case CORRIDOR_DISRUPTION, FAMINE_BLOCKADE, BROKEN_TREATY -> WarGoalType.FORCE_TRADE;
         case CLAIM_HEAT, EXPANSION_PRESSURE -> WarGoalType.CONQUER_SETTLEMENT;
      };
   }

   private static boolean involves(TheaterStory story, FactionId a, FactionId b) {
      String as = a.asString();
      String bs = b.asString();
      return as.equals(story.factionA()) || bs.equals(story.factionA())
         || as.equals(story.factionB()) || bs.equals(story.factionB());
   }

   private static String pairKey(String a, String b) {
      return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
   }
}
