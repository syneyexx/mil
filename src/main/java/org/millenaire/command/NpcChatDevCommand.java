package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.chat.NpcChatSelfCheck;
import org.millenaire.chat.NpcGossipScheduler;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.NpcRumor;
import org.millenaire.chat.NpcRumorKnowledge;

public final class NpcChatDevCommand {
   private NpcChatDevCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("npcchat-selfcheck").executes(NpcChatDevCommand::selfCheck));
      parent.then(
         Commands.literal("npcchat-rumors")
            .then(Commands.argument("culture", StringArgumentType.string()).executes(NpcChatDevCommand::rumors))
      );
      parent.then(Commands.literal("npcchat-spread-day").executes(NpcChatDevCommand::spreadDay));
   }

   private static int selfCheck(CommandContext<CommandSourceStack> ctx) {
      String result = NpcChatSelfCheck.run();
      if ("PASS".equals(result)) {
         ctx.getSource().sendSuccess(() -> Component.translatable("command.millenaire.npcchat.selfcheck.pass"), false);
         return 1;
      }
      ctx.getSource().sendFailure(Component.translatable("command.millenaire.npcchat.selfcheck.fail", result));
      return 0;
   }

   private static int rumors(CommandContext<CommandSourceStack> ctx) {
      String culturePath = StringArgumentType.getString(ctx, "culture");
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath);
      ServerLevel level = ctx.getSource().getLevel();
      NpcMindSavedData data = NpcMindSavedData.get(level);
      List<NpcRumor> list = new ArrayList<>(data.activeRumorsForCulture(cultureId));
      if (list.isEmpty()) {
         ctx.getSource().sendSuccess(() -> Component.literal("No active rumors for " + culturePath), false);
         return 1;
      }
      for (NpcRumor rumor : list) {
         Map<Integer, Integer> hops = new HashMap<>();
         int knowers = 0;
         for (NpcMind mind : data.getMinds().values()) {
            NpcRumorKnowledge k = mind.getKnowledge(rumor.id());
            if (k != null) {
               knowers++;
               hops.merge(k.hop(), 1, Integer::sum);
            }
         }
         final long rumorId = rumor.id();
         final String canonical = rumor.canonical();
         final String hopSummary = hops.toString();
         final int knowerCount = knowers;
         ctx.getSource()
            .sendSuccess(
               () -> Component.literal("#" + rumorId + " " + canonical + " | hops=" + hopSummary + " | knowers=" + knowerCount),
               false
            );
      }
      return 1;
   }

   private static int spreadDay(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      NpcGossipScheduler.forceAdvanceDay(level);
      ctx.getSource().sendSuccess(() -> Component.literal("Forced NPC gossip day tick"), false);
      return 1;
   }
}
