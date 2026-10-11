package org.millenaire.faction.diplomacy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.interaction.CasusBelliEvaluator;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.CulturalTemperament;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.politics.ForeignPolicyPreference;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;
import org.slf4j.Logger;

/**
 * Budget-capped autonomous diplomacy for NPC culture factions.
 * Uses relationship scores + political foreign-policy pressure + cultural temperament biases;
 * does not invent wars without hostility.
 */
public final class DiplomaticAiEvaluator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int FRIENDLY_TRADE_SCORE = 35;
   private static final int HOSTILE_EMBARGO_SCORE = -40;
   private static final int HOSTILE_WAR_SCORE = -70;
   private static final int NAP_MID_HOSTILE_SCORE = -55;

   private DiplomaticAiEvaluator() {
   }

   public static void tick(ServerLevel level) {
      if (!isEnabled() || level == null) {
         return;
      }
      int budget = budget();
      if (budget <= 0) {
         return;
      }

      List<Faction> cultures = new ArrayList<>();
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() == FactionType.CULTURE && faction.getCultureId() != null && faction.getSettlementCount() > 0) {
            cultures.add(faction);
         }
      }
      if (cultures.size() < 2) {
         return;
      }
      cultures.sort(Comparator.comparing(f -> f.getId().asString()));

      int cursor = Math.floorMod((int)(level.getGameTime() / 200L), cultures.size());
      int actions = 0;
      int scanned = 0;
      int maxScans = Math.min(cultures.size() * cultures.size(), budget * 8);

      for (int i = 0; i < cultures.size() && actions < budget && scanned < maxScans; i++) {
         Faction source = cultures.get((cursor + i) % cultures.size());
         for (Faction target : cultures) {
            if (actions >= budget || scanned >= maxScans) {
               break;
            }
            if (source.getId().equals(target.getId())) {
               continue;
            }
            scanned++;
            if (tryAct(level, source, target)) {
               actions++;
            }
         }
      }
      if (actions > 0) {
         LOGGER.debug("[Millenaire] Diplomatic AI performed {} actions (budget={})", actions, budget);
      }
   }

   private static boolean tryAct(ServerLevel level, Faction source, Faction target) {
      FactionId a = source.getId();
      FactionId b = target.getId();
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      var relationships = RelationshipSavedData.get(level).getRelationshipManager();
      int score = relationships.getRelationshipScore(a, b);
      boolean atWar = WarSavedData.get(level).getWarManager().areAtWar(a, b);

      CulturalTemperament temperament = temperamentOf(source);

      // PoliticsQueries foreign-policy pressure (government stances) already drives thresholds below.
      // Temperament.martial adds a small bonus on top of WAR preference for martial cultures.
      int tradePressure = PoliticsQueries.foreignPolicyPressure(level, a, ForeignPolicyPreference.TRADE);
      int warPressure = PoliticsQueries.foreignPolicyPressure(level, a, ForeignPolicyPreference.WAR)
         + temperament.martial() / 25;
      int peacePressure = PoliticsQueries.foreignPolicyPressure(level, a, ForeignPolicyPreference.PEACE);
      int isolation = PoliticsQueries.foreignPolicyPressure(level, a, ForeignPolicyPreference.ISOLATION);

      // Stronger isolation early-out when culture DNA is isolationist.
      int isolationThreshold = temperament.isolation() >= 70 ? 25 : 40;
      if (isolation >= isolationThreshold || temperament.isolation() >= 85) {
         return false;
      }

      int tradeNeed = FRIENDLY_TRADE_SCORE + temperament.tradeScoreBias();
      int embargoNeed = HOSTILE_EMBARGO_SCORE - temperament.embargoScoreBias();
      int warNeed = HOSTILE_WAR_SCORE - temperament.warScoreBias();

      // Friendly trade
      if (!atWar
         && score >= tradeNeed
         && tradePressure >= 15
         && !diplomacy.hasTradeAgreement(a, b)
         && !diplomacy.isEmbargoed(a, b)
         && !diplomacy.isEmbargoed(b, a)) {
         var agreement = diplomacy.establishTradeAgreement(level, a, b, 0L);
         if (agreement != null) {
            relationships.modifyRelationship(level, a, b, 5, RelationshipChangeReason.TRADE);
            return true;
         }
      }

      // Prefer NAP before war for honor/isolation temperaments in the mid-hostile band.
      if (!atWar
         && temperament.prefersNapBeforeWar()
         && score <= NAP_MID_HOSTILE_SCORE
         && score > warNeed
         && !diplomacy.hasActiveAgreement(AgreementType.NON_AGGRESSION, a, b)
         && !diplomacy.isPeaceful(a, b)) {
         var nap = diplomacy.establishNonAggressionPact(level, a, b, 0L);
         if (nap != null) {
            return true;
         }
      }

      // Embargo when hostile
      if (!atWar
         && score <= embargoNeed
         && warPressure >= 10
         && !diplomacy.isEmbargoed(a, b)) {
         var agreement = diplomacy.imposeEmbargo(level, a, b, 0L, "diplomatic_ai");
         if (agreement != null) {
            relationships.modifyRelationship(level, a, b, -5, RelationshipChangeReason.INSULT);
            return true;
         }
      }

      // War when very hostile + war pressure (and not already peaceful treaty)
      if (!atWar
         && score <= warNeed
         && warPressure >= 25
         && peacePressure < warPressure
         && !diplomacy.isPeaceful(a, b)) {
         int casus = CasusBelliEvaluator.scoreStrength(level, a, b);
         if (casus < 15 && temperament.honor() >= 70) {
            // Honorbound cultures hesitate without a clear justification.
            return false;
         }
         WarGoalType goal = casus >= 40 ? WarGoalType.HUMILIATION : WarGoalType.MILITARY_SUPERIORITY;
         var war = WarSavedData.get(level).getWarManager().declareWar(level, a, b, goal);
         return war != null;
      }

      // Soft peace recovery when both sides are no longer hostile and not at war
      if (!atWar
         && score >= 10
         && peacePressure >= 20
         && diplomacy.isEmbargoed(a, b)) {
         return diplomacy.terminateActiveAgreement(
            level, AgreementType.EMBARGO, a, b, AgreementTerminationReason.EXPLICIT
         );
      }

      return false;
   }

   private static CulturalTemperament temperamentOf(Faction source) {
      if (source.getCultureId() == null) {
         return CulturalTemperament.DEFAULT;
      }
      Culture culture = ModCultures.getCulture(source.getCultureId());
      return culture == null ? CulturalTemperament.DEFAULT : culture.temperament();
   }

   private static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationDiplomaticAiEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static int budget() {
      try {
         return MillenaireServerConfig.SERVER.civilizationDiplomaticAiBudget.getAsInt();
      } catch (IllegalStateException e) {
         return 2;
      }
   }
}
