package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Bridges village-level owner controls (relations / raids) into faction diplomacy and war.
 */
public final class SovereignDiplomacyBridge {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SovereignDiplomacyBridge() {
   }

   public static void onControlledRelationChanged(ServerLevel level, Village source, Village target, int villageRelationValue) {
      FactionId sourceFaction = factionId(level, source);
      FactionId targetFaction = factionId(level, target);
      if (sourceFaction == null || targetFaction == null || sourceFaction.equals(targetFaction)) {
         return;
      }
      int score = switch (villageRelationValue) {
         case 100 -> 60;
         case -100 -> -70;
         default -> 0;
      };
      try {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .setRelationship(level, sourceFaction, targetFaction, score, RelationshipChangeReason.PLAYER_ACTION);
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .setRelationship(level, targetFaction, sourceFaction, score / 2, RelationshipChangeReason.PLAYER_ACTION);
      } catch (Exception e) {
         LOGGER.debug("[Millenaire] Relation bridge failed: {}", e.toString());
      }
   }

   public static void onRaidPlanned(ServerLevel level, Village attacker, Village defender) {
      FactionId attackFaction = factionId(level, attacker);
      FactionId defendFaction = factionId(level, defender);
      if (attackFaction == null || defendFaction == null || attackFaction.equals(defendFaction)) {
         return;
      }
      Faction attack = FactionSavedData.get(level).getFactionManager().getFaction(attackFaction);
      try {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .setRelationship(level, attackFaction, defendFaction, -80, RelationshipChangeReason.RAID);
         if (WarSavedData.get(level).getWarManager().areAtWar(attackFaction, defendFaction)) {
            return;
         }
         // Player-initiated raids declare war immediately. NPC→player war is driven by culture bounty thresholds.
         if (attack != null && attack.getType() == FactionType.PLAYER) {
            WarSavedData.get(level).getWarManager().declareWar(level, attackFaction, defendFaction, WarGoalType.MILITARY_SUPERIORITY);
         }
      } catch (Exception e) {
         LOGGER.debug("[Millenaire] Raid→war bridge failed: {}", e.toString());
      }
   }

   /**
    * Bounty-driven raid: hostility always; war declaration deferred to bounty scheduler thresholds.
    */
   public static void onBountyRaidPlanned(
      ServerLevel level,
      Village attacker,
      Village defender,
      org.millenaire.faction.crime.CultureBountyRecord record
   ) {
      FactionId attackFaction = factionId(level, attacker);
      FactionId defendFaction = factionId(level, defender);
      if (attackFaction == null || defendFaction == null || attackFaction.equals(defendFaction)) {
         return;
      }
      try {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .setRelationship(level, attackFaction, defendFaction, -90, RelationshipChangeReason.RAID);
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .setRelationship(level, defendFaction, attackFaction, -70, RelationshipChangeReason.RAID);
      } catch (Exception e) {
         LOGGER.debug("[Millenaire] Bounty raid relation bridge failed: {}", e.toString());
      }
   }

   @Nullable
   private static FactionId factionId(ServerLevel level, Village village) {
      if (village == null) {
         return null;
      }
      return village.getFactionId();
   }
}
