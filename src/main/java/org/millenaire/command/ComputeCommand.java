package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.compute.ComputeMetrics;
import org.millenaire.perf.compute.ComputeOffload;
import org.millenaire.perf.compute.MillenaireComputePool;

/**
 * Read-only compute-pool diagnostics under {@code /millenaire dev compute}.
 */
public final class ComputeCommand {
   private ComputeCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("compute");
      root.executes(ComputeCommand::status);
      root.then(Commands.literal("status").executes(ComputeCommand::status));
      root.then(Commands.literal("metrics").executes(ComputeCommand::metrics));
      parent.then(root);
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      String msg = "enabled="
         + ComputeOffload.masterEnabled()
         + " paths="
         + ComputeOffload.villagePaths()
         + " highway="
         + ComputeOffload.highwayPlan()
         + " worldgen="
         + ComputeOffload.worldgenPlan()
         + " waypoints="
         + ComputeOffload.waypointGraph()
         + " workersCfg="
         + ComputeOffload.configuredWorkerThreads()
         + " applyBudget="
         + ComputeOffload.applyBudgetPerTick()
         + " "
         + MillenaireComputePool.describe()
         + " masterCfg="
         + MillenaireServerConfig.SERVER.computeOffloadEnabled.get();
      ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }

   private static int metrics(CommandContext<CommandSourceStack> ctx) {
      ctx.getSource().sendSuccess(() -> Component.literal(ComputeMetrics.snapshot()), false);
      return 1;
   }
}
