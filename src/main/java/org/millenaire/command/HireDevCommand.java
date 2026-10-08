package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class HireDevCommand {
   private static final double SEARCH_RADIUS = 8.0;

   private HireDevCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)Commands.literal("hire").then(Commands.literal("clear").executes(HireDevCommand::clearHire)))
            .then(Commands.argument("hours", IntegerArgumentType.integer(0)).executes(HireDevCommand::doHire))
      );
   }

   private static MillVillager findNearest(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      AABB box = player.getBoundingBox().inflate(8.0);
      List<MillVillager> list = level.getEntitiesOfClass(MillVillager.class, box);
      MillVillager best = null;
      double bestSq = Double.MAX_VALUE;

      for (MillVillager v : list) {
         double d = player.distanceToSqr(v);
         if (d < bestSq) {
            bestSq = d;
            best = v;
         }
      }

      return best;
   }

   private static int doHire(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      if (source.getEntity() instanceof ServerPlayer player) {
         int var11 = IntegerArgumentType.getInteger(ctx, "hours");
         MillVillager v = findNearest(player);
         if (v != null && v.getVillageId() != null) {
            ServerLevel level = player.serverLevel();
            Village village = Village.resolve(level, v.getVillageId());
            if (village == null) {
               source.sendFailure(Component.literal("Villager has no resolvable village."));
               return 0;
            }

            long until = level.getGameTime() + var11 * 1000L;
            if (var11 == 0) {
               until = level.getGameTime() + 24000L;
            }

            village.setVillagerHired(level, v.getUUID(), player.getUUID(), until);
            long u = until;
            source.sendSuccess(() -> Component.literal("Hired " + v.getName().getString() + " until gametime " + u), false);
            return 1;
         } else {
            source.sendFailure(Component.literal("No nearby villager (with a village) found."));
            return 0;
         }
      } else {
         source.sendFailure(Component.literal("Must be run by a player."));
         return 0;
      }
   }

   private static int clearHire(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      if (source.getEntity() instanceof ServerPlayer player) {
         MillVillager v = findNearest(player);
         if (v != null && v.getVillageId() != null) {
            ServerLevel level = player.serverLevel();
            Village village = Village.resolve(level, v.getVillageId());
            if (village == null) {
               source.sendFailure(Component.literal("Villager has no resolvable village."));
               return 0;
            } else {
               village.setVillagerHired(level, v.getUUID(), null, 0L);
               source.sendSuccess(() -> Component.literal("Cleared hire on " + v.getName().getString()), false);
               return 1;
            }
         } else {
            source.sendFailure(Component.literal("No nearby villager (with a village) found."));
            return 0;
         }
      } else {
         source.sendFailure(Component.literal("Must be run by a player."));
         return 0;
      }
   }
}
