package org.millenaire.worldgen.materialize;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.BuildingInstance;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.path.VillagePathManager;
import org.millenaire.world.StructureAvoidance;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Context-aware placement permission for roads and bridges.
 * Block material alone never proves a registered road. Incomplete protection scans never ALLOW.
 */
public final class RoadPlacementGuard {
   public enum Intent {
      PATH_SURFACE,
      PATH_SIDE,
      BRIDGE_SUPPORT,
      BRIDGE_DECK,
      CLEAR_PASSAGE,
      CONNECT_EXISTING_PATH
   }

   public enum Decision {
      ALLOW,
      SKIP_ALREADY_PATH,
      BLOCKED_PROTECTED,
      BLOCKED_BUILDING,
      BLOCKED_STRUCTURE,
      BLOCKED_MATERIAL,
      DEFERRED_UNKNOWN,
      BLOCKED_UNKNOWN
   }

   public record Result(Decision decision, String reason) {
      public boolean allowed() {
         return this.decision == Decision.ALLOW || this.decision == Decision.SKIP_ALREADY_PATH;
      }

      public boolean alreadyPath() {
         return this.decision == Decision.SKIP_ALREADY_PATH;
      }

      public boolean deferred() {
         return this.decision == Decision.DEFERRED_UNKNOWN;
      }

      public static Result allow() {
         return new Result(Decision.ALLOW, "");
      }

      public static Result already() {
         return new Result(Decision.SKIP_ALREADY_PATH, "existing_path");
      }

      public static Result deferred(String reason) {
         return new Result(Decision.DEFERRED_UNKNOWN, reason == null ? "incomplete_scan" : reason);
      }

      public static Result blocked(Decision d, String reason) {
         return new Result(d, reason == null ? "" : reason);
      }
   }

   private static final int FOOTPRINT_PAD = 1;
   private static final int MAX_NEARBY_VILLAGE_SCAN = 48;
   private static final int NEARBY_SEARCH_RADIUS = 160;

   private RoadPlacementGuard() {
   }

   public static Result evaluate(
      ServerLevel level,
      BlockPos pos,
      Intent intent,
      @Nullable SettlementPlan owningPlan,
      @Nullable BlockState desiredPath
   ) {
      if (level == null || pos == null) {
         return Result.blocked(Decision.BLOCKED_UNKNOWN, "invalid_position");
      }
      if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
         return Result.deferred("chunk_not_loaded");
      }
      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.STRUCTURE_BLOCK)) {
         return Result.blocked(Decision.BLOCKED_PROTECTED, "protected_block");
      }
      BlockEntity be = level.getBlockEntity(pos);
      if (be != null) {
         return Result.blocked(Decision.BLOCKED_PROTECTED, "block_entity");
      }

      // Protection before any "already ready" short-circuit.
      FootprintProbe villageProbe = intersectsVillageBuilding(level, pos);
      if (villageProbe == FootprintProbe.HIT) {
         if (intent == Intent.CONNECT_EXISTING_PATH && isPlannedEntranceConnection(level, pos, owningPlan)) {
            // Explicit planned access only — not a blanket floor/material exception.
         } else if (intent != Intent.CONNECT_EXISTING_PATH) {
            return Result.blocked(Decision.BLOCKED_BUILDING, "village_building");
         } else {
            return Result.blocked(Decision.BLOCKED_BUILDING, "village_building");
         }
      }
      if (villageProbe == FootprintProbe.INCOMPLETE) {
         return Result.deferred("village_scan_incomplete");
      }

      FootprintProbe plannedProbe = intersectsPlannedBuilding(level, pos, owningPlan);
      if (plannedProbe == FootprintProbe.HIT) {
         if (!(intent == Intent.CONNECT_EXISTING_PATH && isPlannedEntranceConnection(level, pos, owningPlan))) {
            return Result.blocked(Decision.BLOCKED_BUILDING, "planned_building");
         }
      }
      if (plannedProbe == FootprintProbe.INCOMPLETE) {
         return Result.deferred("planned_scan_incomplete");
      }

      if (StructureAvoidance.hasConflict(level, pos, 4)) {
         return Result.blocked(Decision.BLOCKED_STRUCTURE, "vanilla_structure");
      }

      boolean registered = isActuallyRegisteredRoad(level, pos);
      boolean matchesDesired = desiredPath != null && BridgePlacementHelper.statesMatch(state, desiredPath);

      if (intent == Intent.CLEAR_PASSAGE) {
         if (state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
            return Result.allow();
         }
         return Result.blocked(Decision.BLOCKED_MATERIAL, "passage_blocked");
      }

      if (intent == Intent.PATH_SURFACE || intent == Intent.CONNECT_EXISTING_PATH || intent == Intent.BRIDGE_DECK) {
         if (registered || (matchesDesired && intent != Intent.BRIDGE_DECK)) {
            return Result.already();
         }
         if (intent == Intent.BRIDGE_DECK && matchesDesired) {
            return Result.already();
         }
      }

      return switch (intent) {
         case PATH_SURFACE, PATH_SIDE, CONNECT_EXISTING_PATH -> evaluatePathSurface(state);
         case BRIDGE_SUPPORT -> evaluateBridgeSupport(state);
         case BRIDGE_DECK -> evaluateBridgeDeck(state, desiredPath);
         case CLEAR_PASSAGE -> Result.blocked(Decision.BLOCKED_UNKNOWN, "unreachable");
      };
   }

   private enum FootprintProbe {
      CLEAR,
      HIT,
      INCOMPLETE
   }

   private static Result evaluatePathSurface(BlockState state) {
      if (state.getFluidState().isEmpty() && isNaturalTerrain(state)) {
         return Result.allow();
      }
      if (!state.getFluidState().isEmpty()) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "water_needs_bridge");
      }
      if (isClearableVegetation(state)) {
         return Result.allow();
      }
      if (isBuiltMaterial(state) || isPathLikeMaterial(state)) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "built_material");
      }
      return Result.blocked(Decision.BLOCKED_UNKNOWN, "uncertain_surface");
   }

   private static Result evaluateBridgeSupport(BlockState state) {
      if (!state.getFluidState().isEmpty()) {
         return Result.allow();
      }
      if (state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
         return Result.allow();
      }
      // Own bridge supports use stone — never treat them as natural end-ground.
      if (state.is(Blocks.STONE)) {
         return Result.allow();
      }
      if (state.canOcclude() && isNaturalTerrain(state) && !state.is(Blocks.STONE)) {
         return Result.already();
      }
      if (isBuiltMaterial(state) || isPathLikeMaterial(state)) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "support_built_material");
      }
      return Result.blocked(Decision.BLOCKED_UNKNOWN, "support_uncertain");
   }

   private static Result evaluateBridgeDeck(BlockState state, @Nullable BlockState desiredPath) {
      if (!state.getFluidState().isEmpty() || state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
         return Result.allow();
      }
      if (desiredPath != null && BridgePlacementHelper.statesMatch(state, desiredPath)) {
         return Result.already();
      }
      // Stone supports must not count as an already-finished deck.
      if (state.is(Blocks.STONE)) {
         return Result.allow();
      }
      if (isBuiltMaterial(state) || isPathLikeMaterial(state)) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "deck_built_material");
      }
      return Result.blocked(Decision.BLOCKED_UNKNOWN, "deck_uncertain");
   }

   public static boolean isNaturalTerrain(BlockState state) {
      if (state == null || state.isAir()) {
         return false;
      }
      return state.is(Blocks.GRASS_BLOCK)
         || state.is(Blocks.DIRT)
         || state.is(Blocks.COARSE_DIRT)
         || state.is(Blocks.ROOTED_DIRT)
         || state.is(Blocks.PODZOL)
         || state.is(Blocks.MYCELIUM)
         || state.is(Blocks.SAND)
         || state.is(Blocks.RED_SAND)
         || state.is(Blocks.GRAVEL)
         || state.is(Blocks.CLAY)
         || state.is(Blocks.MUD)
         || state.is(Blocks.SNOW)
         || state.is(Blocks.SNOW_BLOCK)
         || state.is(BlockTags.BASE_STONE_OVERWORLD)
         || state.is(Blocks.DIRT_PATH)
         || state.is(Blocks.FARMLAND);
   }

   public static boolean isClearableVegetation(BlockState state) {
      return state.is(BlockTags.REPLACEABLE)
         || state.is(BlockTags.FLOWERS)
         || state.is(BlockTags.SAPLINGS)
         || state.is(Blocks.SHORT_GRASS)
         || state.is(Blocks.TALL_GRASS)
         || state.is(Blocks.FERN)
         || state.is(Blocks.LARGE_FERN)
         || state.is(Blocks.DEAD_BUSH)
         || state.is(Blocks.SNOW);
   }

   public static boolean isBuiltMaterial(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
         return false;
      }
      if (isNaturalTerrain(state) || isClearableVegetation(state)) {
         return false;
      }
      if (!state.getFluidState().isEmpty()) {
         return false;
      }
      return state.is(BlockTags.PLANKS)
         || state.is(BlockTags.LOGS)
         || state.is(BlockTags.DOORS)
         || state.is(BlockTags.FENCES)
         || state.is(BlockTags.WALLS)
         || state.is(BlockTags.STAIRS)
         || state.is(BlockTags.SLABS)
         || state.is(BlockTags.WOOL)
         || state.is(Blocks.GLASS)
         || state.is(Blocks.GLASS_PANE)
         || state.is(BlockTags.IMPERMEABLE)
         || state.is(Blocks.BRICKS)
         || state.is(Blocks.STONE_BRICKS)
         || state.is(Blocks.MOSSY_STONE_BRICKS)
         || state.is(Blocks.CRACKED_STONE_BRICKS)
         || state.is(Blocks.CUT_SANDSTONE)
         || state.is(Blocks.SMOOTH_SANDSTONE)
         || state.is(Blocks.CUT_RED_SANDSTONE)
         || state.is(Blocks.TERRACOTTA)
         || state.is(BlockTags.TERRACOTTA)
         || state.is(Blocks.BOOKSHELF)
         || state.is(Blocks.LADDER)
         || state.is(Blocks.TORCH)
         || state.is(Blocks.WALL_TORCH)
         || state.is(Blocks.CRAFTING_TABLE)
         || state.is(Blocks.FURNACE)
         || state.is(Blocks.SMOKER)
         || state.is(Blocks.BLAST_FURNACE)
         || state.is(Blocks.CHEST)
         || state.is(Blocks.TRAPPED_CHEST)
         || state.is(Blocks.BARREL)
         || state.is(Blocks.HOPPER)
         || state.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("millenaire");
   }

   /** Path-like materials — not ownership proof. */
   public static boolean isPathLikeMaterial(BlockState state) {
      return state.is(Blocks.DIRT_PATH)
         || state.is(Blocks.GRAVEL)
         || state.is(Blocks.COBBLESTONE)
         || state.is(Blocks.MOSSY_COBBLESTONE)
         || state.is(Blocks.STONE_BRICKS)
         || state.is(Blocks.SANDSTONE)
         || state.is(Blocks.PACKED_MUD)
         || state.is(Blocks.OAK_PLANKS);
   }

   /**
    * @deprecated Material alone is not registration proof. Prefer {@link #isActuallyRegisteredRoad}.
    */
   @Deprecated
   public static boolean isRegisteredPath(BlockState state) {
      return isPathLikeMaterial(state);
   }

   public static boolean isActuallyRegisteredRoad(ServerLevel level, BlockPos pos) {
      if (level == null || pos == null) {
         return false;
      }
      if (RegisteredRoadIndex.isRegistered(level, pos)) {
         return true;
      }
      List<Village> villages = VillageSavedData.get(level).getVillageManager().getAllVillages();
      long radiusSq = (long)NEARBY_SEARCH_RADIUS * NEARBY_SEARCH_RADIUS;
      for (Village village : villages) {
         if (village.getCenter() == null) {
            continue;
         }
         long dx = (long)village.getCenter().getX() - pos.getX();
         long dz = (long)village.getCenter().getZ() - pos.getZ();
         if (dx * dx + dz * dz > radiusSq) {
            continue;
         }
         VillagePathManager paths = village.getPathManager();
         if (paths == null) {
            continue;
         }
         boolean[] hit = {false};
         paths.forEachPath((pathPos, levelId) -> {
            if (!hit[0] && pathPos.getX() == pos.getX() && pathPos.getZ() == pos.getZ()
               && Math.abs(pathPos.getY() - pos.getY()) <= 1) {
               hit[0] = true;
            }
         });
         if (hit[0]) {
            return true;
         }
      }
      return false;
   }

   private static boolean isPlannedEntranceConnection(ServerLevel level, BlockPos pos, @Nullable SettlementPlan owningPlan) {
      if (owningPlan == null) {
         return false;
      }
      for (PlannedBuilding building : owningPlan.buildings()) {
         // Gate / entrance roles may connect via their origin column only.
         String role = building.role() == null ? "" : building.role().toLowerCase();
         if (!(role.contains("gate") || role.contains("entrance") || role.contains("access"))) {
            continue;
         }
         BlockPos origin = building.origin();
         if (origin.getX() == pos.getX() && origin.getZ() == pos.getZ()) {
            return true;
         }
      }
      return false;
   }

   private static FootprintProbe intersectsVillageBuilding(ServerLevel level, BlockPos pos) {
      List<Village> villages = VillageSavedData.get(level).getVillageManager().getAllVillages();
      int scanned = 0;
      boolean anyNearbyUnscanned = false;
      for (Village village : villages) {
         if (village.getCenter() == null) {
            continue;
         }
         long dx = (long)village.getCenter().getX() - pos.getX();
         long dz = (long)village.getCenter().getZ() - pos.getZ();
         int radius = Math.max(48, village.getBuildings().size() * 8 + 16);
         if (dx * dx + dz * dz > (long)radius * radius) {
            continue;
         }
         if (++scanned > MAX_NEARBY_VILLAGE_SCAN) {
            anyNearbyUnscanned = true;
            break;
         }
         for (BuildingInstance building : village.getBuildings()) {
            if (building.containsPos(pos)) {
               return FootprintProbe.HIT;
            }
            for (int ox = -FOOTPRINT_PAD; ox <= FOOTPRINT_PAD; ox++) {
               for (int oz = -FOOTPRINT_PAD; oz <= FOOTPRINT_PAD; oz++) {
                  if (ox == 0 && oz == 0) {
                     continue;
                  }
                  if (building.containsPos(pos.offset(ox, 0, oz))) {
                     return FootprintProbe.HIT;
                  }
               }
            }
         }
      }
      return anyNearbyUnscanned ? FootprintProbe.INCOMPLETE : FootprintProbe.CLEAR;
   }

   private static FootprintProbe intersectsPlannedBuilding(ServerLevel level, BlockPos pos, @Nullable SettlementPlan owningPlan) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      ChunkPos chunk = new ChunkPos(pos);
      Set<SettlementPlan> candidates = new HashSet<>();
      for (SettlementPlan plan : store.plansTouchingChunk(chunk)) {
         candidates.add(plan);
      }
      // Also check neighbouring chunks for footprints that cross the border.
      for (int cx = -1; cx <= 1; cx++) {
         for (int cz = -1; cz <= 1; cz++) {
            if (cx == 0 && cz == 0) {
               continue;
            }
            for (SettlementPlan plan : store.plansTouchingChunk(new ChunkPos(chunk.x + cx, chunk.z + cz))) {
               candidates.add(plan);
            }
         }
      }
      if (owningPlan != null) {
         candidates.add(owningPlan);
      }
      int scanned = 0;
      boolean incomplete = false;
      for (SettlementPlan plan : candidates) {
         if (++scanned > 32) {
            incomplete = true;
            break;
         }
         for (PlannedBuilding building : plan.buildings()) {
            if (!building.intersectsChunk(chunk) && !touchesNeighbour(building, chunk)) {
               continue;
            }
            BlockPos origin = building.origin();
            int effW = Math.max(building.effectiveWidth(), 1);
            int effD = Math.max(building.effectiveDepth(), 1);
            int minX = origin.getX() - FOOTPRINT_PAD;
            int maxX = origin.getX() + effW + FOOTPRINT_PAD - 1;
            int minZ = origin.getZ() - FOOTPRINT_PAD;
            int maxZ = origin.getZ() + effD + FOOTPRINT_PAD - 1;
            if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
               return FootprintProbe.HIT;
            }
         }
      }
      return incomplete ? FootprintProbe.INCOMPLETE : FootprintProbe.CLEAR;
   }

   private static boolean touchesNeighbour(PlannedBuilding building, ChunkPos chunk) {
      int minCx = building.origin().getX() >> 4;
      int maxCx = (building.origin().getX() + Math.max(building.effectiveWidth(), 1) - 1) >> 4;
      int minCz = building.origin().getZ() >> 4;
      int maxCz = (building.origin().getZ() + Math.max(building.effectiveDepth(), 1) - 1) >> 4;
      return chunk.x >= minCx - 1 && chunk.x <= maxCx + 1 && chunk.z >= minCz - 1 && chunk.z <= maxCz + 1;
   }

   public static int maxStepRise() {
      try {
         int configured = MillenaireServerConfig.SERVER.worldgenMaximumTerrainModification.getAsInt();
         // Authoritative slope uses the same binder as regional planner (2); config ≥0 keeps that policy.
         return Math.max(1, configured >= 0 ? 2 : 2);
      } catch (IllegalStateException e) {
         return 2;
      }
   }

   /** Pack XZ for road cell registries. */
   public static long packCell(int x, int z) {
      return ((long)x & 0xFFFFFFFFL) << 32 | ((long)z & 0xFFFFFFFFL);
   }

   public static int unpackX(long packed) {
      return (int)(packed >> 32);
   }

   public static int unpackZ(long packed) {
      return (int)packed;
   }
}
