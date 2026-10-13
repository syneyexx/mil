package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageTreasuryService;

public final class BankCommand {
   private BankCommand() {
   }

   public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("millenaire")
            .then(
               Commands.literal("bank")
                  .then(Commands.literal("balance").executes(BankCommand::balance))
                  .then(
                     Commands.literal("deposit")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(BankCommand::deposit))
                  )
                  .then(
                     Commands.literal("withdraw")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(BankCommand::withdraw))
                  )
            )
      );
   }

   private static Village findOwnedNearby(ServerPlayer player, ServerLevel level) {
      Village best = null;
      double bestDist = Double.MAX_VALUE;
      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (!v.isControlledBy(player.getUUID())) {
            continue;
         }
         double d = v.getCenter().distSqr(player.blockPosition());
         if (d < bestDist) {
            bestDist = d;
            best = v;
         }
      }
      return best != null && bestDist <= 128.0 * 128.0 ? best : null;
   }

   private static int balance(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      if (!(source.getEntity() instanceof ServerPlayer player)) {
         source.sendFailure(Component.literal("Players only."));
         return 0;
      }
      ServerLevel level = player.server.getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      Village village = findOwnedNearby(player, level);
      if (village == null) {
         source.sendFailure(Component.translatable("message.millenaire.designate.too_far"));
         return 0;
      }
      int bal = VillageTreasuryService.balance(level, village);
      source.sendSuccess(
         () -> Component.translatable("panel.millenaire.bank.balance", MoneyHelper.formatPrice(bal), bal),
         false
      );
      return 1;
   }

   private static int deposit(CommandContext<CommandSourceStack> ctx) {
      return transfer(ctx, true);
   }

   private static int withdraw(CommandContext<CommandSourceStack> ctx) {
      return transfer(ctx, false);
   }

   private static int transfer(CommandContext<CommandSourceStack> ctx, boolean deposit) {
      CommandSourceStack source = ctx.getSource();
      if (!(source.getEntity() instanceof ServerPlayer player)) {
         source.sendFailure(Component.literal("Players only."));
         return 0;
      }
      ServerLevel level = player.server.getLevel(Level.OVERWORLD);
      if (level == null) {
         return 0;
      }
      Village village = findOwnedNearby(player, level);
      if (village == null) {
         source.sendFailure(Component.translatable("message.millenaire.designate.too_far"));
         return 0;
      }
      int amount = IntegerArgumentType.getInteger(ctx, "amount");
      if (deposit) {
         if (!MoneyHelper.removeDeniers(player.getInventory(), amount)) {
            source.sendFailure(Component.translatable("message.millenaire.bank.not_enough_player"));
            return 0;
         }
         int added = VillageTreasuryService.deposit(level, village, amount, VillageTreasuryService.REASON_PLAYER_DEPOSIT);
         if (added < amount) {
            MoneyHelper.addDeniers(player.getInventory(), amount - added, player);
         }
         int finalAdded = added;
         source.sendSuccess(() -> Component.translatable("message.millenaire.bank.deposited", MoneyHelper.formatPrice(finalAdded)), false);
      } else {
         if (!VillageTreasuryService.withdraw(level, village, amount, VillageTreasuryService.REASON_PLAYER_WITHDRAW)) {
            source.sendFailure(Component.translatable("message.millenaire.bank.not_enough_treasury"));
            return 0;
         }
         MoneyHelper.addDeniers(player.getInventory(), amount, player);
         source.sendSuccess(() -> Component.translatable("message.millenaire.bank.withdrawn", MoneyHelper.formatPrice(amount)), false);
      }
      return 1;
   }
}
