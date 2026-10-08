package org.millenaire.faction.war;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Logical army movement. Uses world height when chunks are available; never teleports to destinations.
 */
public final class ArmyMovementController {
   private ArmyMovementController() {
   }

   public static boolean advanceToward(ServerLevel level, ArmyWartimeState state, @Nullable BlockPos destination, long gameTime) {
      if (level == null || state == null || destination == null || state.isDestroyed() || state.isInBattle()) {
         return false;
      }

      if (!level.dimension().location().equals(state.getDimensionId())) {
         return false;
      }

      BlockPos current = state.getPosition();
      if (current.closerThan(destination, 2.0)) {
         state.setPosition(snapToSurface(level, destination), state.getDimensionId(), gameTime);
         state.setDestination(null);
         return true;
      }

      int dx = Integer.signum(destination.getX() - current.getX());
      int dz = Integer.signum(destination.getZ() - current.getZ());
      int step = WarConfig.ARMY_MOVE_BLOCKS_PER_UPDATE;
      int nextX = current.getX() + dx * step;
      int nextZ = current.getZ() + dz * step;
      if (dx != 0 && Math.abs(nextX - destination.getX()) > Math.abs(current.getX() - destination.getX())) {
         nextX = destination.getX();
      }

      if (dz != 0 && Math.abs(nextZ - destination.getZ()) > Math.abs(current.getZ() - destination.getZ())) {
         nextZ = destination.getZ();
      }

      BlockPos next = snapToSurface(level, new BlockPos(nextX, current.getY(), nextZ));
      state.setPosition(next, state.getDimensionId(), gameTime);
      state.setDestination(destination);
      return next.closerThan(destination, 2.0);
   }

   public static BlockPos snapToSurface(ServerLevel level, BlockPos pos) {
      if (level == null || pos == null) {
         return pos;
      }

      if (!level.hasChunkAt(pos)) {
         return pos;
      }

      int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
      return new BlockPos(pos.getX(), y, pos.getZ());
   }

   public static double distance(BlockPos a, BlockPos b) {
      if (a == null || b == null) {
         return Double.MAX_VALUE;
      }

      return Math.sqrt(a.distToCenterSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
   }

   public static boolean sameDimension(ResourceLocation a, ResourceLocation b) {
      return a != null && a.equals(b);
   }

   public static BlockPos retreatPosition(BlockPos from, BlockPos awayFrom) {
      if (from == null) {
         return BlockPos.ZERO;
      }

      if (awayFrom == null) {
         return from.offset(WarConfig.RETREAT_DISTANCE_BLOCKS, 0, 0);
      }

      int dx = Integer.signum(from.getX() - awayFrom.getX());
      int dz = Integer.signum(from.getZ() - awayFrom.getZ());
      if (dx == 0 && dz == 0) {
         dx = 1;
      }

      return from.offset(dx * WarConfig.RETREAT_DISTANCE_BLOCKS, 0, dz * WarConfig.RETREAT_DISTANCE_BLOCKS);
   }
}
