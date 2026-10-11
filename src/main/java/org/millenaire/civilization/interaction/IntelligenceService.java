package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarSavedData;

/**
 * Generates rumors from war/famine/corridor state for high-standing players.
 */
public final class IntelligenceService {
   private static final int MIN_STANDING = 30;

   private IntelligenceService() {
   }

   public static void generateRumors(ServerLevel level, FactionId playerFaction) {
      if (level == null || playerFaction == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      List<RumorEntry> fresh = new ArrayList<>();
      long now = level.getGameTime();
      long expires = now + InteractionConfig.RUMOR_TTL_TICKS;

      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war == null) {
            continue;
         }
         FactionId about = war.getPrimaryTargetFactionId();
         if (!highStanding(level, playerFaction, about) && !highStanding(level, playerFaction, war.getInitiatorFactionId())) {
            continue;
         }
         fresh.add(new RumorEntry(
            "war",
            "civilization.millenaire.rumor.war",
            about.asString(),
            expires,
            0.75f
         ));
      }

      for (FamineAlertService.FamineAlert alert : FamineAlertService.scan(level)) {
         FactionId about = FactionId.tryParse(alert.factionId());
         if (about == null || !highStanding(level, playerFaction, about)) {
            continue;
         }
         fresh.add(new RumorEntry(
            "famine",
            "civilization.millenaire.rumor.famine",
            alert.factionId(),
            expires,
            0.65f
         ));
      }

      for (TradeCorridorRecord c : data.corridors().values()) {
         if (!c.recognized()) {
            continue;
         }
         FactionId a = FactionId.tryParse(c.sourceFaction());
         FactionId b = FactionId.tryParse(c.targetFaction());
         if (a == null || b == null) {
            continue;
         }
         if (!highStanding(level, playerFaction, a) && !highStanding(level, playerFaction, b)) {
            continue;
         }
         fresh.add(new RumorEntry(
            c.disrupted() ? "corridor_disrupted" : "corridor",
            c.disrupted()
               ? "civilization.millenaire.rumor.corridor_disrupted"
               : "civilization.millenaire.rumor.corridor",
            c.sourceFaction(),
            expires,
            c.disrupted() ? 0.8f : 0.55f
         ));
      }

      for (TheaterStory story : data.theaterStories()) {
         if ("coup".equals(story.type()) || "politics".equals(story.type())) {
            FactionId about = FactionId.tryParse(story.factionA());
            if (about != null && highStanding(level, playerFaction, about)) {
               fresh.add(new RumorEntry(
                  "coup",
                  "civilization.millenaire.rumor.coup",
                  story.factionA(),
                  expires,
                  0.5f
               ));
            }
         }
      }

      for (RumorEntry rumor : fresh) {
         data.addRumor(rumor);
      }
   }

   public static void tickForOpenPlayers(ServerLevel level) {
      if (level == null) {
         return;
      }
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() == FactionType.PLAYER) {
            generateRumors(level, faction.getId());
         }
      }
      // Expire old rumors
      InteractionSavedData data = InteractionSavedData.get(level);
      long now = level.getGameTime();
      List<RumorEntry> kept = new ArrayList<>();
      for (RumorEntry r : data.rumors()) {
         if (!r.isExpired(now)) {
            kept.add(r);
         }
      }
      if (kept.size() != data.rumors().size()) {
         data.setRumors(kept);
      }
   }

   private static boolean highStanding(ServerLevel level, FactionId player, FactionId other) {
      if (other == null) {
         return false;
      }
      return RelationshipSavedData.get(level).getRelationshipManager().getRelationshipScore(player, other) >= MIN_STANDING;
   }
}
