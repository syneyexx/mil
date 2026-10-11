package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.faction.FactionId;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

/**
 * Lists open frontier summaries from colonization candidates.
 * Does not found second capitals — metadata/listing only.
 */
public final class CharterColonyService {
   private CharterColonyService() {
   }

   public record FrontierSummary(
      String parentVillageId,
      String parentVillageName,
      String factionId,
      String statusKey,
      int x,
      int z
   ) {
   }

   public static List<FrontierSummary> listOpenFrontiers(ServerLevel level, @Nullable FactionId factionId, int max) {
      List<FrontierSummary> out = new ArrayList<>();
      if (level == null || max <= 0) {
         return out;
      }
      try {
         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            if (factionId != null && (village.getFactionId() == null || !factionId.equals(village.getFactionId()))) {
               continue;
            }
            for (ColonizationOperation op : ColonizationService.listForVillage(level, village.getId().uuid(), 4)) {
               if (op == null || !op.isActive()) {
                  continue;
               }
               out.add(new FrontierSummary(
                  village.getId().uuid().toString(),
                  village.getVillageName() == null ? "" : village.getVillageName(),
                  village.getFactionId() == null ? "" : village.getFactionId().asString(),
                  op.statusCode() == null ? "frontier" : op.statusCode(),
                  village.getCenter().getX(),
                  village.getCenter().getZ()
               ));
               if (out.size() >= max) {
                  return List.copyOf(out);
               }
            }
         }
      } catch (Exception ignored) {
         // Safe empty when colonization APIs unavailable.
      }
      return List.copyOf(out);
   }

   public static boolean offerCharter(ServerLevel level, FactionId playerFaction, FactionId partnerFaction) {
      if (level == null || playerFaction == null || partnerFaction == null) {
         return false;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      for (CaravanCharter existing : data.caravanCharters()) {
         if (existing.active()
            && existing.playerFaction().equals(playerFaction.asString())
            && existing.partnerFaction().equals(partnerFaction.asString())) {
            return false;
         }
      }
      data.addCaravanCharter(new CaravanCharter(
         playerFaction.asString(),
         partnerFaction.asString(),
         null,
         true,
         level.getGameTime()
      ));
      data.addTheaterStory(new TheaterStory(
         "charter",
         "civilization.millenaire.theater.caravan_charter",
         playerFaction.asString(),
         partnerFaction.asString(),
         2,
         level.getGameTime()
      ));
      return true;
   }
}
