package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;
import org.millenaire.block.MillPathBlock;
import org.millenaire.block.MillPathSlabBlock;
import org.millenaire.block.PathTier;
import org.millenaire.block.RicePaddyBlock;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.goal.NavigationHelperUtils;

public class MillWalkNodeEvaluator extends WalkNodeEvaluator {
   static final float ON_TOP_OF_BARRIER_MALUS = 16.0F;
   @Nullable
   private final Mob mob;
   private long cachedVillageTick = Long.MIN_VALUE;
   private boolean cachedNoLeafClearing;
   private final Node[] millReusableNeighbors = new Node[Plane.HORIZONTAL.length()];
   private static final int WATER_START_PROBE_UP = 2;
   private static final int WATER_START_PROBE_DOWN = -1;
   private static final int WATER_START_PROBE_RADIUS = 2;

   public MillWalkNodeEvaluator() {
      this(null);
   }

   public MillWalkNodeEvaluator(@Nullable Mob mob) {
      this.mob = mob;
   }

   public Node getStart() {
      if (this.mob != null && this.mob.isInWater() && this.currentContext != null) {
         MutableBlockPos cursor = new MutableBlockPos();
         int x0 = this.mob.getBlockX();
         int y0 = this.mob.getBlockY();
         int z0 = this.mob.getBlockZ();
         int bestDist = Integer.MAX_VALUE;
         int bestX = x0;
         int bestY = y0;
         int bestZ = z0;

         for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                  if (dist != 0 && dist < bestDist && this.isDryStandingCell(cursor, x0 + dx, y0 + dy, z0 + dz)) {
                     bestDist = dist;
                     bestX = x0 + dx;
                     bestY = y0 + dy;
                     bestZ = z0 + dz;
                  }
               }
            }
         }

         if (bestDist != Integer.MAX_VALUE) {
            return super.getStartNode(cursor.set(bestX, bestY, bestZ));
         }
      }

      return super.getStart();
   }

   private boolean isDryStandingCell(MutableBlockPos cursor, int x, int y, int z) {
      BlockState feet = this.currentContext.getBlockState(cursor.set(x, y, z));
      BlockState head = this.currentContext.getBlockState(cursor.set(x, y + 1, z));
      if (feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()) {
         if (!feet.isSuffocating(this.currentContext.level(), cursor.set(x, y, z)) && !head.isSuffocating(this.currentContext.level(), cursor.set(x, y + 1, z))
            )
          {
            BlockState below = this.currentContext.getBlockState(cursor.set(x, y - 1, z));
            return !below.getFluidState().isEmpty() ? false : !below.getCollisionShape(this.currentContext.level(), cursor.set(x, y - 1, z)).isEmpty();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public PathType getPathType(PathfindingContext context, int x, int y, int z) {
      PathType base = super.getPathType(context, x, y, z);
      BlockState here = context.level().getBlockState(new BlockPos(x, y, z));
      if (isLitCampfire(here)) {
         return PathType.BLOCKED;
      }

      if (base == PathType.FENCE) {
         BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
         return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN) && this.canOpenDoors()
            ? PathType.DOOR_WOOD_CLOSED
            : PathType.BLOCKED;
      }

      if (base == PathType.LEAVES) {
         return this.isLeafTraversalBlocked(here) ? PathType.BLOCKED : PathType.OPEN;
      }

      if (base == PathType.WATER || base == PathType.WATER_BORDER) {
         BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
         if (state.getBlock() instanceof RicePaddyBlock) {
            return PathType.OPEN;
         }
      }

      if (base == PathType.WALKABLE) {
         BlockState hereState = context.level().getBlockState(new BlockPos(x, y, z));
         if (hereState.getBlock() instanceof MillPathBlock || hereState.getBlock() instanceof MillPathSlabBlock) {
            return PathType.COCOA;
         }

         if (y > context.level().getMinBuildHeight()) {
            BlockState belowState = context.level().getBlockState(new BlockPos(x, y - 1, z));
            if (belowState.getBlock() instanceof MillPathBlock || belowState.getBlock() instanceof MillPathSlabBlock) {
               return PathType.COCOA;
            }
         }
      }

      return base;
   }

   public int getNeighbors(Node[] outputArray, Node node) {
      int count = 0;
      int verticalDeltaLimit = 0;
      Mob pathMob = super.mob;
      PathType above = this.getCachedPathType(node.x, node.y + 1, node.z);
      PathType here = this.getCachedPathType(node.x, node.y, node.z);
      if (pathMob.getPathfindingMalus(above) >= 0.0F && here != PathType.STICKY_HONEY) {
         verticalDeltaLimit = Mth.floor(Math.max(1.0F, pathMob.maxUpStep()));
         if (this.isOnPathTile(node.x, node.y, node.z)) {
            verticalDeltaLimit++;
         }
      }

      double floor = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));

      for (Direction direction : Plane.HORIZONTAL) {
         Node neighbor = this.findAcceptedNode(node.x + direction.getStepX(), node.y, node.z + direction.getStepZ(), verticalDeltaLimit, floor, direction, here);
         this.millReusableNeighbors[direction.get2DDataValue()] = neighbor;
         if (this.isNeighborValid(neighbor, node)) {
            outputArray[count++] = neighbor;
         }
      }

      for (Direction direction1 : Plane.HORIZONTAL) {
         Direction direction2 = direction1.getClockWise();
         if (this.isDiagonalValid(node, this.millReusableNeighbors[direction1.get2DDataValue()], this.millReusableNeighbors[direction2.get2DDataValue()])) {
            Node diagonal = this.findAcceptedNode(
               node.x + direction1.getStepX() + direction2.getStepX(),
               node.y,
               node.z + direction1.getStepZ() + direction2.getStepZ(),
               verticalDeltaLimit,
               floor,
               direction1,
               here
            );
            if (this.isDiagonalValid(diagonal)) {
               outputArray[count++] = diagonal;
            }
         }
      }

      return count;
   }

   private boolean isOnPathTile(int x, int y, int z) {
      if (this.currentContext == null) {
         return false;
      }

      BlockState hereState = this.currentContext.getBlockState(new BlockPos(x, y, z));
      if (hereState.getBlock() instanceof MillPathBlock || hereState.getBlock() instanceof MillPathSlabBlock) {
         return true;
      }

      if (y <= this.currentContext.level().getMinBuildHeight()) {
         return false;
      }

      BlockState belowState = this.currentContext.getBlockState(new BlockPos(x, y - 1, z));
      return belowState.getBlock() instanceof MillPathBlock || belowState.getBlock() instanceof MillPathSlabBlock;
   }

   protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
      Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
      if ((node == null || node.costMalus < 0.0F) && this.getCachedPathType(x, y, z) == PathType.OPEN) {
         Node pathLanding = this.tryPathLandingBelow(x, y, z);
         if (pathLanding != null) {
            node = pathLanding;
         }
      }

      if (node != null && node.type == PathType.COCOA) {
         PathTier tier = this.resolvePathTier(node.x, node.y, node.z);
         if (tier != null && tier.preferenceMalus() > node.costMalus) {
            node.costMalus = tier.preferenceMalus();
         }
      }

      if (node != null && this.isOnTopOfBarrier(node.x, node.y, node.z)) {
         node.costMalus = Math.max(node.costMalus, 16.0F);
      }

      return node;
   }

   @Nullable
   private Node tryPathLandingBelow(int x, int y, int z) {
      int maxFall = super.mob.getMaxFallDistance() + 1;
      int minY = this.currentContext.level().getMinBuildHeight();

      for (int i = y - 1; i >= minY; i--) {
         if (y - i > maxFall) {
            return null;
         }

         PathType belowType = this.getCachedPathType(x, i, z);
         if (belowType != PathType.OPEN) {
            float malus = super.mob.getPathfindingMalus(belowType);
            if (malus >= 0.0F && this.isOnPathTile(x, i, z)) {
               Node node = this.getNode(x, i, z);
               node.type = belowType;
               node.costMalus = Math.max(node.costMalus, malus);
               return node;
            }

            return null;
         }
      }

      return null;
   }

   protected double getFloorLevel(BlockPos pos) {
      if (this.currentContext != null) {
         BlockState here = this.currentContext.getBlockState(pos);
         if (!here.isAir() && here.getFluidState().isEmpty()) {
            double maxY = NavigationHelperUtils.collisionTopOffset(this.currentContext.level(), pos, here);
            if (maxY > 0.0 && maxY < 1.0) {
               return pos.getY() + maxY;
            }
         }
      }

      return super.getFloorLevel(pos);
   }

   private PathTier resolvePathTier(int x, int y, int z) {
      BlockState hereState = this.currentContext.level().getBlockState(new BlockPos(x, y, z));
      PathTier tier = tierOf(hereState.getBlock());
      if (tier != null) {
         return tier;
      } else if (y > this.currentContext.level().getMinBuildHeight()) {
         BlockState belowState = this.currentContext.level().getBlockState(new BlockPos(x, y - 1, z));
         return tierOf(belowState.getBlock());
      } else {
         return null;
      }
   }

   private boolean isOnTopOfBarrier(int x, int y, int z) {
      if (this.currentContext == null) {
         return false;
      }

      if (y <= this.currentContext.level().getMinBuildHeight()) {
         return false;
      }

      BlockState belowState = this.currentContext.level().getBlockState(new BlockPos(x, y - 1, z));
      return belowState.is(BlockTags.FENCES) || belowState.is(BlockTags.WALLS) || belowState.is(Blocks.IRON_BARS);
   }

   private static PathTier tierOf(Block block) {
      if (block instanceof MillPathBlock mpb) {
         return mpb.tier();
      } else {
         return block instanceof MillPathSlabBlock msb ? msb.tier() : null;
      }
   }

   private boolean isLeafTraversalBlocked(BlockState leafState) {
      if (this.mob instanceof MillVillager v) {
         if (!(this.mob.level() instanceof ServerLevel sl)) {
            return false;
         } else {
            long tick = sl.getGameTime();
            if (tick != this.cachedVillageTick) {
               ResourceLocation typeId = v.getVillagerTypeId();
               if (typeId == null) {
                  this.cachedNoLeafClearing = true;
               } else {
                  VillagerType vt = ModCultures.getVillagerType(typeId);
                  this.cachedNoLeafClearing = vt != null && vt.hasTag("noleafclearing");
               }

               this.cachedVillageTick = tick;
            }

            return this.cachedNoLeafClearing ? true : !MillVillager.isClearableLeaf(leafState);
         }
      } else {
         return false;
      }
   }

   static boolean isLitCampfire(BlockState state) {
      return state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && (Boolean)state.getValue(CampfireBlock.LIT);
   }
}
