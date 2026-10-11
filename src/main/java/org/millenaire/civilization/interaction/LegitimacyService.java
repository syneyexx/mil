package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;

/**
 * Soft legitimacy score per faction (−100..100). Chronicle drifts via theater stories.
 */
public final class LegitimacyService {
   private LegitimacyService() {
   }

   public static int get(ServerLevel level, FactionId factionId) {
      if (level == null || factionId == null) {
         return 0;
      }
      return InteractionSavedData.get(level).getLegitimacy(factionId.asString());
   }

   public static int add(ServerLevel level, FactionId factionId, int delta, @Nullable String theaterType) {
      if (level == null || factionId == null || delta == 0) {
         return get(level, factionId);
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      int next = data.getLegitimacy(factionId.asString()) + delta;
      data.setLegitimacy(factionId.asString(), next);
      if (theaterType != null && !theaterType.isBlank()) {
         data.addTheaterStory(new TheaterStory(
            theaterType,
            "civilization.millenaire.theater.legitimacy",
            factionId.asString(),
            null,
            Math.min(5, Math.abs(delta) / 5 + 1),
            level.getGameTime()
         ));
      }
      return data.getLegitimacy(factionId.asString());
   }

   public static void passiveDrift(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      for (String factionId : data.legitimacy().keySet()) {
         int value = data.getLegitimacy(factionId);
         if (value == 0) {
            continue;
         }
         int step = value > 0 ? -InteractionConfig.LEGITIMACY_PASSIVE_DRIFT : InteractionConfig.LEGITIMACY_PASSIVE_DRIFT;
         data.setLegitimacy(factionId, value + step);
      }
   }
}
