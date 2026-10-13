package org.millenaire.item;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.LockedChestBlock;
import org.millenaire.block.LockedChestBlockEntity;
import org.millenaire.crime.CrimeConstants;
import org.millenaire.crime.CrimeService;
import org.millenaire.crime.PlayerBountySavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

public class LockpickItem extends Item {
   public LockpickItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext ctx) {
      Level level = ctx.getLevel();
      BlockPos pos = ctx.getClickedPos();
      Player player = ctx.getPlayer();
      ItemStack stack = ctx.getItemInHand();
      if (player == null) {
         return InteractionResult.PASS;
      }

      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof LockedChestBlock)) {
         return InteractionResult.PASS;
      }

      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (!(level.getBlockEntity(pos) instanceof LockedChestBlockEntity chest)) {
         return InteractionResult.PASS;
      }

      if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
         return InteractionResult.PASS;
      }

      if (player.isSpectator()) {
         return InteractionResult.PASS;
      }

      if (!PlayerBountySavedData.crimeEnabled()) {
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_crime_disabled"), true);
         return InteractionResult.FAIL;
      }

      if (player.isCreative()) {
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_creative"), true);
         return InteractionResult.FAIL;
      }

      if (!chest.isNaturallyLockedFor(player)) {
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_not_needed"), true);
         return InteractionResult.FAIL;
      }

      Village village = null;
      if (chest.getBuildingId() != null) {
         village = VillageSavedData.get(serverLevel).getVillageManager().findVillageContaining(chest.getBuildingId());
      }

      if (village != null && village.isControlledBy(player.getUUID())) {
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_not_needed"), true);
         return InteractionResult.FAIL;
      }

      if (chest.isPickedOpenFor(player, serverLevel.getGameTime()) || !chest.isLockedFor(player)) {
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_already"), true);
         return LockedChestBlock.openForPlayer(state, level, pos, player);
      }

      boolean success = ThreadLocalRandom.current().nextFloat() < CrimeConstants.LOCKPICK_SUCCESS_CHANCE;
      stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
      if (success) {
         chest.setPickedOpen(player.getUUID(), serverLevel.getGameTime() + CrimeConstants.LOCKPICK_DURATION_TICKS);
         serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.4F);
         player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_success"), true);
         return LockedChestBlock.openForPlayer(state, level, pos, player);
      }

      serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 0.6F);
      player.displayClientMessage(Component.translatable("message.millenaire.crime.lockpick_fail"), true);
      CrimeService.onLockpickFailed(serverPlayer, village, village != null ? village.getCultureId() : null, pos);
      return InteractionResult.SUCCESS;
   }
}
