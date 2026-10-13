package org.millenaire.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Optional;
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

   private WaystonePlacer() {
   }

   public static boolean isWaystonesLoaded() {
      return ModList.get().isLoaded("waystones");
   }

   public static void tryPlaceForMillenaireVillage(ServerLevel level, Village village) {
      if (!MillenaireServerConfig.SERVER.placeWaystonesInMillenaire.getAsBoolean()) {
         return;
      }
      String name = village.getVillageName() != null ? village.getVillageName() : village.getCultureId().getPath();
      if (village.isWaystonePlaced()) {
         if (tryRepairNear(level, village.getCenter(), name)) {
            return;
         }
         // Marked placed but missing/broken — allow a fresh place
         village.setWaystonePlaced(false);
      }
      if (placeNear(level, village.getCenter(), name)) {
         village.setWaystonePlaced(true);
      }
   }

   public static boolean tryPlaceAtVanilla(ServerLevel level, BlockPos center, String name) {
      if (!MillenaireServerConfig.SERVER.placeWaystonesInVanilla.getAsBoolean()) {
         return false;
      }
      return placeNear(level, center, name);
   }

   private static boolean placeNear(ServerLevel level, BlockPos center, String name) {
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

      return placeCompleteWaystone(level, waystone, spot, name);
   }

   /** Repair incomplete (lower-only) waystones near center, or rename an existing complete one. */
   private static boolean tryRepairNear(ServerLevel level, BlockPos center, String name) {
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

      applyWaystoneName(level, foundLower, name);
      scheduleNameRetry(level, foundLower, name);
      return true;
   }

   private static boolean placeCompleteWaystone(ServerLevel level, Block waystone, BlockPos spot, String name) {
      Object placed = tryPlaceViaApi(level, spot);
      if (placed != null) {
         boolean named = configurePlacedWaystone(placed, name);
         if (!named) {
            applyWaystoneName(level, spot, name);
            scheduleNameRetry(level, spot, name);
         }
         LOGGER.info("[Millenaire] Placed waystone '{}' at {} via API", name, spot.toShortString());
         return true;
      }

      BlockState lower = withHalf(waystone.defaultBlockState(), DoubleBlockHalf.LOWER);
      BlockState upper = withHalf(waystone.defaultBlockState(), DoubleBlockHalf.UPPER);
      level.setBlock(spot, lower, 3);
      level.setBlock(spot.above(), upper, 3);
      applyWaystoneName(level, spot, name);
      scheduleNameRetry(level, spot, name);
      LOGGER.info("[Millenaire] Placed waystone '{}' at {} via setBlock fallback", name, spot.toShortString());
      return true;
   }

   private static void scheduleNameRetry(ServerLevel level, BlockPos pos, String name) {
      if (name == null || name.isBlank()) {
         return;
      }
      level.getServer().execute(() -> {
         if (!applyWaystoneNameReturning(level, pos, name)) {
            LOGGER.warn("[Millenaire] Could not set waystone name '{}' at {}", name, pos.toShortString());
         }
      });
   }

   private static BlockState withHalf(BlockState state, DoubleBlockHalf half) {
      if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half);
      }
      // Soft fallback: try any enum property named "half"
      for (var prop : state.getProperties()) {
         if ("half".equals(prop.getName()) && prop.getPossibleValues().stream().anyMatch(v -> half.name().equalsIgnoreCase(String.valueOf(v)))) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            BlockState set = state.setValue((net.minecraft.world.level.block.state.properties.Property)prop, Enum.valueOf((Class)prop.getValueClass(), half.name()));
            return set;
         }
      }
      return state;
   }

   private static void applyWaystoneName(ServerLevel level, BlockPos lowerPos, String name) {
      applyWaystoneNameReturning(level, lowerPos, name);
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
         LOGGER.debug("[Millenaire] WaystonesAPI.placeWaystone failed: {}", e.toString());
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
               // Balm Identifier or similar — try constructing from namespace/path
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
         // Fallback: WaystoneType.values() / first entry matching "waystone"
         for (Method m : waystoneTypeClass.getMethods()) {
            if ("values".equals(m.getName()) && m.getParameterCount() == 0) {
               Object values = m.invoke(null);
               if (values instanceof Iterable<?> iterable) {
                  for (Object v : iterable) {
                     if (v != null && String.valueOf(v).toLowerCase().contains("waystone") && !String.valueOf(v).toLowerCase().contains("share")) {
                        return v;
                     }
                  }
               }
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.debug("[Millenaire] Could not resolve WaystoneType: {}", e.toString());
      }
      return null;
   }

   private static boolean configurePlacedWaystone(Object waystone, String name) {
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
         // WaystoneBlockEntity often exposes getWaystone() -> MutableWaystone
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
