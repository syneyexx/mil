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
import org.millenaire.world.StructureAvoidance;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Context-aware placement permission for roads and bridges.
 * A loaded chunk and the absence of a block entity are never sufficient proof to overwrite.
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
      BLOCKED_UNKNOWN
   }

   public record Result(Decision decision, String reason) {
      public boolean allowed() {
         return this.decision == Decision.ALLOW || this.decision == Decision.SKIP_ALREADY_PATH;
      }

      public boolean alreadyPath() {
         return this.decision == Decision.SKIP_ALREADY_PATH;
      }

      public static Result allow() {
         return new Result(Decision.ALLOW, "");
      }

      public static Result already() {
         return new Result(Decision.SKIP_ALREADY_PATH, "existing_path");
      }

      public static Result blocked(Decision d, String reason) {
         return new Result(d, reason == null ? "" : reason);
      }
   }

   private static final int FOOTPRINT_PAD = 1;
   private static final int MAX_VILLAGE_SCAN = 24;

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
      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.STRUCTURE_BLOCK)) {
         return Result.blocked(Decision.BLOCKED_PROTECTED, "protected_block");
      }
      BlockEntity be = level.getBlockEntity(pos);
      if (be != null) {
         return Result.blocked(Decision.BLOCKED_PROTECTED, "block_entity");
      }
      if (isRegisteredPath(state) || (desiredPath != null && BridgePlacementHelper.statesMatch(state, desiredPath))) {
         if (intent == Intent.PATH_SURFACE || intent == Intent.CONNECT_EXISTING_PATH || intent == Intent.BRIDGE_DECK) {
            return Result.already();
         }
      }
      if (intent == Intent.CLEAR_PASSAGE) {
         if (state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
            return Result.allow();
         }
         return Result.blocked(Decision.BLOCKED_MATERIAL, "passage_blocked");
      }
      if (intersectsVillageBuilding(level, pos)) {
         return Result.blocked(Decision.BLOCKED_BUILDING, "village_building");
      }
      if (intersectsPlannedBuilding(level, pos, owningPlan)) {
         return Result.blocked(Decision.BLOCKED_BUILDING, "planned_building");
      }
      if (StructureAvoidance.hasConflict(level, pos, 4)) {
         return Result.blocked(Decision.BLOCKED_STRUCTURE, "vanilla_structure");
      }
      return switch (intent) {
         case PATH_SURFACE, PATH_SIDE, CONNECT_EXISTING_PATH -> evaluatePathSurface(state);
         case BRIDGE_SUPPORT -> evaluateBridgeSupport(state);
         case BRIDGE_DECK -> evaluateBridgeDeck(state);
         case CLEAR_PASSAGE -> Result.blocked(Decision.BLOCKED_UNKNOWN, "unreachable");
      };
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
      if (isBuiltMaterial(state)) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "built_material");
      }
      // Unknown / ambiguous — conservative deny.
      return Result.blocked(Decision.BLOCKED_UNKNOWN, "uncertain_surface");
   }

   private static Result evaluateBridgeSupport(BlockState state) {
      if (!state.getFluidState().isEmpty()) {
         return Result.allow();
      }
      if (state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
         return Result.allow();
      }
      if (state.canOcclude() && isNaturalTerrain(state)) {
         // Solid natural ground ends the support column.
         return Result.already();
      }
      if (isBuiltMaterial(state)) {
         return Result.blocked(Decision.BLOCKED_MATERIAL, "support_built_material");
      }
      return Result.blocked(Decision.BLOCKED_UNKNOWN, "support_uncertain");
   }

   private static Result evaluateBridgeDeck(BlockState state) {
      if (!state.getFluidState().isEmpty() || state.isAir() || state.canBeReplaced() || isClearableVegetation(state)) {
         return Result.allow();
      }
      if (isRegisteredPath(state) || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.STONE)) {
         return Result.already();
      }
      if (isBuiltMaterial(state)) {
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
      if (isNaturalTerrain(state) || isClearableVegetation(state) || isRegisteredPath(state)) {
         return false;
      }
      if (!state.getFluidState().isEmpty()) {
         return false;
      }
      // Worked building materials — never treat as free terrain.
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

   public static boolean isRegisteredPath(BlockState state) {
      return state.is(Blocks.DIRT_PATH)
         || state.is(Blocks.GRAVEL)
         || state.is(Blocks.COBBLESTONE)
         || state.is(Blocks.MOSSY_COBBLESTONE)
         || state.is(Blocks.STONE_BRICKS)
         || state.is(Blocks.SANDSTONE)
         || state.is(Blocks.PACKED_MUD)
         || state.is(Blocks.OAK_PLANKS);
   }

   private static boolean intersectsVillageBuilding(ServerLevel level, BlockPos pos) {
      List<Village> villages = VillageSavedData.get(level).getVillageManager().getAllVillages();
      int scanned = 0;
      for (Village village : villages) {
         if (++scanned > MAX_VILLAGE_SCAN) {
            break;
         }
         long dx = (long)village.getCenter().getX() - pos.getX();
         long dz = (long)village.getCenter().getZ() - pos.getZ();
         int radius = Math.max(48, village.getBuildings().size() * 8);
         if (dx * dx + dz * dz > (long)radius * radius) {
            continue;
         }
         for (BuildingInstance building : village.getBuildings()) {
            if (building.containsPos(pos)) {
               return true;
            }
            // Soft pad: test neighbouring columns against the footprint cache.
            for (int ox = -FOOTPRINT_PAD; ox <= FOOTPRINT_PAD; ox++) {
               for (int oz = -FOOTPRINT_PAD; oz <= FOOTPRINT_PAD; oz++) {
                  if (ox == 0 && oz == 0) {
                     continue;
                  }
                  if (building.containsPos(pos.offset(ox, 0, oz))) {
                     return true;
                  }
               }
            }
         }
      }
      return false;
   }

   private static boolean intersectsPlannedBuilding(ServerLevel level, BlockPos pos, @Nullable SettlementPlan owningPlan) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      ChunkPos chunk = new ChunkPos(pos);
      Set<SettlementPlan> candidates = new HashSet<>();
      for (SettlementPlan plan : store.plansTouchingChunk(chunk)) {
         candidates.add(plan);
      }
      if (owningPlan != null) {
         candidates.add(owningPlan);
      }
      int scanned = 0;
      for (SettlementPlan plan : candidates) {
         if (++scanned > 8) {
            break;
         }
         for (PlannedBuilding building : plan.buildings()) {
            if (!building.intersectsChunk(chunk)) {
               continue;
            }
            BlockPos origin = building.origin();
            int minX = origin.getX() - FOOTPRINT_PAD;
            int maxX = origin.getX() + Math.max(building.width(), 1) + FOOTPRINT_PAD - 1;
            int minZ = origin.getZ() - FOOTPRINT_PAD;
            int maxZ = origin.getZ() + Math.max(building.depth(), 1) + FOOTPRINT_PAD - 1;
            if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
               return true;
            }
         }
      }
      return false;
   }

   public static int maxStepRise() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.worldgenMaximumTerrainModification.getAsInt() >= 0 ? 2 : 2);
      } catch (IllegalStateException e) {
         return 2;
      }
   }
}
