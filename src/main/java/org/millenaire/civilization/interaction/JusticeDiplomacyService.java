package org.millenaire.civilization.interaction;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.crime.CultureBountyManager;
import org.millenaire.faction.crime.CultureBountySavedData;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;

/**
 * Blood-money and sanctuary diplomacy hooks into the culture bounty ledger.
 */
public final class JusticeDiplomacyService {
   private JusticeDiplomacyService() {
   }

   public static boolean payBloodMoney(
      ServerLevel level,
      ServerPlayer player,
      ResourceLocation cultureId,
      @Nullable FactionId cultureFaction
   ) {
      if (level == null || player == null || cultureId == null) {
         return false;
      }
      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      var record = manager.get(player.getUUID(), cultureId);
      if (record == null || record.bountyScore() <= 0) {
         return false;
      }
      manager.reduceBounty(level, player.getUUID(), cultureId, InteractionConfig.BLOOD_MONEY_BOUNTY_REDUCTION);
      if (cultureFaction != null) {
         FactionId playerFaction = playerFactionId(level, player);
         if (playerFaction != null) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .modifyRelationship(level, cultureFaction, playerFaction, 10, RelationshipChangeReason.PEACE);
         }
         LegitimacyService.add(level, cultureFaction, 3, "justice");
      }
      InteractionSavedData.get(level).addTheaterStory(new TheaterStory(
         "justice",
         "civilization.millenaire.theater.blood_money",
         cultureId.toString(),
         player.getUUID().toString(),
         2,
         level.getGameTime()
      ));
      return true;
   }

   /** Refusing sanctuary costs relationship / legitimacy. */
   public static void sanctuaryRefuseCost(ServerLevel level, FactionId host, FactionId refugeeSource) {
      if (level == null || host == null || refugeeSource == null) {
         return;
      }
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, refugeeSource, host, -8, RelationshipChangeReason.INSULT);
      LegitimacyService.add(level, host, -4, "sanctuary_refuse");
      InteractionSavedData.get(level).addTheaterStory(new TheaterStory(
         "justice",
         "civilization.millenaire.theater.sanctuary_refused",
         host.asString(),
         refugeeSource.asString(),
         3,
         level.getGameTime()
      ));
   }

   @Nullable
   private static FactionId playerFactionId(ServerLevel level, ServerPlayer player) {
      for (var faction : org.millenaire.faction.FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() == org.millenaire.faction.FactionType.PLAYER
            && faction.isSovereignOwnedBy(player.getUUID())) {
            return faction.getId();
         }
      }
      return null;
   }
}
