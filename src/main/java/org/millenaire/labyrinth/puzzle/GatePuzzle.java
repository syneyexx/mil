package org.millenaire.labyrinth.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ModItems;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;
import org.millenaire.labyrinth.portal.ConfiguratieVorm;

/**
 * Three relic gates in GATE rooms. Each needs the matching relic item nearby
 * (or in inventory) plus the matching Configuratie sleutel vorm.
 */
public final class GatePuzzle {
   public enum GateSlot {
      ZEGEL(ConfiguratieVorm.SLEUTEL_A, -2),
      MASKER(ConfiguratieVorm.SLEUTEL_B, 0),
      KOORD(ConfiguratieVorm.SLEUTEL_C, 2);

      final ConfiguratieVorm vorm;
      final int xOffset;

      GateSlot(ConfiguratieVorm vorm, int xOffset) {
         this.vorm = vorm;
         this.xOffset = xOffset;
      }
   }

   private static final BlockState AIR = Blocks.AIR.defaultBlockState();

   private GatePuzzle() {
   }

   public static boolean tryOpen(ServerPlayer player, ServerLevel level, ConfiguratieVorm vorm) {
      if (!puzzlesEnabled()) {
         return false;
      }
      GateSlot slot = slotForVorm(vorm);
      if (slot == null) {
         return false;
      }

      BlockPos feet = player.blockPosition();
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      if (maze.getRoomTypeGlobal(cell[0], cell[1]) != LabyrinthMaze.RoomType.GATE) {
         return false;
      }

      LabyrinthPuzzleData data = LabyrinthPuzzleData.get(level);
      if (data.isGateOpen(cell[0], cell[1], slot)) {
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.gate.already"), true);
         return true;
      }

      if (!playerHasOrOffersRelic(player, level, slot)) {
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.gate.need_relic", relicName(slot)), true);
         return true;
      }

      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int maxZ = cellMinZ + LabyrinthKeys.CELL_SIZE - 1;
      int gateX = centerX + slot.xOffset;

      applyOpenedGate(level, cell[0], cell[1], slot);

      consumeRelic(player, slot);
      data.openGate(cell[0], cell[1], slot);
      BlockPos fx = new BlockPos(gateX, LabyrinthKeys.FLOOR_Y + 2, maxZ - 1);
      level.playSound(null, fx, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
      level.sendParticles(ParticleTypes.END_ROD, fx.getX() + 0.5, fx.getY(), fx.getZ() + 0.5, 20, 0.3, 0.8, 0.3, 0.02);
      player.displayClientMessage(Component.translatable("labyrinth.puzzle.gate.opened", relicName(slot)), true);
      return true;
   }

   /** Clear bars for an already-opened gate (also used on chunk restore). */
   public static void applyOpenedGate(ServerLevel level, int cellX, int cellZ, GateSlot slot) {
      int cellMinX = cellX * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cellZ * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int maxZ = cellMinZ + LabyrinthKeys.CELL_SIZE - 1;
      int gateX = centerX + slot.xOffset;
      int gateZ = maxZ - 1;
      for (int y = LabyrinthKeys.FLOOR_Y + 1; y <= LabyrinthKeys.FLOOR_Y + 3; y++) {
         BlockPos pos = new BlockPos(gateX, y, gateZ);
         if (level.isLoaded(pos)) {
            level.setBlock(pos, AIR, 3);
         }
      }
   }

   private static GateSlot slotForVorm(ConfiguratieVorm vorm) {
      for (GateSlot slot : GateSlot.values()) {
         if (slot.vorm == vorm) {
            return slot;
         }
      }
      return null;
   }

   private static boolean playerHasOrOffersRelic(ServerPlayer player, ServerLevel level, GateSlot slot) {
      ItemStack want = relicStack(slot);
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         if (player.getInventory().getItem(i).is(want.getItem())) {
            return true;
         }
      }
      // Also accept relic dropped on the matching pedestal.
      int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos pedestal = new BlockPos(centerX + slot.xOffset, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1);
      AABB box = new AABB(pedestal).inflate(0.75);
      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
         if (entity.getItem().is(want.getItem())) {
            return true;
         }
      }
      return false;
   }

   private static void consumeRelic(ServerPlayer player, GateSlot slot) {
      ItemStack want = relicStack(slot);
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(want.getItem())) {
            stack.shrink(1);
            return;
         }
      }
      // Consume pedestal offering if inventory lacked the relic.
      int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos pedestal = new BlockPos(centerX + slot.xOffset, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1);
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pedestal).inflate(0.75))) {
         if (entity.getItem().is(want.getItem())) {
            entity.getItem().shrink(1);
            if (entity.getItem().isEmpty()) {
               entity.discard();
            }
            return;
         }
      }
   }

   private static ItemStack relicStack(GateSlot slot) {
      return switch (slot) {
         case ZEGEL -> new ItemStack(ModItems.LABYRINTH_ZEGEL.get());
         case MASKER -> new ItemStack(ModItems.LABYRINTH_MASKER.get());
         case KOORD -> new ItemStack(ModItems.LABYRINTH_KOORD.get());
      };
   }

   private static String relicName(GateSlot slot) {
      return switch (slot) {
         case ZEGEL -> "Zegel";
         case MASKER -> "Masker";
         case KOORD -> "Koord";
      };
   }

   private static boolean puzzlesEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthPuzzlesEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
