package org.millenaire.goal;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.entity.BlockHazards;
import org.millenaire.entity.MillVillager;
import org.slf4j.Logger;

public final class NavigationHelperUtils {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int TELEPORT_FACTOR = 4;
   private static final int RANDOM_SAFE_ATTEMPTS = 20;
   private static final int RANDOM_SAFE_RADIUS = 2;

   private NavigationHelperUtils() {
   }

   public static double horizontalDistSq(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   public static boolean isSafeLanding(BlockGetter level, BlockPos pos) {
      if (level.getBlockState(pos).isSuffocating(level, pos)) {
         return false;
      } else if (level.getBlockState(pos.above()).isSuffocating(level, pos.above())) {
         return false;
      } else {
         return BlockHazards.isHazardousAt(level, pos) ? false : hasStandingSupport(level, pos);
      }
   }

   public static boolean hasStandingSupport(BlockGetter level, BlockPos pos) {
      if (collisionTopOffset(level, pos, level.getBlockState(pos)) > 0.0) {
         return true;
      }

      BlockPos below = pos.below();
      return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
   }

   public static double collisionTopOffset(BlockGetter level, BlockPos pos, BlockState state) {
      if (!state.isAir() && state.getFluidState().isEmpty()) {
         VoxelShape shape = state.getCollisionShape(level, pos);
         if (shape.isEmpty()) {
            return 0.0;
         }

         double maxY = shape.max(Axis.Y);
         return maxY < 0.0 ? 0.0 : maxY;
      } else {
         return 0.0;
      }
   }

   public static int villagerStandY(Level level, int x, int z) {
      int top = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      int minY = level.getMinBuildHeight();
      MutableBlockPos cursor = new MutableBlockPos();
      return resolveStandY(top, minY, y -> {
         cursor.set(x, y, z);
         return level.getBlockState(cursor).isAir();
      }, y -> {
         cursor.set(x, y, z);
         return collisionTopOffset(level, cursor, level.getBlockState(cursor));
      });
   }

   public static int villagerStandYAt(Level level, BlockPos pos) {
      return villagerStandY(level, pos.getX(), pos.getZ());
   }

   static int resolveStandY(int topY, int minY, IntPredicate isAir, IntToDoubleFunction collisionTopAt) {
      for (int y = topY; y > minY; y--) {
         if (!isAir.test(y) && !isAir.test(y - 1)) {
            return collisionTopAt.applyAsDouble(y) >= 1.0 ? y + 1 : y;
         }
      }

      return topY;
   }

   public static void teleportToSafe(MillVillager villager, BlockPos target) {
      Level level = villager.level();
      if (isSafeLanding(level, target)) {
         villager.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
         villager.getNavigation().stop();
      } else if (!tryTeleportTowards(villager, target)) {
         BlockPos outdoorPos = villager.getLastOutdoorPos();
         if (outdoorPos != null && isSafeLanding(level, outdoorPos)) {
            LOGGER.debug("[Millenaire] NavHelperUtils — TP to lastOutdoorPos {}", outdoorPos.toShortString());
            villager.teleportTo(outdoorPos.getX() + 0.5, outdoorPos.getY(), outdoorPos.getZ() + 0.5);
            villager.getNavigation().stop();
         } else {
            BlockPos safePos = findRandomSafePos(level, villager.blockPosition());
            if (safePos != null) {
               LOGGER.debug("[Millenaire] NavHelperUtils — TP to random safe pos {}", safePos.toShortString());
               villager.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
               villager.getNavigation().stop();
            } else {
               int surfaceY = villagerStandY(level, target.getX(), target.getZ());
               BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());

               for (int nudge = 0; nudge < 5; nudge++) {
                  BlockPos candidate = surfacePos.above(nudge);
                  if (isSafeLanding(level, candidate)) {
                     villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                     villager.getNavigation().stop();
                     return;
                  }
               }

               LOGGER.warn("[Millenaire] NavHelperUtils — Step 5 fallback may suffocate at {}", surfacePos.toShortString());
               villager.teleportTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5);
               villager.getNavigation().stop();
            }
         }
      }
   }

   public static void teleportToSafeNearTarget(MillVillager villager, BlockPos target) {
      teleportToSafeNearTarget(villager, target, p -> false);
   }

   public static void teleportToSafeNearTarget(MillVillager villager, BlockPos target, Predicate<BlockPos> excluded) {
      Level level = villager.level();
      if (!excluded.test(target) && isSafeLanding(level, target)) {
         villager.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
         villager.getNavigation().stop();
      } else if (!tryTeleportTowards(villager, target)) {
         BlockPos safePos = findRandomSafePos(level, target, excluded);
         if (safePos != null) {
            LOGGER.debug("[Millenaire] NavHelperUtils — TP safe near target: {}", safePos.toShortString());
            villager.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
            villager.getNavigation().stop();
         } else {
            int surfaceY = villagerStandY(level, target.getX(), target.getZ());
            BlockPos surfacePos = new BlockPos(target.getX(), surfaceY, target.getZ());

            for (int nudge = 0; nudge < 5; nudge++) {
               BlockPos candidate = surfacePos.above(nudge);
               if (isSafeLanding(level, candidate)) {
                  villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                  villager.getNavigation().stop();
                  return;
               }
            }

            LOGGER.warn("[Millenaire] NavHelperUtils — target fallback unsafe at {}", surfacePos.toShortString());
            villager.teleportTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5);
            villager.getNavigation().stop();
         }
      }
   }

   public static boolean tryTeleportTowards(MillVillager villager, BlockPos dest) {
      BlockPos current = villager.blockPosition();
      int dx = dest.getX() - current.getX();
      int dz = dest.getZ() - current.getZ();
      if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
         return false;
      }

      int xDir = Integer.signum(dx);
      int zDir = Integer.signum(dz);
      int targetX = current.getX() + xDir * 4;
      int targetZ = current.getZ() + zDir * 4;
      int surfaceY = villagerStandY(villager.level(), targetX, targetZ);
      Level level = villager.level();

      for (int nudge = 0; nudge <= 1; nudge++) {
         BlockPos candidate = new BlockPos(targetX, surfaceY + nudge, targetZ);
         if (isSafeLanding(level, candidate)) {
            LOGGER.debug("[Millenaire] NavHelperUtils — partial TP towards dest: {}", candidate.toShortString());
            villager.teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            villager.getNavigation().stop();
            return true;
         }
      }

      return false;
   }

   @Nullable
   public static BlockPos findRandomSafePos(Level level, BlockPos origin) {
      return findRandomSafePos(level, origin, p -> false);
   }

   @Nullable
   public static BlockPos findRandomSafePos(Level level, BlockPos origin, Predicate<BlockPos> excluded) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      List<BlockPos> candidates = new ArrayList<>(20);

      for (int i = 0; i < 20; i++) {
         int rdx = random.nextInt(-2, 3);
         int dy = random.nextInt(-1, 2);
         int rdz = random.nextInt(-2, 3);
         if (rdx != 0 || dy != 0 || rdz != 0) {
            BlockPos test = origin.offset(rdx, dy, rdz);
            if (!excluded.test(test)) {
               BlockPos below = test.below();
               if (level.getBlockState(below).isSolidRender(level, below) && isSafeLanding(level, test)) {
                  candidates.add(test);
               }
            }
         }
      }

      return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
   }
}
