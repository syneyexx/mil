package org.millenaire.living.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.social.NpcSocialProfile;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.living.social.SocialSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public final class LivingDevCommand {
   private LivingDevCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("living")
            .then(Commands.literal("needs").executes(LivingDevCommand::needs))
            .then(Commands.literal("events").executes(LivingDevCommand::events))
            .then(Commands.literal("requests").executes(LivingDevCommand::requests))
            .then(Commands.literal("social").executes(LivingDevCommand::social))
      );
   }

   private static Village nearest(ServerPlayer player) {
      Village best = null;
      double bestDist = Double.MAX_VALUE;
      for (Village v : VillageSavedData.get(player.serverLevel()).getVillageManager().getAllVillages()) {
         double d = player.blockPosition().distSqr(v.getCenter());
         if (d < bestDist) {
            bestDist = d;
            best = v;
         }
      }
      return best;
   }

   private static int needs(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      Village village = nearest(player);
      if (village == null) {
         ctx.getSource().sendFailure(Component.translatable("living.millenaire.command.no_village"));
         return 0;
      }
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(player.serverLevel(), village, true);
      ctx.getSource().sendSuccess(
         () -> Component.literal("Needs for " + village.getVillageName() + " (" + snap.needs().size() + ")"), false
      );
      for (VillageNeed need : snap.needs()) {
         ctx.getSource().sendSuccess(
            () -> Component.literal(
               " - " + need.type() + " " + need.severity() + " " + need.summaryDetail() + " shortage=" + need.shortage()
            ),
            false
         );
      }
      return 1;
   }

   private static int events(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         return 0;
      }
      Village village = nearest(player);
      if (village == null) {
         ctx.getSource().sendFailure(Component.translatable("living.millenaire.command.no_village"));
         return 0;
      }
      List<LivingEvent> recent = LivingEventService.recentPublic(player.serverLevel(), village.getId(), 12);
      ctx.getSource().sendSuccess(() -> Component.literal("Recent living events: " + recent.size()), false);
      for (LivingEvent event : recent) {
         ctx.getSource().sendSuccess(
            () -> Component.literal(" - " + event.eventType() + " @" + event.gameTime() + " " + event.details()), false
         );
      }
      return 1;
   }

   private static int requests(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         return 0;
      }
      ServerLevel level = player.serverLevel();
      List<VillageRequest> list = VillageRequestSavedData.get(level).forPlayer(player.getUUID());
      ctx.getSource().sendSuccess(() -> Component.literal("Your living requests: " + list.size()), false);
      for (VillageRequest r : list) {
         ctx.getSource().sendSuccess(
            () -> Component.literal(
               " - " + r.requestId().substring(0, 8) + " " + r.state() + " " + r.type() + " " + r.totalDelivered() + "/" + r.totalRequired()
            ),
            false
         );
      }
      return 1;
   }

   private static int social(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         return 0;
      }
      Village village = nearest(player);
      if (village == null) {
         ctx.getSource().sendFailure(Component.translatable("living.millenaire.command.no_village"));
         return 0;
      }
      SocialSavedData data = SocialSavedData.get(player.serverLevel());
      int profiles = 0;
      int relationships = 0;
      for (var rec : village.getVillagerRecords().values()) {
         NpcSocialProfile profile = data.get(rec.getUuid());
         if (profile != null) {
            profiles++;
            relationships += profile.relationships().size();
            NpcSocialService.ensurePersonality(profile, rec);
         }
      }
      int finalProfiles = profiles;
      int finalRelationships = relationships;
      ctx.getSource().sendSuccess(
         () -> Component.literal(
            "Social in " + village.getVillageName() + ": profiles=" + finalProfiles + " relationships=" + finalRelationships
         ),
         false
      );
      return 1;
   }
}
