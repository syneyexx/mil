package org.millenaire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.village.Village;
import org.millenaire.village.VillageIntegrityChecker;
import org.millenaire.village.VillageSavedData;

public final class ResetVillagersCommand {
   private ResetVillagersCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(Commands.literal("resetvillagers").executes(ResetVillagersCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerLevel level = src.getLevel();
      ServerPlayer player = src.getPlayer();
      BlockPos searchPos = player != null ? player.blockPosition() : BlockPos.containing(src.getPosition());
      Village village = VillageSavedData.get(level).getVillageManager().findNearestVillage(searchPos, 5000.0);
      if (village == null) {
         src.sendFailure(Component.translatable("command.millenaire.error.no_village_radius", new Object[]{5000}));
         return 0;
      } else {
         int respawned = VillageIntegrityChecker.forceRespawnMissing(level, village);
         village.markDirty();
         String name = village.getVillageName();
         src.sendSuccess(() -> Component.translatable("command.millenaire.resetvillagers.success", new Object[]{name, respawned}), true);
         return respawned;
      }
   }
}
