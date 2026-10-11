package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.millenaire.chat.LmStudioModelDiscovery;
import org.millenaire.chat.LmStudioModelInfo;
import org.millenaire.chat.NpcModelClient;
import org.millenaire.chat.NpcModelResolver;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Player/op commands for LM Studio model discovery, selection, and connectivity probe.
 */
public final class NpcChatModelCommand {
   private static final HttpClient PROBE_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2L))
      .followRedirects(Redirect.NORMAL)
      .version(Version.HTTP_1_1)
      .build();

   private static final SuggestionProvider<CommandSourceStack> LOADED_MODELS = (ctx, builder) -> {
      List<String> ids = LmStudioModelDiscovery.cached().loaded().stream().map(LmStudioModelInfo::effectiveModelId).toList();
      return SharedSuggestionProvider.suggest(ids, builder);
   };

   private NpcChatModelCommand() {
   }

   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> model = Commands.literal("model").executes(NpcChatModelCommand::status);
      model.then(
         Commands.literal("select")
            .then(
               Commands.argument("id", StringArgumentType.greedyString())
                  .suggests(LOADED_MODELS)
                  .executes(NpcChatModelCommand::select)
            )
      );
      model.then(Commands.literal("clear").executes(NpcChatModelCommand::clearSelection));
      model.then(Commands.literal("refresh").executes(NpcChatModelCommand::refresh));

      LiteralArgumentBuilder<CommandSourceStack> npcchat = Commands.literal("npcchat").requires(source -> source.hasPermission(2));
      npcchat.then(model);
      npcchat.then(
         Commands.literal("models")
            .executes(NpcChatModelCommand::list)
            .then(Commands.literal("refresh").executes(NpcChatModelCommand::refresh))
      );
      npcchat.then(Commands.literal("probe").executes(NpcChatModelCommand::probe));
      event.getDispatcher().register(Commands.literal("millenaire").then(npcchat));
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      return refreshAndReport(ctx.getSource(), false);
   }

   private static int list(CommandContext<CommandSourceStack> ctx) {
      return refreshAndReport(ctx.getSource(), true);
   }

   private static int refresh(CommandContext<CommandSourceStack> ctx) {
      return refreshAndReport(ctx.getSource(), true);
   }

   private static int select(CommandContext<CommandSourceStack> ctx) {
      if (!NpcModelResolver.usesLmStudio()) {
         ctx.getSource().sendFailure(NpcModelClient.lmStudioOnlyComponent());
         return 0;
      }
      String id = StringArgumentType.getString(ctx, "id").trim();
      CommandSourceStack source = ctx.getSource();
      LmStudioModelDiscovery.refreshAsync(true).thenAccept(snap -> source.getServer().execute(() -> {
         if (!snap.reachable()) {
            source.sendFailure(Component.translatable("gui.millenaire.npcchat.lmstudio.unreachable"));
            return;
         }
         if (snap.find(id).isEmpty()) {
            source.sendFailure(Component.translatable("command.millenaire.npcchat.model.not_loaded", id));
            return;
         }
         LmStudioModelDiscovery.selectManual(id);
         LmStudioModelInfo info = snap.find(id).orElseThrow();
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.selected", info.label()), true);
      }));
      return 1;
   }

   private static int clearSelection(CommandContext<CommandSourceStack> ctx) {
      LmStudioModelDiscovery.clearManualSelection();
      ctx.getSource().sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.cleared"), false);
      return 1;
   }

   private static int refreshAndReport(CommandSourceStack source, boolean listAll) {
      if (!NpcModelResolver.usesLmStudio()) {
         source.sendFailure(NpcModelClient.lmStudioOnlyComponent());
         return 0;
      }
      CompletableFuture<LmStudioModelDiscovery.Snapshot> future = LmStudioModelDiscovery.refreshAsync(true);
      future.thenAccept(snap -> source.getServer().execute(() -> report(source, snap, listAll)));
      return 1;
   }

   private static void report(CommandSourceStack source, LmStudioModelDiscovery.Snapshot snap, boolean listAll) {
      boolean changed = LmStudioModelDiscovery.consumeChangeFlag();
      if (!snap.reachable()) {
         source.sendFailure(Component.translatable("gui.millenaire.npcchat.lmstudio.unreachable"));
         return;
      }
      List<LmStudioModelInfo> loaded = snap.loaded();
      if (loaded.isEmpty()) {
         source.sendFailure(Component.translatable("gui.millenaire.npcchat.lmstudio.no_loaded"));
         return;
      }
      NpcModelResolver.Result resolved = NpcModelResolver.resolveLmStudio(snap);
      if (listAll || loaded.size() > 1) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.loaded_count", loaded.size()), false);
         for (LmStudioModelInfo info : loaded) {
            boolean active = resolved.ok() && info.effectiveModelId().equals(resolved.modelId());
            source.sendSuccess(() -> Component.literal((active ? "* " : "  ") + info.label()), false);
         }
      }
      if (resolved.ok()) {
         String mode = loaded.size() == 1 ? "auto" : "selected";
         source.sendSuccess(
            () -> Component.translatable("command.millenaire.npcchat.model.active", resolved.displayLabel(), mode),
            false
         );
      } else {
         source.sendFailure(resolved.messageComponent());
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.select_hint"), false);
      }
      if (changed) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.changed"), false);
      }
   }

   private static int probe(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         source.sendFailure(Component.translatable("gui.millenaire.npcchat.disabled"));
         return 0;
      }
      String rawBase = MillenaireServerConfig.SERVER.npcChatBaseUrl.get();
      if (!NpcModelResolver.usesLmStudio()) {
         source.sendFailure(NpcModelClient.lmStudioOnlyComponent());
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.probe.base", rawBase == null ? "" : rawBase), false);
         return 0;
      }
      String base = NpcModelClient.normalizeCompletionsBaseUrl(rawBase);
      String modelsUrl = LmStudioModelDiscovery.toOpenAiModelsUrl(rawBase);
      source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.probe.start", base == null ? "" : base), false);

      CompletableFuture.supplyAsync(() -> {
         long t0 = System.currentTimeMillis();
         ProbeResult result = new ProbeResult();
         result.baseUrl = base;
         result.modelsUrl = modelsUrl;
         try {
            if (modelsUrl == null) {
               result.error = "invalid_url";
               return result;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
               .uri(URI.create(modelsUrl))
               .timeout(Duration.ofSeconds(3L))
               .GET()
               .header("Accept", "application/json");
            String apiKey = NpcModelClient.resolveApiKeyAllowingBlankForLmStudio();
            if (apiKey != null && !apiKey.isBlank()) {
               builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = PROBE_CLIENT.send(builder.build(), BodyHandlers.ofString());
            result.latencyMs = System.currentTimeMillis() - t0;
            result.httpStatus = response.statusCode();
            result.ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (result.ok) {
               result.modelCount = LmStudioModelDiscovery.parseOpenAiCompatModels(response.body()).size();
            }
         } catch (Exception e) {
            result.latencyMs = System.currentTimeMillis() - t0;
            result.error = e.getClass().getSimpleName();
            result.ok = false;
         }
         return result;
      }).thenAccept(result -> source.getServer().execute(() -> {
         if (!result.ok) {
            source.sendFailure(Component.translatable(
               "command.millenaire.npcchat.probe.fail",
               result.httpStatus > 0 ? String.valueOf(result.httpStatus) : (result.error == null ? "?" : result.error),
               result.latencyMs
            ));
            return;
         }
         source.sendSuccess(() -> Component.translatable(
            "command.millenaire.npcchat.probe.ok",
            result.modelCount,
            result.latencyMs
         ), false);
         LmStudioModelDiscovery.refreshAsync(true).thenAccept(snap -> source.getServer().execute(() -> {
            NpcModelResolver.Result resolved = NpcModelResolver.resolveLmStudio(snap);
            if (resolved.ok()) {
               source.sendSuccess(
                  () -> Component.translatable("command.millenaire.npcchat.model.active", resolved.displayLabel(), "probe"),
                  false
               );
            } else {
               source.sendFailure(resolved.messageComponent());
            }
         }));
      }));
      return 1;
   }

   private static final class ProbeResult {
      String baseUrl;
      String modelsUrl;
      boolean ok;
      int httpStatus;
      int modelCount;
      long latencyMs;
      String error;
   }
}
