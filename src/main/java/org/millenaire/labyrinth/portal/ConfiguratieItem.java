package org.millenaire.labyrinth.portal;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

/**
 * Configuratie — opens or closes the Labyrinth hinge for the holder.
 * Stacks to 1; 60-tick cooldown after use.
 */
public class ConfiguratieItem extends Item {
   private static final int COOLDOWN_TICKS = 60;
   private static final String FALLEN_KING_TAG = "fallenking_10_thebrokendoor";

   public ConfiguratieItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      }
      if (!(player instanceof ServerPlayer serverPlayer)) {
         return InteractionResultHolder.pass(stack);
      }
      if (!LabyrinthTeleporter.isLabyrinthEnabled(player)) {
         serverPlayer.displayClientMessage(Component.translatable("labyrinth.teleport.disabled"), true);
         return InteractionResultHolder.fail(stack);
      }

      boolean ok;
      if (level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         ok = LabyrinthTeleporter.leave(serverPlayer);
      } else {
         if (!mayEnterSurvival(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("labyrinth.teleport.need_fallenking"), true);
            return InteractionResultHolder.fail(stack);
         }
         ok = LabyrinthTeleporter.enter(serverPlayer);
      }

      if (ok) {
         player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
         return InteractionResultHolder.consume(stack);
      }
      return InteractionResultHolder.fail(stack);
   }

   private static boolean mayEnterSurvival(ServerPlayer player) {
      if (player.getAbilities().instabuild && MillenaireServerConfig.SERVER.labyrinthAllowCreativeEntry.getAsBoolean()) {
         return true;
      }
      if (!MillenaireServerConfig.SERVER.labyrinthRequireFallenKingTag.getAsBoolean()) {
         return true;
      }
      ServerLevel overworld = player.server.overworld();
      if (overworld == null) {
         return false;
      }
      PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);
      return questData.getPlayerTags(player.getUUID()).contains(FALLEN_KING_TAG);
   }
}
