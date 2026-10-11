package org.millenaire.worldboss.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.worldboss.ActiveWorldBossEvent;
import org.millenaire.worldboss.WorldBossDefinition;
import org.millenaire.worldboss.WorldBossDefinitionRegistry;
import org.millenaire.worldboss.WorldBossEligibility;
import org.millenaire.worldboss.WorldBossEventPlanner;
import org.millenaire.worldboss.WorldBossEventSavedData;
import org.millenaire.worldboss.WorldBossTimerLogic;

public final class WorldBossCommand {
   private WorldBossCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("worldboss")
         .requires(source -> source.hasPermission(2))
         .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
         .then(Commands.literal("next").executes(ctx -> next(ctx.getSource())))
         .then(
            Commands.literal("start")
               .executes(ctx -> start(ctx.getSource(), null))
               .then(
                  Commands.argument("bossId", StringArgumentType.string())
                     .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "bossId")))
               )
         )
         .then(Commands.literal("end").executes(ctx -> end(ctx.getSource())))
         .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));

      dispatcher.register(Commands.literal("millenaire").then(root));
      dispatcher.register(root);
   }

   private static int status(CommandSourceStack source) {
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No overworld"));
         return 0;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      ActiveWorldBossEvent active = data.active();
      boolean enabled = WorldBossEligibility.eventsEnabled();
      source.sendSuccess(
         () -> Component.literal(
            "WorldBoss enabled="
               + enabled
               + " elapsed="
               + data.elapsedActiveTicks()
               + "/"
               + data.nextIntervalTicks()
               + " remaining="
               + WorldBossTimerLogic.remaining(data.elapsedActiveTicks(), data.nextIntervalTicks())
               + " lastBoss="
               + data.lastBossId()
               + " lastOutcome="
               + data.lastOutcome()
         ),
         false
      );
      if (active != null) {
         source.sendSuccess(
            () -> Component.literal(
               "Active event="
                  + active.eventId()
                  + " boss="
                  + active.bossId()
                  + " state="
                  + active.state()
                  + " entity="
                  + active.bossEntityUuid()
            ),
            false
         );
      }
      return 1;
   }

   private static int next(CommandSourceStack source) {
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return 0;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      int remaining = WorldBossTimerLogic.remaining(data.elapsedActiveTicks(), data.nextIntervalTicks());
      source.sendSuccess(
         () -> Component.translatable("millenaire.worldboss.command.next", remaining, remaining / 1200),
         false
      );
      return 1;
   }

   private static int start(CommandSourceStack source, String bossIdRaw) {
      ResourceLocation id = null;
      if (bossIdRaw != null) {
         id = ResourceLocation.tryParse(bossIdRaw.contains(":") ? bossIdRaw : "millenaire:" + bossIdRaw);
         if (id == null || WorldBossDefinitionRegistry.get().getOrNull(id) == null) {
            source.sendFailure(Component.translatable("millenaire.worldboss.command.unknown_boss", bossIdRaw));
            return 0;
         }
      }
      boolean ok = WorldBossEventPlanner.forceStart(source.getServer(), id);
      if (!ok) {
         source.sendFailure(Component.translatable("millenaire.worldboss.command.start_failed"));
         return 0;
      }
      source.sendSuccess(() -> Component.translatable("millenaire.worldboss.command.started"), true);
      return 1;
   }

   private static int end(CommandSourceStack source) {
      boolean ok = WorldBossEventPlanner.forceEnd(source.getServer());
      if (!ok) {
         source.sendFailure(Component.translatable("millenaire.worldboss.command.no_active"));
         return 0;
      }
      source.sendSuccess(() -> Component.translatable("millenaire.worldboss.command.ended"), true);
      return 1;
   }

   private static int list(CommandSourceStack source) {
      for (WorldBossDefinition def : WorldBossDefinitionRegistry.get().all()) {
         source.sendSuccess(
            () -> Component.literal(def.id() + " weight=" + def.weight() + " entity=" + def.entityType()),
            false
         );
      }
      return 1;
   }
}
