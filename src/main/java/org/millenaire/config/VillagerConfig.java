package org.millenaire.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class VillagerConfig {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String CONTENT_DIR = "villager_config";
   private static final String JSON_EXT = ".json";
   private static final String DEFAULT_NAME = "default";
   private static VillagerConfig DEFAULT = new VillagerConfig(Map.of(), Map.of());
   private static boolean buildVillagePaths = true;
   private static Map<String, VillagerConfig> NAMED_CONFIGS = Map.of();
   private static final Set<String> warnedUnknownKeys = ConcurrentHashMap.newKeySet();
   private final Map<Item, Integer> foodGrowth;
   private final Map<Item, Integer> foodConception;

   private VillagerConfig(Map<Item, Integer> foodGrowth, Map<Item, Integer> foodConception) {
      this.foodGrowth = foodGrowth;
      this.foodConception = foodConception;
   }

   public static void load() {
      ContentFs fs = CustomContentIndex.current().forGlobalContent("villager_config");
      DEFAULT = loadDefault(fs);
      LOGGER.info("[Millenaire] Loaded default VillagerConfig: {} growth foods, {} conception foods", DEFAULT.foodGrowth.size(), DEFAULT.foodConception.size());
      String scopePrefix = "villager_config/";
      TreeSet<String> overlayNames = new TreeSet<>();
      fs.walk("", 0).forEach(res -> {
         String relPath = res.relPath();
         if (relPath.startsWith(scopePrefix) && relPath.endsWith(".json")) {
            String leaf = relPath.substring(scopePrefix.length());
            if (!leaf.startsWith("_") && leaf.indexOf(47) < 0) {
               String namex = leaf.substring(0, leaf.length() - ".json".length());
               if (!"default".equals(namex)) {
                  overlayNames.add(namex);
               }
            }
         }
      });
      Map<String, VillagerConfig> named = new HashMap<>();

      for (String name : overlayNames) {
         VillagerConfig cfg = loadOverride(fs, name);
         if (cfg != null) {
            named.put(name, cfg);
            LOGGER.info(
               "[Millenaire] Loaded VillagerConfig overlay '{}': {} growth foods, {} conception foods",
               new Object[]{name, cfg.foodGrowth.size(), cfg.foodConception.size()}
            );
         }
      }

      NAMED_CONFIGS = Map.copyOf(named);
   }

   public static VillagerConfig get(@Nullable String key) {
      if (key != null && !key.isEmpty()) {
         String lower = key.toLowerCase(Locale.ROOT);
         VillagerConfig cfg = NAMED_CONFIGS.get(lower);
         if (cfg == null) {
            if (warnedUnknownKeys.add(lower)) {
               LOGGER.warn("Unknown villager_config '{}' (no villager_config/{}.json in any content layer); using the default config", key, lower);
            }

            return DEFAULT;
         } else {
            return cfg;
         }
      } else {
         return DEFAULT;
      }
   }

   public static VillagerConfig getDefault() {
      return DEFAULT;
   }

   public Map<Item, Integer> getFoodGrowth() {
      return this.foodGrowth;
   }

   public Map<Item, Integer> getFoodConception() {
      return this.foodConception;
   }

   public static Map<Item, Integer> getDefaultFoodGrowth() {
      return DEFAULT.foodGrowth;
   }

   public static Map<Item, Integer> getDefaultFoodConception() {
      return DEFAULT.foodConception;
   }

   private static VillagerConfig loadDefault(ContentFs fs) {
      List<Resource> layers = fs.findAll("default.json");
      if (layers.isEmpty()) {
         LOGGER.warn("Villager config not found: {}/{}.json", "villager_config", "default");
         return new VillagerConfig(Map.of(), Map.of());
      }

      LinkedHashMap<Item, Integer> foodGrowth = new LinkedHashMap<>();
      LinkedHashMap<Item, Integer> foodConception = new LinkedHashMap<>();
      buildVillagePaths = true;

      for (int i = layers.size() - 1; i >= 0; i--) {
         VillagerConfig.Layer layer = parseLayer(layers.get(i));
         if (layer != null) {
            foodGrowth.putAll(layer.foodGrowth());
            foodConception.putAll(layer.foodConception());
            if (layer.foodGrowthOverrides() != null) {
               applyOverrides(foodGrowth, layer.foodGrowthOverrides());
            }

            if (layer.foodConceptionOverrides() != null) {
               applyOverrides(foodConception, layer.foodConceptionOverrides());
            }

            if (layer.buildVillagePaths() != null) {
               buildVillagePaths = layer.buildVillagePaths();
            }
         }
      }

      return new VillagerConfig(sortByDescendingValue(foodGrowth), sortByDescendingValue(foodConception));
   }

   @Nullable
   private static VillagerConfig loadOverride(ContentFs fs, String name) {
      Optional<Resource> resource = fs.findFirst(name + ".json");
      if (resource.isEmpty()) {
         LOGGER.debug("Overlay config not found (skipping): {}/{}.json", "villager_config", name);
         return null;
      }

      VillagerConfig.Layer layer = parseLayer(resource.get());
      if (layer == null) {
         return null;
      }

      LinkedHashMap<Item, Integer> growthMap = new LinkedHashMap<>(DEFAULT.foodGrowth);
      LinkedHashMap<Item, Integer> conceptionMap = new LinkedHashMap<>(DEFAULT.foodConception);
      if (layer.foodGrowthOverrides() != null) {
         applyOverrides(growthMap, layer.foodGrowthOverrides());
      }

      if (layer.foodConceptionOverrides() != null) {
         applyOverrides(conceptionMap, layer.foodConceptionOverrides());
      }

      return new VillagerConfig(sortByDescendingValue(growthMap), sortByDescendingValue(conceptionMap));
   }

   @Nullable
   private static VillagerConfig.Layer parseLayer(Resource resource) {
      try (
         InputStream is = resource.open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject root = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "root");
         LinkedHashMap<Item, Integer> foodGrowth = new LinkedHashMap<>();
         LinkedHashMap<Item, Integer> foodConception = new LinkedHashMap<>();
         if (root.has("food_growth")) {
            parseItemMapInto(GsonHelper.getAsJsonObject(root, "food_growth"), foodGrowth);
         }

         if (root.has("food_conception")) {
            parseItemMapInto(GsonHelper.getAsJsonObject(root, "food_conception"), foodConception);
         }

         JsonObject growthOverrides = root.has("food_growth_overrides") ? validateItemMap(GsonHelper.getAsJsonObject(root, "food_growth_overrides")) : null;
         JsonObject conceptionOverrides = root.has("food_conception_overrides")
            ? validateItemMap(GsonHelper.getAsJsonObject(root, "food_conception_overrides"))
            : null;
         Boolean buildPaths = root.has("build_village_paths") ? GsonHelper.getAsBoolean(root, "build_village_paths") : null;
         return new VillagerConfig.Layer(foodGrowth, foodConception, growthOverrides, conceptionOverrides, buildPaths);
      } catch (Exception e) {
         LOGGER.error("Error reading {} from {} — file ignored: {}", new Object[]{resource.relPath(), resource.source().displayName(), e.getMessage()});
         return null;
      }
   }

   private static JsonObject validateItemMap(JsonObject obj) {
      for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
         if (!e.getKey().startsWith("_")) {
            GsonHelper.convertToInt(e.getValue(), e.getKey());
         }
      }

      return obj;
   }

   private static void applyOverrides(Map<Item, Integer> target, JsonObject overrides) {
      for (java.util.Map.Entry<String, JsonElement> e : overrides.entrySet()) {
         String key = e.getKey();
         if (!key.startsWith("_")) {
            Item item = ItemHelper.resolve(key);
            if (item != null && item != Items.AIR) {
               int value = GsonHelper.convertToInt(e.getValue(), key);
               if (value <= 0) {
                  target.remove(item);
               } else {
                  target.put(item, value);
               }
            }
         }
      }
   }

   private static Map<Item, Integer> sortByDescendingValue(Map<Item, Integer> map) {
      record Entry(Item item, int value) {
      }

      List<Entry> entries = new ArrayList<>();

      for (java.util.Map.Entry<Item, Integer> e : map.entrySet()) {
         entries.add(new Entry(e.getKey(), e.getValue()));
      }

      entries.sort((a, b) -> Integer.compare(b.value, a.value));
      LinkedHashMap<Item, Integer> result = new LinkedHashMap<>();

      for (Entry entry : entries) {
         result.put(entry.item, entry.value);
      }

      return result;
   }

   private static void parseItemMapInto(JsonObject obj, Map<Item, Integer> target) {
      for (java.util.Map.Entry<String, JsonElement> e : obj.entrySet()) {
         String key = e.getKey();
         if (!key.startsWith("_")) {
            Item item = ItemHelper.resolve(key);
            if (item != null && item != Items.AIR) {
               target.put(item, GsonHelper.convertToInt(e.getValue(), key));
            }
         }
      }
   }

   public static boolean isBuildVillagePaths() {
      return buildVillagePaths;
   }

   private record Layer(
      Map<Item, Integer> foodGrowth,
      Map<Item, Integer> foodConception,
      @Nullable JsonObject foodGrowthOverrides,
      @Nullable JsonObject foodConceptionOverrides,
      @Nullable Boolean buildVillagePaths
   ) {
   }
}
