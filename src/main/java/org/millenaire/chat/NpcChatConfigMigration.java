package org.millenaire.chat;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import javax.annotation.Nullable;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

/**
 * Migrates leftover cloud NPC-chat base URLs (Grok/OpenAI/etc.) to the local LM Studio default.
 * New worlds already get {@link #DEFAULT_LOCAL_BASE_URL} from {@link MillenaireServerConfig};
 * this rewrites stale per-world serverconfig values on load.
 */
public final class NpcChatConfigMigration {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static final String DEFAULT_LOCAL_BASE_URL = "http://127.0.0.1:1234/v1";

   private NpcChatConfigMigration() {
   }

   /**
    * If {@code baseUrl} is a known cloud API host, returns the local LM Studio default; otherwise empty.
    */
   public static Optional<String> cloudBaseUrlReplacement(@Nullable String baseUrl) {
      if (baseUrl == null || baseUrl.isBlank()) {
         return Optional.empty();
      }
      if (LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl(baseUrl)) {
         return Optional.empty();
      }
      if (!LmStudioModelDiscovery.isRemoteCloudApiUrl(baseUrl)) {
         return Optional.empty();
      }
      return Optional.of(DEFAULT_LOCAL_BASE_URL);
   }

   /**
    * Rewrites a loaded SERVER npcChat.baseUrl when it still points at a cloud API.
    *
    * @return true when the value was changed
    */
   public static boolean migrateLoadedServerConfig() {
      if (MillenaireServerConfig.SERVER == null || MillenaireServerConfig.SERVER.npcChatBaseUrl == null) {
         return false;
      }
      if (!MillenaireServerConfig.SPEC.isLoaded()) {
         return false;
      }
      String current = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      Optional<String> replacement = cloudBaseUrlReplacement(current);
      if (replacement.isEmpty()) {
         return false;
      }
      String next = replacement.get();
      MillenaireServerConfig.SERVER.npcChatBaseUrl.set(next);
      try {
         MillenaireServerConfig.SERVER.npcChatBaseUrl.save();
      } catch (Exception e) {
         LOGGER.warn("Migrated npcChat.baseUrl in memory but failed to save serverconfig: {}", e.toString());
      }
      LOGGER.warn(
         "Migrated npcChat.baseUrl from cloud API '{}' to '{}' (local LM Studio). Restart chat / use /millenaire npcchat probe.",
         current,
         next
      );
      return true;
   }
}
