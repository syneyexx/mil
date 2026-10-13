package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.world.road.RoadNetworkService;

public final class RoadsCommand {
   private RoadsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("roads").requires(s -> s.hasPermission(2));
      root.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())));
      root.then(
         Commands.literal("debug")
            .then(
               Commands.literal("show")
                  .then(Commands.argument("state", StringArgumentType.word()).executes(ctx -> debug(ctx.getSource(), StringArgumentType.getString(ctx, "state"))))
            )
      );
      root.then(
         Commands.literal("rebuild")
            .then(Commands.literal("all").executes(ctx -> rebuildAll(ctx.getSource())))
            .then(Commands.argument("edgeId", LongArgumentType.longArg()).executes(ctx -> rebuildEdge(ctx.getSource(), LongArgumentType.getLong(ctx, "edgeId"))))
      );
      root.then(Commands.literal("pause").executes(ctx -> pause(ctx.getSource(), true)));
      root.then(Commands.literal("resume").executes(ctx -> pause(ctx.getSource(), false)));
      dispatcher.register(root);
   }

   private static int info(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         source.sendFailure(Component.literal("No overworld"));
         return 0;
      }
      for (String line : RoadNetworkService.get().infoLines(level)) {
         source.sendSuccess(() -> Component.literal(line), false);
      }
      return 1;
   }

   private static int debug(CommandSourceStack source, String state) {
      boolean on = state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true");
      RoadNetworkService.get().setDebugParticles(on);
      source.sendSuccess(() -> Component.literal("Road debug particles: " + (on ? "on" : "off")), true);
      return 1;
   }

   private static int rebuildAll(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      RoadNetworkService.get().rebuildAll(level);
      source.sendSuccess(() -> Component.literal("All road edges reset; will replan and rebuild on chunk load."), true);
      return 1;
   }

   private static int rebuildEdge(CommandSourceStack source, long edgeId) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      RoadNetworkService.get().rebuildEdge(level, edgeId);
      source.sendSuccess(() -> Component.literal("Edge " + edgeId + " reset."), true);
      return 1;
   }

   private static int pause(CommandSourceStack source, boolean paused) {
      RoadNetworkService.get().getBuilder().setPaused(paused);
      source.sendSuccess(() -> Component.literal(paused ? "Road building paused." : "Road building resumed."), true);
      return 1;
   }
}
