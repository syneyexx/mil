package org.millenaire.companion;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.millenaire.entity.MillVillager;

public final class CompanionCommand {
   private CompanionCommand() {
   }

   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> companion = Commands.literal("companion")
         .requires(source -> source.hasPermission(2));

      companion.then(Commands.literal("summon").executes(ctx -> summon(ctx, null))
         .then(
            Commands.argument("villagerType", StringArgumentType.string())
               .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "villagerType")))
         ));
      companion.then(
         Commands.literal("bind")
            .then(Commands.argument("target", EntityArgument.entity()).executes(CompanionCommand::bind))
      );
      companion.then(Commands.literal("dismiss").executes(CompanionCommand::dismiss));
      companion.then(Commands.literal("status").executes(CompanionCommand::status));

      event.getDispatcher().register(Commands.literal("millenaire").then(companion));
   }

   private static int summon(CommandContext<CommandSourceStack> ctx, String typeRaw) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      ResourceLocation type = null;
      if (typeRaw != null && !typeRaw.isBlank()) {
         type = ResourceLocation.tryParse(typeRaw.contains(":") ? typeRaw : "millenaire:" + typeRaw);
         if (type == null) {
            ctx.getSource().sendFailure(Component.translatable("command.millenaire.companion.bad_type"));
            return 0;
         }
      }
      return CompanionService.summon(player, type);
   }

   private static int bind(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      Entity entity = EntityArgument.getEntity(ctx, "target");
      if (!(entity instanceof MillVillager villager)) {
         ctx.getSource().sendFailure(Component.translatable("command.millenaire.companion.not_villager"));
         return 0;
      }
      return CompanionService.bind(player, villager);
   }

   private static int dismiss(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      return CompanionService.dismiss(player);
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      return CompanionService.status(player);
   }
}
