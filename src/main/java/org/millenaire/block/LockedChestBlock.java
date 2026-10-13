package org.millenaire.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import org.millenaire.commerce.LockedChestMenu;
import org.millenaire.item.LockpickItem;
import org.millenaire.item.SummoningWandItem;

public class LockedChestBlock extends ChestBlock {
   public static final MapCodec<LockedChestBlock> CODEC = simpleCodec(LockedChestBlock::new);

   public LockedChestBlock(Properties properties) {
      super(properties, () -> (BlockEntityType)ModBlockEntities.LOCKED_CHEST.get());
   }

   public MapCodec<? extends ChestBlock> codec() {
      return CODEC;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new LockedChestBlockEntity(pos, state);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return createTickerHelper(type, (BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), ChestBlockEntity::lidAnimateTick);
   }

   public BlockEntityType<? extends ChestBlockEntity> blockEntityType() {
      return (BlockEntityType<? extends ChestBlockEntity>)ModBlockEntities.LOCKED_CHEST.get();
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      return this.handleInteraction(state, level, pos, player, hitResult);
   }

   protected ItemInteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      // Lockpick and summoning wand must own the click — do not open the locked menu first.
      return stack.getItem() instanceof SummoningWandItem || stack.getItem() instanceof LockpickItem
         ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
         : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
   }

   private InteractionResult handleInteraction(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      return openForPlayer(state, level, pos, player);
   }

   /** Opens the locked-chest menu; used by empty-hand use and after a successful lockpick. */
   public static InteractionResult openForPlayer(BlockState state, Level level, BlockPos pos, Player player) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (!(state.getBlock() instanceof LockedChestBlock block)) {
         return InteractionResult.PASS;
      }

      if (level.getBlockEntity(pos) instanceof LockedChestBlockEntity chest) {
         boolean locked = chest.isLockedFor(player);

         Container container = ChestBlock.getContainer(block, state, level, pos, true);
         if (container == null) {
            return InteractionResult.PASS;
         }

         if (locked) {
            player.displayClientMessage(Component.translatable("block.millenaire.locked_chest.use_lockpick"), true);
         }

         Component title = chest.getDisplayName();
         boolean isLocked = locked;
         var buildingId = chest.getBuildingId();
         BlockPos chestPos = pos;
         if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
               new SimpleMenuProvider(
                  (containerId, playerInventory, p) -> new LockedChestMenu(containerId, playerInventory, container, isLocked, buildingId, chestPos),
                  title
               ),
               buf -> {
                  buf.writeByte(container.getContainerSize() / 9);
                  buf.writeBoolean(isLocked);
               }
            );
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      if (!state.is(newState.getBlock())) {
         level.updateNeighbourForOutputSignal(pos, this);
         level.removeBlockEntity(pos);
      }
   }
}
