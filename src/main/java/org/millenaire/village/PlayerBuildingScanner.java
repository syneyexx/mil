package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.millenaire.building.SpecialPoint;

public final class PlayerBuildingScanner {
   private PlayerBuildingScanner() {
   }

   public record ScanResult(
      List<SpecialPoint> points,
      List<BlockPos> bedPositions,
      int farmlandCount,
      int treeCount,
      int chestCount,
      int craftingCount,
      int waterCount,
      int bedCount
   ) {
   }

   public static ScanResult scan(ServerLevel level, BlockPos center) {
      int half = PlayerBuildingRole.HALF;
      int minX = center.getX() - half;
      int maxX = center.getX() + half;
      int minZ = center.getZ() - half;
      int maxZ = center.getZ() + half;
      int minY = center.getY() - PlayerBuildingRole.SCAN_Y_DOWN;
      int maxY = center.getY() + PlayerBuildingRole.SCAN_Y_UP;

      List<SpecialPoint> points = new ArrayList<>();
      List<BlockPos> bedPositions = new ArrayList<>();
      int farmland = 0;
      int trees = 0;
      int chests = 0;
      int crafting = 0;
      int water = 0;
      boolean sleepingSet = false;
      boolean craftingSet = false;
      boolean sellingSet = false;
      boolean defendingSet = false;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (!level.isLoaded(pos)) {
                  continue;
               }
               BlockState state = level.getBlockState(pos);

               if (state.is(Blocks.FARMLAND)) {
                  farmland++;
                  String crop = detectCropSubtype(level, pos.above());
                  points.add(new SpecialPoint(SpecialPoint.SOIL, crop, null, pos.above()));
               } else if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)) {
                  trees++;
               } else if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                  chests++;
                  points.add(new SpecialPoint(SpecialPoint.CHEST, null, null, pos));
                  if (!sellingSet) {
                     points.add(new SpecialPoint(SpecialPoint.SELLING_POS, null, null, pos));
                     sellingSet = true;
                  }
               } else if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)) {
                  crafting++;
                  if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)) {
                     points.add(new SpecialPoint(SpecialPoint.FURNACE, null, null, pos));
                  }
                  if (!craftingSet) {
                     points.add(new SpecialPoint(SpecialPoint.CRAFTING_POS, null, null, pos));
                     craftingSet = true;
                  }
               } else if (state.is(Blocks.WATER) || state.is(Blocks.WATER_CAULDRON)) {
                  water++;
               } else if (state.getBlock() instanceof BedBlock) {
                  if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                     continue;
                  }
                  bedPositions.add(pos);
                  if (!sleepingSet) {
                     points.add(new SpecialPoint(SpecialPoint.SLEEPING_POS, null, null, pos));
                     sleepingSet = true;
                  }
               }
            }
         }
      }

      int wr = PlayerBuildingRole.WATER_RANGE;
      for (int x = center.getX() - wr; x <= center.getX() + wr; x++) {
         for (int z = center.getZ() - wr; z <= center.getZ() + wr; z++) {
            for (int y = center.getY() - 2; y <= center.getY() + 2; y++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (!level.isLoaded(pos)) {
                  continue;
               }
               BlockState state = level.getBlockState(pos);
               if (state.is(Blocks.WATER)) {
                  water++;
                  BlockPos above = pos.above();
                  if (level.getBlockState(above).isAir()) {
                     points.add(new SpecialPoint(SpecialPoint.FISHING_SPOT, null, null, above));
                  }
               }
            }
         }
      }

      if (!sleepingSet) {
         points.add(new SpecialPoint(SpecialPoint.SLEEPING_POS, null, null, center));
      }
      if (!defendingSet) {
         points.add(new SpecialPoint(SpecialPoint.DEFENDING_POS, null, null, center));
      }

      return new ScanResult(points, List.copyOf(bedPositions), farmland, trees, chests, crafting, water, bedPositions.size());
   }

   private static String detectCropSubtype(ServerLevel level, BlockPos cropPos) {
      BlockState above = level.getBlockState(cropPos);
      if (above.getBlock() instanceof CropBlock) {
         if (above.is(Blocks.CARROTS)) {
            return "carrot";
         }
         if (above.is(Blocks.POTATOES)) {
            return "potato";
         }
         if (above.is(Blocks.BEETROOTS)) {
            return "beetroot";
         }
      }
      return "wheat";
   }
}
