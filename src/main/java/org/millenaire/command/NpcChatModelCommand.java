package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.millenaire.chat.LmStudioModelDiscovery;
import org.millenaire.chat.LmStudioModelInfo;
import org.millenaire.chat.NpcModelResolver;

/**
 * Player/op commands for LM Studio model discovery and selection.
 * Remote OpenAI-compatible providers keep using the configured model name.
 */
public final class NpcChatModelCommand {
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
      event.getDispatcher().register(Commands.literal("millenaire").then(npcchat));
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      if (!NpcModelResolver.usesLmStudio()) {
         NpcModelResolver.Result remote = NpcModelResolver.resolveCachedOrManual();
         if (remote.ok()) {
            source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.remote", remote.modelId()), false);
         } else {
            source.sendFailure(Component.translatable(remote.messageKey()));
         }
         return remote.ok() ? 1 : 0;
      }
      return refreshAndReport(source, false);
   }

   private static int list(CommandContext<CommandSourceStack> ctx) {
      return refreshAndReport(ctx.getSource(), true);
   }

   private static int refresh(CommandContext<CommandSourceStack> ctx) {
      return refreshAndReport(ctx.getSource(), true);
   }

   private static int select(CommandContext<CommandSourceStack> ctx) {
      if (!NpcModelResolver.usesLmStudio()) {
         ctx.getSource().sendFailure(Component.translatable("command.millenaire.npcchat.model.not_lmstudio"));
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
         source.sendFailure(Component.translatable("command.millenaire.npcchat.model.not_lmstudio"));
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
         source.sendFailure(Component.translatable(resolved.messageKey()));
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.select_hint"), false);
      }
      if (changed) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.npcchat.model.changed"), false);
      }
   }
}
