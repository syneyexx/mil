package org.millenaire.labyrinth.spawn;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
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
import org.millenaire.labyrinth.gen.LabyrinthMaze;
import org.millenaire.labyrinth.portal.LabyrinthTeleporter;

/**
 * Soft ambient population for the Labyrinth: hospitable COMMON/SOCIAL Gebondene near players,
 * plus rare Poortwachter near GATE rooms and The Architect near the Core.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthSpawnHandler {
   private static final int INTERVAL_TICKS = 100;
   private static final double SCAN_RADIUS = 40.0;
   private static final List<GebondeneKind> AMBIENT_KINDS = GebondeneKind.ofTiers(
      GebondeneKind.Tier.COMMON,
      GebondeneKind.Tier.SOCIAL
   );

   private LabyrinthSpawnHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % INTERVAL_TICKS != 0) {
         return;
      }
      if (!LabyrinthTeleporter.isLabyrinthEnabled(null)) {
         return;
      }

      ServerLevel labyrinth = LabyrinthTeleporter.getOrCreateLabyrinth(event.getServer());
      if (labyrinth == null) {
         return;
      }

      for (ServerPlayer player : labyrinth.players()) {
         if (!player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
            continue;
         }
         trySpawnNearPlayer(labyrinth, player);
         maybeSpawnGatekeeper(labyrinth, player);
         maybeSpawnArchitect(labyrinth, player);
      }
   }

   private static int nearbyCap() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthHostileCapPerPlayer.getAsInt();
      } catch (IllegalStateException ignored) {
         return 8;
      }
   }

   private static void trySpawnNearPlayer(ServerLevel level, ServerPlayer player) {
      AABB box = player.getBoundingBox().inflate(SCAN_RADIUS);
      int nearby = level.getEntitiesOfClass(GebondeneEntity.class, box).size();
      if (nearby >= nearbyCap()) {
         return;
      }
      if (AMBIENT_KINDS.isEmpty()) {
         return;
      }

      RandomSource random = level.getRandom();
      BlockPos spawnPos = findOpenCorridorNear(level, player.blockPosition(), random);
      if (spawnPos == null) {
         return;
      }

      GebondeneKind kind = AMBIENT_KINDS.get(random.nextInt(AMBIENT_KINDS.size()));
      spawnGebondene(level, spawnPos, kind, GebondeneAttitude.GASTVRIJ);
   }

   private static void maybeSpawnGatekeeper(ServerLevel level, ServerPlayer player) {
      RandomSource random = level.getRandom();
      if (random.nextInt(40) != 0) {
         return;
      }

      int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);

      // Search a small neighbourhood for a GATE cell.
      BlockPos gatePos = null;
      outer:
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            int lx = localX + dx;
            int lz = localZ + dz;
            if (maze.getRoomType(lx, lz) == LabyrinthMaze.RoomType.GATE) {
               int worldX = (regionX * LabyrinthKeys.REGION_SIZE + lx) * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
               int worldZ = (regionZ * LabyrinthKeys.REGION_SIZE + lz) * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
               gatePos = new BlockPos(worldX, LabyrinthKeys.FLOOR_Y + 1, worldZ);
               break outer;
            }
         }
      }
      if (gatePos == null || !isOpenCorridorCell(level, gatePos)) {
         return;
      }

      AABB gateBox = new AABB(gatePos).inflate(6.0);
      boolean already = level.getEntitiesOfClass(
         GebondeneEntity.class,
         gateBox,
         e -> e.getKind() == GebondeneKind.POORTWACHTER
      ).size() > 0;
      if (already) {
         return;
      }
      spawnGebondene(level, gatePos, GebondeneKind.POORTWACHTER, GebondeneAttitude.GASTVRIJ);
   }

   private static void maybeSpawnArchitect(ServerLevel level, ServerPlayer player) {
      int coreX = LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int coreZ = LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos core = new BlockPos(coreX, LabyrinthKeys.FLOOR_Y + 1, coreZ);
      if (player.blockPosition().distSqr(core) > 48.0 * 48.0) {
         return;
      }
      if (!level.isLoaded(core)) {
         return;
      }
      AABB coreBox = new AABB(core).inflate(24.0);
      boolean present = !level.getEntitiesOfClass(
         GebondeneEntity.class,
         coreBox,
         e -> e.getKind() == GebondeneKind.ARCHITECT
      ).isEmpty();
      if (present) {
         return;
      }
      BlockPos spawnAt = isOpenCorridorCell(level, core) ? core : findOpenCorridorNear(level, core, level.getRandom());
      if (spawnAt == null) {
         return;
      }
      spawnGebondene(level, spawnAt, GebondeneKind.ARCHITECT, GebondeneAttitude.GASTVRIJ);
   }

   private static void spawnGebondene(ServerLevel level, BlockPos pos, GebondeneKind kind, GebondeneAttitude attitude) {
      GebondeneEntity entity = ModEntities.GEBONDENE.get().create(level);
      if (entity == null) {
         return;
      }
      entity.setKind(kind);
      // If the nearest player already angered the Orde, spawn already hunting.
      GebondeneAttitude spawnAttitude = attitude;
      for (ServerPlayer nearbyPlayer : level.players()) {
         if (nearbyPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < 48.0 * 48.0
            && org.millenaire.labyrinth.entity.LabyrinthOrdeData.get(level).isHostile(nearbyPlayer.getUUID())) {
            spawnAttitude = GebondeneAttitude.JACHT;
            break;
         }
      }
      entity.setAttitude(spawnAttitude);
      entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
      level.addFreshEntity(entity);
   }

   private static BlockPos findOpenCorridorNear(ServerLevel level, BlockPos origin, RandomSource random) {
      for (int attempt = 0; attempt < 12; attempt++) {
         int dx = random.nextInt(17) - 8;
         int dz = random.nextInt(17) - 8;
         if (dx == 0 && dz == 0) {
            continue;
         }
         BlockPos candidate = new BlockPos(origin.getX() + dx, LabyrinthKeys.FLOOR_Y + 1, origin.getZ() + dz);
         if (isOpenCorridorCell(level, candidate)) {
            return candidate;
         }
      }
      return null;
   }

   private static boolean isOpenCorridorCell(ServerLevel level, BlockPos feet) {
      if (!level.isLoaded(feet)) {
         return false;
      }
      BlockState floor = level.getBlockState(feet.below());
      BlockState body = level.getBlockState(feet);
      BlockState head = level.getBlockState(feet.above());
      return floor.isSolidRender(level, feet.below())
         && body.isAir()
         && head.isAir();
   }
}
