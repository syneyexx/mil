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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

/**
 * LM Studio chat-completions client (OpenAI-compatible local HTTP). Never blocks the server thread.
 * Concurrency uses an in-flight counter so live config changes cannot grant extra permits mid-flight.
 */
public final class NpcModelClient {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new Gson();
   private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3L))
      .followRedirects(Redirect.NORMAL)
      .version(Version.HTTP_1_1)
      .build();
   private static final AtomicInteger IN_FLIGHT = new AtomicInteger(0);
   private static final Object LOCK = new Object();
   /** Default max tokens for interactive chat turns (keep replies short/fast). */
   public static final int CHAT_MAX_TOKENS = 100;

   private NpcModelClient() {
   }

   public static boolean isConfigured() {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return false;
      }
      String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      if (baseUrl == null || baseUrl.isBlank()) {
         return false;
      }
      return NpcModelResolver.usesLmStudio();
   }

   /** Human-readable reason when {@link #isConfigured()} is false; null when configured. */
   @Nullable
   public static String notConfiguredMessageKey() {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return "gui.millenaire.npcchat.disabled";
      }
      String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      if (baseUrl == null || baseUrl.isBlank()) {
         return "gui.millenaire.npcchat.missing_base_url";
      }
      if (!NpcModelResolver.usesLmStudio()) {
         return "gui.millenaire.npcchat.lmstudio.only";
      }
      return null;
   }

   /** Localized system line when {@link #isConfigured()} is false; null when configured. */
   @Nullable
   public static Component notConfiguredComponent() {
      String key = notConfiguredMessageKey();
      if (key == null) {
         return null;
      }
      if ("gui.millenaire.npcchat.lmstudio.only".equals(key)) {
         return lmStudioOnlyComponent();
      }
      return Component.translatable(key);
   }

   /** {@code gui.millenaire.npcchat.lmstudio.only} including the rejected baseUrl. */
   public static Component lmStudioOnlyComponent() {
      String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      return Component.translatable("gui.millenaire.npcchat.lmstudio.only", baseUrl == null ? "" : baseUrl);
   }

   @Nullable
   static String resolveApiKey() {
      String key = MillenaireServerConfig.SERVER.npcChatApiKey.get();
      if (key != null && !key.isBlank()) {
         return key.trim();
      }
      String lmEnv = System.getenv("LM_API_TOKEN");
      if (lmEnv != null && !lmEnv.isBlank()) {
         return lmEnv.trim();
      }
      // LM Studio local default: no token required.
      return "";
   }

   /** API key for LM Studio discovery; blank string means "send no Authorization unless configured". */
   @Nullable
   public static String resolveApiKeyAllowingBlankForLmStudio() {
      return resolveApiKey();
   }

   public static boolean tryAcquire() {
      int max = MillenaireServerConfig.SERVER.npcChatMaxConcurrent.getAsInt();
      synchronized (LOCK) {
         int current = IN_FLIGHT.get();
         if (current >= max) {
            return false;
         }
         IN_FLIGHT.incrementAndGet();
         return true;
      }
   }

   public static void release() {
      synchronized (LOCK) {
         int next = IN_FLIGHT.decrementAndGet();
         if (next < 0) {
            IN_FLIGHT.set(0);
            LOGGER.warn("NPC model client permit accounting underflow; reset to 0");
         }
      }
   }

   public static int inFlight() {
      return IN_FLIGHT.get();
   }

   public static CompletableFuture<Optional<NpcModelReply>> sendAsync(String systemPrompt, String userPrompt, UUID targetId) {
      return sendRawAsync(systemPrompt, userPrompt, targetId, CHAT_MAX_TOKENS).thenApply(raw -> raw.flatMap(NpcReplyParser::parse));
   }

   /**
    * Resolves the model (including LM Studio auto-detect), then performs the HTTP call.
    * Returns empty when resolution or the provider fails. Callers should use
    * {@link #resolveFailureMessageKey()} / {@link #httpFailureMessageKey()} after an empty result.
    */
   public static CompletableFuture<Optional<String>> sendRawAsync(String systemPrompt, String userPrompt, UUID targetId, int maxTokens) {
      return NpcModelResolver.resolveAsync().thenCompose(resolution -> {
         if (!resolution.ok()) {
            lastResolveFailureKey = resolution.messageKey();
            lastHttpFailureKey = null;
            LOGGER.warn("NPC chat model resolution failed for {}: {}", targetId, resolution.status());
            return CompletableFuture.completedFuture(Optional.empty());
         }
         lastResolveFailureKey = null;
         lastResolvedModelLabel = resolution.displayLabel();
         return sendWithModel(systemPrompt, userPrompt, targetId, maxTokens, resolution.modelId());
      });
   }

   private static volatile String lastResolveFailureKey;
   private static volatile String lastHttpFailureKey;
   private static volatile String lastResolvedModelLabel;

   @Nullable
   public static String resolveFailureMessageKey() {
      return lastResolveFailureKey;
   }

   @Nullable
   public static String httpFailureMessageKey() {
      return lastHttpFailureKey;
   }

   /** Prefer resolve failure, then HTTP failure, then generic failed. */
   public static String failureMessageKeyOrDefault() {
      if (lastResolveFailureKey != null) {
         return lastResolveFailureKey;
      }
      if (lastHttpFailureKey != null) {
         return lastHttpFailureKey;
      }
      return "gui.millenaire.npcchat.failed";
   }

   @Nullable
   public static String lastResolvedModelLabel() {
      return lastResolvedModelLabel;
   }

   private static CompletableFuture<Optional<String>> sendWithModel(
      String systemPrompt, String userPrompt, UUID targetId, int maxTokens, String model
   ) {
      String apiKey = resolveApiKey();
      if (apiKey == null) {
         lastHttpFailureKey = "gui.millenaire.npcchat.lmstudio.unreachable";
         return CompletableFuture.completedFuture(Optional.empty());
      }
      String baseUrl = normalizeCompletionsBaseUrl(MillenaireServerConfig.SERVER.npcChatBaseUrl.get());
      if (baseUrl == null) {
         lastHttpFailureKey = "gui.millenaire.npcchat.missing_base_url";
         return CompletableFuture.completedFuture(Optional.empty());
      }
      int timeout = MillenaireServerConfig.SERVER.npcChatTimeoutSeconds.getAsInt();

      JsonObject body = new JsonObject();
      body.addProperty("model", model);
      body.addProperty("temperature", 0.7);
      body.addProperty("max_tokens", Math.max(32, Math.min(maxTokens, 800)));
      body.addProperty("stream", false);
      JsonArray messages = new JsonArray();
      JsonObject system = new JsonObject();
      system.addProperty("role", "system");
      system.addProperty("content", systemPrompt == null ? "" : systemPrompt);
      messages.add(system);
      JsonObject user = new JsonObject();
      user.addProperty("role", "user");
      user.addProperty("content", userPrompt == null ? "" : userPrompt);
      messages.add(user);
      body.add("messages", messages);

      HttpRequest.Builder requestBuilder;
      try {
         requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(timeout))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(GSON.toJson(body)));
         if (!apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
         }
      } catch (Exception e) {
         LOGGER.warn("NPC chat request build failed for {}: {}", targetId, e.toString());
         lastHttpFailureKey = "gui.millenaire.npcchat.lmstudio.unreachable";
         return CompletableFuture.completedFuture(Optional.empty());
      }

      HttpRequest request = requestBuilder.build();
      LOGGER.info("NPC chat POST {} model={} target={}", baseUrl + "/chat/completions", model, targetId);
      return CLIENT.sendAsync(request, BodyHandlers.ofString()).handle((response, error) -> {
         if (error != null) {
            LOGGER.warn("NPC chat request failed for {}: {}", targetId, error.toString());
            lastHttpFailureKey = "gui.millenaire.npcchat.lmstudio.unreachable";
            return Optional.empty();
         }
         int status = response.statusCode();
         if (status < 200 || status >= 300) {
            String snippet = response.body() == null ? "" : response.body();
            if (snippet.length() > 240) {
               snippet = snippet.substring(0, 240);
            }
            LOGGER.warn("NPC chat non-2xx for {}: {} body={}", targetId, status, snippet);
            lastHttpFailureKey = "gui.millenaire.npcchat.lmstudio.http_error";
            return Optional.empty();
         }
         String content = extractContent(response.body());
         if (content == null || content.isBlank()) {
            LOGGER.warn("NPC chat empty body for {}", targetId);
            lastHttpFailureKey = "gui.millenaire.npcchat.lmstudio.empty_reply";
            return Optional.empty();
         }
         lastHttpFailureKey = null;
         return Optional.of(content);
      });
   }

   /**
    * Ensures the OpenAI-compat base ends with {@code /v1} so completions hit {@code /v1/chat/completions}.
    */
   @Nullable
   public static String normalizeCompletionsBaseUrl(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      String value = raw.trim();
      while (value.endsWith("/")) {
         value = value.substring(0, value.length() - 1);
      }
      if (value.endsWith("/v1")) {
         return value;
      }
      // Strip accidental /chat/completions suffix if pasted from docs.
      if (value.endsWith("/chat/completions")) {
         value = value.substring(0, value.length() - "/chat/completions".length());
         while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
         }
         if (value.endsWith("/v1")) {
            return value;
         }
      }
      return value + "/v1";
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
