package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.worldgen.starter.StarterSettlementSavedData;
import org.millenaire.worldgen.starter.StarterSettlementService;

/**
 * Admin helpers for the guaranteed starter settlement (default Gabbers Koninkrijk).
 */
public final class StarterSettlementCommand {
   private StarterSettlementCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("starter")
            .then(Commands.literal("status").executes(StarterSettlementCommand::status))
            .then(Commands.literal("retry").executes(StarterSettlementCommand::retry))
      );
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No overworld."));
         return 0;
      }
      StarterSettlementSavedData data = StarterSettlementSavedData.get(overworld);
      String centre = data.centre() != null ? data.centre().toShortString() : "-";
      source.sendSuccess(
         () -> Component.literal(
            "Starter settlement: placed="
               + data.isPlaced()
               + " gaveUp="
               + data.hasGaveUp()
               + " attempts="
               + data.attempts()
               + " centre="
               + centre
         ),
         false
      );
      return 1;
   }

   private static int retry(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         source.sendFailure(Component.literal("No overworld."));
         return 0;
      }
      StarterSettlementSavedData before = StarterSettlementSavedData.get(overworld);
      if (before.isPlaced() && before.villageId() != null) {
         source.sendFailure(Component.literal("Starter already placed at " + before.centre()));
         return 0;
      }
      boolean armed = StarterSettlementService.retry(overworld);
      StarterSettlementSavedData after = StarterSettlementSavedData.get(overworld);
      if (after.isPlaced()) {
         source.sendSuccess(
            () -> Component.literal("Starter settlement placed at " + after.centre()),
            true
         );
         return 1;
      }
      if (armed) {
         source.sendSuccess(
            () -> Component.literal(
               "Starter retry armed (attempts=" + after.attempts() + ", gaveUp=" + after.hasGaveUp() + "). Keep the world running."
            ),
            true
         );
         return 1;
      }
      source.sendFailure(Component.literal("Starter retry skipped (culture village already present or disabled)."));
      return 0;
   }
}
