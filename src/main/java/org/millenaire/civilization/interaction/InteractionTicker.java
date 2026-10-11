package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.CulturalTemperament;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Periodic interaction maintenance called from {@link org.millenaire.civilization.CivilizationOrchestrator}.
 */
public final class InteractionTicker {
   private InteractionTicker() {
   }

   public static void tick(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      long now = level.getGameTime();
      data.setLastTickerPassGameTime(now);

      TradeCorridorService.refreshDisruptions(level);
      ClaimHeatService.decay(level);
      ClaimHeatService.recompute(level);
      expireTimedState(level, data, now);
      maybeStartFestivals(level, data, now);
      generateTheaterStories(level, data, now);
      MarketDayService.tickFlags(level);
      FamineAlertService.tickTheater(level);
      tickEnvoyOffers(level, data, now);
      tickCallToArms(level, data, now);
      tickTributeDue(level, data, now);
      tickJointWorks(level, data, now);
      tickPoliticsLeakage(level, data, now);
      LegitimacyService.passiveDrift(level);
      IntelligenceService.tickForOpenPlayers(level);
   }

   private static void expireTimedState(ServerLevel level, InteractionSavedData data, long now) {
      List<FestivalWindow> festivals = new ArrayList<>();
      for (FestivalWindow f : data.festivals()) {
         if (f.isActive(now) || f.endGameTime() > now) {
            if (f.endGameTime() > now) {
               festivals.add(f);
            }
         }
      }
      if (festivals.size() != data.festivals().size()) {
         data.setFestivals(festivals);
      }

      List<CallToArmsOffer> offers = new ArrayList<>();
      for (CallToArmsOffer o : data.callToArms()) {
         if (o.status() == CallToArmsOffer.Status.PENDING && o.expiresAt() > 0L && now >= o.expiresAt()) {
            offers.add(o.withStatus(CallToArmsOffer.Status.EXPIRED));
         } else {
            offers.add(o);
         }
      }
      data.setCallToArms(offers);

      List<MercenaryContract> mercs = new ArrayList<>();
      for (MercenaryContract m : data.mercenaries()) {
         if (m.active() && m.expiresAt() > 0L && now >= m.expiresAt()) {
            mercs.add(m.withActive(false));
         } else {
            mercs.add(m);
         }
      }
      data.setMercenaries(mercs);

      List<FosterageBond> bonds = new ArrayList<>();
      for (FosterageBond b : data.fosterage()) {
         if (!b.isExpired(now)) {
            bonds.add(b);
         }
      }
      if (bonds.size() != data.fosterage().size()) {
         data.setFosterage(bonds);
      }

      List<EnvoyMission> envoys = new ArrayList<>();
      for (EnvoyMission e : data.envoys()) {
         if (e.pending() && e.isExpired(now)) {
            envoys.add(e.withPending(false));
         } else {
            envoys.add(e);
         }
      }
      data.setEnvoys(envoys);
   }

   private static void maybeStartFestivals(ServerLevel level, InteractionSavedData data, long now) {
      if (data.lastFestivalAttemptGameTime() > 0L
         && now - data.lastFestivalAttemptGameTime() < InteractionConfig.FESTIVAL_ATTEMPT_INTERVAL_TICKS) {
         return;
      }
      data.setLastFestivalAttemptGameTime(now);
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() != FactionType.CULTURE || faction.getCultureId() == null || faction.getSettlementCount() <= 0) {
            continue;
         }
         Culture culture = ModCultures.getCulture(faction.getCultureId());
         CulturalTemperament temperament = culture == null ? CulturalTemperament.DEFAULT : culture.temperament();
         if (temperament.festivalWeight() < 0.7) {
            continue;
         }
         boolean already = false;
         for (FestivalWindow f : data.festivals()) {
            if (f.factionId().equals(faction.getId().asString()) && f.isActive(now)) {
               already = true;
               break;
            }
         }
         if (already) {
            continue;
         }
         String host = null;
         for (VillageId vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v != null) {
               host = v.getId().uuid().toString();
               break;
            }
         }
         FestivalWindow window = new FestivalWindow(
            faction.getCultureId().toString(),
            faction.getId().asString(),
            now,
            now + InteractionConfig.FESTIVAL_DURATION_TICKS,
            host
         );
         data.addFestival(window);
         data.addTheaterStory(new TheaterStory(
            "festival",
            "civilization.millenaire.theater.festival",
            faction.getId().asString(),
            null,
            1,
            now
         ));
         break;
      }
   }

   private static void generateTheaterStories(ServerLevel level, InteractionSavedData data, long now) {
      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war == null) {
            continue;
         }
         boolean recent = false;
         String a = war.getInitiatorFactionId().asString();
         String b = war.getPrimaryTargetFactionId().asString();
         for (TheaterStory s : data.theaterStories()) {
            if ("war".equals(s.type()) && a.equals(s.factionA()) && b.equals(s.factionB()) && now - s.gameTime() < 24000L) {
               recent = true;
               break;
            }
         }
         if (!recent) {
            data.addTheaterStory(new TheaterStory(
               "war",
               "civilization.millenaire.theater.war",
               a,
               b,
               4,
               now
            ));
         }
      }
   }

   private static void tickEnvoyOffers(ServerLevel level, InteractionSavedData data, long now) {
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      List<Faction> players = new ArrayList<>();
      List<Faction> cultures = new ArrayList<>();
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() == FactionType.PLAYER && faction.getImmigrationPolicy() == ImmigrationPolicy.OPEN) {
            players.add(faction);
         } else if (faction.getType() == FactionType.CULTURE && faction.getSettlementCount() > 0) {
            cultures.add(faction);
         }
      }
      int created = 0;
      for (Faction culture : cultures) {
         if (created >= 2) {
            break;
         }
         for (Faction player : players) {
            if (!diplomacy.hasTradeAgreement(culture.getId(), player.getId())) {
               continue;
            }
            boolean pending = false;
            for (EnvoyMission e : data.envoys()) {
               if (e.pending()
                  && e.cultureFaction().equals(culture.getId().asString())
                  && e.playerFaction().equals(player.getId().asString())) {
                  pending = true;
                  break;
               }
            }
            if (pending) {
               continue;
            }
            data.addEnvoy(new EnvoyMission(
               culture.getId().asString(),
               player.getId().asString(),
               EnvoyMission.Agenda.CHARTER,
               now + InteractionConfig.ENVOY_DURATION_TICKS,
               true
            ));
            created++;
            break;
         }
      }
   }

   private static void tickCallToArms(ServerLevel level, InteractionSavedData data, long now) {
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      int created = 0;
      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war == null || created >= 2) {
            break;
         }
         FactionId allyAtWar = war.getInitiatorFactionId();
         FactionId enemy = war.getPrimaryTargetFactionId();
         for (FactionId partner : diplomacy.getAllies(allyAtWar)) {
            Faction partnerFaction = FactionSavedData.get(level).getFactionManager().getFaction(partner);
            if (partnerFaction == null || partnerFaction.getType() != FactionType.PLAYER) {
               continue;
            }
            boolean exists = false;
            for (CallToArmsOffer o : data.callToArms()) {
               if (o.status() == CallToArmsOffer.Status.PENDING
                  && o.targetPlayerFaction().equals(partner.asString())
                  && o.enemyFaction().equals(enemy.asString())) {
                  exists = true;
                  break;
               }
            }
            if (exists) {
               continue;
            }
            data.addCallToArms(new CallToArmsOffer(
               allyAtWar.asString(),
               enemy.asString(),
               partner.asString(),
               now + InteractionConfig.CALL_TO_ARMS_DURATION_TICKS,
               CallToArmsOffer.Status.PENDING
            ));
            data.addTheaterStory(new TheaterStory(
               "call_to_arms",
               "civilization.millenaire.theater.call_to_arms",
               allyAtWar.asString(),
               partner.asString(),
               3,
               now
            ));
            created++;
            break;
         }
      }
   }

   private static void tickTributeDue(ServerLevel level, InteractionSavedData data, long now) {
      List<ProtectorateLink> next = new ArrayList<>();
      for (ProtectorateLink link : data.protectorates()) {
         if (!link.active()) {
            next.add(link);
            continue;
         }
         if (link.tributeDueGameTime() > 0L && now >= link.tributeDueGameTime()) {
            FactionId suzerain = FactionId.tryParse(link.suzerain());
            if (suzerain != null) {
               LegitimacyService.add(level, suzerain, -2, "tribute_overdue");
            }
            next.add(link.withTributeDue(now + InteractionConfig.PROTECTORATE_TRIBUTE_INTERVAL_TICKS));
            data.addTheaterStory(new TheaterStory(
               "tribute",
               "civilization.millenaire.theater.tribute_due",
               link.suzerain(),
               link.vassal(),
               2,
               now
            ));
         } else {
            next.add(link);
         }
      }
      data.setProtectorates(next);
   }

   private static void tickJointWorks(ServerLevel level, InteractionSavedData data, long now) {
      List<JointWorkProject> next = new ArrayList<>();
      for (JointWorkProject project : data.jointProjects()) {
         if (project.status() != JointWorkProject.Status.ACTIVE) {
            next.add(project);
            continue;
         }
         int progress = Math.min(100, project.progress() + 5);
         if (progress >= 100) {
            next.add(project.withProgress(100).withStatus(JointWorkProject.Status.COMPLETE));
            FactionId a = FactionId.tryParse(project.factionA());
            FactionId b = FactionId.tryParse(project.factionB());
            if (a != null && b != null) {
               org.millenaire.faction.relationship.RelationshipSavedData.get(level)
                  .getRelationshipManager()
                  .modifyRelationship(
                     level,
                     a,
                     b,
                     6,
                     org.millenaire.faction.relationship.RelationshipChangeReason.TRADE
                  );
               LegitimacyService.add(level, a, 2, "joint_work");
            }
            data.addTheaterStory(new TheaterStory(
               "joint_work",
               "civilization.millenaire.theater.joint_work_complete",
               project.factionA(),
               project.factionB(),
               2,
               now
            ));
         } else {
            next.add(project.withProgress(progress));
         }
      }
      data.setJointProjects(next);
   }

   private static void tickPoliticsLeakage(ServerLevel level, InteractionSavedData data, long now) {
      int emitted = 0;
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (emitted >= 3 || faction.getType() != FactionType.CULTURE) {
            continue;
         }
         var conflict = org.millenaire.faction.politics.PoliticsQueries.conflictState(level, faction.getId());
         if (conflict == null || !conflict.isUnstable()) {
            continue;
         }
         boolean recent = false;
         for (TheaterStory s : data.theaterStories()) {
            if ("politics".equals(s.type())
               && faction.getId().asString().equals(s.factionA())
               && now - s.gameTime() < 24000L) {
               recent = true;
               break;
            }
         }
         if (recent) {
            continue;
         }
         data.addTheaterStory(new TheaterStory(
            "politics",
            "civilization.millenaire.theater.politics_leak",
            faction.getId().asString(),
            conflict.name().toLowerCase(),
            conflict.ordinal() + 1,
            now
         ));
         // Instability makes neighbors opportunistic: small relationship pressure toward isolation.
         for (Faction other : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
            if (other.getId().equals(faction.getId()) || other.getType() != FactionType.CULTURE) {
               continue;
            }
            org.millenaire.faction.relationship.RelationshipSavedData.get(level)
               .getRelationshipManager()
               .modifyRelationship(
                  level,
                  other.getId(),
                  faction.getId(),
                  -2,
                  org.millenaire.faction.relationship.RelationshipChangeReason.INSULT
               );
            break;
         }
         emitted++;
      }
   }

   public static boolean isFestivalActive(ServerLevel level, String cultureId) {
      if (level == null || cultureId == null) {
         return false;
      }
      long now = level.getGameTime();
      for (FestivalWindow f : InteractionSavedData.get(level).festivals()) {
         if (cultureId.equals(f.cultureId()) && f.isActive(now)) {
            return true;
         }
      }
      return false;
   }
}
