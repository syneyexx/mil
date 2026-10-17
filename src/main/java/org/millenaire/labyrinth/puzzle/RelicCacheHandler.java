package org.millenaire.labyrinth.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ModItems;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Spawns Labyrinth relics once per themed hub so gates require exploration.
 * FORGE→Zegel, ARCHIVE→Masker, CHAPEL→Koord.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class RelicCacheHandler {
   private RelicCacheHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (!puzzlesEnabled()) {
         return;
      }
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % 40 != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }
      LabyrinthPuzzleData data = LabyrinthPuzzleData.get(labyrinth);
      for (ServerPlayer player : labyrinth.players()) {
         maybeSpawn(labyrinth, player, data);
      }
   }

   private static void maybeSpawn(ServerLevel level, ServerPlayer player, LabyrinthPuzzleData data) {
      BlockPos feet = player.blockPosition();
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      LabyrinthMaze.RoomType type = maze.getRoomTypeGlobal(cell[0], cell[1]);
      ItemStack relic = switch (type) {
         case FORGE -> new ItemStack(ModItems.LABYRINTH_ZEGEL.get());
         case ARCHIVE -> new ItemStack(ModItems.LABYRINTH_MASKER.get());
         case CHAPEL -> new ItemStack(ModItems.LABYRINTH_KOORD.get());
         default -> null;
      };
      if (relic == null) {
         return;
      }
      if (data.hasRelicSpawned(cell[0], cell[1])) {
         return;
      }
      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;
      double x = centerX + 0.5;
      double y = LabyrinthKeys.FLOOR_Y + 1.2;
      double z = centerZ + 2.5;
      ItemEntity entity = new ItemEntity(level, x, y, z, relic);
      entity.setUnlimitedLifetime();
      entity.setPickUpDelay(20);
      level.addFreshEntity(entity);
      data.markRelicSpawned(cell[0], cell[1]);
   }

   private static boolean puzzlesEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthPuzzlesEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
