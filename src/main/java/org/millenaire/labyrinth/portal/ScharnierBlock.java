package org.millenaire.labyrinth.portal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Scharnier (hinge) block — interacting teleports the player out of the Labyrinth.
 * Suitable for later {@code DeferredBlock} registration with {@link #defaultProperties()}.
 */
public class ScharnierBlock extends Block {
   public static final MapCodec<ScharnierBlock> CODEC = simpleCodec(ScharnierBlock::new);

   public ScharnierBlock(Properties properties) {
      super(properties);
   }

   public ScharnierBlock() {
      this(defaultProperties());
   }

   public static Properties defaultProperties() {
      return BlockBehaviour.Properties.of()
         .mapColor(MapColor.COLOR_BLACK)
         .strength(50.0F, 1200.0F)
         .sound(SoundType.METAL)
         .requiresCorrectToolForDrops();
   }

   @Override
   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      return tryLeave(level, player);
   }

   @Override
   protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hitResult
   ) {
      InteractionResult leave = tryLeave(level, player);
      if (leave.consumesAction()) {
         return ItemInteractionResult.SUCCESS;
      }
      if (leave == InteractionResult.FAIL) {
         return ItemInteractionResult.FAIL;
      }
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
   }

   private static InteractionResult tryLeave(Level level, Player player) {
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (player instanceof ServerPlayer serverPlayer) {
         boolean ok = LabyrinthTeleporter.leave(serverPlayer);
         return ok ? InteractionResult.CONSUME : InteractionResult.FAIL;
      }
      return InteractionResult.PASS;
   }
}
