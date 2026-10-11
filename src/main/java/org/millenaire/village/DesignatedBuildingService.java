package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.building.DesignatedBuildingPlans;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.slf4j.Logger;

/**
 * Creates and maintains player-designated buildings (purpose contracts on player-built volumes).
 */
public final class DesignatedBuildingService {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int MIN_WIDTH = 3;
   public static final int MIN_DEPTH = 3;
   public static final int MIN_HEIGHT = 3;
   public static final int MIN_HEIGHT_OPEN_AIR = 1;
   public static final int MAX_WIDTH = 48;
   public static final int MAX_DEPTH = 48;
   public static final int MAX_HEIGHT = 32;
   public static final int MIN_FARMLAND_SOILS = 4;
   public static final int MAX_SOIL_POINTS = 128;
   public static final int MAX_ANIMAL_SPAWN_POINTS = 8;
   public static final int MAX_TREE_SPAWN_POINTS = 16;

   private DesignatedBuildingService() {
   }

   public enum FailureReason {
      NOT_OWNER,
      TOO_FAR,
      INVALID_PURPOSE,
      TOO_SMALL,
      TOO_LARGE,
      OUTSIDE_RADIUS,
      OVERLAP,
      MISSING_CHEST,
      MISSING_BED,
      MISSING_SOIL,
      CAP_REACHED,
      UNREACHABLE,
      GENERIC
   }

   public record ValidationResult(
      boolean ok, @Nullable DesignatedBuildingService.FailureReason reason, int chestCount, int bedCount, int soilCount, int height
   ) {
      public static DesignatedBuildingService.ValidationResult fail(DesignatedBuildingService.FailureReason reason) {
         return new DesignatedBuildingService.ValidationResult(false, reason, 0, 0, 0, 0);
      }
   }

   public record ScanResult(
      List<BlockPos> chests,
      List<BlockPos> beds,
      List<BlockPos> soils,
      List<BlockPos> animalSpawnCandidates,
      List<BlockPos> treeSpawns,
      BlockPos sellingPos,
      int height
   ) {
   }

   public static int minHeightFor(BuildingPurpose purpose) {
      return purpose.isOpenAir() ? MIN_HEIGHT_OPEN_AIR : MIN_HEIGHT;
   }

   public static int detectHeight(ServerLevel level, BlockPos origin, int width, int depth) {
      return detectHeight(level, origin, width, depth, MIN_HEIGHT);
   }

   public static int detectHeight(ServerLevel level, BlockPos origin, int width, int depth, int minHeight) {
      int maxPossible = Math.min(MAX_HEIGHT, level.getMaxBuildHeight() - origin.getY());
      int height = 0;

      for (int dy = 0; dy < maxPossible; dy++) {
         boolean blockFound = false;

         for (int dx = 0; dx < width && !blockFound; dx++) {
            for (int dz = 0; dz < depth && !blockFound; dz++) {
               if (!level.getBlockState(origin.offset(dx, dy, dz)).isAir()) {
                  blockFound = true;
               }
            }
         }

         if (blockFound) {
            height = dy + 1;
         } else if (dy >= minHeight) {
            break;
         }
      }

      return Math.max(height, 1);
   }

   public static DesignatedBuildingService.ScanResult scan(ServerLevel level, BlockPos origin, int width, int depth, int height) {
      List<BlockPos> chests = new ArrayList<>();
      List<BlockPos> beds = new ArrayList<>();
      Set<BlockPos> soils = new LinkedHashSet<>();
      List<BlockPos> animalSpawnCandidates = new ArrayList<>();
      List<BlockPos> treeSpawns = new ArrayList<>();

      for (int dx = 0; dx < width; dx++) {
         for (int dy = 0; dy < height; dy++) {
            for (int dz = 0; dz < depth; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (!level.isLoaded(pos)) {
                  continue;
               }

               BlockState state = level.getBlockState(pos);
               if (state.getBlock() instanceof ChestBlock && level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                  chests.add(pos.immutable());
               } else if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                  beds.add(pos.immutable());
               } else if (state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                  beds.add(pos.immutable());
               }

               if (isSoilSurface(state)) {
                  soils.add(pos.immutable());
               } else if (state.getBlock() instanceof CropBlock) {
                  BlockPos below = pos.below();
                  if (level.isLoaded(below) && isSoilSurface(level.getBlockState(below))) {
                     soils.add(below.immutable());
                  }
               }

               if (state.getBlock() instanceof SaplingBlock || state.is(BlockTags.SAPLINGS)) {
                  treeSpawns.add(pos.immutable());
               }

               if (isAnimalSpawnSurface(state) && isStandable(level, pos.above())) {
                  animalSpawnCandidates.add(pos.above().immutable());
               }
            }
         }
      }

      BlockPos sellingPos = pickSellingPos(level, origin, width, depth, height, chests, beds, soils, animalSpawnCandidates);

      return new DesignatedBuildingService.ScanResult(
         chests, beds, List.copyOf(soils), animalSpawnCandidates, treeSpawns, sellingPos, height
      );
   }

   public static DesignatedBuildingService.ValidationResult validate(
      ServerLevel level, Village village, BuildingPurpose purpose, BlockPos origin, int width, int depth, @Nullable BuildingId replaceId
   ) {
      if (width < MIN_WIDTH || depth < MIN_DEPTH) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.TOO_SMALL);
      }

      if (width > MAX_WIDTH || depth > MAX_DEPTH) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.TOO_LARGE);
      }

      int minHeight = minHeightFor(purpose);
      int height = detectHeight(level, origin, width, depth, minHeight);
      if (height < minHeight) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.TOO_SMALL);
      }

      if (height > MAX_HEIGHT) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.TOO_LARGE);
      }

      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 90;
      if (!withinRadius(village.getCenter(), radius, origin, width, depth)) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.OUTSIDE_RADIUS);
      }

      if (overlapsExisting(village, origin, width, depth, replaceId)) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.OVERLAP);
      }

      int count = countPurpose(village, purpose, replaceId);
      if (count >= purpose.maxCount()) {
         return DesignatedBuildingService.ValidationResult.fail(DesignatedBuildingService.FailureReason.CAP_REACHED);
      }

      DesignatedBuildingService.ScanResult scan = scan(level, origin, width, depth, height);
      if (purpose.requiresChest() && scan.chests().isEmpty()) {
         return new DesignatedBuildingService.ValidationResult(
            false, DesignatedBuildingService.FailureReason.MISSING_CHEST, 0, scan.beds().size(), scan.soils().size(), height
         );
      }

      if (purpose.requiresBed() && scan.beds().isEmpty()) {
         return new DesignatedBuildingService.ValidationResult(
            false, DesignatedBuildingService.FailureReason.MISSING_BED, scan.chests().size(), 0, scan.soils().size(), height
         );
      }

      if (purpose.isFarmland() && scan.soils().size() < MIN_FARMLAND_SOILS) {
         return new DesignatedBuildingService.ValidationResult(
            false, DesignatedBuildingService.FailureReason.MISSING_SOIL, scan.chests().size(), scan.beds().size(), scan.soils().size(), height
         );
      }

      if (!isStandable(level, scan.sellingPos()) && !isStandable(level, scan.sellingPos().above())) {
         return new DesignatedBuildingService.ValidationResult(
            false, DesignatedBuildingService.FailureReason.UNREACHABLE, scan.chests().size(), scan.beds().size(), scan.soils().size(), height
         );
      }

      return new DesignatedBuildingService.ValidationResult(true, null, scan.chests().size(), scan.beds().size(), scan.soils().size(), height);
   }

   @Nullable
   public static BuildingInstance designate(
      ServerLevel level, Village village, ServerPlayer player, BuildingPurpose purpose, BlockPos cornerA, BlockPos cornerB
   ) {
      BlockPos min = new BlockPos(Math.min(cornerA.getX(), cornerB.getX()), Math.min(cornerA.getY(), cornerB.getY()), Math.min(cornerA.getZ(), cornerB.getZ()));
      BlockPos max = new BlockPos(Math.max(cornerA.getX(), cornerB.getX()), Math.max(cornerA.getY(), cornerB.getY()), Math.max(cornerA.getZ(), cornerB.getZ()));
      int width = max.getX() - min.getX() + 1;
      int depth = max.getZ() - min.getZ() + 1;
      BlockPos origin = new BlockPos(min.getX(), min.getY(), min.getZ());

      DesignatedBuildingService.ValidationResult validation = validate(level, village, purpose, origin, width, depth, null);
      if (!validation.ok()) {
         player.sendSystemMessage(Component.translatable(errorKey(validation.reason())));
         return null;
      }

      int height = validation.height();
      DesignatedBuildingService.ScanResult scan = scan(level, origin, width, depth, height);
      BuildingInstance building = new BuildingInstance(
         BuildingId.random(),
         DesignatedBuildingPlans.planId(purpose),
         origin,
         Rotation.NONE,
         BuildingInstance.Status.COMPLETE,
         DesignatedBuildingPlans.planSetId(purpose),
         "0",
         0
      );
      building.setDesignation(purpose, width, depth, height);
      building.setUpgradesAllowed(false);
      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      if (plan != null) {
         building.addRuntimeTags(plan.tags());
      }

      applyScan(building, scan);
      building.setDesignationFeaturesValid(featuresValid(purpose, scan));
      village.addBuilding(building);
      building.linkChestsToBuilding(level);
      village.markDirty();
      LOGGER.info(
         "[Millenaire] Designated {} at {} ({}x{}x{}, chests={}, beds={}, soils={}) in village {}",
         purpose.id(),
         origin,
         width,
         depth,
         height,
         scan.chests().size(),
         scan.beds().size(),
         scan.soils().size(),
         village.getId().uuid()
      );
      player.sendSystemMessage(
         Component.translatable(
            "gui.millenaire.designate.success." + purpose.id(),
            String.valueOf(scan.chests().size()),
            String.valueOf(scan.beds().size()),
            String.valueOf(scan.soils().size())
         )
      );
      return building;
   }

   public static boolean undesignate(ServerLevel level, Village village, BuildingInstance building) {
      if (building == null || !building.isPlayerDesignated()) {
         return false;
      }

      return village.forgetBuilding(level, building);
   }

   public static void refreshAll(ServerLevel level, Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building.isPlayerDesignated()) {
            refresh(level, building);
         }
      }
   }

   public static void refresh(ServerLevel level, BuildingInstance building) {
      if (!building.isPlayerDesignated() || building.getPurpose() == null) {
         return;
      }

      BuildingPurpose purpose = building.getPurpose();
      BlockPos origin = building.getOrigin();
      int width = building.getDesignatedWidth();
      int depth = building.getDesignatedDepth();
      int minHeight = minHeightFor(purpose);
      int height = Math.max(building.getDesignatedHeight(), detectHeight(level, origin, width, depth, minHeight));
      if (height != building.getDesignatedHeight()) {
         building.setDesignation(purpose, width, depth, height);
      }

      DesignatedBuildingService.ScanResult scan = scan(level, origin, width, depth, height);
      building.setDesignationFeaturesValid(featuresValid(purpose, scan));
      applyScan(building, scan);
      building.linkChestsToBuilding(level);
   }

   public static void applyScan(BuildingInstance building, DesignatedBuildingService.ScanResult scan) {
      BuildingPurpose purpose = building.getPurpose();
      List<SpecialPoint> points = new ArrayList<>();
      for (BlockPos chest : scan.chests()) {
         points.add(new SpecialPoint(SpecialPoint.CHEST, null, null, chest));
      }

      points.add(new SpecialPoint(SpecialPoint.SELLING_POS, null, null, scan.sellingPos()));
      if (!scan.beds().isEmpty()) {
         points.add(new SpecialPoint(SpecialPoint.SLEEPING_POS, null, null, scan.beds().getFirst()));
      }

      if (purpose != null && purpose.isFarmland()) {
         for (BlockPos soil : sampleEvenly(scan.soils(), MAX_SOIL_POINTS)) {
            points.add(new SpecialPoint(SpecialPoint.SOIL, "wheat", null, soil));
         }
      }

      if (purpose != null && purpose.animalSpawnSubtype() != null) {
         String animal = purpose.animalSpawnSubtype();
         List<BlockPos> spawnPts = sampleEvenly(scan.animalSpawnCandidates(), MAX_ANIMAL_SPAWN_POINTS);
         if (spawnPts.isEmpty()) {
            BlockPos fallback = scan.sellingPos();
            spawnPts = List.of(fallback);
         }
         for (BlockPos spawn : spawnPts) {
            points.add(new SpecialPoint(SpecialPoint.ANIMAL_SPAWN, animal, null, spawn));
         }
      }

      if (purpose == BuildingPurpose.GROVE) {
         List<BlockPos> trees = sampleEvenly(scan.treeSpawns(), MAX_TREE_SPAWN_POINTS);
         for (BlockPos tree : trees) {
            points.add(new SpecialPoint(SpecialPoint.TREE_SPAWN, "oak", null, tree));
         }
      }

      building.setResolvedPoints(points);
      building.clearInventory();
      building.initInventory();

      if (purpose != null && purpose.requiresBed()) {
         syncBeds(building, scan.beds());
      }
   }

   private static boolean featuresValid(BuildingPurpose purpose, DesignatedBuildingService.ScanResult scan) {
      if (purpose.requiresBed() && scan.beds().isEmpty()) {
         return false;
      }
      if (purpose.requiresChest() && scan.chests().isEmpty()) {
         return false;
      }
      if (purpose.isFarmland() && scan.soils().size() < MIN_FARMLAND_SOILS) {
         return false;
      }
      return true;
   }

   private static void syncBeds(BuildingInstance building, List<BlockPos> beds) {
      org.millenaire.building.BedManager mgr = building.getBedManager();
      java.util.HashSet<BlockPos> wanted = new java.util.HashSet<>(beds);
      for (BlockPos existing : List.copyOf(mgr.getBedPositions())) {
         if (!wanted.contains(existing)) {
            mgr.remove(existing);
         }
      }

      for (BlockPos bed : beds) {
         mgr.add(bed);
      }

      building.setBedManagerInitialized(true);
   }

   public static String errorKey(@Nullable DesignatedBuildingService.FailureReason reason) {
      if (reason == null) {
         return "gui.millenaire.designate.error.generic";
      }

      return switch (reason) {
         case NOT_OWNER -> "gui.millenaire.designate.error.not_owner";
         case TOO_FAR -> "gui.millenaire.designate.error.too_far";
         case INVALID_PURPOSE -> "gui.millenaire.designate.error.invalid_purpose";
         case TOO_SMALL -> "gui.millenaire.designate.error.too_small";
         case TOO_LARGE -> "gui.millenaire.designate.error.too_large";
         case OUTSIDE_RADIUS -> "gui.millenaire.designate.error.outside_radius";
         case OVERLAP -> "gui.millenaire.designate.error.overlap";
         case MISSING_CHEST -> "gui.millenaire.designate.error.missing_chest";
         case MISSING_BED -> "gui.millenaire.designate.error.missing_bed";
         case MISSING_SOIL -> "gui.millenaire.designate.error.missing_soil";
         case CAP_REACHED -> "gui.millenaire.designate.error.cap_reached";
         case UNREACHABLE -> "gui.millenaire.designate.error.unreachable";
         case GENERIC -> "gui.millenaire.designate.error.generic";
      };
   }

   private static BlockPos pickSellingPos(
      ServerLevel level,
      BlockPos origin,
      int width,
      int depth,
      int height,
      List<BlockPos> chests,
      List<BlockPos> beds,
      Set<BlockPos> soils,
      List<BlockPos> animalSpawnCandidates
   ) {
      for (BlockPos chest : chests) {
         if (isStandable(level, chest) || isStandable(level, chest.above())) {
            return chest;
         }
      }
      for (BlockPos bed : beds) {
         if (isStandable(level, bed) || isStandable(level, bed.above())) {
            return bed;
         }
      }
      for (BlockPos soil : soils) {
         BlockPos above = soil.above();
         if (isStandable(level, above)) {
            return above;
         }
      }
      for (BlockPos spawn : animalSpawnCandidates) {
         if (isStandable(level, spawn)) {
            return spawn;
         }
      }
      BlockPos center = origin.offset(width / 2, Math.min(1, Math.max(0, height - 1)), depth / 2);
      if (isStandable(level, center) || isStandable(level, center.above())) {
         return center;
      }
      // Last resort: any air block with solid below inside the volume.
      for (int dy = 0; dy < height; dy++) {
         for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (isStandable(level, pos)) {
                  return pos;
               }
            }
         }
      }
      return center;
   }

   private static boolean isSoilSurface(BlockState state) {
      return state.is(Blocks.FARMLAND)
         || state.is(Blocks.DIRT)
         || state.is(Blocks.GRASS_BLOCK)
         || state.is(Blocks.COARSE_DIRT)
         || state.is(Blocks.ROOTED_DIRT);
   }

   private static boolean isAnimalSpawnSurface(BlockState state) {
      return state.is(Blocks.GRASS_BLOCK)
         || state.is(Blocks.DIRT)
         || state.is(Blocks.COARSE_DIRT)
         || state.is(Blocks.PODZOL)
         || state.is(Blocks.MYCELIUM)
         || state.is(BlockTags.DIRT);
   }

   private static List<BlockPos> sampleEvenly(List<BlockPos> candidates, int max) {
      if (candidates.isEmpty() || max <= 0) {
         return List.of();
      }
      if (candidates.size() <= max) {
         return List.copyOf(candidates);
      }
      List<BlockPos> sampled = new ArrayList<>(max);
      double step = (double)candidates.size() / max;
      for (int i = 0; i < max; i++) {
         sampled.add(candidates.get(Math.min(candidates.size() - 1, (int)Math.floor(i * step))));
      }
      return sampled;
   }

   private static boolean withinRadius(BlockPos center, int radius, BlockPos origin, int width, int depth) {
      long r2 = (long)radius * radius;
      int[] xs = {origin.getX(), origin.getX() + width - 1};
      int[] zs = {origin.getZ(), origin.getZ() + depth - 1};
      for (int x : xs) {
         for (int z : zs) {
            long dx = x - center.getX();
            long dz = z - center.getZ();
            if (dx * dx + dz * dz > r2) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean overlapsExisting(Village village, BlockPos origin, int width, int depth, @Nullable BuildingId replaceId) {
      int minX = origin.getX();
      int maxX = origin.getX() + width;
      int minZ = origin.getZ();
      int maxZ = origin.getZ() + depth;

      for (BuildingInstance other : village.getBuildings()) {
         if (replaceId != null && other.getId().equals(replaceId)) {
            continue;
         }

         if (other.getEffectiveWidth() <= 0) {
            continue;
         }

         int oMinX = other.getOrigin().getX() + other.getCachedMinX();
         int oMaxX = other.getOrigin().getX() + other.getCachedMaxX() + 1;
         int oMinZ = other.getOrigin().getZ() + other.getCachedMinZ();
         int oMaxZ = other.getOrigin().getZ() + other.getCachedMaxZ() + 1;
         if (maxX > oMinX && minX < oMaxX && maxZ > oMinZ && minZ < oMaxZ) {
            return true;
         }
      }

      for (ControlledQueuedProject q : village.getControlledQueue()) {
         ControlledProjectsService.PlannedFootprint fp = ControlledProjectsService.resolvePlannedFootprint(
            q.planSetId(), q.variant(), q.level(), q.plannedLocation()
         );
         if (fp != null
            && maxX > fp.bounds().minX()
            && minX < fp.bounds().maxX()
            && maxZ > fp.bounds().minZ()
            && minZ < fp.bounds().maxZ()) {
            return true;
         }
      }

      return false;
   }

   private static int countPurpose(Village village, BuildingPurpose purpose, @Nullable BuildingId replaceId) {
      int count = 0;
      for (BuildingInstance b : village.getBuildings()) {
         if (replaceId != null && b.getId().equals(replaceId)) {
            continue;
         }

         if (b.isPlayerDesignated() && purpose.equals(b.getPurpose())) {
            count++;
         }
      }

      return count;
   }

   private static boolean isStandable(ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos)) {
         return false;
      }

      BlockState feet = level.getBlockState(pos);
      BlockState head = level.getBlockState(pos.above());
      BlockState ground = level.getBlockState(pos.below());
      return feet.getCollisionShape(level, pos).isEmpty()
         && head.getCollisionShape(level, pos.above()).isEmpty()
         && !ground.getCollisionShape(level, pos.below()).isEmpty();
   }
}
