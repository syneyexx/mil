package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.civilization.presentation.CivilizationActionService.ActionResult;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;

/**
 * Server handlers for civilization interaction action ids.
 */
public final class InteractionActions {
   private InteractionActions() {
   }

   public static ActionResult offerCaravanCharter(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId partner = FactionId.tryParse(targetFactionId);
      if (partner == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      if (!CharterColonyService.offerCharter(level, sovereign.getId(), partner)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      }
      return ActionResult.ok("civilization.millenaire.action.offer_caravan_charter.ok");
   }

   public static ActionResult emergencyRelief(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId recipient = FactionId.tryParse(targetFactionId);
      if (recipient == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      if (!FamineAlertService.emergencyRelief(level, sovereign.getId(), recipient)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      }
      return ActionResult.ok("civilization.millenaire.action.emergency_relief.ok");
   }

   public static ActionResult payBloodMoney(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      ResourceLocation cultureId = null;
      FactionId cultureFaction = FactionId.tryParse(targetFactionId);
      if (cultureFaction != null) {
         Faction f = FactionSavedData.get(level).getFactionManager().getFaction(cultureFaction);
         if (f != null && f.getCultureId() != null) {
            cultureId = f.getCultureId();
         }
      }
      if (cultureId == null && village.getCultureId() != null) {
         cultureId = village.getCultureId();
      }
      if (cultureId == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      if (!JusticeDiplomacyService.payBloodMoney(level, player, cultureId, cultureFaction)) {
         return ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      }
      return ActionResult.ok("civilization.millenaire.action.pay_blood_money.ok");
   }

   public static ActionResult respondCallToArms(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId, boolean accept
   ) {
      InteractionSavedData data = InteractionSavedData.get(level);
      List<CallToArmsOffer> next = new ArrayList<>();
      boolean matched = false;
      for (CallToArmsOffer offer : data.callToArms()) {
         if (!matched
            && offer.status() == CallToArmsOffer.Status.PENDING
            && offer.targetPlayerFaction().equals(sovereign.getId().asString())
            && (targetFactionId == null || targetFactionId.isBlank() || offer.enemyFaction().equals(targetFactionId)
               || offer.allyFaction().equals(targetFactionId))) {
            matched = true;
            if (accept) {
               FactionId enemy = FactionId.tryParse(offer.enemyFaction());
               if (enemy != null && !WarSavedData.get(level).getWarManager().areAtWar(sovereign.getId(), enemy)) {
                  WarSavedData.get(level).getWarManager().declareWar(level, sovereign.getId(), enemy, WarGoalType.DEFEND_SETTLEMENT);
               }
               next.add(offer.withStatus(CallToArmsOffer.Status.ACCEPTED));
               LegitimacyService.add(level, sovereign.getId(), 5, "call_to_arms");
            } else {
               next.add(offer.withStatus(CallToArmsOffer.Status.REFUSED));
               FactionId ally = FactionId.tryParse(offer.allyFaction());
               if (ally != null) {
                  RelationshipSavedData.get(level)
                     .getRelationshipManager()
                     .modifyRelationship(level, ally, sovereign.getId(), -10, RelationshipChangeReason.INSULT);
               }
               LegitimacyService.add(level, sovereign.getId(), -3, "call_to_arms_refuse");
            }
         } else {
            next.add(offer);
         }
      }
      if (!matched) {
         return ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      }
      data.setCallToArms(next);
      return ActionResult.ok(accept
         ? "civilization.millenaire.action.respond_call_to_arms.ok"
         : "civilization.millenaire.action.refuse_call_to_arms.ok");
   }

   public static ActionResult offerProtectorate(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId vassal = FactionId.tryParse(targetFactionId);
      if (vassal == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      data.addProtectorate(new ProtectorateLink(
         sovereign.getId().asString(),
         vassal.asString(),
         level.getGameTime() + InteractionConfig.PROTECTORATE_TRIBUTE_INTERVAL_TICKS,
         true
      ));
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, vassal, sovereign.getId(), 8, RelationshipChangeReason.ALLIANCE);
      data.addTheaterStory(new TheaterStory(
         "protectorate",
         "civilization.millenaire.theater.protectorate",
         sovereign.getId().asString(),
         vassal.asString(),
         2,
         level.getGameTime()
      ));
      return ActionResult.ok("civilization.millenaire.action.offer_protectorate.ok");
   }

   public static ActionResult respondEnvoy(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId, boolean accept
   ) {
      InteractionSavedData data = InteractionSavedData.get(level);
      List<EnvoyMission> next = new ArrayList<>();
      boolean matched = false;
      for (EnvoyMission mission : data.envoys()) {
         if (!matched
            && mission.pending()
            && mission.playerFaction().equals(sovereign.getId().asString())
            && (targetFactionId == null || targetFactionId.isBlank() || mission.cultureFaction().equals(targetFactionId))) {
            matched = true;
            next.add(mission.withPending(false));
            FactionId culture = FactionId.tryParse(mission.cultureFaction());
            if (accept) {
               if (culture != null) {
                  RelationshipSavedData.get(level)
                     .getRelationshipManager()
                     .modifyRelationship(level, culture, sovereign.getId(), 6, RelationshipChangeReason.TRADE);
               }
               if (mission.agenda() == EnvoyMission.Agenda.CHARTER && culture != null) {
                  CharterColonyService.offerCharter(level, sovereign.getId(), culture);
               }
               LegitimacyService.add(level, sovereign.getId(), 2, "envoy");
            } else if (culture != null) {
               RelationshipSavedData.get(level)
                  .getRelationshipManager()
                  .modifyRelationship(level, culture, sovereign.getId(), -4, RelationshipChangeReason.INSULT);
            }
         } else {
            next.add(mission);
         }
      }
      if (!matched) {
         return ActionResult.fail("civilization.millenaire.action.blocked.unavailable");
      }
      data.setEnvoys(next);
      return ActionResult.ok(accept
         ? "civilization.millenaire.action.accept_envoy.ok"
         : "civilization.millenaire.action.decline_envoy.ok");
   }

   public static ActionResult subsidizeCorridor(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId partner = FactionId.tryParse(targetFactionId);
      if (partner == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, partner, sovereign.getId(), 5, RelationshipChangeReason.TRADE);
      LegitimacyService.add(level, sovereign.getId(), 2, "corridor_subsidy");
      InteractionSavedData.get(level).addTheaterStory(new TheaterStory(
         "corridor",
         "civilization.millenaire.theater.corridor_subsidy",
         sovereign.getId().asString(),
         partner.asString(),
         1,
         level.getGameTime()
      ));
      return ActionResult.ok("civilization.millenaire.action.subsidize_corridor.ok");
   }

   public static ActionResult brokerPeace(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId other = FactionId.tryParse(targetFactionId);
      if (other == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      var warManager = WarSavedData.get(level).getWarManager();
      if (!warManager.areAtWar(sovereign.getId(), other)) {
         // Try brokering between selected pair encoded as "a|b"
         if (targetFactionId != null && targetFactionId.contains("|")) {
            String[] parts = targetFactionId.split("\\|", 2);
            FactionId a = FactionId.tryParse(parts[0]);
            FactionId b = FactionId.tryParse(parts[1]);
            if (a != null && b != null && warManager.areAtWar(a, b)) {
               DiplomacySavedData.get(level).getDiplomaticManager().establishPeace(level, a, b, 0L);
               LegitimacyService.add(level, sovereign.getId(), 6, "broker_peace");
               return ActionResult.ok("civilization.millenaire.action.broker_peace.ok");
            }
         }
         return ActionResult.fail("civilization.millenaire.action.blocked.no_war");
      }
      DiplomacySavedData.get(level).getDiplomaticManager().establishPeace(level, sovereign.getId(), other, 0L);
      LegitimacyService.add(level, sovereign.getId(), 4, "broker_peace");
      return ActionResult.ok("civilization.millenaire.action.broker_peace.ok");
   }

   public static ActionResult hireMercenaries(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId provider = FactionId.tryParse(targetFactionId);
      if (provider == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      InteractionSavedData.get(level).addMercenary(new MercenaryContract(
         sovereign.getId().asString(),
         provider.asString(),
         level.getGameTime() + InteractionConfig.MERCENARY_CONTRACT_DURATION_TICKS,
         true
      ));
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, provider, sovereign.getId(), 4, RelationshipChangeReason.ALLIANCE);
      return ActionResult.ok("civilization.millenaire.action.hire_mercenaries.ok");
   }

   public static ActionResult propagandaMarket(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      LegitimacyService.add(level, sovereign.getId(), 5, "propaganda");
      if (targetFactionId != null && !targetFactionId.isBlank()) {
         FactionId other = FactionId.tryParse(targetFactionId);
         if (other != null) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .modifyRelationship(level, other, sovereign.getId(), 3, RelationshipChangeReason.PLAYER_ACTION);
         }
      }
      return ActionResult.ok("civilization.millenaire.action.propaganda_market.ok");
   }

   public static ActionResult interrogatePrisoner(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId about = FactionId.tryParse(targetFactionId);
      InteractionSavedData data = InteractionSavedData.get(level);
      data.addRumor(new RumorEntry(
         "interrogation",
         "civilization.millenaire.rumor.interrogation",
         about == null ? (targetFactionId == null ? "" : targetFactionId) : about.asString(),
         level.getGameTime() + InteractionConfig.RUMOR_TTL_TICKS,
         0.85f
      ));
      data.addTheaterStory(new TheaterStory(
         "intel",
         "civilization.millenaire.theater.interrogation",
         sovereign.getId().asString(),
         about == null ? null : about.asString(),
         2,
         level.getGameTime()
      ));
      return ActionResult.ok("civilization.millenaire.action.interrogate_prisoner.ok");
   }

   public static ActionResult proposeJointWork(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId partner = FactionId.tryParse(targetFactionId);
      if (partner == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      String id = sovereign.getId().asString() + "|" + partner.asString() + "|" + level.getGameTime();
      InteractionSavedData data = InteractionSavedData.get(level);
      data.addJointProject(new JointWorkProject(
         id,
         sovereign.getId().asString(),
         partner.asString(),
         JointWorkProject.Kind.ROAD,
         10,
         JointWorkProject.Status.ACTIVE
      ));
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, partner, sovereign.getId(), 4, RelationshipChangeReason.TRADE);
      data.addTheaterStory(new TheaterStory(
         "joint_work",
         "civilization.millenaire.theater.joint_work",
         sovereign.getId().asString(),
         partner.asString(),
         2,
         level.getGameTime()
      ));
      return ActionResult.ok("civilization.millenaire.action.propose_joint_work.ok");
   }

   public static ActionResult offerFosterage(
      ServerLevel level, ServerPlayer player, Village village, Faction sovereign, @Nullable String targetFactionId
   ) {
      FactionId partner = FactionId.tryParse(targetFactionId);
      if (partner == null) {
         return ActionResult.fail("civilization.millenaire.action.blocked.select_target");
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      data.addFosterage(new FosterageBond(
         sovereign.getId().asString(),
         partner.asString(),
         null,
         level.getGameTime() + InteractionConfig.FOSTERAGE_DURATION_TICKS,
         FosterageBond.Kind.FOSTER
      ));
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, partner, sovereign.getId(), 6, RelationshipChangeReason.ALLIANCE);
      data.addTheaterStory(new TheaterStory(
         "fosterage",
         "civilization.millenaire.theater.fosterage",
         sovereign.getId().asString(),
         partner.asString(),
         2,
         level.getGameTime()
      ));
      return ActionResult.ok("civilization.millenaire.action.offer_fosterage.ok");
   }
}
