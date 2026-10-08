package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

public final class RaidCommand {
   private static final int SEARCH_RADIUS = 256;

   private RaidCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("raid")
            .then(
               Commands.literal("trigger")
                  .then(
                     Commands.argument("attacker_pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("target_pos", BlockPosArgument.blockPos()).executes(RaidCommand::executeTrigger))
                  )
            )
      );
   }

   static int executeTrigger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Raid trigger only works in the overworld."));
         return 0;
      } else {
         BlockPos attackerPos = BlockPosArgument.getLoadedBlockPos(ctx, "attacker_pos");
         BlockPos targetPos = BlockPosArgument.getLoadedBlockPos(ctx, "target_pos");
         VillageSavedData savedData = VillageSavedData.get(level);
         VillageManager villageManager = savedData.getVillageManager();
         Village attacker = villageManager.findNearestVillage(attackerPos, 256.0);
         if (attacker == null) {
            source.sendFailure(Component.literal("No village found within 256 blocks of attacker pos " + attackerPos.toShortString() + "."));
            return 0;
         } else {
            Village target = villageManager.findNearestVillage(targetPos, 256.0);
            if (target == null) {
               source.sendFailure(Component.literal("No village found within 256 blocks of target pos " + targetPos.toShortString() + "."));
               return 0;
            } else if (attacker.getId().equals(target.getId())) {
               source.sendFailure(Component.literal("Attacker and target resolved to the same village (" + nameOf(attacker) + ")."));
               return 0;
            } else {
               int raiderCount = countAvailableRaiders(attacker);
               if (raiderCount == 0) {
                  source.sendFailure(Component.literal("No raiders in attacker village (" + nameOf(attacker) + ")."));
                  return 0;
               } else {
                  RaidManager.planRaid(attacker, target, level);
                  RaidManager.startRaidForced(attacker, target, level);
                  String msg = "Raid triggered: " + nameOf(attacker) + " -> " + nameOf(target) + " (" + raiderCount + " raiders dispatched)";
                  source.sendSuccess(() -> Component.literal(msg), true);
                  return 1;
               }
            }
         }
      }
   }

   private static int countAvailableRaiders(Village attacker) {
      int count = 0;

      for (VillagerRecord r : attacker.getVillagerRecords().values()) {
         if (!r.isKilled() && !r.isRaidingVillage() && !r.isAwayRaiding()) {
            VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
            if (type != null && type.isRaider()) {
               count++;
            }
         }
      }

      return count;
   }

   private static String nameOf(Village v) {
      String name = v.getVillageName();
      return name != null && !name.isBlank() ? name : v.getVillageTypeId().getPath();
   }
}
