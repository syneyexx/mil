package org.millenaire.worldgen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.millenaire.worldgen.WorldgenEngine;

public final class WorldgenDiagCommand {
   private WorldgenDiagCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("worldgen")
         .requires(src -> src.hasPermission(2))
         .then(
            Commands.literal("near")
               .executes(ctx -> {
                  ServerLevel level = ctx.getSource().getLevel();
                  BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                  List<SettlementPlan> plans = SettlementPlanner.plansNear(level, pos, 512);
                  if (plans.isEmpty()) {
                     ctx.getSource().sendSuccess(() -> Component.literal("No Millénaire worldgen plans within 512 blocks."), false);
                     return 1;
                  }
                  for (SettlementPlan plan : plans) {
                     ctx.getSource()
                        .sendSuccess(
                           () -> Component.literal(
                              String.format(
                                 "%s status=%s type=%s center=%s fail=%s buildings=%d/%d",
                                 plan.planId(),
                                 plan.status(),
                                 plan.villageTypeId(),
                                 plan.center().toShortString(),
                                 plan.failureReason(),
                                 plan.buildings().stream().filter(b -> b.materialized()).count(),
                                 plan.buildings().size()
                              )
                           ),
                           false
                        );
                  }
                  return plans.size();
               })
         )
         .then(
            Commands.literal("content")
               .executes(ctx -> {
                  WorldgenContentSnapshot snap = WorldgenContentSnapshot.require();
                  ctx.getSource()
                     .sendSuccess(
                        () -> Component.literal(
                           "Worldgen snapshot v"
                              + snap.plannerVersion()
                              + " villages="
                              + snap.naturalVillages().size()
                              + " lone="
                              + snap.naturalLoneBuildings().size()
                              + " invalid="
                              + snap.invalidCount()
                              + " engine="
                              + WorldgenEngine.isEnabled()
                        ),
                        false
                     );
                  int shown = 0;
                  for (String line : snap.reportLines()) {
                     if (shown++ >= 40) {
                        ctx.getSource().sendSuccess(() -> Component.literal("... truncated ..."), false);
                        break;
                     }
                     String copy = line;
                     ctx.getSource().sendSuccess(() -> Component.literal(copy), false);
                  }
                  return 1;
               })
         )
         .then(
            Commands.literal("version")
               .executes(ctx -> {
                  ctx.getSource()
                     .sendSuccess(
                        () -> Component.literal("Millénaire worldgen planner version " + org.millenaire.worldgen.plan.WorldgenSeeds.PLANNER_VERSION),
                        false
                     );
                  return 1;
               })
         )
         .then(
            Commands.literal("metrics")
               .executes(ctx -> {
                  ctx.getSource()
                     .sendSuccess(() -> Component.literal(org.millenaire.worldgen.WorldgenMetrics.snapshot()), false);
                  return 1;
               })
         );

      dispatcher.register(Commands.literal("millenaire").then(root));
   }
}
