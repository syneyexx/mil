package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.world.settlement.Settlement;
import org.millenaire.world.settlement.SettlementSavedData;
import org.millenaire.world.settlement.SettlementSource;
import org.millenaire.world.settlement.SettlementTakeover;

public final class SettlementsCommand {
   private SettlementsCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("settlements").requires(s -> s.hasPermission(2));
      root.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())));
      root.then(Commands.literal("near").executes(ctx -> near(ctx.getSource())));
      root.then(Commands.literal("promote").then(Commands.literal("near").executes(ctx -> promote(ctx.getSource()))));
      root.then(Commands.literal("demote").then(Commands.literal("near").executes(ctx -> demote(ctx.getSource()))));
      dispatcher.register(root);
   }

   private static int info(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      var reg = SettlementSavedData.get(level).getRegistry();
      source.sendSuccess(
         () -> Component.literal(
            "Settlements total="
               + reg.size()
               + " vanilla="
               + reg.countBySource(SettlementSource.VANILLA)
               + " millenaire="
               + reg.countBySource(SettlementSource.MILLENAIRE)
               + " promotedVanilla="
               + reg.promotedCount()
               + " cap="
               + SettlementTakeover.maxActive()
               + " takeover="
               + SettlementTakeover.enabled()
         ),
         false
      );
      return 1;
   }

   private static int near(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      BlockPos pos = BlockPos.containing(source.getPosition());
      Settlement s = SettlementSavedData.get(level).getRegistry().getNearest(pos, 2000);
      if (s == null) {
         source.sendFailure(Component.literal("No settlement nearby"));
         return 0;
      }
      source.sendSuccess(
         () -> Component.literal(
            s.displayName()
               + " ["
               + s.source()
               + "] culture="
               + s.cultureId()
               + " at "
               + s.center().toShortString()
               + " promoted="
               + s.promoted()
         ),
         false
      );
      return 1;
   }

   private static int promote(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      boolean ok = SettlementTakeover.promoteNearest(level, BlockPos.containing(source.getPosition()));
      source.sendSuccess(() -> Component.literal(ok ? "Promoted nearest vanilla settlement." : "Promote failed."), true);
      return ok ? 1 : 0;
   }

   private static int demote(CommandSourceStack source) {
      ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      boolean ok = SettlementTakeover.demoteNearest(level, BlockPos.containing(source.getPosition()));
      source.sendSuccess(() -> Component.literal(ok ? "Demoted nearest promoted settlement." : "Demote failed."), true);
      return ok ? 1 : 0;
   }
}
