package org.millenaire.labyrinth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

/**
 * Grants quest tag when a player reaches the Core chamber.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthProgressHandler {
   private static final String CORE_TAG = "explored_labyrinth_core";

   private LabyrinthProgressHandler() {
   }

   @SubscribeEvent
   public static void onTick(ServerTickEvent.Post event) {
      if (event.getServer().overworld().getGameTime() % 40 != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }
      int coreX = LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int coreZ = LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos core = new BlockPos(coreX, LabyrinthKeys.FLOOR_Y + 1, coreZ);
      ServerLevel overworld = event.getServer().overworld();
      PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);

      for (ServerPlayer player : labyrinth.players()) {
         if (player.blockPosition().distSqr(core) > 12.0 * 12.0) {
            continue;
         }
         if (questData.getPlayerTags(player.getUUID()).contains(CORE_TAG)) {
            continue;
         }
         questData.setPlayerTag(player.getUUID(), CORE_TAG);
      }
   }
}
