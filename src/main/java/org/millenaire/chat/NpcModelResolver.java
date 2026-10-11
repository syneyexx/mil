package org.millenaire.chat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Resolves the effective chat-completions model id for local LM Studio or Ollama.
 */
public final class NpcModelResolver {
   private NpcModelResolver() {
   }

   public enum Status {
      OK,
      PROVIDER_DISABLED,
      MISSING_BASE_URL,
      NOT_LMSTUDIO,
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
            case MISSING_BASE_URL -> "gui.millenaire.npcchat.missing_base_url";
            case NOT_LMSTUDIO -> "gui.millenaire.npcchat.lmstudio.only";
            case LMSTUDIO_UNREACHABLE -> "gui.millenaire.npcchat.lmstudio.unreachable";
            case LMSTUDIO_NO_LOADED -> "gui.millenaire.npcchat.lmstudio.no_loaded";
            case LMSTUDIO_MULTI_NEEDS_SELECTION -> "gui.millenaire.npcchat.lmstudio.choose_model";
            case OK -> "gui.millenaire.npcchat.lmstudio.ready";
         };
      }

      public Component messageComponent() {
         if (this.status == Status.NOT_LMSTUDIO) {
            return NpcModelClient.lmStudioOnlyComponent();
         }
         return Component.translatable(messageKey());
      }
   }

   /** True when the configured base URL is an accepted local LLM endpoint (LM Studio or Ollama). */
   public static boolean usesLmStudio() {
      return LmStudioModelDiscovery.isAcceptedLocalLlmBaseUrl(MillenaireServerConfig.SERVER.npcChatBaseUrl.get());
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
         return CompletableFuture.completedFuture(new Result(Status.NOT_LMSTUDIO, null, null, List.of()));
      }
      LmStudioModelDiscovery.Snapshot cached = LmStudioModelDiscovery.cached();
      long now = System.currentTimeMillis();
      if (cached.fresh(now) && cached.reachable()) {
         return CompletableFuture.completedFuture(resolveLmStudio(cached));
      }
      return LmStudioModelDiscovery.refreshAsync(false).thenApply(NpcModelResolver::resolveLmStudio);
   }

   public static Result resolveCachedOrManual() {
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         return new Result(Status.PROVIDER_DISABLED, null, null, List.of());
      }
      if (!usesLmStudio()) {
         return new Result(Status.NOT_LMSTUDIO, null, null, List.of());
      }
      return resolveLmStudio(LmStudioModelDiscovery.cached());
   }

   public static Result resolveLmStudio(LmStudioModelDiscovery.Snapshot snap) {
      if (!snap.reachable()) {
         // Fail loud: do not POST to a host discovery cannot reach.
         return new Result(Status.LMSTUDIO_UNREACHABLE, null, null, List.of());
      }
      String configuredTrimmed = configuredModelId();
      List<LmStudioModelInfo> loaded = snap.loaded();
      if (loaded.isEmpty()) {
         // Reachable but nothing loaded — allow a configured id for JIT / OpenAI-compat servers.
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
         return new Result(Status.OK, configuredTrimmed, configuredTrimmed, loaded);
      }

      return new Result(Status.LMSTUDIO_MULTI_NEEDS_SELECTION, null, null, loaded);
   }

   private static String configuredModelId() {
      try {
         if (MillenaireServerConfig.SERVER == null || MillenaireServerConfig.SERVER.npcChatModel == null) {
            return "";
         }
         String configured = MillenaireServerConfig.SERVER.npcChatModel.get();
         return configured == null ? "" : configured.trim();
      } catch (Exception e) {
         return "";
      }
   }
}
