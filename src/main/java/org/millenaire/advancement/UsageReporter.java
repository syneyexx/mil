package org.millenaire.advancement;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.millenaire.Millenaire;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.net.ModApiClient;
import org.millenaire.village.PlayerCultureReputation;
import org.slf4j.Logger;

public final class UsageReporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new Gson();
   private static final String PING_PATH = "/api/mod/v1/stats/ping";
   private static final AtomicBoolean reported = new AtomicBoolean(false);

   private UsageReporter() {
   }

   public static void tryReport(MinecraftServer server) {
      if (!reported.get()) {
         if ((Boolean)MillenaireServerConfig.SERVER.sendStatistics.get()) {
            if (!server.getPlayerList().getPlayers().isEmpty()) {
               if (reported.compareAndSet(false, true)) {
                  ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                  if (overworld != null) {
                     try {
                        String body = buildPingJson(server, overworld);
                        ModApiClient.postJson("/api/mod/v1/stats/ping", "", body);
                     } catch (Exception e) {
                        LOGGER.debug("Failed to build usage report: {}", e.getMessage());
                     }
                  }
               }
            }
         }
      }
   }

   private static String buildPingJson(MinecraftServer server, ServerLevel overworld) {
      AdvancementStatsManager stats = AdvancementStatsManager.get(overworld);
      PlayerCultureReputation cultureRep = PlayerCultureReputation.get(overworld);
      ServerPlayer firstPlayer = server.getPlayerList().getPlayers().isEmpty() ? null : (ServerPlayer)server.getPlayerList().getPlayers().getFirst();
      long totalExp = 0L;
      if (firstPlayer != null) {
         UUID playerId = firstPlayer.getUUID();

         for (String culture : MillAdvancements.ADVANCEMENT_CULTURES) {
            ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", culture);
            totalExp += Math.abs(cultureRep.get(playerId, cultureId));
         }
      }

      JsonObject json = new JsonObject();
      json.addProperty("uuid", String.valueOf(stats.getOrCreateUid()));
      json.addProperty("version", Millenaire.getModVersion());
      json.addProperty("mcVersion", server.getServerVersion());
      json.addProperty("locale", getLanguage(firstPlayer));
      json.addProperty("mode", server.isDedicatedServer() ? "SERVER" : "SOLO");
      json.addProperty("os", System.getProperty("os.name", "unknown"));
      json.addProperty("nbPlayers", Math.max(1, server.getPlayerCount()));
      json.addProperty("totalExp", totalExp);
      return GSON.toJson(json);
   }

   private static String getLanguage(@Nullable ServerPlayer player) {
      if (player != null) {
         String locale = player.clientInformation().language();
         if (locale != null && !locale.isEmpty()) {
            return locale;
         }
      }

      Locale jvmLocale = Locale.getDefault();
      String country = jvmLocale.getCountry();
      return country.isEmpty() ? jvmLocale.getLanguage() : jvmLocale.getLanguage() + "_" + country;
   }

   public static void reset() {
      reported.set(false);
   }

   public static void resetForTesting() {
      reset();
   }
}
