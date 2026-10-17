package org.millenaire.labyrinth.portal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.combat.ConfiguratieCombat;
import org.millenaire.labyrinth.puzzle.GatePuzzle;
import org.millenaire.labyrinth.puzzle.RotaryPuzzle;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

/**
 * Configuratie — portal key and labyrinth world-tool.
 * <ul>
 *   <li>Outside Labyrinth: enter (SCHARNIER)</li>
 *   <li>Inside: sneak-use cycles vorm; use applies vorm (leave / rotate / gates / ketting)</li>
 * </ul>
 */
public class ConfiguratieItem extends Item {
   private static final int COOLDOWN_TICKS = 40;
   private static final int CYCLE_COOLDOWN = 10;
   private static final String FALLEN_KING_TAG = "fallenking_10_thebrokendoor";
   private static final String TAG_VORM = "configuratie_vorm";

   public ConfiguratieItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public Component getName(ItemStack stack) {
      ConfiguratieVorm vorm = getVorm(stack);
      return Component.translatable("item.millenaire.configuratie.vorm." + vorm.id());
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
      ConfiguratieVorm vorm = getVorm(stack);
      tooltip.add(Component.translatable("item.millenaire.configuratie.hint.cycle"));
      tooltip.add(Component.translatable("item.millenaire.configuratie.hint." + vorm.id()));
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

      boolean inside = level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL);

      if (inside && player.isShiftKeyDown()) {
         ConfiguratieVorm next = getVorm(stack).next();
         setVorm(stack, next);
         player.getCooldowns().addCooldown(this, CYCLE_COOLDOWN);
         serverPlayer.displayClientMessage(
            Component.translatable("labyrinth.configuratie.cycled", Component.translatable("item.millenaire.configuratie.vorm." + next.id())),
            true
         );
         return InteractionResultHolder.consume(stack);
      }

      if (!inside) {
         if (!mayEnterSurvival(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("labyrinth.teleport.need_fallenking"), true);
            return InteractionResultHolder.fail(stack);
         }
         boolean ok = LabyrinthTeleporter.enter(serverPlayer);
         if (ok) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            return InteractionResultHolder.consume(stack);
         }
         return InteractionResultHolder.fail(stack);
      }

      // Inside Labyrinth — dispatch by vorm.
      ConfiguratieVorm vorm = getVorm(stack);
      ServerLevel serverLevel = (ServerLevel) level;
      boolean handled = switch (vorm) {
         case SCHARNIER -> LabyrinthTeleporter.leave(serverPlayer);
         case WIEL -> RotaryPuzzle.tryRotate(serverPlayer, serverLevel);
         case SLEUTEL_A, SLEUTEL_B, SLEUTEL_C -> GatePuzzle.tryOpen(serverPlayer, serverLevel, vorm);
         case KETTING -> ConfiguratieCombat.tryPulse(serverPlayer, serverLevel);
      };

      if (handled) {
         player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
         return InteractionResultHolder.consume(stack);
      }
      serverPlayer.displayClientMessage(Component.translatable("labyrinth.configuratie.no_effect"), true);
      return InteractionResultHolder.fail(stack);
   }

   public static ConfiguratieVorm getVorm(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return ConfiguratieVorm.SCHARNIER;
      }
      CompoundTag tag = data.copyTag();
      return ConfiguratieVorm.byIdOrDefault(tag.getString(TAG_VORM));
   }

   public static void setVorm(ItemStack stack, ConfiguratieVorm vorm) {
      CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      CompoundTag tag = data.copyTag();
      tag.putString(TAG_VORM, vorm.id());
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
