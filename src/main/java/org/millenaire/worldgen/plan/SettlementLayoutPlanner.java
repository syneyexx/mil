package org.millenaire.worldgen.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.WallType;
import org.millenaire.world.PlacementConstraints;
import org.millenaire.world.TerrainPreparer;
import org.millenaire.world.TerrainReachability;
import org.millenaire.world.VillageTerrainMap;
import org.millenaire.world.VillageWallGenerator;
import org.millenaire.worldgen.content.VillageTypeWorldgenEntry;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;

/**
 * Stage A continued — deterministic building/road/wall/hamlet layout. Mutates plan records only.
 */
public final class SettlementLayoutPlanner {
   private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

   private SettlementLayoutPlanner() {
   }

   public static boolean ensureLayout(ServerLevel level, SettlementPlan plan) {
      if (plan.status().isTerminalFailure() || plan.status() == SettlementPlanStatus.ACTIVE) {
         return false;
      }
      if (!plan.buildings().isEmpty() && plan.status().ordinal() >= SettlementPlanStatus.RESERVED.ordinal()) {
         return true;
      }
      if (plan.villageTypeId() == null || "none".equals(plan.villageTypeId().getPath())) {
         return false;
      }

      WorldgenContentSnapshot snap = WorldgenContentSnapshot.require();
      VillageTypeWorldgenEntry entry = snap.get(plan.villageTypeId());
      VillageType villageType = ModCultures.getVillageType(plan.villageTypeId());
      if (entry == null || villageType == null) {
         plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, "village_type_missing");
         return false;
      }

      RandomSource layoutRng = WorldgenSeeds.stream(plan.worldSeed(), WorldgenSeeds.SALT_LAYOUT, plan.region());
      RandomSource variantRng = WorldgenSeeds.stream(plan.worldSeed(), WorldgenSeeds.SALT_VARIANT, plan.region());

      VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, plan.center(), villageType.radius());
      TerrainReachability reachability = TerrainReachability.compute(terrainMap, plan.center());

      plan.buildings().clear();
      List<BuildingInstance> existing = new ArrayList<>();

      VillageTypeWorldgenEntry.LayoutSlotRef centre = null;
      List<VillageTypeWorldgenEntry.LayoutSlotRef> starters = new ArrayList<>();
      for (VillageTypeWorldgenEntry.LayoutSlotRef slot : entry.layout()) {
         if (slot.isCentre()) {
            centre = slot;
         } else if (slot.isStart()) {
            starters.add(slot);
         }
      }
      if (centre == null && !plan.loneBuilding()) {
         plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, "no_centre");
         return false;
      }
      if (centre == null && plan.loneBuilding() && !entry.layout().isEmpty()) {
         centre = entry.layout().getFirst();
         starters = List.of();
      }
      if (centre == null) {
         plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, "empty_layout");
         return false;
      }

      // Deterministic starter order (no Collections.shuffle)
      starters = new ArrayList<>(starters);
      starters.sort((a, b) -> {
         int c = Integer.compare(a.planRef().hashCode(), b.planRef().hashCode());
         if (c != 0) {
            return c;
         }
         return a.role().compareTo(b.role());
      });
      // Stable Fisher-Yates with layoutRng
      for (int i = starters.size() - 1; i > 0; i--) {
         int j = layoutRng.nextInt(i + 1);
         VillageTypeWorldgenEntry.LayoutSlotRef tmp = starters.get(i);
         starters.set(i, starters.get(j));
         starters.set(j, tmp);
      }

      PlannedBuilding centreBuilding = placeCentre(level, plan, centre, terrainMap, reachability, existing, variantRng);
      if (centreBuilding == null) {
         plan.markFailed(SettlementFailureReason.INVALID_TERRAIN, "centre_unreachable");
         return false;
      }
      plan.buildings().add(centreBuilding);
      existing.add(
         new BuildingInstance(
            BuildingId.random(),
            centreBuilding.planId(),
            centreBuilding.origin(),
            centreBuilding.rotation(),
            BuildingInstance.Status.PLANNED
         )
      );
      reachability = TerrainReachability.compute(terrainMap, plan.center());

      for (VillageTypeWorldgenEntry.LayoutSlotRef slot : starters) {
         PlannedBuilding placed = placeSlot(level, plan, slot, villageType, terrainMap, reachability, existing, layoutRng, variantRng);
         if (placed == null) {
            if (slot.mandatory()) {
               plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, String.valueOf(slot.planRef()));
               return false;
            }
            continue;
         }
         plan.buildings().add(placed);
         existing.add(
            new BuildingInstance(BuildingId.random(), placed.planId(), placed.origin(), placed.rotation(), BuildingInstance.Status.PLANNED)
         );
         reachability = TerrainReachability.compute(terrainMap, plan.center());
      }

      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
         planWalls(level, plan, villageType, terrainMap, reachability);
      }
      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())
         && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.buildPaths.get())) {
         planRoads(plan, layoutRng);
      }
      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.generateHamlets.get()) && !entry.hamlets().isEmpty()) {
         planHamlets(plan, entry, layoutRng);
      }

      plan.reserveFootprintChunks();
      if (!plan.trySetStatus(SettlementPlanStatus.RESERVED) && plan.status() == SettlementPlanStatus.PLANNED) {
         plan.forceStatus(SettlementPlanStatus.RESERVED);
      }
      SettlementPlanSavedData.get(level).reindexChunks(plan);
      SettlementPlanSavedData.get(level).markDirtyPlan();
      return true;
   }

   @Nullable
   private static PlannedBuilding placeCentre(
      ServerLevel level,
      SettlementPlan plan,
      VillageTypeWorldgenEntry.LayoutSlotRef centre,
      VillageTerrainMap terrainMap,
      TerrainReachability reachability,
      List<BuildingInstance> existing,
      RandomSource variantRng
   ) {
      BuildingPlan planData = resolvePlan(centre, variantRng);
      if (planData == null) {
         return null;
      }
      BlockPos requested = plan.center();
      Rotation rotation = plan.orientation();
      if (centre.offset() != null) {
         requested = plan.center().offset(centre.offset());
         if (centre.rotation() != null) {
            rotation = centre.rotation();
         }
      }
      ClearMargins margins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
      if (centre.planSetId() != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(centre.planSetId());
         if (set != null) {
            margins = set.clearMargins().atLeast(centre.clearMargin());
         }
      }
      if (!reachability.isFootprintReachable(requested.getX(), requested.getZ(), planData.width(), planData.depth(), rotation)) {
         return null;
      }
      int baseY = terrainMap.computeAverageAltitude(requested.getX(), requested.getZ(), planData.width(), planData.depth(), margins, rotation);
      int placementY = baseY + planData.groundLevel();
      BlockPos origin = new BlockPos(requested.getX(), placementY, requested.getZ());
      terrainMap.markBuildingFootprint(requested.getX(), requested.getZ(), planData.width(), planData.depth(), margins, rotation, baseY);
      UUID buildingId = deterministicBuildingId(plan, centre.planRef(), 0);
      return new PlannedBuilding(
         buildingId,
         centre.planSetId(),
         planData.id(),
         centre.variant(),
         0,
         centre.role(),
         origin,
         rotation,
         planData.width(),
         planData.depth(),
         planData.groundLevel()
      );
   }

   @Nullable
   private static PlannedBuilding placeSlot(
      ServerLevel level,
      SettlementPlan plan,
      VillageTypeWorldgenEntry.LayoutSlotRef slot,
      VillageType villageType,
      VillageTerrainMap terrainMap,
      TerrainReachability reachability,
      List<BuildingInstance> existing,
      RandomSource layoutRng,
      RandomSource variantRng
   ) {
      BuildingPlan planData = resolvePlan(slot, variantRng);
      if (planData == null) {
         return null;
      }
      BlockPos requested;
      Rotation rotation;
      if (slot.offset() != null) {
         requested = plan.center().offset(slot.offset());
         rotation = slot.rotation() != null ? slot.rotation() : Rotation.NONE;
      } else {
         // Deterministic ring placement — avoids BuildingLocationFinder's ThreadLocalRandom.
         int minDist = 12;
         int maxDist = Math.max(minDist + 4, villageType.radius() - 8);
         if (slot.minDistance() >= 0) {
            minDist = (int)slot.minDistance();
         }
         if (slot.maxDistance() >= 0) {
            maxDist = (int)slot.maxDistance();
         }
         BlockPos found = null;
         Rotation foundRot = ROTATIONS[layoutRng.nextInt(ROTATIONS.length)];
         for (int attempt = 0; attempt < 48; attempt++) {
            double angle = layoutRng.nextDouble() * Math.PI * 2.0;
            int dist = minDist + (maxDist > minDist ? layoutRng.nextInt(maxDist - minDist + 1) : 0);
            int x = plan.center().getX() + (int)Math.round(Math.cos(angle) * dist);
            int z = plan.center().getZ() + (int)Math.round(Math.sin(angle) * dist);
            Rotation tryRot = ROTATIONS[(foundRot.ordinal() + attempt) % ROTATIONS.length];
            if (!reachability.isFootprintReachable(x, z, planData.width(), planData.depth(), tryRot)) {
               continue;
            }
            boolean overlaps = false;
            for (BuildingInstance ex : existing) {
               BuildingPlan ep = ModCultures.getBuildingPlan(ex.getPlanId());
               if (ep == null) {
                  continue;
               }
               int margin = PlacementConstraints.getDefaultClearMargin() + Math.max(0, slot.clearMargin());
               if (footprintsOverlap(
                  x, z, planData.width(), planData.depth(), tryRot,
                  ex.getOrigin().getX(), ex.getOrigin().getZ(), ep.width(), ep.depth(), ex.getRotation(), margin
               )) {
                  overlaps = true;
                  break;
               }
            }
            if (!overlaps) {
               found = new BlockPos(x, plan.center().getY(), z);
               foundRot = tryRot;
               break;
            }
         }
         if (found == null) {
            return null;
         }
         requested = found;
         rotation = foundRot;
      }
      ClearMargins margins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
      if (slot.planSetId() != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(slot.planSetId());
         if (set != null) {
            margins = set.clearMargins().atLeast(slot.clearMargin());
         }
      }
      int baseY = terrainMap.computeAverageAltitude(requested.getX(), requested.getZ(), planData.width(), planData.depth(), margins, rotation);
      int placementY = baseY + planData.groundLevel();
      BlockPos origin = new BlockPos(requested.getX(), placementY, requested.getZ());
      terrainMap.markBuildingFootprint(requested.getX(), requested.getZ(), planData.width(), planData.depth(), margins, rotation, baseY);
      UUID buildingId = deterministicBuildingId(plan, slot.planRef(), plan.buildings().size() + 1);
      return new PlannedBuilding(
         buildingId,
         slot.planSetId(),
         planData.id(),
         slot.variant(),
         0,
         slot.role(),
         origin,
         rotation,
         planData.width(),
         planData.depth(),
         planData.groundLevel()
      );
   }

   private static boolean footprintsOverlap(
      int ax, int az, int aw, int ad, Rotation aRot,
      int bx, int bz, int bw, int bd, Rotation bRot,
      int margin
   ) {
      int aEffW = TerrainPreparer.effectiveWidth(aw, ad, aRot);
      int aEffD = TerrainPreparer.effectiveDepth(aw, ad, aRot);
      int bEffW = TerrainPreparer.effectiveWidth(bw, bd, bRot);
      int bEffD = TerrainPreparer.effectiveDepth(bw, bd, bRot);
      int aMinX = ax - margin;
      int aMaxX = ax + aEffW + margin;
      int aMinZ = az - margin;
      int aMaxZ = az + aEffD + margin;
      int bMinX = bx - margin;
      int bMaxX = bx + bEffW + margin;
      int bMinZ = bz - margin;
      int bMaxZ = bz + bEffD + margin;
      return aMinX <= bMaxX && aMaxX >= bMinX && aMinZ <= bMaxZ && aMaxZ >= bMinZ;
   }

   @Nullable
   private static BuildingPlan resolvePlan(VillageTypeWorldgenEntry.LayoutSlotRef slot, RandomSource variantRng) {
      if (slot.planSetId() != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(slot.planSetId());
         if (set != null) {
            String variant = WorldgenContentSnapshot.pickVariant(set, variantRng);
            BuildingPlanSet.LevelDef level0 = set.getLevel(variant, 0);
            if (level0 != null) {
               return ModCultures.getBuildingPlan(level0.planId());
            }
         }
      }
      if (slot.planId() != null) {
         return ModCultures.getBuildingPlan(slot.planId());
      }
      return ModCultures.getBuildingPlan(slot.planRef());
   }

   private static UUID deterministicBuildingId(SettlementPlan plan, ResourceLocation planRef, int index) {
      long a = WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_LAYOUT, plan.region().packed(), planRef.hashCode());
      long b = WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_VARIANT, index, plan.planId().getLeastSignificantBits());
      return new UUID(a, b);
   }

   private static void planRoads(SettlementPlan plan, RandomSource roadRng) {
      plan.roads().clear();
      if (plan.buildings().size() < 2) {
         return;
      }
      PlannedBuilding centre = plan.buildings().getFirst();
      for (int i = 1; i < plan.buildings().size(); i++) {
         PlannedBuilding other = plan.buildings().get(i);
         List<BlockPos> waypoints = straightWaypoints(centre.origin(), other.origin(), roadRng);
         UUID roadId = new UUID(
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_ROAD, centre.buildingId().getMostSignificantBits(), other.buildingId().getMostSignificantBits()),
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_ROAD, i, plan.region().packed())
         );
         plan.roads().add(new PlannedRoad(roadId, centre.buildingId(), other.buildingId(), waypoints));
      }
   }

   private static List<BlockPos> straightWaypoints(BlockPos from, BlockPos to, RandomSource rng) {
      List<BlockPos> pts = new ArrayList<>();
      int steps = Math.max(2, (int)Math.ceil(Math.sqrt(from.distSqr(to)) / 4.0));
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         int x = (int)Math.round(from.getX() + (to.getX() - from.getX()) * t);
         int z = (int)Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
         int y = Math.max(from.getY(), to.getY());
         // slight lateral jitter disabled for determinism of connectivity; rng consumed for salt separation
         if (rng.nextBoolean() && rng.nextBoolean()) {
            // no-op consume for stream separation
         }
         pts.add(new BlockPos(x, y, z));
      }
      return pts;
   }

   private static void planWalls(
      ServerLevel level,
      SettlementPlan plan,
      VillageType villageType,
      VillageTerrainMap terrainMap,
      TerrainReachability reachability
   ) {
      plan.walls().clear();
      VillageWallGenerator wallGenerator = new VillageWallGenerator(level);
      RandomSource wallRng = WorldgenSeeds.stream(plan.worldSeed(), WorldgenSeeds.SALT_WALL, plan.region());
      addWallLayer(plan, villageType, villageType.innerWallType(), villageType.innerWallRadius(), wallGenerator, terrainMap, reachability, wallRng);
      addWallLayer(plan, villageType, villageType.outerWallType(), 0, wallGenerator, terrainMap, reachability, wallRng);
   }

   private static void addWallLayer(
      SettlementPlan plan,
      VillageType villageType,
      @Nullable ResourceLocation wallTypeId,
      int radiusOverride,
      VillageWallGenerator wallGenerator,
      VillageTerrainMap terrainMap,
      TerrainReachability reachability,
      RandomSource wallRng
   ) {
      if (wallTypeId == null) {
         return;
      }
      WallType wall = ModCultures.getWallType(wallTypeId);
      if (wall == null) {
         return;
      }
      VillageWallGenerator.WallLocationResult result = wallGenerator.computeWallBuildingLocations(
         villageType, wall, radiusOverride, terrainMap, reachability, plan.center()
      );
      int idx = 0;
      for (VillageWallGenerator.PlannedWallSegment seg : result.segments()) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(seg.planId());
         BuildingPlan planData = null;
         ResourceLocation planSetId = null;
         if (set != null) {
            planSetId = set.id();
            String variant = WorldgenContentSnapshot.pickVariant(set, wallRng);
            BuildingPlanSet.LevelDef level0 = set.getLevel(variant, 0);
            if (level0 != null) {
               planData = ModCultures.getBuildingPlan(level0.planId());
            }
         }
         if (planData == null) {
            planData = ModCultures.getBuildingPlan(seg.planId());
         }
         if (planData == null) {
            idx++;
            continue;
         }
         Rotation rotation = switch (Math.floorMod(seg.orientation(), 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
         };
         UUID segId = new UUID(
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_WALL, wallTypeId.hashCode(), idx),
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_WALL, plan.region().packed(), seg.pos().asLong())
         );
         boolean gate = seg.planId().getPath().contains("gate") || seg.planId().getPath().contains("porte");
         plan.walls()
            .add(
               new PlannedWall(
                  segId,
                  wallTypeId,
                  planSetId,
                  planData.id(),
                  seg.pos(),
                  rotation,
                  gate,
                  planData.width(),
                  planData.depth()
               )
            );
         idx++;
      }
   }

   private static void planHamlets(SettlementPlan plan, VillageTypeWorldgenEntry entry, RandomSource hamletRng) {
      plan.hamlets().clear();
      int idx = 0;
      for (ResourceLocation hamletTypeId : entry.hamlets()) {
         double angle = hamletRng.nextDouble() * Math.PI * 2.0;
         int dist = entry.radius() + 40 + hamletRng.nextInt(40);
         int hx = plan.center().getX() + (int)Math.round(Math.cos(angle) * dist);
         int hz = plan.center().getZ() + (int)Math.round(Math.sin(angle) * dist);
         UUID hamletId = new UUID(
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_HAMLET, plan.planId().getMostSignificantBits(), hamletTypeId.hashCode()),
            WorldgenSeeds.mix(plan.worldSeed(), WorldgenSeeds.SALT_HAMLET, idx, plan.region().packed())
         );
         plan.hamlets().add(new PlannedHamlet(hamletId, hamletTypeId, new BlockPos(hx, plan.center().getY(), hz), plan.planId()));
         idx++;
      }
   }
}
