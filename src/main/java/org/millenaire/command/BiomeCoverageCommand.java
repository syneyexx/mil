package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.compat.BiomeCoverageReporter;

public final class BiomeCoverageCommand {
   private BiomeCoverageCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("biomecoverage")
            .executes(ctx -> {
               ServerLevel level = ctx.getSource().getLevel();
               BiomeCoverageReporter.Report report = BiomeCoverageReporter.scan(level);
               for (String line : BiomeCoverageReporter.format(report, 40)) {
                  ctx.getSource().sendSuccess(() -> Component.literal(line), false);
               }
               return report.uncovered() == 0 ? 1 : 0;
            })
      );
   }
}
