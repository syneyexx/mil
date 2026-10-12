package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

/**
 * Op commands to toggle NPC gossip (daily rumor spread + new rumors from chat).
 */
public final class GossipCommand {
   private static final Logger LOGGER = LogUtils.getLogger();

   private GossipCommand() {
   }

   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> gossip = Commands.literal("gossip")
         .requires(source -> source.hasPermission(2))
         .executes(GossipCommand::status);
      gossip.then(Commands.literal("on").executes(ctx -> setEnabled(ctx, true)));
      gossip.then(Commands.literal("off").executes(ctx -> setEnabled(ctx, false)));
      gossip.then(Commands.literal("status").executes(GossipCommand::status));
      event.getDispatcher().register(Commands.literal("millenaire").then(gossip));
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      boolean enabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.npcChatGossip.get());
      ctx.getSource().sendSuccess(
         () -> Component.translatable(
            enabled ? "command.millenaire.gossip.status.on" : "command.millenaire.gossip.status.off"
         ),
         false
      );
      return enabled ? 1 : 0;
   }

   private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
      MillenaireServerConfig.SERVER.npcChatGossip.set(enabled);
      try {
         MillenaireServerConfig.SERVER.npcChatGossip.save();
      } catch (Exception e) {
         LOGGER.warn("Updated npcChat.gossip in memory but failed to save serverconfig: {}", e.toString());
      }
      ctx.getSource().sendSuccess(
         () -> Component.translatable(
            enabled ? "command.millenaire.gossip.enabled" : "command.millenaire.gossip.disabled"
         ),
         true
      );
      return 1;
   }
}
