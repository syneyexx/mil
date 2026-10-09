package org.millenaire.living.behavior;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

/**
 * Event-driven reactions that seed NPC knowledge. Does not schedule new goals.
 */
public final class NpcReactionService {
   private static boolean registered;

   private NpcReactionService() {
   }

   public static void register() {
      if (registered) {
         return;
      }
      registered = true;
      LivingEventService.addListener(NpcReactionService::onEvent);
   }

   private static void onEvent(LivingEvent event) {
      // Level is not carried on the event; knowledge seeding happens in tick for active villages
      // when recent events are present, and directly from request completion paths.
   }

   public static void tick(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      int budget = MillenaireServerConfig.SERVER.npcReactionProcessingBudget.getAsInt();
      int processed = 0;
      long now = level.getGameTime();
      if (now % 100L != 0L) {
         return;
      }
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (!village.isActive() || processed >= budget) {
            continue;
         }
         var recent = LivingEventService.recentPublic(level, village.getId(), 3);
         for (LivingEvent event : recent) {
            if (now - event.gameTime() > 200L) {
               continue;
            }
            NpcSocialService.learnFromEvent(level, village, event, event.actorId());
            processed++;
            if (processed >= budget) {
               break;
            }
         }
         LivingVillagesService.get().needsEngine().evaluate(level, village, false);
      }
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcWorldReactionsEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
