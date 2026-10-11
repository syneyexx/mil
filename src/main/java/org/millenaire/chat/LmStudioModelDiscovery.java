package org.millenaire.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

/**
 * Discovers loaded LM Studio LLMs via {@code GET /api/v1/models} and {@code loaded_instances}.
 * Never blocks the Minecraft server thread; callers use cached snapshots or async refresh.
 */
public final class LmStudioModelDiscovery {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new Gson();
   private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2L))
      .followRedirects(Redirect.NORMAL)
      .version(Version.HTTP_1_1)
      .build();
   private static final long CACHE_TTL_MS = 5_000L;
   private static final long FORCE_REFRESH_MIN_MS = 750L;

   private static final AtomicReference<Snapshot> CACHE = new AtomicReference<>(Snapshot.empty());
   private static final Object REFRESH_LOCK = new Object();
   private static volatile CompletableFuture<Snapshot> inflightRefresh;
   private static volatile long lastRefreshAttemptMs;
   @Nullable
   private static volatile String manualSelectionId;
   @Nullable
   private static volatile String lastFingerprint;
   private static volatile boolean lastChangeDetected;

   private LmStudioModelDiscovery() {
   }

   public static boolean isLmStudioBaseUrl(@Nullable String baseUrl) {
      if (baseUrl == null || baseUrl.isBlank()) {
         return false;
      }
      String provider = MillenaireServerConfig.SERVER.npcChatProvider.get();
      if (provider != null) {
         String p = provider.trim().toLowerCase();
         if ("openai".equals(p) || "remote".equals(p)) {
            return false;
         }
         if ("lmstudio".equals(p) || "lm_studio".equals(p) || "lm-studio".equals(p)) {
            return true;
         }
         // "auto" / blank / unknown → detect from URL below
      }
      String normalized = normalizeBase(baseUrl).toLowerCase();
      try {
         URI uri = URI.create(normalized.contains("://") ? normalized : "http://" + normalized);
         String host = uri.getHost();
         int port = uri.getPort();
         if (host == null) {
            return false;
         }
         if (host.contains("lmstudio") || normalized.contains("lmstudio")) {
            return true;
         }
         // LM Studio's default port. Includes LAN binds shown in the UI (e.g. 192.168.x.x:1234),
         // not only localhost — otherwise provider=auto disables HTTP entirely.
         if (port == 1234 || port == -1 && normalized.contains(":1234")) {
            return true;
         }
         boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host)
            || "[::1]".equals(host) || "::1".equals(host);
         return loopback && (port == 1234 || port <= 0);
      } catch (Exception e) {
         return normalized.contains(":1234") || normalized.contains("lmstudio");
      }
   }

   public static Snapshot cached() {
      return CACHE.get();
   }

   public static boolean consumeChangeFlag() {
      boolean changed = lastChangeDetected;
      lastChangeDetected = false;
      return changed;
   }

   @Nullable
   public static String manualSelectionId() {
      return manualSelectionId;
   }

   public static void clearManualSelection() {
      manualSelectionId = null;
   }

   public static boolean selectManual(String modelId) {
      if (modelId == null || modelId.isBlank()) {
         return false;
      }
      Snapshot snap = CACHE.get();
      Optional<LmStudioModelInfo> match = snap.find(modelId.trim());
      if (match.isEmpty()) {
         return false;
      }
      manualSelectionId = match.get().effectiveModelId();
      return true;
   }

   public static CompletableFuture<Snapshot> refreshAsync(boolean force) {
      long now = System.currentTimeMillis();
      Snapshot current = CACHE.get();
      if (!force && current.fresh(now) && current.reachable()) {
         return CompletableFuture.completedFuture(current);
      }
      synchronized (REFRESH_LOCK) {
         if (!force && now - lastRefreshAttemptMs < FORCE_REFRESH_MIN_MS && inflightRefresh != null) {
            return inflightRefresh;
         }
         if (!force && current.fresh(now) && current.reachable()) {
            return CompletableFuture.completedFuture(current);
         }
         lastRefreshAttemptMs = now;
         String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
         String apiKey = NpcModelClient.resolveApiKeyAllowingBlankForLmStudio();
         CompletableFuture<Snapshot> future = CompletableFuture.supplyAsync(() -> fetchSnapshot(baseUrl, apiKey, now))
            .exceptionally(err -> {
               LOGGER.warn("LM Studio model discovery failed: {}", err.toString());
               return Snapshot.unreachable(now, err.toString());
            })
            .thenApply(snap -> {
               applySnapshot(snap);
               return snap;
            });
         inflightRefresh = future;
         future.whenComplete((s, e) -> {
            synchronized (REFRESH_LOCK) {
               if (inflightRefresh == future) {
                  inflightRefresh = null;
               }
            }
         });
         return future;
      }
   }

   private static void applySnapshot(Snapshot snap) {
      String fingerprint = snap.fingerprint();
      String previous = lastFingerprint;
      if (previous != null && !Objects.equals(previous, fingerprint)) {
         lastChangeDetected = true;
         LOGGER.info("LM Studio loaded language models changed: {} -> {}", previous, fingerprint);
         if (manualSelectionId != null && snap.find(manualSelectionId).isEmpty()) {
            LOGGER.info("Clearing LM Studio manual model selection {}; no longer loaded", manualSelectionId);
            manualSelectionId = null;
         }
      }
      lastFingerprint = fingerprint;
      CACHE.set(snap);
   }

   private static Snapshot fetchSnapshot(@Nullable String baseUrl, @Nullable String apiKey, long nowMs) {
      String modelsUrl = toNativeModelsUrl(baseUrl);
      if (modelsUrl == null) {
         return Snapshot.unreachable(nowMs, "invalid baseUrl");
      }
      try {
         HttpResponse<String> response = getJson(modelsUrl, apiKey);
         if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Native REST may be disabled/older; still try OpenAI-compat /v1/models.
            List<LmStudioModelInfo> openai = fetchOpenAiCompatModels(baseUrl, apiKey);
            if (!openai.isEmpty()) {
               return Snapshot.ok(nowMs, openai);
            }
            return Snapshot.unreachable(nowMs, "HTTP " + response.statusCode());
         }
         List<LmStudioModelInfo> loaded = parseLoadedLlms(response.body());
         if (loaded.isEmpty()) {
            List<LmStudioModelInfo> openai = fetchOpenAiCompatModels(baseUrl, apiKey);
            if (!openai.isEmpty()) {
               LOGGER.info("LM Studio native /api/v1/models had no loaded LLMs; using {} from /v1/models", openai.size());
               return Snapshot.ok(nowMs, openai);
            }
         }
         return Snapshot.ok(nowMs, loaded);
      } catch (Exception e) {
         try {
            List<LmStudioModelInfo> openai = fetchOpenAiCompatModels(baseUrl, apiKey);
            if (!openai.isEmpty()) {
               return Snapshot.ok(nowMs, openai);
            }
         } catch (Exception ignored) {
            // fall through
         }
         return Snapshot.unreachable(nowMs, e.toString());
      }
   }

   private static List<LmStudioModelInfo> fetchOpenAiCompatModels(@Nullable String baseUrl, @Nullable String apiKey) throws Exception {
      String url = toOpenAiModelsUrl(baseUrl);
      if (url == null) {
         return List.of();
      }
      HttpResponse<String> response = getJson(url, apiKey);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
         return List.of();
      }
      return parseOpenAiCompatModels(response.body());
   }

   private static HttpResponse<String> getJson(String url, @Nullable String apiKey) throws Exception {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
         .uri(URI.create(url))
         .timeout(Duration.ofSeconds(3L))
         .GET()
         .header("Accept", "application/json");
      if (apiKey != null && !apiKey.isBlank()) {
         builder.header("Authorization", "Bearer " + apiKey);
      }
      return CLIENT.send(builder.build(), BodyHandlers.ofString());
   }

   static List<LmStudioModelInfo> parseLoadedLlms(String body) {
      List<LmStudioModelInfo> out = new ArrayList<>();
      JsonObject root = GSON.fromJson(body, JsonObject.class);
      if (root == null) {
         return out;
      }
      JsonArray models = null;
      if (root.has("models") && root.get("models").isJsonArray()) {
         models = root.getAsJsonArray("models");
      } else if (root.has("data") && root.get("data").isJsonArray()) {
         models = root.getAsJsonArray("data");
      }
      if (models == null) {
         return out;
      }
      for (JsonElement el : models) {
         if (!el.isJsonObject()) {
            continue;
         }
         JsonObject model = el.getAsJsonObject();
         String type = text(model, "type");
         if (!isLanguageModelType(type)) {
            continue;
         }
         String key = firstNonBlank(text(model, "key"), text(model, "id"));
         String display = firstNonBlank(text(model, "display_name"), text(model, "name"), key);
         if (!model.has("loaded_instances") || !model.get("loaded_instances").isJsonArray()) {
            // OpenAPI-compat / partial native payloads: accept loaded/ready state, or a bare id.
            String state = text(model, "state");
            if (key != null && !key.isBlank() && (
               state == null || state.isBlank()
                  || "loaded".equalsIgnoreCase(state)
                  || "ready".equalsIgnoreCase(state)
            )) {
               // Only trust bare entries when this looks like an OpenAI-style object (has id, no instances field).
               if ("loaded".equalsIgnoreCase(state) || "ready".equalsIgnoreCase(state) || model.has("id")) {
                  out.add(new LmStudioModelInfo(key, key, display, type));
               }
            }
            continue;
         }
         JsonArray instances = model.getAsJsonArray("loaded_instances");
         if (instances.isEmpty()) {
            continue;
         }
         for (JsonElement instEl : instances) {
            if (!instEl.isJsonObject()) {
               continue;
            }
            JsonObject inst = instEl.getAsJsonObject();
            String instanceId = firstNonBlank(text(inst, "id"), key);
            if (instanceId == null || instanceId.isBlank()) {
               continue;
            }
            out.add(new LmStudioModelInfo(key == null ? instanceId : key, instanceId, display, type));
         }
      }
      out.sort(Comparator.comparing(LmStudioModelInfo::effectiveModelId));
      return out;
   }

   private static boolean isLanguageModelType(@Nullable String type) {
      if (type == null || type.isBlank()) {
         return true;
      }
      String t = type.trim().toLowerCase();
      return "llm".equals(t) || "vlm".equals(t) || "language".equals(t);
   }

   /**
    * OpenAI-compatible {@code GET /v1/models} payload ({@code data: [{id, ...}]}).
    * With JIT loading off, LM Studio typically lists the models the server will serve.
    */
   static List<LmStudioModelInfo> parseOpenAiCompatModels(String body) {
      List<LmStudioModelInfo> out = new ArrayList<>();
      JsonObject root = GSON.fromJson(body, JsonObject.class);
      if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
         return out;
      }
      for (JsonElement el : root.getAsJsonArray("data")) {
         if (!el.isJsonObject()) {
            continue;
         }
         JsonObject model = el.getAsJsonObject();
         String id = firstNonBlank(text(model, "id"), text(model, "key"));
         if (id == null || id.isBlank()) {
            continue;
         }
         String lower = id.toLowerCase();
         if (lower.contains("embed") || lower.contains("embedding")) {
            continue;
         }
         String display = firstNonBlank(text(model, "display_name"), text(model, "name"), id);
         out.add(new LmStudioModelInfo(id, id, display, "llm"));
      }
      out.sort(Comparator.comparing(LmStudioModelInfo::effectiveModelId));
      return out;
   }

   @Nullable
   static String toNativeModelsUrl(@Nullable String baseUrl) {
      return toAuthorityPath(baseUrl, "/api/v1/models");
   }

   @Nullable
   static String toOpenAiModelsUrl(@Nullable String baseUrl) {
      return toAuthorityPath(baseUrl, "/v1/models");
   }

   @Nullable
   private static String toAuthorityPath(@Nullable String baseUrl, String path) {
      if (baseUrl == null || baseUrl.isBlank()) {
         return null;
      }
      String normalized = normalizeBase(baseUrl);
      try {
         URI uri = URI.create(normalized.contains("://") ? normalized : "http://" + normalized);
         String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
         String authority = uri.getRawAuthority();
         if (authority == null || authority.isBlank()) {
            return null;
         }
         return scheme + "://" + authority + path;
      } catch (Exception e) {
         return null;
      }
   }

   private static String normalizeBase(String baseUrl) {
      String value = baseUrl.trim();
      while (value.endsWith("/")) {
         value = value.substring(0, value.length() - 1);
      }
      return value;
   }

   @Nullable
   private static String text(JsonObject obj, String field) {
      if (!obj.has(field) || obj.get(field).isJsonNull()) {
         return null;
      }
      try {
         return obj.get(field).getAsString();
      } catch (Exception e) {
         return null;
      }
   }

   @Nullable
   private static String firstNonBlank(String... values) {
      for (String v : values) {
         if (v != null && !v.isBlank()) {
            return v;
         }
      }
      return null;
   }

   public record Snapshot(long fetchedAtMs, boolean reachable, List<LmStudioModelInfo> loaded, @Nullable String error) {
      public static Snapshot empty() {
         return new Snapshot(0L, false, List.of(), "not_fetched");
      }

      public static Snapshot ok(long fetchedAtMs, List<LmStudioModelInfo> loaded) {
         return new Snapshot(fetchedAtMs, true, List.copyOf(loaded), null);
      }

      public static Snapshot unreachable(long fetchedAtMs, String error) {
         return new Snapshot(fetchedAtMs, false, List.of(), error);
      }

      public boolean fresh(long nowMs) {
         return this.fetchedAtMs > 0L && nowMs - this.fetchedAtMs <= CACHE_TTL_MS;
      }

      public Optional<LmStudioModelInfo> find(String idOrKey) {
         if (idOrKey == null || idOrKey.isBlank()) {
            return Optional.empty();
         }
         String needle = idOrKey.trim();
         for (LmStudioModelInfo info : this.loaded) {
            if (needle.equals(info.effectiveModelId()) || needle.equals(info.modelKey()) || needle.equals(info.instanceId())) {
               return Optional.of(info);
            }
         }
         return Optional.empty();
      }

      public String fingerprint() {
         if (!this.reachable) {
            return "unreachable";
         }
         return this.loaded.stream().map(LmStudioModelInfo::effectiveModelId).collect(Collectors.joining("|"));
      }
   }
}
