package org.millenaire.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Soft optional integration with Blay's Waystones. Places a full double-block waystone
 * and sets its display name — no compile-time dependency.
 */
public final class WaystonePlacer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ResourceLocation WAYSTONE_ID = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");
   private static final int REPAIR_SEARCH_RADIUS = 8;
   private static final int[] NAME_RETRY_DELAYS_TICKS = {1, 5, 20};

   private WaystonePlacer() {
   }

   public static boolean isWaystonesLoaded() {
      return ModList.get().isLoaded("waystones");
   }

   /** Resolve a non-blank display name for a Millenaire village waystone. */
   public static String resolveVillageWaystoneName(Village village) {
      String name = village.getVillageName();
      if (name != null && !name.isBlank()) {
         return name.trim();
      }
      if (village.getCultureId() != null) {
         return village.getCultureId().getPath();
      }
      return "Village";
   }

   /**
    * Re-apply the village name to an existing nearby waystone (create rename, /renamevillage, load).
    * Does not place a new waystone.
    */
   public static void syncNameForVillage(ServerLevel level, Village village) {
      if (!isWaystonesLoaded() || !MillenaireServerConfig.SERVER.placeWaystonesInMillenaire.getAsBoolean()) {
         return;
      }
      if (!village.isWaystonePlaced()) {
         return;
      }
      String name = resolveVillageWaystoneName(village);
      Block waystone = BuiltInRegistries.BLOCK.get(WAYSTONE_ID);
      if (waystone == null || waystone.defaultBlockState().isAir()) {
         return;
      }
      BlockPos foundLower = findExistingLower(level, waystone, village.getCenter());
      if (foundLower == null) {
         return;
      }
      if (applyWaystoneNameReturning(level, foundLower, name)) {
         return;
      }
      scheduleNameRetries(level, foundLower, name, null);
   }

   public static void tryPlaceForMillenaireVillage(ServerLevel level, Village village) {
      if (!MillenaireServerConfig.SERVER.placeWaystonesInMillenaire.getAsBoolean()) {
         return;
      }
      String name = resolveVillageWaystoneName(village);
      if (village.isWaystonePlaced()) {
         if (tryRepairNear(level, village.getCenter(), name, village)) {
            return;
         }
         // Marked placed but missing/broken — allow a fresh place
         village.setWaystonePlaced(false);
      }
      if (placeNear(level, village.getCenter(), name, village)) {
         // waystonePlaced is set only after successful naming (or scheduled retries)
      }
   }

   public static boolean tryPlaceAtVanilla(ServerLevel level, BlockPos center, String name) {
      if (!MillenaireServerConfig.SERVER.placeWaystonesInVanilla.getAsBoolean()) {
         return false;
      }
      String resolved = name != null && !name.isBlank() ? name.trim() : "Village";
      return placeNear(level, center, resolved, null);
   }

   private static boolean placeNear(ServerLevel level, BlockPos center, String name, @Nullable Village village) {
      if (!isWaystonesLoaded()) {
         return false;
      }
      Block waystone = BuiltInRegistries.BLOCK.get(WAYSTONE_ID);
      if (waystone == null || waystone.defaultBlockState().isAir()) {
         LOGGER.debug("[Millenaire] Waystones loaded but block {} missing", WAYSTONE_ID);
         return false;
      }

      BlockPos spot = findSpot(level, center);
      if (spot == null) {
         return false;
      }

      return placeCompleteWaystone(level, waystone, spot, name, village);
   }

   /** Repair incomplete (lower-only) waystones near center, or rename an existing complete one. */
   private static boolean tryRepairNear(ServerLevel level, BlockPos center, String name, @Nullable Village village) {
      if (!isWaystonesLoaded()) {
         return false;
      }
      Block waystone = BuiltInRegistries.BLOCK.get(WAYSTONE_ID);
      if (waystone == null || waystone.defaultBlockState().isAir()) {
         return false;
      }

      BlockPos foundLower = findExistingLower(level, waystone, center);
      if (foundLower == null) {
         return false;
      }

      BlockState lowerState = level.getBlockState(foundLower);
      BlockPos upperPos = foundLower.above();
      BlockState upperState = level.getBlockState(upperPos);
      boolean needsUpper = !upperState.is(waystone)
         || upperState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && upperState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER;

      if (needsUpper) {
         BlockState upper = withHalf(waystone.defaultBlockState(), DoubleBlockHalf.UPPER);
         if (!level.getBlockState(upperPos).canBeReplaced() && !level.getBlockState(upperPos).isAir()) {
            level.destroyBlock(upperPos, false);
         }
         level.setBlock(upperPos, upper, 3);
         if (lowerState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && lowerState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
            level.setBlock(foundLower, withHalf(lowerState, DoubleBlockHalf.LOWER), 3);
         }
         LOGGER.info("[Millenaire] Repaired waystone upper half at {}", foundLower.toShortString());
      }

      if (applyWaystoneNameReturning(level, foundLower, name)) {
         if (village != null) {
            village.setWaystonePlaced(true);
         }
         return true;
      }
      scheduleNameRetries(level, foundLower, name, village);
      if (village != null) {
         village.setWaystonePlaced(true);
      }
      return true;
   }

   private static boolean placeCompleteWaystone(
      ServerLevel level, Block waystone, BlockPos spot, String name, @Nullable Village village
   ) {
      if (name == null || name.isBlank()) {
         LOGGER.warn("[Millenaire] Refusing to place waystone with blank name at {}", spot.toShortString());
         return false;
      }

      Object placed = tryPlaceViaApi(level, spot);
      if (placed != null) {
         boolean named = configurePlacedWaystone(placed, name);
         if (!named) {
            named = applyWaystoneNameReturning(level, spot, name);
         }
         if (named) {
            LOGGER.info("[Millenaire] Placed waystone '{}' at {} via API", name, spot.toShortString());
            if (village != null) {
               village.setWaystonePlaced(true);
            }
            return true;
         }
         scheduleNameRetries(level, spot, name, village);
         if (village != null) {
            village.setWaystonePlaced(true);
         }
         LOGGER.warn("[Millenaire] Placed waystone at {} via API but naming deferred for '{}'", spot.toShortString(), name);
         return true;
      }

      // setBlock fallback: physical blocks without store init often stay unnamed.
      // Prefer failing placement over leaving a blank waystone; log loudly.
      LOGGER.warn(
         "[Millenaire] WaystonesAPI.placeWaystone unavailable; using setBlock fallback at {} (name may need retries)",
         spot.toShortString()
      );
      BlockState lower = withHalf(waystone.defaultBlockState(), DoubleBlockHalf.LOWER);
      BlockState upper = withHalf(waystone.defaultBlockState(), DoubleBlockHalf.UPPER);
      level.setBlock(spot, lower, 3);
      level.setBlock(spot.above(), upper, 3);
      boolean named = applyWaystoneNameReturning(level, spot, name);
      if (named) {
         LOGGER.info("[Millenaire] Placed waystone '{}' at {} via setBlock fallback", name, spot.toShortString());
         if (village != null) {
            village.setWaystonePlaced(true);
         }
         return true;
      }
      scheduleNameRetries(level, spot, name, village);
      if (village != null) {
         village.setWaystonePlaced(true);
      }
      LOGGER.warn("[Millenaire] Placed waystone at {} via setBlock; naming deferred for '{}'", spot.toShortString(), name);
      return true;
   }

   private static void scheduleNameRetries(ServerLevel level, BlockPos pos, String name, @Nullable Village village) {
      if (name == null || name.isBlank()) {
         return;
      }
      scheduleNameRetryAt(level, pos, name, village, 0);
   }

   private static void scheduleNameRetryAt(
      ServerLevel level, BlockPos pos, String name, @Nullable Village village, int attemptIndex
   ) {
      if (attemptIndex >= NAME_RETRY_DELAYS_TICKS.length) {
         LOGGER.warn("[Millenaire] Could not set waystone name '{}' at {} after {} retries", name, pos.toShortString(), NAME_RETRY_DELAYS_TICKS.length);
         return;
      }
      int delay = Math.max(1, NAME_RETRY_DELAYS_TICKS[attemptIndex]);
      scheduleAfterTicks(level, delay, () -> {
         if (applyWaystoneNameReturning(level, pos, name)) {
            if (village != null) {
               village.setWaystonePlaced(true);
            }
            LOGGER.info("[Millenaire] Set waystone name '{}' at {} on retry {}", name, pos.toShortString(), attemptIndex + 1);
            return;
         }
         scheduleNameRetryAt(level, pos, name, village, attemptIndex + 1);
      });
   }

   /** Chain server.execute calls to approximate a short delay without a tick scheduler. */
   private static void scheduleAfterTicks(ServerLevel level, int ticks, Runnable task) {
      if (ticks <= 1) {
         level.getServer().execute(task);
         return;
      }
      level.getServer().execute(() -> scheduleAfterTicks(level, ticks - 1, task));
   }

   private static BlockState withHalf(BlockState state, DoubleBlockHalf half) {
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half);
      }
      for (var prop : state.getProperties()) {
         if ("half".equals(prop.getName()) && prop.getPossibleValues().stream().anyMatch(v -> half.name().equalsIgnoreCase(String.valueOf(v)))) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            BlockState set = state.setValue((net.minecraft.world.level.block.state.properties.Property)prop, Enum.valueOf((Class)prop.getValueClass(), half.name()));
            return set;
         }
      }
      return state;
   }

   private static boolean applyWaystoneNameReturning(ServerLevel level, BlockPos lowerPos, String name) {
      if (name == null || name.isBlank()) {
         return false;
      }
      if (tryNameViaApi(level, lowerPos, name)) {
         return true;
      }
      if (tryNameViaBlockEntity(level, lowerPos, name)) {
         return true;
      }
      return false;
   }

   @Nullable
   private static Object tryPlaceViaApi(ServerLevel level, BlockPos pos) {
      try {
         Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
         Object waystoneType = resolveDefaultWaystoneType();
         if (waystoneType == null) {
            LOGGER.warn("[Millenaire] Could not resolve WaystoneType for placement");
            return null;
         }
         for (Method m : api.getMethods()) {
            if (!"placeWaystone".equals(m.getName()) || m.getParameterCount() != 3) {
               continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (!pts[0].isInstance(level) || !BlockPos.class.isAssignableFrom(pts[1])) {
               continue;
            }
            if (!pts[2].isInstance(waystoneType)) {
               continue;
            }
            Object result = m.invoke(null, level, pos, waystoneType);
            return unwrapOptional(result);
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.warn("[Millenaire] WaystonesAPI.placeWaystone failed: {}", e.toString());
      }
      return null;
   }

   @Nullable
   private static Object resolveDefaultWaystoneType() {
      try {
         Class<?> waystoneTypeClass = Class.forName("net.blay09.mods.waystones.api.WaystoneType");
         for (Method m : waystoneTypeClass.getMethods()) {
            if (!"get".equals(m.getName()) || m.getParameterCount() != 1) {
               continue;
            }
            Class<?> pt = m.getParameterTypes()[0];
            Object idArg = null;
            if (pt == ResourceLocation.class || "net.minecraft.resources.ResourceLocation".equals(pt.getName())) {
               idArg = WAYSTONE_ID;
            } else if (pt == String.class) {
               idArg = WAYSTONE_ID.toString();
            } else {
               try {
                  Object identifier = pt.getConstructor(String.class, String.class).newInstance(WAYSTONE_ID.getNamespace(), WAYSTONE_ID.getPath());
                  idArg = identifier;
               } catch (ReflectiveOperationException ignored) {
                  try {
                     Object identifier = pt.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, WAYSTONE_ID.getNamespace(), WAYSTONE_ID.getPath());
                     idArg = identifier;
                  } catch (ReflectiveOperationException ignored2) {
                  }
               }
            }
            if (idArg == null) {
               continue;
            }
            Object type = m.invoke(null, idArg);
            if (type != null) {
               return unwrapOptional(type);
            }
         }
         // Fallback: WaystoneType.values() may return Stream or Iterable
         for (Method m : waystoneTypeClass.getMethods()) {
            if (!"values".equals(m.getName()) || m.getParameterCount() != 0) {
               continue;
            }
            Object values = m.invoke(null);
            Iterator<?> it = null;
            if (values instanceof Stream<?> stream) {
               it = stream.iterator();
            } else if (values instanceof Iterable<?> iterable) {
               it = iterable.iterator();
            } else if (values != null && values.getClass().isArray()) {
               Object[] arr = (Object[])values;
               it = java.util.Arrays.asList(arr).iterator();
            }
            if (it == null) {
               continue;
            }
            while (it.hasNext()) {
               Object v = it.next();
               if (v == null) {
                  continue;
               }
               String s = String.valueOf(v).toLowerCase();
               if (s.contains("waystone") && !s.contains("share")) {
                  return v;
               }
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Could not resolve WaystoneType: {}", e.toString());
      }
      return null;
   }

   private static boolean configurePlacedWaystone(Object waystone, String name) {
      if (name == null || name.isBlank()) {
         return false;
      }
      boolean named = setWaystoneDisplayName(waystone, name);
      setWaystoneVisibilityGlobal(waystone);
      return named;
   }

   private static void setWaystoneVisibilityGlobal(Object waystone) {
      try {
         Class<?> visibilityClass = Class.forName("net.blay09.mods.waystones.api.WaystoneVisibility");
         Object global = null;
         for (Object constant : visibilityClass.getEnumConstants()) {
            if ("GLOBAL".equals(String.valueOf(constant))) {
               global = constant;
               break;
            }
         }
         if (global == null) {
            return;
         }
         for (Method m : waystone.getClass().getMethods()) {
            if ("setVisibility".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(global)) {
               m.invoke(waystone, global);
               return;
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Could not set waystone visibility: {}", e.toString());
      }
   }

   private static boolean tryNameViaApi(ServerLevel level, BlockPos pos, String name) {
      try {
         Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
         Method getWaystoneAt = null;
         for (Method m : api.getMethods()) {
            if ("getWaystoneAt".equals(m.getName()) && m.getParameterCount() == 2) {
               getWaystoneAt = m;
               break;
            }
         }
         if (getWaystoneAt == null) {
            return false;
         }
         Object result = getWaystoneAt.invoke(null, level, pos);
         Object waystone = unwrapOptional(result);
         if (waystone == null) {
            return false;
         }
         boolean named = setWaystoneDisplayName(waystone, name);
         if (named) {
            setWaystoneVisibilityGlobal(waystone);
         }
         return named;
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] WaystonesAPI naming failed: {}", e.toString());
         return false;
      }
   }

   private static boolean tryNameViaBlockEntity(ServerLevel level, BlockPos pos, String name) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) {
         return false;
      }
      try {
         for (Method m : be.getClass().getMethods()) {
            if (("getWaystone".equals(m.getName()) || "getWaystoneData".equals(m.getName())) && m.getParameterCount() == 0) {
               Object waystone = unwrapOptional(m.invoke(be));
               if (waystone != null && setWaystoneDisplayName(waystone, name)) {
                  setWaystoneVisibilityGlobal(waystone);
                  be.setChanged();
                  return true;
               }
            }
         }
         for (Method m : be.getClass().getMethods()) {
            if ("setWaystoneName".equals(m.getName()) && m.getParameterCount() == 1) {
               Class<?> pt = m.getParameterTypes()[0];
               if (pt == String.class) {
                  m.invoke(be, name);
                  be.setChanged();
                  return true;
               }
               if (Component.class.isAssignableFrom(pt)) {
                  m.invoke(be, Component.literal(name));
                  be.setChanged();
                  return true;
               }
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Waystone BE naming failed: {}", e.toString());
      }
      return false;
   }

   private static boolean setWaystoneDisplayName(Object waystone, String name) {
      if (name == null || name.isBlank()) {
         return false;
      }
      try {
         for (Method m : waystone.getClass().getMethods()) {
            if (!"setName".equals(m.getName()) || m.getParameterCount() != 1) {
               continue;
            }
            Class<?> pt = m.getParameterTypes()[0];
            if (Component.class.isAssignableFrom(pt)) {
               m.invoke(waystone, Component.literal(name));
               return true;
            }
            if (pt == String.class) {
               m.invoke(waystone, name);
               return true;
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] MutableWaystone.setName failed: {}", e.toString());
      }
      return false;
   }

   @Nullable
   private static Object unwrapOptional(Object result) {
      if (result == null) {
         return null;
      }
      if (result instanceof Optional<?> opt) {
         return opt.orElse(null);
      }
      return result;
   }

   @Nullable
   private static BlockPos findExistingLower(ServerLevel level, Block waystone, BlockPos center) {
      int r = REPAIR_SEARCH_RADIUS;
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            for (int dy = -2; dy <= 4; dy++) {
               BlockPos p = center.offset(dx, dy, dz);
               BlockState state = level.getBlockState(p);
               if (!state.is(waystone)) {
                  continue;
               }
               if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                  && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                  continue;
               }
               return p;
            }
         }
      }
      return null;
   }

   @Nullable
   private static BlockPos findSpot(ServerLevel level, BlockPos center) {
      int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 1}, {-3, 1}, {1, 3}, {1, -3}};
      for (int[] off : offsets) {
         BlockPos base = center.offset(off[0], 0, off[1]);
         BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
         if (level.getBlockState(surface).canBeReplaced() && level.getBlockState(surface.above()).canBeReplaced()) {
            return surface;
         }
         if (level.getBlockState(surface.above()).canBeReplaced() && level.getBlockState(surface.above(2)).canBeReplaced()
            && !level.getBlockState(surface).isAir()) {
            return surface.above();
         }
      }
      BlockPos fallback = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(2, 0, 2));
      if (level.getBlockState(fallback).canBeReplaced() && level.getBlockState(fallback.above()).canBeReplaced()) {
         return fallback;
      }
      return null;
   }
}
