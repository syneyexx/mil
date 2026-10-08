package org.millenaire.catalog;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.world.SiteValidator;

public final class CatalogBiomeLocator {
   private static final int SEARCH_RADIUS = 6400;
   private static final int HORIZONTAL_STEP = 32;
   private static final int VERTICAL_STEP = 64;
   private static final int DRY_LAND_RADIUS = 128;
   private static final int DRY_LAND_STEP = 8;
   private static final int SPOT_RADIUS = 96;
   private static final int SPOT_STEP = 16;
   private static final int SITE_MARGIN = 12;

   private CatalogBiomeLocator() {
   }

   public static BlockPos findCultureAnchor(ServerLevel level, ResourceLocation cultureId, BlockPos searchOrigin) {
      Predicate<Holder<Biome>> matches = biomeMatcherFor(cultureId);
      if (matches == null) {
         return searchOrigin;
      }

      Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(matches, searchOrigin, 6400, 32, 64);
      return found == null ? searchOrigin : (BlockPos)found.getFirst();
   }

   @Nullable
   public static Predicate<Holder<Biome>> biomeMatcherFor(ResourceLocation cultureId) {
      Set<TagKey<Biome>> tags = new HashSet<>();

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (cultureId.equals(vt.culture()) && vt.spawnable()) {
            tags.addAll(vt.biomeTags());
         }
      }

      return tags.isEmpty() ? null : holder -> {
         for (TagKey<Biome> tag : tags) {
            if (holder.is(tag)) {
               return true;
            }
         }

         return false;
      };
   }

   public static BlockPos snapToDryLand(ServerLevel level, int x, int z) {
      int sea = level.getSeaLevel();

      for (int r = 0; r <= 128; r += 8) {
         for (int dx = -r; dx <= r; dx += 8) {
            for (int dz = -r; dz <= r; dz += 8) {
               if (r == 0 || Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int cx = x + dx;
                  int cz = z + dz;
                  int top = level.getHeight(Types.WORLD_SURFACE, cx, cz);
                  if (top - 1 >= sea && level.getBlockState(new BlockPos(cx, top - 1, cz)).getFluidState().isEmpty()) {
                     return new BlockPos(cx, top, cz);
                  }
               }
            }
         }
      }

      return new BlockPos(x, level.getHeight(Types.WORLD_SURFACE, x, z), z);
   }

   @Nullable
   public static BlockPos findValidatedSite(ServerLevel level, BlockPos center, int siteRadius, @Nullable Predicate<Holder<Biome>> biomeMatch) {
      for (int r = 0; r <= 96; r += 16) {
         for (int dx = -r; dx <= r; dx += 16) {
            for (int dz = -r; dz <= r; dz += 16) {
               if (r == 0 || Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int cx = center.getX() + dx;
                  int cz = center.getZ() + dz;
                  int cy = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                  BlockPos siteCenter = new BlockPos(cx, cy, cz);
                  if ((biomeMatch == null || biomeInterior(level, siteCenter, siteRadius, biomeMatch)) && SiteValidator.validate(level, siteCenter, siteRadius)
                     )
                   {
                     return siteCenter;
                  }
               }
            }
         }
      }

      return null;
   }

   private static boolean biomeInterior(ServerLevel level, BlockPos center, int radius, Predicate<Holder<Biome>> match) {
      int[][] offsets = new int[][]{{0, 0}, {-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};

      for (int[] o : offsets) {
         BlockPos p = new BlockPos(center.getX() + o[0], center.getY(), center.getZ() + o[1]);
         if (!match.test(level.getBiome(p))) {
            return false;
         }
      }

      return true;
   }

   @Nullable
   public static BlockPos findBuildingSpot(ServerLevel level, BlockPos center, int footW, int footD, @Nullable ResourceLocation cultureId) {
      int siteRadius = Math.max(footW, footD) / 2 + 12;
      Predicate<Holder<Biome>> match = cultureId == null ? null : biomeMatcherFor(cultureId);
      BlockPos site = findValidatedSite(level, center, siteRadius, match);
      return site == null ? null : new BlockPos(site.getX() - footW / 2, site.getY(), site.getZ() - footD / 2);
   }
}
