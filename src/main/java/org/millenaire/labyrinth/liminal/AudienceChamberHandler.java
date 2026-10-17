package org.millenaire.labyrinth.liminal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.LabyrinthOrdeData;
import org.millenaire.labyrinth.gen.LabyrinthMaze;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;

/**
 * First audience with De Ruit: quest tag, Orde notice, cinematic whisper.
 * Millénaire analogue of "audience with god" — no Hellraiser IP.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class AudienceChamberHandler {
   public static final String AUDIENCE_TAG = "labyrinth_audience_ruit";
   private static final Set<UUID> SESSION_GREETED = new HashSet<>();

   private AudienceChamberHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % 20 != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }

      int audX = LabyrinthKeys.audienceCenterBlockX();
      int audZ = LabyrinthKeys.audienceCenterBlockZ();
      BlockPos heart = new BlockPos(audX, LabyrinthKeys.FLOOR_Y + 1, audZ);
      PlayerQuestData questData = PlayerQuestData.get(overworld, QuestRegistry::get);

      for (ServerPlayer player : labyrinth.players()) {
         if (player.blockPosition().distSqr(heart) > 10.0 * 10.0) {
            SESSION_GREETED.remove(player.getUUID());
            continue;
         }

         // Confirm cell type (safety if keys drift).
         int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
         int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
         int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
         LabyrinthMaze maze = LabyrinthMaze.forRegion(labyrinth.getSeed(), regionX, regionZ);
         if (maze.getResolvedRoomType(
               LabyrinthMaze.localCellFromGlobal(cell[0]),
               LabyrinthMaze.localCellFromGlobal(cell[1])
            ) != LabyrinthMaze.RoomType.AUDIENCE
            && !LabyrinthKeys.inAudienceFootprint(cell[0], cell[1])) {
            continue;
         }

         boolean first = !questData.getPlayerTags(player.getUUID()).contains(AUDIENCE_TAG);
         if (first) {
            questData.setPlayerTag(player.getUUID(), AUDIENCE_TAG);
            LabyrinthOrdeData orde = LabyrinthOrdeData.get(labyrinth);
            orde.addReputation(player.getUUID(), regionX, regionZ, 15);
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            player.displayClientMessage(Component.translatable("labyrinth.audience.first"), false);
            player.displayClientMessage(Component.translatable("labyrinth.audience.first_sub"), true);
            labyrinth.playSound(null, heart, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.2F, 0.5F);
            labyrinth.sendParticles(
               ParticleTypes.END_ROD,
               audX + 0.5,
               LabyrinthKeys.FLOOR_Y + 7.0,
               audZ + 0.5,
               40,
               0.8,
               1.2,
               0.8,
               0.02
            );
            SESSION_GREETED.add(player.getUUID());
         } else if (SESSION_GREETED.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("labyrinth.audience.return"), true);
            labyrinth.playSound(null, heart, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.6F, 0.7F);
         }

         // Soft particle breath from De Ruit while present.
         if (labyrinth.getGameTime() % 40 == 0) {
            labyrinth.sendParticles(
               ParticleTypes.SOUL,
               audX + 0.5,
               LabyrinthKeys.FLOOR_Y + 7.0,
               audZ + 0.5,
               8,
               0.5,
               0.8,
               0.5,
               0.01
            );
         }
      }
   }
}
