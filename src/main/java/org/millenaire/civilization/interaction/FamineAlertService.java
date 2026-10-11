package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.economy.EconomicBalanceLevel;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.economy.ResourceBalance;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;

/**
 * Scans economic pressure / food-ish deficits and exposes famine alerts for theater + relief.
 */
public final class FamineAlertService {
   private FamineAlertService() {
   }

   public record FamineAlert(String factionId, String factionName, EconomicBalanceLevel balance, int foodDeficitSeverity) {
   }

   public static List<FamineAlert> scan(ServerLevel level) {
      List<FamineAlert> out = new ArrayList<>();
      if (level == null) {
         return out;
      }
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         FactionEconomicProfile profile = EconomySavedData.get(level).getTradeManager().getProfile(faction.getId());
         if (profile == null) {
            continue;
         }
         EconomicBalanceLevel balance = profile.getBalanceLevel();
         if (balance != EconomicBalanceLevel.UNDER_PRESSURE && balance != EconomicBalanceLevel.IMPORT_DEPENDENT) {
            continue;
         }
         int foodSeverity = foodDeficitSeverity(profile);
         if (foodSeverity <= 0 && balance != EconomicBalanceLevel.IMPORT_DEPENDENT) {
            continue;
         }
         out.add(new FamineAlert(
            faction.getId().asString(),
            faction.getDisplayName() == null ? faction.getId().asString() : faction.getDisplayName(),
            balance,
            Math.max(1, foodSeverity)
         ));
      }
      return out;
   }

   public static void tickTheater(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      for (FamineAlert alert : scan(level)) {
         boolean recent = false;
         for (TheaterStory story : data.theaterStories()) {
            if ("famine".equals(story.type())
               && alert.factionId().equals(story.factionA())
               && level.getGameTime() - story.gameTime() < 24000L) {
               recent = true;
               break;
            }
         }
         if (!recent) {
            data.addTheaterStory(new TheaterStory(
               "famine",
               "civilization.millenaire.theater.famine",
               alert.factionId(),
               null,
               Math.min(5, 2 + alert.foodDeficitSeverity() / 4),
               level.getGameTime()
            ));
         }
      }
   }

   /**
    * Emergency relief: relationship + legitimacy boost. Does not invent goods —
    * caller is expected to have delivered real items via existing support paths.
    */
   public static boolean emergencyRelief(ServerLevel level, FactionId donor, FactionId recipient) {
      if (level == null || donor == null || recipient == null || donor.equals(recipient)) {
         return false;
      }
      boolean underPressure = false;
      for (FamineAlert alert : scan(level)) {
         if (recipient.asString().equals(alert.factionId())) {
            underPressure = true;
            break;
         }
      }
      if (!underPressure) {
         return false;
      }
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, recipient, donor, InteractionConfig.EMERGENCY_RELIEF_RELATIONSHIP, RelationshipChangeReason.TRADE);
      LegitimacyService.add(level, donor, InteractionConfig.EMERGENCY_RELIEF_LEGITIMACY, "relief");
      InteractionSavedData.get(level).addTheaterStory(new TheaterStory(
         "relief",
         "civilization.millenaire.theater.emergency_relief",
         donor.asString(),
         recipient.asString(),
         2,
         level.getGameTime()
      ));
      return true;
   }

   private static int foodDeficitSeverity(@Nullable FactionEconomicProfile profile) {
      if (profile == null) {
         return 0;
      }
      int severity = 0;
      for (ResourceBalance balance : profile.getLastBalances().values()) {
         if (!balance.hasDeficit()) {
            continue;
         }
         String path = balance.resourceId().id().getPath();
         if (isFoodish(path)) {
            severity += balance.deficitAmount();
         }
      }
      return severity;
   }

   private static boolean isFoodish(String path) {
      if (path == null) {
         return false;
      }
      String lower = path.toLowerCase();
      return lower.contains("bread")
         || lower.contains("wheat")
         || lower.contains("potato")
         || lower.contains("carrot")
         || lower.contains("beef")
         || lower.contains("pork")
         || lower.contains("chicken")
         || lower.contains("mutton")
         || lower.contains("fish")
         || lower.contains("rice")
         || lower.contains("food")
         || lower.contains("apple")
         || lower.contains("crop");
   }
}
