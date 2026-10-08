package org.millenaire.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.Millenaire;
import org.millenaire.advancement.AdvancementStatsManager;
import org.slf4j.Logger;

public final class DonorChecker {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String PATH = "/api/mod/v1/donor-check";
   private static final Gson GSON = new Gson();

   private DonorChecker() {
   }

   public static void onPlayerLogin(ServerPlayer player) {
      MinecraftServer server = player.server;
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld != null) {
         UUID playerId = player.getUUID();
         String login = player.getName().getString();
         String mlnUuid = String.valueOf(AdvancementStatsManager.get(overworld).getOrCreateUid());
         String modVersion = Millenaire.getModVersion();
         JsonObject body = new JsonObject();
         body.addProperty("login", login);
         body.addProperty("mlnUuid", mlnUuid);
         body.addProperty("modVersion", modVersion);
         ModApiClient.postJson("/api/mod/v1/donor-check", "", GSON.toJson(body)).thenAccept(resp -> {
            if (resp != null && resp.status() == 200) {
               try {
                  boolean isDonor = JsonParser.parseString(resp.body()).getAsJsonObject().get("isDonor").getAsBoolean();
                  server.execute(() -> DonorStatusData.get(overworld).setDonor(playerId, isDonor));
               } catch (Exception e) {
                  LOGGER.debug("Donor check parse failed: {}", e.getMessage());
               }
            }
         });
      }
   }
}
