package org.millenaire.living;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.actions.NpcActionRegistry;
import org.millenaire.living.behavior.NpcReactionService;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.requests.VillageRequestService;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.living.social.SocialInteractionResolver;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Server lifecycle hooks for Living Villages. Does not replace VillageManager ticking.
 */
public final class LivingVillagesLifecycle {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static boolean listenersRegistered;

   private LivingVillagesLifecycle() {
   }

   public static void bootstrap() {
      if (listenersRegistered) {
         return;
      }
      listenersRegistered = true;
      LivingEventService.addListener(event -> {
         // Knowledge seeding is handled when we have level+village in reaction path.
      });
      NpcReactionService.register();
      LOGGER.info("[Living] Living Villages lifecycle bootstrapped");
   }

   public static void onVillageManagerTick(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      LivingEventService.beginTick(level.getGameTime());
      LivingVillagesService.get().needsEngine().tickActiveVillages(level);
      VillageRequestService.tickMaintenance(level);
      long now = level.getGameTime();
      if (now % MillenaireServerConfig.SERVER.npcMoodDecayInterval.getAsInt() == 0L) {
         NpcSocialService.tickMoodDecay(level);
      }
      if (now % 12000L == 0L) {
         NpcSocialService.periodicCleanup(level, VillageSavedData.get(level).getVillageManager().getAllVillages());
      }
      NpcReactionService.tick(level);
   }

   public static void onChatCompleted(ServerLevel level, Village village, VillagerRecord a, VillagerRecord b) {
      if (!enabled()) {
         return;
      }
      SocialInteractionResolver.onChatCompleted(level, village, a, b);
   }

   public static void onPlayerLogout(ServerPlayer player) {
      if (player != null) {
         NpcActionRegistry.clearPlayer(player.getUUID());
      }
   }

   public static void onServerStopping() {
      // Pending proposals are ephemeral; persisted requests/events/social remain in SavedData.
   }

   public static void onVillageRemoved(ServerLevel level, Village village) {
      if (village == null) {
         return;
      }
      LivingVillagesService.get().needsEngine().onVillageRemoved(village.getId().uuid());
      LivingEventService.pruneVillage(level, village.getId().uuid());
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }

}
