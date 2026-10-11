package org.millenaire.worldboss.spawn;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.StructureAvoidance;

/**
 * Spawns a large aerial boss near a player without long force-loads or destructive landing.
 */
public final class AerialNearPlayerSpawnStrategy implements WorldBossSpawnStrategy {
   private final double clearanceRadius;
   private final double clearanceHeight;

   public AerialNearPlayerSpawnStrategy(double clearanceRadius, double clearanceHeight) {
      this.clearanceRadius = clearanceRadius;
      this.clearanceHeight = clearanceHeight;
   }

   @Override
   public Optional<BlockPos> findSpawn(ServerLevel level, ServerPlayer anchor, RandomSource random) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      int minDist = cfg.worldBossSpawnMinDistance.getAsInt();
      int maxDist = cfg.worldBossSpawnMaxDistance.getAsInt();
      if (maxDist < minDist) {
         int tmp = minDist;
         minDist = maxDist;
         maxDist = tmp;
      }
      int hover = cfg.worldBossHoverHeight.getAsInt();
      // One candidate per SELECTING pulse: amortize structure/clearance cost across ticks.
      int attempts = 1;
      BlockPos playerPos = anchor.blockPosition();

      for (int i = 0; i < attempts; i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist + 1));
         int x = playerPos.getX() + Mth.floor(Math.cos(angle) * dist);
         int z = playerPos.getZ() + Mth.floor(Math.sin(angle) * dist);
         if (!level.getWorldBorder().isWithinBounds(x, z)) {
            continue;
         }
         if (!level.hasChunk(x >> 4, z >> 4)) {
            continue;
         }
         int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         int y = groundY + hover;
         int maxY = level.getMaxBuildHeight() - 8;
         if (y > maxY) {
            y = maxY;
         }
         if (y - groundY < 6) {
            continue;
         }
         BlockPos candidate = new BlockPos(x, y, z);
         if (!hasClearance(level, candidate)) {
            continue;
         }
         if (isNearVillageOrStructure(level, candidate)) {
            continue;
         }
         return Optional.of(candidate);
      }
      return Optional.empty();
   }

   private boolean hasClearance(ServerLevel level, BlockPos center) {
      AABB box = new AABB(
         center.getX() - this.clearanceRadius,
         center.getY() - 2.0,
         center.getZ() - this.clearanceRadius,
         center.getX() + this.clearanceRadius,
         center.getY() + this.clearanceHeight,
         center.getZ() + this.clearanceRadius
      );
      return level.noCollision(box);
   }

   private static boolean isNearVillageOrStructure(ServerLevel level, BlockPos pos) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      int margin = cfg.worldBossVillageAvoidRadius.getAsInt();
      Village nearest = VillageSavedData.get(level).getVillageManager().findNearestVillage(pos, margin);
      if (nearest != null) {
         int radius = Math.max(48, margin);
         if (nearest.getCenter().distSqr(pos) <= (long) radius * radius) {
            return true;
         }
      }
      return StructureAvoidance.hasConflictSparse(level, pos, margin);
   }
}
