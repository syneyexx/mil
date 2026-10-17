package org.millenaire.labyrinth.puzzle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Re-applies SavedData puzzle mutations after chunk load so rotary walkways
 * and opened relic gates survive unload/reload.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthPuzzleRestore {
   private LabyrinthPuzzleRestore() {
   }

   @SubscribeEvent
   public static void onChunkLoad(ChunkEvent.Load event) {
      if (!(event.getLevel() instanceof ServerLevel level)) {
         return;
      }
      if (!level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         return;
      }
      if (!puzzlesEnabled()) {
         return;
      }
      if (!(event.getChunk() instanceof LevelChunk chunk)) {
         return;
      }

      ChunkPos pos = chunk.getPos();
      int minX = pos.getMinBlockX();
      int minZ = pos.getMinBlockZ();
      int maxX = pos.getMaxBlockX();
      int maxZ = pos.getMaxBlockZ();

      int cellMinX = Math.floorDiv(minX, LabyrinthKeys.CELL_SIZE);
      int cellMinZ = Math.floorDiv(minZ, LabyrinthKeys.CELL_SIZE);
      int cellMaxX = Math.floorDiv(maxX, LabyrinthKeys.CELL_SIZE);
      int cellMaxZ = Math.floorDiv(maxZ, LabyrinthKeys.CELL_SIZE);

      LabyrinthPuzzleData data = LabyrinthPuzzleData.get(level);
      long seed = level.getSeed();

      for (int cx = cellMinX; cx <= cellMaxX; cx++) {
         for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
            int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cx);
            int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cz);
            LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
            LabyrinthMaze.RoomType type = maze.getRoomTypeGlobal(cx, cz);
            if (type == LabyrinthMaze.RoomType.ROTARY) {
               int rotation = data.getRotary(cx, cz);
               if (rotation != 0) {
                  RotaryPuzzle.applySavedRotation(level, cx, cz, rotation);
               }
            } else if (type == LabyrinthMaze.RoomType.GATE) {
               for (GatePuzzle.GateSlot slot : GatePuzzle.GateSlot.values()) {
                  if (data.isGateOpen(cx, cz, slot)) {
                     GatePuzzle.applyOpenedGate(level, cx, cz, slot);
                  }
               }
            }
         }
      }
   }

   private static boolean puzzlesEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthPuzzlesEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
