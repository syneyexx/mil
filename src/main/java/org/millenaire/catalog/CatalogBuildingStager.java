package org.millenaire.catalog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.HearthLightingUtil;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.TerrainPreparer;

public final class CatalogBuildingStager {
   public static final int NO_FORCED_BASE_Y = Integer.MIN_VALUE;
   public static final int VOID_BASE_Y = 128;
   private static final int PAD_DEPTH = 4;
   private static final int CATALOG_CLEAR_MARGIN = 1;
   private static final int CATALOG_EARTH_LAYER = 3;
   private static final int PAD_SCAN_BORDER = 8;

   private CatalogBuildingStager() {
   }

   @Nullable
   public static CatalogBuildingStager.PlacedBuilding placeInVoid(
      ServerLevel level, BuildingPlanSet planSet, String variant, int targetLevel, BlockPos cell, int clearHeight
   ) {
      BuildingPlanSet.LevelDef level0Def = planSet.getLevel(variant, 0);
      BuildingPlan level0Plan = level0Def == null ? null : ModCultures.getBuildingPlan(level0Def.planId());
      if (level0Plan == null) {
         return null;
      }

      BlockPos pos = new BlockPos(cell.getX(), 128, cell.getZ());
      fillDirtPad(level, pos, level0Plan.width(), level0Plan.depth());
      CatalogBuildingStager.PlacedBuilding placed = place(level, planSet, variant, targetLevel, pos, clearHeight, 128);
      if (placed == null) {
         return null;
      }

      int groundBottomY = lowestSolidY(level, pos, level0Plan.width(), level0Plan.depth());
      return new CatalogBuildingStager.PlacedBuilding(
         placed.nominalOrigin(),
         placed.origin(),
         placed.width(),
         placed.height(),
         placed.depth(),
         placed.aboveFloorBlocks(),
         placed.baseY(),
         groundBottomY,
         placed.doorOrientation()
      );
   }

   private static void fillDirtPad(ServerLevel level, BlockPos origin, int width, int depth) {
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = 0; dx < width; dx++) {
         for (int dz = 0; dz < depth; dz++) {
            for (int y = 125; y <= 128; y++) {
               level.setBlock(cursor.set(origin.getX() + dx, y, origin.getZ() + dz), Blocks.DIRT.defaultBlockState(), 2);
            }
         }
      }
   }

   private static int lowestSolidY(ServerLevel level, BlockPos origin, int width, int depth) {
      int minY = 128;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -8; dx < width + 8; dx++) {
         for (int dz = -8; dz < depth + 8; dz++) {
            for (int y = 112; y <= 128; y++) {
               if (!level.getBlockState(cursor.set(origin.getX() + dx, y, origin.getZ() + dz)).isAir()) {
                  if (y < minY) {
                     minY = y;
                  }
                  break;
               }
            }
         }
      }

      return minY;
   }

   @Nullable
   public static CatalogBuildingStager.PlacedBuilding place(
      ServerLevel level, BuildingPlanSet planSet, String variant, int targetLevel, BlockPos pos, int clearHeight, int forcedBaseY
   ) {
      Rotation rotation = Rotation.NONE;
      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, targetLevel);
      if (levelDef == null) {
         return null;
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
      if (plan == null) {
         return null;
      }

      BuildingPlanSet.LevelDef level0Def = planSet.getLevel(variant, 0);
      BuildingPlan level0Plan = level0Def != null ? ModCultures.getBuildingPlan(level0Def.planId()) : plan;
      if (level0Plan == null) {
         level0Plan = plan;
      }

      ClearMargins margins = ClearMargins.symmetric(1);
      boolean[][] snowMap = TerrainPreparer.checkForSnow(level, pos, level0Plan.width(), level0Plan.depth(), rotation, margins);
      int baseY;
      if (forcedBaseY != Integer.MIN_VALUE) {
         baseY = forcedBaseY;
      } else {
         ClearMargins effective = margins.forRotation(rotation);
         int effWidth = TerrainPreparer.effectiveWidth(level0Plan.width(), level0Plan.depth(), rotation);
         int effDepth = TerrainPreparer.effectiveDepth(level0Plan.width(), level0Plan.depth(), rotation);
         BlockPos effOrigin = TerrainPreparer.effectiveOrigin(pos, level0Plan.width(), level0Plan.depth(), rotation);
         baseY = TerrainPreparer.computeAverageSurfaceHeight(level, effOrigin, effWidth, effDepth, effective);
         baseY = Math.max(baseY, level.getSeaLevel() - level0Plan.groundLevel());
      }

      int effectiveClearHeight = Math.max(level0Plan.height(), clearHeight);
      TerrainPreparer.clearAndFlattenAtY(
         level, pos, level0Plan.width(), effectiveClearHeight, level0Plan.depth(), rotation, level0Plan.groundLevel(), baseY, margins
      );
      TerrainPreparer.decayOrphanedLeaves(level, pos, level0Plan.width(), level0Plan.depth(), rotation, baseY, margins);
      int placementY = baseY + level0Plan.groundLevel();
      BlockPos origin = new BlockPos(pos.getX(), placementY, pos.getZ());
      if (!BuildingPlacer.placeInstantly(level, level0Plan, origin, rotation, true)) {
         return null;
      }

      HearthLightingUtil.lightHearthsInArea(level, origin, new Vec3i(level0Plan.width(), level0Plan.height(), level0Plan.depth()));

      for (int lvl = 1; lvl <= targetLevel; lvl++) {
         BuildingPlanSet.LevelDef upgradeDef = planSet.getLevel(variant, lvl);
         if (upgradeDef != null) {
            BuildingPlan upgradePlan = ModCultures.getBuildingPlan(upgradeDef.planId());
            if (upgradePlan != null) {
               int upgradePlacementY = baseY + upgradePlan.groundLevel();
               BlockPos upgradeOrigin = new BlockPos(pos.getX(), upgradePlacementY, pos.getZ());
               BuildingPlacer.placeUpgradeInstantly(level, upgradePlan, upgradeOrigin, rotation);
               HearthLightingUtil.lightHearthsInArea(level, upgradeOrigin, new Vec3i(upgradePlan.width(), upgradePlan.height(), upgradePlan.depth()));
            }
         }
      }

      TerrainPreparer.restoreSnow(level, pos, level0Plan.width(), level0Plan.depth(), rotation, snowMap, margins);
      int capturedY = baseY + plan.groundLevel();
      BlockPos nominalOrigin = new BlockPos(pos.getX(), capturedY, pos.getZ());
      Set<String> subKeys = cumulativeSubBuildings(planSet, variant, targetLevel);
      if (subKeys.isEmpty()) {
         carveSocle(level, pos, plan.width(), plan.depth(), baseY);
         return tightBounds(level, nominalOrigin, plan.width(), plan.height(), plan.depth(), baseY, plan.buildingOrientation());
      }

      int scanBottomY = nominalOrigin.getY();
      int scanTopY = nominalOrigin.getY() + plan.height();
      int scanWidth = plan.width();
      int scanDepth = plan.depth();

      for (String subKey : subKeys) {
         BuildingPlan subPlan = placeSubBuilding(level, planSet, subKey, origin, rotation);
         if (subPlan != null) {
            scanBottomY = Math.min(scanBottomY, origin.getY());
            scanTopY = Math.max(scanTopY, origin.getY() + subPlan.height());
            scanWidth = Math.max(scanWidth, subPlan.width());
            scanDepth = Math.max(scanDepth, subPlan.depth());
         }
      }

      carveSocle(level, pos, scanWidth, scanDepth, baseY);
      BlockPos scanOrigin = new BlockPos(pos.getX(), scanBottomY, pos.getZ());
      return tightBounds(level, scanOrigin, scanWidth, scanTopY - scanBottomY, scanDepth, baseY, plan.buildingOrientation());
   }

   private static void carveSocle(ServerLevel level, BlockPos pos, int width, int depth, int baseY) {
      int top = baseY - 3;
      int bottom = 92;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -8; dx < width + 8; dx++) {
         for (int dz = -8; dz < depth + 8; dz++) {
            for (int y = top - 1; y >= bottom; y--) {
               cursor.set(pos.getX() + dx, y, pos.getZ() + dz);
               if (!level.getBlockState(cursor).isAir()) {
                  level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   static Set<String> cumulativeSubBuildings(BuildingPlanSet planSet, String variant, int targetLevel) {
      Set<String> keys = new LinkedHashSet<>(planSet.startingSubBuildings());

      for (int lvl = 0; lvl <= targetLevel; lvl++) {
         BuildingPlanSet.LevelDef def = planSet.getLevel(variant, lvl);
         if (def != null) {
            keys.addAll(def.subBuildings());
         }
      }

      return keys;
   }

   @Nullable
   private static BuildingPlan placeSubBuilding(ServerLevel level, BuildingPlanSet parentSet, String subKey, BlockPos origin, Rotation rotation) {
      ResourceLocation subSetId = ResourceLocation.fromNamespaceAndPath("millenaire", parentSet.culture().getPath() + "/" + subKey.toLowerCase(Locale.ROOT));
      BuildingPlanSet subSet = ModCultures.getBuildingPlanSet(subSetId);
      if (subSet != null && !subSet.variants().isEmpty()) {
         String variant = subSet.variants().keySet().stream().sorted().findFirst().orElse(null);
         if (variant == null) {
            return null;
         }

         BuildingPlanSet.LevelDef def = subSet.getLevel(variant, 0);
         BuildingPlan subPlan = def == null ? null : ModCultures.getBuildingPlan(def.planId());
         if (subPlan == null) {
            return null;
         }

         BuildingPlacer.placeInstantly(level, subPlan, origin, rotation, true);
         return subPlan;
      } else {
         return null;
      }
   }

   private static CatalogBuildingStager.PlacedBuilding tightBounds(
      ServerLevel level, BlockPos nominalOrigin, int width, int height, int depth, int baseY, int doorOrientation
   ) {
      boolean[] solid = new boolean[width * height * depth];
      MutableBlockPos cursor = new MutableBlockPos();
      boolean any = false;

      for (int dx = 0; dx < width; dx++) {
         for (int dy = 0; dy < height; dy++) {
            for (int dz = 0; dz < depth; dz++) {
               if (!level.getBlockState(cursor.set(nominalOrigin.getX() + dx, nominalOrigin.getY() + dy, nominalOrigin.getZ() + dz)).isAir()) {
                  solid[idx(dx, dy, dz, height, depth)] = true;
                  any = true;
               }
            }
         }
      }

      if (!any) {
         return new CatalogBuildingStager.PlacedBuilding(nominalOrigin, nominalOrigin, width, height, depth, 0, baseY, nominalOrigin.getY(), doorOrientation);
      }

      boolean[] visited = new boolean[solid.length];
      int[] best = null;
      int[] bestByLevel = null;
      int bestSize = 0;
      Deque<Integer> stack = new ArrayDeque<>();

      for (int seed = 0; seed < solid.length; seed++) {
         if (solid[seed] && !visited[seed]) {
            visited[seed] = true;
            stack.push(seed);
            int size = 0;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int[] byLevel = new int[height];

            while (!stack.isEmpty()) {
               int cur = stack.pop();
               int cz = cur % depth;
               int cy = cur / depth % height;
               int cx = cur / (depth * height);
               size++;
               byLevel[cy]++;
               if (cx < minX) {
                  minX = cx;
               }

               if (cy < minY) {
                  minY = cy;
               }

               if (cz < minZ) {
                  minZ = cz;
               }

               if (cx > maxX) {
                  maxX = cx;
               }

               if (cy > maxY) {
                  maxY = cy;
               }

               if (cz > maxZ) {
                  maxZ = cz;
               }

               for (int ox = -1; ox <= 1; ox++) {
                  for (int oy = -1; oy <= 1; oy++) {
                     for (int oz = -1; oz <= 1; oz++) {
                        if (ox != 0 || oy != 0 || oz != 0) {
                           int nx = cx + ox;
                           int ny = cy + oy;
                           int nz = cz + oz;
                           if (nx >= 0 && ny >= 0 && nz >= 0 && nx < width && ny < height && nz < depth) {
                              int ni = idx(nx, ny, nz, height, depth);
                              if (solid[ni] && !visited[ni]) {
                                 visited[ni] = true;
                                 stack.push(ni);
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (size > bestSize) {
               bestSize = size;
               best = new int[]{minX, minY, minZ, maxX, maxY, maxZ};
               bestByLevel = byLevel;
            }
         }
      }

      int floorLevel = best[1];
      int aboveFloor = 0;

      for (int dy = floorLevel + 1; dy < height; dy++) {
         aboveFloor += bestByLevel[dy];
      }

      BlockPos boxOrigin = new BlockPos(nominalOrigin.getX() + best[0], nominalOrigin.getY() + best[1], nominalOrigin.getZ() + best[2]);
      return new CatalogBuildingStager.PlacedBuilding(
         nominalOrigin, boxOrigin, best[3] - best[0] + 1, best[4] - best[1] + 1, best[5] - best[2] + 1, aboveFloor, baseY, boxOrigin.getY(), doorOrientation
      );
   }

   private static int idx(int dx, int dy, int dz, int height, int depth) {
      return (dx * height + dy) * depth + dz;
   }

   public record PlacedBuilding(
      BlockPos nominalOrigin, BlockPos origin, int width, int height, int depth, int aboveFloorBlocks, int baseY, int groundBottomY, int doorOrientation
   ) {
   }
}
