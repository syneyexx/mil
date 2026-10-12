package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.tick.EntitySimulationLod;
import org.millenaire.perf.tick.ServerTickGovernor;
import org.millenaire.perf.tick.VillageSimulationLod;
import org.millenaire.perf.tick.VillagerAiCadence;

/**
 * Read-only tick-governor diagnostics under {@code /millenaire dev tick}.
 */
public final class TickCommand {
   private TickCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tick");
      root.executes(TickCommand::status);
      root.then(Commands.literal("status").executes(TickCommand::status));
      root.then(Commands.literal("metrics").executes(TickCommand::metrics));
      root.then(Commands.literal("dump").executes(TickCommand::dump));
      parent.then(root);
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      String msg = "governor="
         + ServerTickGovernor.enabled()
         + " adaptive="
         + ServerTickGovernor.adaptiveBudgetEnabled()
         + " pressure="
         + ServerTickGovernor.pressure().name().toLowerCase()
         + " villageLod="
         + VillageSimulationLod.lodEnabled()
         + " midInterval="
         + VillageSimulationLod.midInterval()
         + " entityLod="
         + EntitySimulationLod.enabled()
         + " entityFarSleep="
         + EntitySimulationLod.farSleepEnabled()
         + " aiCadence="
         + VillagerAiCadence.enabled()
         + " aiMidDist="
         + VillagerAiCadence.midDistance()
         + " aiMidInterval="
         + VillagerAiCadence.midInterval()
         + " globalBudgetMs="
         + ServerTickGovernor.globalBudgetMs()
         + " effGlobalMs="
         + ServerTickGovernor.effectiveGlobalBudgetMs()
         + " worldgenReserveMs="
         + ServerTickGovernor.phaseWorldgenMs()
         + " goalPickBudget="
         + MillenaireServerConfig.SERVER.entityGoalPickBudgetPerTick.getAsInt()
         + " pathMaxStarts="
         + MillenaireServerConfig.SERVER.pathMaxStartsPerTick.getAsInt()
         + " legacyTimeBudgetMs="
         + MillenaireServerConfig.SERVER.worldgenTickTimeBudgetMs.getAsInt();
      ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }

   private static int metrics(CommandContext<CommandSourceStack> ctx) {
      ctx.getSource().sendSuccess(() -> Component.literal(ServerTickGovernor.diagnostic()), false);
      return 1;
   }

   private static int dump(CommandContext<CommandSourceStack> ctx) {
      ctx.getSource().sendSuccess(() -> Component.literal(ServerTickGovernor.dumpSnapshot()), false);
      return 1;
   }
}
