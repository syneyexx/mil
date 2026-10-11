package org.millenaire.compat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.registries.Registries;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Maps any overworld surface biome to Millenaire culture pools when datapack tags miss.
 */
public final class ClimateBiomeFallback {
   public static final TagKey<Biome> C_IS_PLAINS = tag("c", "is_plains");
   public static final TagKey<Biome> C_IS_FOREST = tag("c", "is_forest");
   public static final TagKey<Biome> C_IS_TAIGA = tag("c", "is_taiga");
   public static final TagKey<Biome> C_IS_JUNGLE = tag("c", "is_jungle");
   public static final TagKey<Biome> C_IS_SWAMP = tag("c", "is_swamp");
   public static final TagKey<Biome> C_IS_SAVANNA = tag("c", "is_savanna");
   public static final TagKey<Biome> C_IS_DESERT = tag("c", "is_desert");
   public static final TagKey<Biome> C_IS_BADLANDS = tag("c", "is_badlands");
   public static final TagKey<Biome> C_IS_SNOWY = tag("c", "is_snowy");
   public static final TagKey<Biome> C_IS_SNOWY_PLAINS = tag("c", "is_snowy_plains");
   public static final TagKey<Biome> C_IS_MOUNTAIN = tag("c", "is_mountain");
   public static final TagKey<Biome> C_IS_HILL = tag("c", "is_hill");
   public static final TagKey<Biome> C_IS_OCEAN = tag("c", "is_ocean");
   public static final TagKey<Biome> C_IS_RIVER = tag("c", "is_river");
   public static final TagKey<Biome> C_IS_BEACH = tag("c", "is_beach");
   public static final TagKey<Biome> C_IS_CAVE = tag("c", "is_cave");
   public static final TagKey<Biome> MILL_MEDITERRANEAN = tag("millenaire", "is_mediterranean");
   public static final TagKey<Biome> MILL_LAVENDER = tag("millenaire", "is_lavender");
   public static final TagKey<Biome> MILL_CHERRY = tag("millenaire", "is_cherry_grove");
   public static final TagKey<Biome> MILL_TEMPERATE_FOREST = tag("millenaire", "is_temperate_forest");
   public static final TagKey<Biome> MILL_MEADOW = tag("millenaire", "is_meadow");
   public static final TagKey<Biome> MILL_HOT_SPRINGS = tag("millenaire", "is_hot_springs");
   public static final TagKey<Biome> MILL_VOLCANIC = tag("millenaire", "is_volcanic");
   public static final TagKey<Biome> MILL_SETTLEMENT_SURFACE = tag("millenaire", "is_settlement_surface");

   private static final Set<String> MODPACK_NAMESPACES = Set.of(
      "biomesoplenty",
      "biomeswevegone",
      "natures_spirit",
      "byg",
      "terrablender"
   );

   private ClimateBiomeFallback() {
   }

   public enum ClimateBand {
      SNOWY,
      COLD_TAIGA,
      TEMPERATE,
      MEDITERRANEAN,
      JUNGLE,
      SWAMP,
      SAVANNA,
      DESERT,
      BADLANDS,
      EXCLUDED
   }

   public record Classification(ClimateBand band, Set<String> culturePaths, boolean surfaceEligible) {
      public static Classification excluded() {
         return new Classification(ClimateBand.EXCLUDED, Set.of(), false);
      }
   }

   public static boolean isFallbackEnabled() {
      try {
         MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
         return cfg == null || Boolean.TRUE.equals(cfg.worldgenUntaggedBiomeFallback.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static boolean isModpackBiome(Holder<Biome> biome) {
      ResourceLocation id = idOf(biome);
      return id != null && MODPACK_NAMESPACES.contains(id.getNamespace());
   }

   public static boolean isSurfaceEligible(Holder<Biome> biome) {
      if (biome == null) {
         return false;
      }
      if (biome.is(MILL_SETTLEMENT_SURFACE)) {
         return true;
      }
      if (isExcluded(biome)) {
         return false;
      }
      ResourceLocation id = idOf(biome);
      if (id == null) {
         return false;
      }
      String ns = id.getNamespace();
      if ("minecraft".equals(ns) || MODPACK_NAMESPACES.contains(ns) || "auroras".equals(ns)) {
         return true;
      }
      // Other overworld biome mods: allow unless path looks non-surface.
      return !looksNonSurface(id.getPath());
   }

   public static Classification classify(Holder<Biome> biome) {
      if (biome == null || isExcluded(biome)) {
         return Classification.excluded();
      }
      if (!isSurfaceEligible(biome)) {
         return Classification.excluded();
      }

      ResourceLocation id = idOf(biome);
      String path = id != null ? id.getPath().toLowerCase(Locale.ROOT) : "";

      if (biome.is(C_IS_SNOWY) || biome.is(C_IS_SNOWY_PLAINS) || biome.is(BiomeTags.IS_TAIGA) && path.contains("snow")
         || pathContains(path, "snow", "frozen", "ice", "glacier", "tundra", "borealis", "wintry", "frost")) {
         return cultures(ClimateBand.SNOWY, "inuits");
      }
      if (biome.is(MILL_MEDITERRANEAN) || biome.is(MILL_LAVENDER)
         || pathContains(path, "mediterranean", "lavender", "cypress", "chaparral", "olive")) {
         return cultures(ClimateBand.MEDITERRANEAN, "byzantines", "seljuk", "norman");
      }
      if (biome.is(MILL_CHERRY) || biome.is(MILL_HOT_SPRINGS)
         || pathContains(path, "sakura", "cherry", "sugi", "wisteria", "hot_spring", "onsen")) {
         return cultures(ClimateBand.TEMPERATE, "japanese", "norman");
      }
      if (biome.is(C_IS_JUNGLE) || biome.is(BiomeTags.IS_JUNGLE)
         || pathContains(path, "jungle", "rainforest", "tropic", "bamboo")) {
         return cultures(ClimateBand.JUNGLE, "mayan", "indian");
      }
      if (biome.is(C_IS_SWAMP) || pathContains(path, "swamp", "marsh", "bog", "bayou", "wetland", "mangrove")) {
         return cultures(ClimateBand.SWAMP, "mayan", "indian", "norman");
      }
      if (biome.is(C_IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS) && pathContains(path, "desert", "dune", "sandy")
         || pathContains(path, "desert", "dune", "outback", "wasteland", "arid", "xeric", "scorch")) {
         return cultures(ClimateBand.DESERT, "seljuk", "indian");
      }
      if (biome.is(C_IS_BADLANDS) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(MILL_VOLCANIC)
         || pathContains(path, "badland", "mesa", "red_rock", "volcan", "crag")) {
         return cultures(ClimateBand.BADLANDS, "seljuk", "indian");
      }
      if (biome.is(C_IS_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA)
         || pathContains(path, "savanna", "scrub", "baobab", "dryland")) {
         return cultures(ClimateBand.SAVANNA, "seljuk", "indian", "byzantines");
      }
      if (biome.is(C_IS_TAIGA) || biome.is(BiomeTags.IS_TAIGA)
         || pathContains(path, "taiga", "conifer", "fir", "spruce", "boreal", "maple_woods", "zelkova")) {
         return cultures(ClimateBand.COLD_TAIGA, "japanese", "norman", "inuits", "illagers");
      }
      if (biome.is(C_IS_FOREST) || biome.is(BiomeTags.IS_FOREST) || biome.is(MILL_TEMPERATE_FOREST)
         || pathContains(path, "forest", "wood", "grove", "thicket")) {
         return cultures(ClimateBand.TEMPERATE, "norman", "japanese", "byzantines", "gabbers", "outlawz", "illagers");
      }
      if (biome.is(C_IS_PLAINS) || biome.is(MILL_MEADOW) || biome.is(C_IS_HILL) || biome.is(C_IS_MOUNTAIN)
         || pathContains(path, "plains", "field", "meadow", "prairie", "grassland", "steppe", "highland", "orchard", "pasture")) {
         return cultures(ClimateBand.TEMPERATE, "norman", "byzantines", "gabbers", "outlawz", "illagers");
      }

      // Climate numbers when tags/names give nothing useful.
      float temp = biome.value().getBaseTemperature();
      if (temp < 0.15F) {
         return cultures(ClimateBand.SNOWY, "inuits");
      }
      if (temp < 0.4F) {
         return cultures(ClimateBand.COLD_TAIGA, "japanese", "norman", "inuits", "illagers");
      }
      if (temp > 1.1F) {
         return cultures(ClimateBand.DESERT, "seljuk", "indian");
      }
      if (temp > 0.9F) {
         return cultures(ClimateBand.SAVANNA, "seljuk", "indian", "mayan");
      }
      return cultures(ClimateBand.TEMPERATE, "norman", "byzantines", "japanese", "gabbers", "outlawz", "illagers");
   }

   private static Classification cultures(ClimateBand band, String... paths) {
      Set<String> set = new LinkedHashSet<>();
      for (String p : paths) {
         set.add(p);
      }
      return new Classification(band, Set.copyOf(set), true);
   }

   public static boolean isExcluded(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
         || biome.is(C_IS_OCEAN) || biome.is(C_IS_RIVER) || biome.is(C_IS_BEACH) || biome.is(C_IS_CAVE)) {
         return true;
      }
      ResourceLocation id = idOf(biome);
      if (id == null) {
         return true;
      }
      String path = id.getPath().toLowerCase(Locale.ROOT);
      if (looksNonSurface(path)) {
         return true;
      }
      // Explicit nether/end namespaces / vanilla dims
      return path.startsWith("nether") || path.contains("the_end") || "the_void".equals(path);
   }

   private static boolean looksNonSurface(String path) {
      return pathContains(
         path,
         "ocean",
         "river",
         "beach",
         "shore",
         "stony_shore",
         "deep_dark",
         "dripstone_cave",
         "lush_cave",
         "cave",
         "grotto",
         "nether",
         "crimson",
         "warped",
         "soul_sand",
         "basalt_delta",
         "end_",
         "the_end",
         "small_end",
         "end_barrens",
         "end_highlands",
         "end_midlands",
         "end_wilds",
         "end_reef",
         "end_corruption",
         "visceral",
         "undergrowth",
         "withered",
         "spider_nest",
         "crystalline_chasm",
         "erupting_inferno",
         "glowing_grotto"
      );
   }

   private static boolean pathContains(String path, String... needles) {
      for (String n : needles) {
         if (path.contains(n)) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   public static ResourceLocation idOf(Holder<Biome> biome) {
      return biome.unwrapKey().map(ResourceKey::location).orElse(null);
   }

   private static TagKey<Biome> tag(String ns, String path) {
      return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ns, path));
   }

   public static List<String> culturePaths(Holder<Biome> biome) {
      return List.copyOf(classify(biome).culturePaths());
   }
}
