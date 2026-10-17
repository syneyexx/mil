package org.millenaire.labyrinth.spawn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Natural Labyrinth population: every {@link GebondeneKind} can appear.
 * Common/social fill corridors; gate/rare/leader are rarer but never excluded.
 * Poortwachter prefers GATE rooms; Architect prefers the Core.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthSpawnHandler {
   private static final int INTERVAL_TICKS = 80;
   private static final double SCAN_RADIUS = 48.0;

   /** Relative spawn weights so every kind can roll, rarer tiers less often. */
   private static final Map<GebondeneKind.Tier, Integer> TIER_WEIGHT = tierWeights();

   private LabyrinthSpawnHandler() {
   }

   private static Map<GebondeneKind.Tier, Integer> tierWeights() {
      Map<GebondeneKind.Tier, Integer> map = new EnumMap<>(GebondeneKind.Tier.class);
      map.put(GebondeneKind.Tier.COMMON, 10);
      map.put(GebondeneKind.Tier.SOCIAL, 8);
      map.put(GebondeneKind.Tier.RARE, 4);
      map.put(GebondeneKind.Tier.GATE, 3);
      map.put(GebondeneKind.Tier.LEADER, 2);
      return Map.copyOf(map);
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

      RandomSource random = level.getRandom();
      // Attempt up to two ambient spawns per interval so rarer kinds still appear over time.
      int attempts = 1 + (nearby < nearbyCap() / 2 ? 1 : 0);
      for (int i = 0; i < attempts; i++) {
         if (level.getEntitiesOfClass(GebondeneEntity.class, box).size() >= nearbyCap()) {
            break;
         }
         BlockPos spawnPos = findOpenCorridorNear(level, player.blockPosition(), random);
         if (spawnPos == null) {
            continue;
         }
         GebondeneKind kind = pickWeightedKind(random, spawnPos, level);
         if (kind == null) {
            continue;
         }
         // Soft uniqueness: skip if that exact kind already saturates the scan.
         long sameKind = level.getEntitiesOfClass(
            GebondeneEntity.class,
            box,
            e -> e.getKind() == kind
         ).size();
         int kindCap = kindCap(kind);
         if (sameKind >= kindCap) {
            continue;
         }
         spawnGebondene(level, spawnPos, kind, GebondeneAttitude.GASTVRIJ);
      }
   }

   private static int kindCap(GebondeneKind kind) {
      return switch (kind.tier()) {
         case COMMON -> 3;
         case SOCIAL -> 2;
         case RARE, GATE -> 1;
         case LEADER -> 1;
      };
   }

   private static GebondeneKind pickWeightedKind(RandomSource random, BlockPos pos, ServerLevel level) {
      List<GebondeneKind> pool = new ArrayList<>();
      List<Integer> weights = new ArrayList<>();
      int total = 0;

      int[] cell = LabyrinthMaze.globalCellFromBlock(pos.getX(), pos.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);
      LabyrinthMaze.RoomType room = maze.getRoomType(localX, localZ);
      boolean nearCore = pos.distSqr(
         new BlockPos(
            LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2,
            LabyrinthKeys.FLOOR_Y + 1,
            LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2
         )
      ) < 40.0 * 40.0;

      for (GebondeneKind kind : GebondeneKind.values()) {
         int weight = TIER_WEIGHT.getOrDefault(kind.tier(), 1);
         if (kind == GebondeneKind.POORTWACHTER && room == LabyrinthMaze.RoomType.GATE) {
            weight *= 4;
         }
         if (kind == GebondeneKind.ARCHITECT && nearCore) {
            weight *= 3;
         }
         if (kind == GebondeneKind.SPIJKERMEESTER && nearCore) {
            weight *= 2;
         }
         if (kind.tier() == GebondeneKind.Tier.GATE && room == LabyrinthMaze.RoomType.GATE) {
            weight *= 2;
         }
         pool.add(kind);
         weights.add(weight);
         total += weight;
      }
      if (total <= 0 || pool.isEmpty()) {
         return null;
      }
      int roll = random.nextInt(total);
      for (int i = 0; i < pool.size(); i++) {
         roll -= weights.get(i);
         if (roll < 0) {
            return pool.get(i);
         }
      }
      return pool.get(pool.size() - 1);
   }

   private static void maybeSpawnGatekeeper(ServerLevel level, ServerPlayer player) {
      RandomSource random = level.getRandom();
      if (random.nextInt(24) != 0) {
         return;
      }

      int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);

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

      AABB gateBox = new AABB(gatePos).inflate(8.0);
      // Prefer a GATE-tier inhabitant at gates (not only Poortwachter).
      List<GebondeneKind> gateKinds = GebondeneKind.ofTiers(GebondeneKind.Tier.GATE);
      boolean alreadyGate = !level.getEntitiesOfClass(
         GebondeneEntity.class,
         gateBox,
         e -> e.getKind().tier() == GebondeneKind.Tier.GATE
      ).isEmpty();
      if (alreadyGate || gateKinds.isEmpty()) {
         return;
      }
      GebondeneKind kind = gateKinds.get(random.nextInt(gateKinds.size()));
      spawnGebondene(level, gatePos, kind, GebondeneAttitude.GASTVRIJ);
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
      // Bring a lieutenant so Spijkermeester/Klokkenluider are not Core-orphans.
      RandomSource random = level.getRandom();
      if (random.nextBoolean()) {
         BlockPos lieutenant = findOpenCorridorNear(level, spawnAt, random);
         if (lieutenant != null) {
            GebondeneKind escort = random.nextBoolean() ? GebondeneKind.SPIJKERMEESTER : GebondeneKind.KLOKKENLUIDER;
            long same = level.getEntitiesOfClass(
               GebondeneEntity.class,
               coreBox,
               e -> e.getKind() == escort
            ).size();
            if (same == 0) {
               spawnGebondene(level, lieutenant, escort, GebondeneAttitude.GASTVRIJ);
            }
         }
      }
   }

   private static void spawnGebondene(ServerLevel level, BlockPos pos, GebondeneKind kind, GebondeneAttitude attitude) {
      GebondeneEntity entity = ModEntities.GEBONDENE.get().create(level);
      if (entity == null) {
         return;
      }
      entity.setKind(kind);
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
      for (int attempt = 0; attempt < 16; attempt++) {
         int dx = random.nextInt(21) - 10;
         int dz = random.nextInt(21) - 10;
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
