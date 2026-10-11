package org.millenaire.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.fml.ModList;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Places a Waystones waystone in Millenaire villages and vanilla villages when Waystones is loaded.
 */
public final class WaystoneVillagePlacer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ResourceLocation WAYSTONE_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");
   private static final ResourceLocation WAYSTONE_STYLE_ID = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");
   private static final int NEARBY_CHECK_RADIUS = 48;
   private static final int[][] OFFSETS = {
      {4, 0}, {-4, 0}, {0, 4}, {0, -4},
      {6, 0}, {-6, 0}, {0, 6}, {0, -6},
      {8, 4}, {-8, 4}, {4, 8}, {-4, -8},
      {10, 0}, {-10, 0}, {0, 10}, {0, -10},
      {8, 8}, {-8, 8}, {8, -8}, {-8, -8}
   };

   @Nullable
   private static Method placeWaystoneMethod;
   @Nullable
   private static Method getWaystoneAtMethod;
   @Nullable
   private static Method setNameMethod;
   @Nullable
   private static Method getNameMethod;
   @Nullable
   private static Class<?> mutableWaystoneClass;
   @Nullable
   private static Object defaultStyle;
   private static boolean apiResolved;
   private static boolean apiAvailable;

   private WaystoneVillagePlacer() {
   }

   public static boolean isEnabled() {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      if (cfg == null || !Boolean.TRUE.equals(cfg.placeVillageWaystones.get())) {
         return false;
      }
      return ModList.get().isLoaded("waystones");
   }

   /** Millenaire settlement activation hook. */
   public static void tryPlace(ServerLevel level, Village village) {
      if (!isEnabled() || village == null || village.getCenter() == null) {
         return;
      }
      if (village.getCultureId() != null) {
         var culture = org.millenaire.culture.ModCultures.getCulture(village.getCultureId());
         if (culture != null && culture.excludeFromTrains()) {
            return;
         }
      }
      if (village.getVillageTypeId() != null) {
         var type = org.millenaire.culture.ModCultures.getVillageType(village.getVillageTypeId());
         if (type != null && type.loneBuilding()) {
            return;
         }
      }
      String key = "millenaire:" + village.getId().uuid();
      tryPlaceAtSite(
         level,
         village.getCenter(),
         key,
         "Millenaire village '" + safeName(village) + "'",
         resolveWaystoneName(village)
      );
   }

   /**
    * Scans a loaded chunk for a vanilla village structure and places a waystone at its centre if missing.
    */
   public static void onChunkLoad(ServerLevel level, ChunkPos chunkPos) {
      if (!isEnabled() || level.dimension() != Level.OVERWORLD) {
         return;
      }
      BlockPos probe = chunkPos.getMiddleBlockPosition(level.getSeaLevel());
      StructureStart start = level.structureManager().getStructureWithPieceAt(probe, StructureTags.VILLAGE);
      if (start == null || !start.isValid()) {
         // Also probe chunk corners — village pieces may not cover the chunk middle.
         for (int dx = 0; dx <= 15; dx += 15) {
            for (int dz = 0; dz <= 15; dz += 15) {
               BlockPos corner = new BlockPos(chunkPos.getBlockX(dx), level.getSeaLevel(), chunkPos.getBlockZ(dz));
               start = level.structureManager().getStructureWithPieceAt(corner, StructureTags.VILLAGE);
               if (start != null && start.isValid()) {
                  break;
               }
            }
            if (start != null && start.isValid()) {
               break;
            }
         }
      }
      if (start == null || !start.isValid()) {
         return;
      }
      BlockPos center = start.getBoundingBox().getCenter();
      String key = "vanilla:" + SectionPos.blockToSectionCoord(center.getX()) + "," + SectionPos.blockToSectionCoord(center.getZ());
      tryPlaceAtSite(level, center, key, "vanilla village", "Village");
   }

   /**
    * Backfill Millenaire villages that were activated before waystones were enabled / Waystones was installed.
    * Also repairs incomplete (half) waystones from older placement bugs.
    */
   public static void tickBackfill(ServerLevel level) {
      if (!isEnabled()) {
         return;
      }
      WaystonePlacementSavedData data = WaystonePlacementSavedData.get(level);
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (village == null || village.getCenter() == null) {
            continue;
         }
         if (village.getVillageTypeId() != null) {
            var type = org.millenaire.culture.ModCultures.getVillageType(village.getVillageTypeId());
            if (type != null && type.loneBuilding()) {
               continue;
            }
         }
         if (village.getCultureId() != null) {
            var culture = org.millenaire.culture.ModCultures.getCulture(village.getCultureId());
            if (culture != null && culture.excludeFromTrains()) {
               continue;
            }
         }
         String key = "millenaire:" + village.getId().uuid();
         String waystoneName = resolveWaystoneName(village);
         if (repairIncompleteNearby(level, village.getCenter())) {
            data.mark(key);
            ensureNearbyWaystoneNamed(level, village.getCenter(), waystoneName);
            continue;
         }
         if (data.has(key) && hasCompleteNearbyWaystone(level, village.getCenter())) {
            ensureNearbyWaystoneNamed(level, village.getCenter(), waystoneName);
            continue;
         }
         if (data.has(key) && !hasCompleteNearbyWaystone(level, village.getCenter())) {
            data.unmark(key);
         }
         tryPlaceAtSite(
            level,
            village.getCenter(),
            key,
            "Millenaire village '" + safeName(village) + "'",
            waystoneName
         );
      }
   }

   private static void tryPlaceAtSite(ServerLevel level, BlockPos center, String key, String label, String waystoneName) {
      WaystonePlacementSavedData data = WaystonePlacementSavedData.get(level);
      if (repairIncompleteNearby(level, center)) {
         data.mark(key);
         ensureNearbyWaystoneNamed(level, center, waystoneName);
         return;
      }
      if (data.has(key)) {
         ensureNearbyWaystoneNamed(level, center, waystoneName);
         return;
      }
      if (hasCompleteNearbyWaystone(level, center)) {
         data.mark(key);
         ensureNearbyWaystoneNamed(level, center, waystoneName);
         return;
      }
      BlockPos candidate = findSpot(level, center);
      if (candidate == null) {
         LOGGER.debug("[Millenaire] No free spot for waystone ({}) near {}", label, center.toShortString());
         return;
      }
      if (!placeWaystoneBlock(level, candidate, waystoneName)) {
         return;
      }
      data.mark(key);
      LOGGER.info("[Millenaire] Placed waystone '{}' for {} at {}", waystoneName, label, candidate.toShortString());
   }

   private static boolean placeWaystoneBlock(ServerLevel level, BlockPos pos, String waystoneName) {
      resolveApi();
      if (apiAvailable && placeWaystoneMethod != null && defaultStyle != null) {
         try {
            Object result = placeWaystoneMethod.invoke(null, level, pos, defaultStyle);
            if (result instanceof Optional<?> opt) {
               if (opt.isPresent()) {
                  applyWaystoneName(opt.get(), waystoneName);
                  return true;
               }
            } else if (result != null) {
               applyWaystoneName(result, waystoneName);
               return true;
            }
         } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Millenaire] WaystonesAPI place failed, falling back to block set: {}", e.toString());
         }
      }
      boolean placed = placeDoubleBlockFallback(level, pos, WAYSTONE_BLOCK_ID);
      if (placed) {
         ensureWaystoneNamedAt(level, pos, waystoneName);
      }
      return placed;
   }

   private static boolean placeDoubleBlockFallback(ServerLevel level, BlockPos pos, ResourceLocation blockId) {
      Block waystone = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
      if (waystone == null) {
         return false;
      }
      if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.above()).canBeReplaced()) {
         return false;
      }
      BlockState base = waystone.defaultBlockState();
      BlockState lower = withHalf(base, DoubleBlockHalf.LOWER);
      BlockState upper = withHalf(base, DoubleBlockHalf.UPPER);
      boolean okLower = level.setBlock(pos, lower, 3);
      boolean okUpper = level.setBlock(pos.above(), upper, 3);
      if (okLower && okUpper) {
         tryInitWaystoneBlockEntity(level, pos);
         return true;
      }
      return false;
   }

   private static BlockState withHalf(BlockState state, DoubleBlockHalf half) {
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half);
      }
      return state;
   }

   private static void tryInitWaystoneBlockEntity(ServerLevel level, BlockPos lowerPos) {
      try {
         BlockEntity be = level.getBlockEntity(lowerPos);
         if (be == null) {
            return;
         }
         // Best-effort: Waystones registers BEs that become usable once both halves exist.
         be.setChanged();
      } catch (RuntimeException e) {
         LOGGER.debug("[Millenaire] Waystone BE init skipped: {}", e.toString());
      }
   }

   private static void resolveApi() {
      if (apiResolved) {
         return;
      }
      apiResolved = true;
      // Preferred: Waystones 1.21.1+ API (WaystoneStyle / Level).
      try {
         Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
         Class<?> styleClass = Class.forName("net.blay09.mods.waystones.api.WaystoneStyle");
         Class<?> stylesClass = Class.forName("net.blay09.mods.waystones.api.WaystoneStyles");
         Object style = null;
         try {
            Method getStyle = stylesClass.getMethod("getStyle", ResourceLocation.class);
            style = getStyle.invoke(null, WAYSTONE_STYLE_ID);
         } catch (NoSuchMethodException ignored) {
            Field defaultField = stylesClass.getField("DEFAULT");
            style = defaultField.get(null);
         }
         if (style == null) {
            throw new IllegalStateException("WaystoneStyles returned null style");
         }
         placeWaystoneMethod = api.getMethod("placeWaystone", Level.class, BlockPos.class, styleClass);
         resolveNamingHelpers(api);
         defaultStyle = style;
         apiAvailable = true;
         LOGGER.info("[Millenaire] WaystonesAPI reflection ready (WaystoneStyle)");
         return;
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] WaystoneStyle API resolve failed: {}", e.toString());
      }
      // Legacy fallback: older WaystoneType + ServerLevel signature.
      try {
         Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
         Class<?> typeClass = Class.forName("net.blay09.mods.waystones.api.WaystoneType");
         Method getType = typeClass.getMethod("get", ResourceLocation.class);
         Object type = getType.invoke(null, WAYSTONE_STYLE_ID);
         placeWaystoneMethod = api.getMethod("placeWaystone", ServerLevel.class, BlockPos.class, typeClass);
         resolveNamingHelpers(api);
         defaultStyle = type;
         apiAvailable = type != null;
         if (apiAvailable) {
            LOGGER.info("[Millenaire] WaystonesAPI reflection ready (legacy WaystoneType)");
            return;
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Legacy WaystoneType API resolve failed: {}", e.toString());
      }
      apiAvailable = false;
      LOGGER.warn("[Millenaire] WaystonesAPI not available; using double-block placement fallback");
   }

   private static void resolveNamingHelpers(Class<?> api) {
      try {
         getWaystoneAtMethod = api.getMethod("getWaystoneAt", Level.class, BlockPos.class);
      } catch (NoSuchMethodException e) {
         try {
            getWaystoneAtMethod = api.getMethod("getWaystoneAt", ServerLevel.class, BlockPos.class);
         } catch (NoSuchMethodException ignored) {
            getWaystoneAtMethod = null;
         }
      }
      try {
         Class<?> waystoneClass = Class.forName("net.blay09.mods.waystones.api.Waystone");
         getNameMethod = waystoneClass.getMethod("getName");
         mutableWaystoneClass = Class.forName("net.blay09.mods.waystones.api.MutableWaystone");
         setNameMethod = mutableWaystoneClass.getMethod("setName", Component.class);
      } catch (ReflectiveOperationException e) {
         LOGGER.debug("[Millenaire] Waystone naming helpers unavailable: {}", e.toString());
         mutableWaystoneClass = null;
         setNameMethod = null;
         getNameMethod = null;
      }
   }

   private static String resolveWaystoneName(Village village) {
      if (village == null) {
         return "Village";
      }
      String villageName = village.getVillageName();
      if (villageName != null && !villageName.isBlank()) {
         return villageName.trim();
      }
      if (village.getCultureId() != null) {
         Culture culture = ModCultures.getCulture(village.getCultureId());
         if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
            return culture.displayName().trim();
         }
         return village.getCultureId().getPath();
      }
      return "Village";
   }

   private static void ensureNearbyWaystoneNamed(ServerLevel level, BlockPos center, String waystoneName) {
      BlockPos pos = findCompleteNearbyWaystone(level, center);
      if (pos != null) {
         ensureWaystoneNamedAt(level, pos, waystoneName);
      }
   }

   private static void ensureWaystoneNamedAt(ServerLevel level, BlockPos pos, String waystoneName) {
      if (waystoneName == null || waystoneName.isBlank()) {
         return;
      }
      resolveApi();
      Object waystone = getWaystoneAt(level, pos);
      if (waystone == null && pos != null) {
         // Double-block: try lower half if we landed on upper.
         waystone = getWaystoneAt(level, pos.below());
      }
      if (waystone == null) {
         return;
      }
      if (!isBlankWaystoneName(waystone)) {
         return;
      }
      applyWaystoneName(waystone, waystoneName);
   }

   @Nullable
   private static Object getWaystoneAt(ServerLevel level, BlockPos pos) {
      if (getWaystoneAtMethod == null || level == null || pos == null) {
         return null;
      }
      try {
         Object result = getWaystoneAtMethod.invoke(null, level, pos);
         if (result instanceof Optional<?> opt) {
            return opt.orElse(null);
         }
         return result;
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] getWaystoneAt failed at {}: {}", pos.toShortString(), e.toString());
         return null;
      }
   }

   private static boolean isBlankWaystoneName(Object waystone) {
      if (waystone == null || getNameMethod == null) {
         return true;
      }
      try {
         Object name = getNameMethod.invoke(waystone);
         if (name == null) {
            return true;
         }
         if (name instanceof Component component) {
            String plain = component.getString();
            return plain == null || plain.isBlank();
         }
         String plain = name.toString();
         return plain == null || plain.isBlank();
      } catch (ReflectiveOperationException | RuntimeException e) {
         return true;
      }
   }

   private static void applyWaystoneName(Object waystone, String waystoneName) {
      if (waystone == null || waystoneName == null || waystoneName.isBlank() || setNameMethod == null) {
         return;
      }
      try {
         Object target = waystone;
         if (mutableWaystoneClass != null && !mutableWaystoneClass.isInstance(waystone)) {
            // Some API returns may already be MutableWaystone; otherwise skip.
            return;
         }
         setNameMethod.invoke(target, Component.literal(waystoneName));
         LOGGER.debug("[Millenaire] Named waystone '{}'", waystoneName);
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Failed to name waystone '{}': {}", waystoneName, e.toString());
      }
   }

   /** True only when a complete (LOWER+UPPER) waystone is nearby. */
   private static boolean hasCompleteNearbyWaystone(ServerLevel level, BlockPos center) {
      return findCompleteNearbyWaystone(level, center) != null;
   }

   /**
    * If a lone LOWER half exists nearby, place the missing UPPER half and return true.
    */
   private static boolean repairIncompleteNearby(ServerLevel level, BlockPos center) {
      BlockPos lower = findIncompleteLowerHalf(level, center);
      if (lower == null) {
         return false;
      }
      BlockState lowerState = level.getBlockState(lower);
      BlockPos upperPos = lower.above();
      if (!level.getBlockState(upperPos).canBeReplaced() && !isWaystoneBlock(level.getBlockState(upperPos))) {
         return false;
      }
      BlockState upper = withHalf(lowerState.getBlock().defaultBlockState(), DoubleBlockHalf.UPPER);
      // Preserve facing / other properties from lower when present.
      for (var prop : lowerState.getProperties()) {
         if (prop != BlockStateProperties.DOUBLE_BLOCK_HALF && upper.hasProperty(prop)) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            BlockState cast = upper.setValue((net.minecraft.world.level.block.state.properties.Property) prop, lowerState.getValue(prop));
            upper = cast;
         }
      }
      boolean ok = level.setBlock(upperPos, upper, 3);
      if (ok) {
         tryInitWaystoneBlockEntity(level, lower);
         LOGGER.info("[Millenaire] Repaired incomplete waystone at {}", lower.toShortString());
      }
      return ok;
   }

   @Nullable
   private static BlockPos findCompleteNearbyWaystone(ServerLevel level, BlockPos center) {
      Block waystone = BuiltInRegistries.BLOCK.getOptional(WAYSTONE_BLOCK_ID).orElse(null);
      if (waystone == null) {
         return null;
      }
      int r = NEARBY_CHECK_RADIUS;
      for (int dx = -r; dx <= r; dx += 4) {
         for (int dz = -r; dz <= r; dz += 4) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int dy = -4; dy <= 4; dy++) {
               BlockPos p = new BlockPos(x, y + dy, z);
               if (isCompleteWaystoneAt(level, p)) {
                  return p;
               }
            }
         }
      }
      for (String path : new String[]{"mossy_waystone", "sandy_waystone"}) {
         Block variant = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("waystones", path)).orElse(null);
         if (variant == null) {
            continue;
         }
         for (int dx = -r; dx <= r; dx += 8) {
            for (int dz = -r; dz <= r; dz += 8) {
               int x = center.getX() + dx;
               int z = center.getZ() + dz;
               int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
               BlockPos p = new BlockPos(x, y, z);
               if (isCompleteWaystoneAt(level, p) || isCompleteWaystoneAt(level, p.below())) {
                  return p;
               }
            }
         }
      }
      return null;
   }

   @Nullable
   private static BlockPos findIncompleteLowerHalf(ServerLevel level, BlockPos center) {
      int r = NEARBY_CHECK_RADIUS;
      for (int dx = -r; dx <= r; dx += 4) {
         for (int dz = -r; dz <= r; dz += 4) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int dy = -4; dy <= 4; dy++) {
               BlockPos p = new BlockPos(x, y + dy, z);
               if (isIncompleteLowerHalf(level, p)) {
                  return p;
               }
            }
         }
      }
      return null;
   }

   private static boolean isCompleteWaystoneAt(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (!isWaystoneBlock(state)) {
         return false;
      }
      if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         // Unknown shape — treat as complete if block exists.
         return true;
      }
      if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
         BlockState above = level.getBlockState(pos.above());
         return isWaystoneBlock(above)
            && (!above.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
               || above.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER);
      }
      if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
         BlockState below = level.getBlockState(pos.below());
         return isWaystoneBlock(below)
            && (!below.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
               || below.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER);
      }
      return false;
   }

   private static boolean isIncompleteLowerHalf(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (!isWaystoneBlock(state)) {
         return false;
      }
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
         && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
         return false;
      }
      BlockState above = level.getBlockState(pos.above());
      return !isWaystoneBlock(above);
   }

   private static boolean isWaystoneBlock(BlockState state) {
      if (state == null || state.isAir()) {
         return false;
      }
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      return id != null && "waystones".equals(id.getNamespace()) && id.getPath().contains("waystone");
   }

   @Nullable
   private static BlockPos findSpot(ServerLevel level, BlockPos center) {
      for (int[] oz : OFFSETS) {
         int x = center.getX() + oz[0];
         int z = center.getZ() + oz[1];
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos pos = new BlockPos(x, y, z);
         if (!level.isLoaded(pos)) {
            continue;
         }
         if (level.getBlockState(pos).canBeReplaced()
            && level.getBlockState(pos.above()).canBeReplaced()
            && level.getBlockState(pos.below()).isSolid()) {
            return pos;
         }
      }
      return null;
   }

   private static String safeName(Village village) {
      String name = village.getVillageName();
      return name == null || name.isBlank() ? village.getId().uuid().toString() : name;
   }
}

