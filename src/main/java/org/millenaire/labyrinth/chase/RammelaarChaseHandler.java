package org.millenaire.labyrinth.chase;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.ModEntities;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;
import org.millenaire.labyrinth.entity.LabyrinthOrdeData;
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Corridor chase: when the Orde is alarmed/hostile, or the player is in/near a GATE room,
 * a Rammelaar blinks into the corridor ahead — except along a seed-stable "safe" side branch.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class RammelaarChaseHandler {
   private static final int INTERVAL = 45;
   private static final double SCAN = 28.0;

   private RammelaarChaseHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (!chaseEnabled()) {
         return;
      }
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % INTERVAL != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }
      LabyrinthOrdeData orde = LabyrinthOrdeData.get(labyrinth);
      for (ServerPlayer player : labyrinth.players()) {
         tickChase(labyrinth, player, orde);
      }
   }

   private static void tickChase(ServerLevel level, ServerPlayer player, LabyrinthOrdeData orde) {
      BlockPos feet = player.blockPosition();
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      LabyrinthMaze.RoomType room = maze.getRoomTypeGlobal(cell[0], cell[1]);

      boolean nearGate = room == LabyrinthMaze.RoomType.GATE || room == LabyrinthMaze.RoomType.ROTARY;
      boolean alarmed = orde.isHostile(player.getUUID());
      if (!nearGate && !alarmed) {
         // Soft escalate cue only if a nearby Gebondene is already GEALARMEERD/JACHT.
         AABB box = player.getBoundingBox().inflate(16.0);
         boolean anyAlarm = !level.getEntitiesOfClass(
            GebondeneEntity.class,
            box,
            e -> e.getAttitude() != GebondeneAttitude.GASTVRIJ
         ).isEmpty();
         if (!anyAlarm) {
            return;
         }
      }

      int safeFlag = safeWallFlag(level.getSeed(), cell[0], cell[1]);
      float yaw = player.getYRot();
      int aheadFlag = yawToWall(yaw);
      if (aheadFlag == safeFlag) {
         // Safe branch — no blink; soft particle hint.
         if (level.getRandom().nextInt(4) == 0) {
            level.sendParticles(
               ParticleTypes.SMOKE,
               player.getX(),
               player.getY() + 1.0,
               player.getZ(),
               4,
               0.2,
               0.2,
               0.2,
               0.01
            );
         }
         return;
      }

      BlockPos blink = aheadPos(feet, aheadFlag, 4);
      if (!level.isLoaded(blink)) {
         return;
      }

      List<GebondeneEntity> existing = level.getEntitiesOfClass(
         GebondeneEntity.class,
         player.getBoundingBox().inflate(SCAN),
         e -> e.getKind() == GebondeneKind.RAMMELAAR && e.getAttitude() == GebondeneAttitude.JACHT
      );
      GebondeneEntity hunter;
      if (existing.isEmpty()) {
         hunter = ModEntities.GEBONDENE.get().create(level);
         if (hunter == null) {
            return;
         }
         hunter.setKind(GebondeneKind.RAMMELAAR);
         hunter.setAttitude(GebondeneAttitude.JACHT);
         hunter.moveTo(blink.getX() + 0.5, LabyrinthKeys.FLOOR_Y + 1.0, blink.getZ() + 0.5, yaw + 180.0F, 0.0F);
         level.addFreshEntity(hunter);
      } else {
         hunter = existing.get(0);
         hunter.teleportTo(blink.getX() + 0.5, LabyrinthKeys.FLOOR_Y + 1.0, blink.getZ() + 0.5);
      }
      hunter.setTarget(player);
      hunter.setAttitude(GebondeneAttitude.JACHT);
      level.playSound(null, blink, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.7F, 0.7F);
      level.sendParticles(ParticleTypes.SOUL, blink.getX() + 0.5, blink.getY() + 1.0, blink.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
   }

   /**
    * Deterministic safe exit wall for this cell — player can learn it after failed chases.
    */
   static int safeWallFlag(long seed, int cellX, int cellZ) {
      int hash = Long.hashCode(seed ^ (((long) cellX) * 341873128712L) ^ (((long) cellZ) * 132897987541L));
      return switch (Math.floorMod(hash, 4)) {
         case 0 -> LabyrinthMaze.WALL_N;
         case 1 -> LabyrinthMaze.WALL_E;
         case 2 -> LabyrinthMaze.WALL_S;
         default -> LabyrinthMaze.WALL_W;
      };
   }

   private static int yawToWall(float yaw) {
      float normalized = Mth.wrapDegrees(yaw);
      if (normalized >= -45 && normalized < 45) {
         return LabyrinthMaze.WALL_S;
      }
      if (normalized >= 45 && normalized < 135) {
         return LabyrinthMaze.WALL_W;
      }
      if (normalized >= -135 && normalized < -45) {
         return LabyrinthMaze.WALL_E;
      }
      return LabyrinthMaze.WALL_N;
   }

   private static BlockPos aheadPos(BlockPos from, int wallFlag, int dist) {
      return switch (wallFlag) {
         case LabyrinthMaze.WALL_N -> from.north(dist);
         case LabyrinthMaze.WALL_E -> from.east(dist);
         case LabyrinthMaze.WALL_S -> from.south(dist);
         default -> from.west(dist);
      };
   }

   private static boolean chaseEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthChaseEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
