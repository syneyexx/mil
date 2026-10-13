package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.crime.BountyHudSync;
import org.millenaire.crime.PlayerBountySavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class BountyCommand {
   private BountyCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder)Commands.literal("bounty").requires(source -> source.hasPermission(2)))
                  .then(
                     Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player()).executes(BountyCommand::getAll))
                  )
                  .then(
                     Commands.literal("set")
                        .then(
                           Commands.argument("player", EntityArgument.player())
                              .then(
                                 Commands.argument("amount", IntegerArgumentType.integer(0, 1000))
                                    .executes(BountyCommand::setNearest)
                                    .then(
                                       Commands.argument("culture", ResourceLocationArgument.id())
                                          .executes(BountyCommand::setCulture)
                                    )
                              )
                        )
                  )
                  .then(
                     Commands.literal("add")
                        .then(
                           Commands.argument("player", EntityArgument.player())
                              .then(
                                 Commands.argument("amount", IntegerArgumentType.integer())
                                    .executes(BountyCommand::addNearest)
                                    .then(
                                       Commands.argument("culture", ResourceLocationArgument.id())
                                          .executes(BountyCommand::addCulture)
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int getAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      ServerLevel level = ctx.getSource().getLevel();
      Map<ResourceLocation, Integer> all = PlayerBountySavedData.get(level).getAll(target.getUUID());
      if (all.isEmpty()) {
         ctx.getSource().sendSuccess(() -> Component.translatable("command.millenaire.bounty.empty", target.getName().getString()), false);
         return 1;
      }

      for (Entry<ResourceLocation, Integer> entry : all.entrySet()) {
         int value = entry.getValue();
         ctx.getSource()
            .sendSuccess(
               () -> Component.translatable("command.millenaire.bounty.line", target.getName().getString(), entry.getKey().toString(), value),
               false
            );
      }

      return all.size();
   }

   private static int setNearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      return mutateNearest(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true);
   }

   private static int addNearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      return mutateNearest(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false);
   }

   private static int setCulture(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      int amount = IntegerArgumentType.getInteger(ctx, "amount");
      ResourceLocation culture = ResourceLocationArgument.getId(ctx, "culture");
      PlayerBountySavedData.get(ctx.getSource().getLevel()).set(target.getUUID(), culture, amount);
      BountyHudSync.syncToPlayer(target);
      ctx.getSource()
         .sendSuccess(
            () -> Component.translatable("command.millenaire.bounty.set", target.getName().getString(), culture.toString(), amount), true
         );
      return 1;
   }

   private static int addCulture(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      int amount = IntegerArgumentType.getInteger(ctx, "amount");
      ResourceLocation culture = ResourceLocationArgument.getId(ctx, "culture");
      int next = PlayerBountySavedData.get(ctx.getSource().getLevel()).add(target.getUUID(), culture, amount);
      BountyHudSync.syncToPlayer(target);
      ctx.getSource()
         .sendSuccess(
            () -> Component.translatable("command.millenaire.bounty.set", target.getName().getString(), culture.toString(), next), true
         );
      return 1;
   }

   private static int mutateNearest(CommandContext<CommandSourceStack> ctx, int amount, boolean set) throws CommandSyntaxException {
      CommandSourceStack source = ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_overworld_villages"));
         return 0;
      }

      ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
      ServerPlayer executor = source.getPlayer();
      BlockPos searchPos = executor != null ? executor.blockPosition() : target.blockPosition();
      Village village = VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 500.0);
      if (village == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.no_village_radius", 500));
         return 0;
      }

      UUID playerId = target.getUUID();
      ResourceLocation culture = village.getCultureId();
      PlayerBountySavedData data = PlayerBountySavedData.get(level);
      int next = set ? amount : data.add(playerId, culture, amount);
      if (set) {
         data.set(playerId, culture, amount);
         next = amount;
      }

      int display = next;
      BountyHudSync.syncToPlayer(target);
      source.sendSuccess(
         () -> Component.translatable("command.millenaire.bounty.set", target.getName().getString(), culture.toString(), display), true
      );
      return 1;
   }
}
