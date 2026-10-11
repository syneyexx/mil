package org.millenaire.chat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Resolves the effective chat-completions model id.
 * LM Studio: auto-detect loaded LLMs via /api/v1/models.
 * Other providers: keep the configured manual model name.
 */
public final class NpcModelResolver {
   private NpcModelResolver() {
   }

   public enum Status {
      OK,
      PROVIDER_DISABLED,
      MISSING_API_KEY,
      MISSING_BASE_URL,
      MISSING_MANUAL_MODEL,
      LMSTUDIO_UNREACHABLE,
      LMSTUDIO_NO_LOADED,
      LMSTUDIO_MULTI_NEEDS_SELECTION
   }

   public record Result(Status status, @Nullable String modelId, @Nullable String displayLabel, List<LmStudioModelInfo> loaded) {
      public boolean ok() {
         return this.status == Status.OK && this.modelId != null && !this.modelId.isBlank();
      }

      public String messageKey() {
         return switch (this.status) {
            case PROVIDER_DISABLED -> "gui.millenaire.npcchat.disabled";
            case MISSING_API_KEY -> "gui.millenaire.npcchat.missing_api_key";
            case MISSING_BASE_URL -> "gui.millenaire.npcchat.missing_base_url";
            case MISSING_MANUAL_MODEL -> "gui.millenaire.npcchat.missing_manual_model";
            case LMSTUDIO_UNREACHABLE -> "gui.millenaire.npcchat.lmstudio.unreachable";
            case LMSTUDIO_NO_LOADED -> "gui.millenaire.npcchat.lmstudio.no_loaded";
            case LMSTUDIO_MULTI_NEEDS_SELECTION -> "gui.millenaire.npcchat.lmstudio.choose_model";
            case OK -> "gui.millenaire.npcchat.lmstudio.ready";
         };
      }
   }

   public static boolean usesLmStudio() {
      return LmStudioModelDiscovery.isLmStudioBaseUrl(MillenaireServerConfig.SERVER.npcChatBaseUrl.get());
   }

   public static CompletableFuture<Result> resolveAsync() {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return CompletableFuture.completedFuture(new Result(Status.PROVIDER_DISABLED, null, null, List.of()));
      }
      String baseUrl = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      if (baseUrl == null || baseUrl.isBlank()) {
         return CompletableFuture.completedFuture(new Result(Status.MISSING_BASE_URL, null, null, List.of()));
      }
      if (!usesLmStudio()) {
         return CompletableFuture.completedFuture(resolveManualRemote());
      }
      return LmStudioModelDiscovery.refreshAsync(false).thenApply(NpcModelResolver::resolveLmStudio);
   }

   public static Result resolveCachedOrManual() {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return new Result(Status.PROVIDER_DISABLED, null, null, List.of());
      }
      if (!usesLmStudio()) {
         return resolveManualRemote();
      }
      return resolveLmStudio(LmStudioModelDiscovery.cached());
   }

   private static Result resolveManualRemote() {
      String key = NpcModelClient.resolveApiKey();
      if (key == null) {
         return new Result(Status.MISSING_API_KEY, null, null, List.of());
      }
      String model = MillenaireServerConfig.SERVER.npcChatModel.get();
      if (model == null || model.isBlank()) {
         return new Result(Status.MISSING_MANUAL_MODEL, null, null, List.of());
      }
      return new Result(Status.OK, model.trim(), model.trim(), List.of());
   }

   public static Result resolveLmStudio(LmStudioModelDiscovery.Snapshot snap) {
      String configured = MillenaireServerConfig.SERVER.npcChatModel.get();
      String configuredTrimmed = configured == null ? "" : configured.trim();

      if (!snap.reachable()) {
         // Still allow a manually configured model id — chat/completions may work even when
         // /api/v1/models discovery fails (firewall, older LM Studio, brief startup race).
         if (!configuredTrimmed.isEmpty()) {
            return new Result(Status.OK, configuredTrimmed, configuredTrimmed, List.of());
         }
         return new Result(Status.LMSTUDIO_UNREACHABLE, null, null, List.of());
      }
      List<LmStudioModelInfo> loaded = snap.loaded();
      if (loaded.isEmpty()) {
         if (!configuredTrimmed.isEmpty()) {
            return new Result(Status.OK, configuredTrimmed, configuredTrimmed, loaded);
         }
         return new Result(Status.LMSTUDIO_NO_LOADED, null, null, loaded);
      }
      if (loaded.size() == 1) {
         LmStudioModelInfo only = loaded.get(0);
         return new Result(Status.OK, only.effectiveModelId(), only.label(), loaded);
      }

      String manualRuntime = LmStudioModelDiscovery.manualSelectionId();
      if (manualRuntime != null) {
         Optional<LmStudioModelInfo> chosen = snap.find(manualRuntime);
         if (chosen.isPresent()) {
            return new Result(Status.OK, chosen.get().effectiveModelId(), chosen.get().label(), loaded);
         }
      }

      if (!configuredTrimmed.isEmpty()) {
         Optional<LmStudioModelInfo> configuredMatch = snap.find(configuredTrimmed);
         if (configuredMatch.isPresent()) {
            return new Result(Status.OK, configuredMatch.get().effectiveModelId(), configuredMatch.get().label(), loaded);
         }
         // Configured id may still be accepted by /v1/chat/completions even if not in the snapshot.
         return new Result(Status.OK, configuredTrimmed, configuredTrimmed, loaded);
      }

      return new Result(Status.LMSTUDIO_MULTI_NEEDS_SELECTION, null, null, loaded);
   }
}
