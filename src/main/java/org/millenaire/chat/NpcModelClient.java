package org.millenaire.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import javax.annotation.Nullable;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

public final class NpcModelClient {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new Gson();
   private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(4L))
      .followRedirects(Redirect.NORMAL)
      .version(Version.HTTP_1_1)
      .build();
   private static final Semaphore SEMAPHORE = new Semaphore(0);
   private static int configuredPermits = -1;

   private NpcModelClient() {
   }

   public static boolean isConfigured() {
      return (Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get() && resolveApiKey() != null;
   }

   @Nullable
   static String resolveApiKey() {
      String key = MillenaireServerConfig.SERVER.npcChatApiKey.get();
      if (key != null && !key.isBlank()) {
         return key.trim();
      }
      String env = System.getenv("MILLENAIRE_NPC_API_KEY");
      return env != null && !env.isBlank() ? env.trim() : null;
   }

   private static synchronized void ensurePermits() {
      int desired = MillenaireServerConfig.SERVER.npcChatMaxConcurrent.getAsInt();
      if (desired != configuredPermits) {
         SEMAPHORE.drainPermits();
         SEMAPHORE.release(desired);
         configuredPermits = desired;
      }
   }

   public static boolean tryAcquire() {
      ensurePermits();
      return SEMAPHORE.tryAcquire();
   }

   public static void release() {
      SEMAPHORE.release();
   }

   public static CompletableFuture<Optional<NpcModelReply>> sendAsync(String systemPrompt, String userPrompt, java.util.UUID villagerId) {
      return sendRawAsync(systemPrompt, userPrompt, villagerId, 260).thenApply(raw -> raw.flatMap(NpcReplyParser::parse));
   }

   public static CompletableFuture<Optional<String>> sendRawAsync(String systemPrompt, String userPrompt, java.util.UUID villagerId, int maxTokens) {
      String apiKey = resolveApiKey();
      if (apiKey == null) {
         return CompletableFuture.completedFuture(Optional.empty());
      }
      String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      if (baseUrl == null || baseUrl.isBlank()) {
         return CompletableFuture.completedFuture(Optional.empty());
      }
      while (baseUrl.endsWith("/")) {
         baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
      }
      String model = MillenaireServerConfig.SERVER.npcChatModel.get();
      int timeout = MillenaireServerConfig.SERVER.npcChatTimeoutSeconds.getAsInt();

      JsonObject body = new JsonObject();
      body.addProperty("model", model);
      body.addProperty("temperature", 0.8);
      body.addProperty("max_tokens", maxTokens);
      JsonArray messages = new JsonArray();
      JsonObject system = new JsonObject();
      system.addProperty("role", "system");
      system.addProperty("content", systemPrompt);
      messages.add(system);
      JsonObject user = new JsonObject();
      user.addProperty("role", "user");
      user.addProperty("content", userPrompt);
      messages.add(user);
      body.add("messages", messages);

      HttpRequest request = HttpRequest.newBuilder()
         .uri(URI.create(baseUrl + "/chat/completions"))
         .timeout(Duration.ofSeconds(timeout))
         .header("Content-Type", "application/json")
         .header("Authorization", "Bearer " + apiKey)
         .POST(BodyPublishers.ofString(GSON.toJson(body)))
         .build();

      return CLIENT.sendAsync(request, BodyHandlers.ofString()).handle((response, error) -> {
         if (error != null) {
            LOGGER.warn("NPC chat request failed for villager {}: {}", villagerId, error.toString());
            return Optional.empty();
         }
         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            LOGGER.warn("NPC chat non-2xx for villager {}: {}", villagerId, status);
            return Optional.empty();
         }
         String content = extractContent(response.body());
         if (content == null || content.isBlank()) {
            LOGGER.warn("NPC chat empty body for villager {}", villagerId);
            return Optional.empty();
         }
         return Optional.of(content);
      });
   }

   @Nullable
   private static String extractContent(String body) {
      try {
         JsonObject root = GSON.fromJson(body, JsonObject.class);
         if (root == null || !root.has("choices")) {
            return null;
         }
         JsonArray choices = root.getAsJsonArray("choices");
         if (choices == null || choices.isEmpty()) {
            return null;
         }
         JsonObject first = choices.get(0).getAsJsonObject();
         if (!first.has("message")) {
            return null;
         }
         JsonObject message = first.getAsJsonObject("message");
         return message.has("content") ? message.get("content").getAsString() : null;
      } catch (Exception e) {
         return null;
      }
   }
}
