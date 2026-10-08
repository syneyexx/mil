package org.millenaire.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceKind;
import org.millenaire.culture.JsonLoaderUtils;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class ToolCategoryRegistry {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String CONTENT_DIR = "tool_categories";
   private static final String JSON_EXT = ".json";
   private static volatile Map<String, ToolCategory> CATEGORIES = Map.of();
   private static final AtomicBoolean warnedMeleeWeapons = new AtomicBoolean(false);

   private ToolCategoryRegistry() {
   }

   public static void load() {
      ContentFs fs = CustomContentIndex.current().forGlobalContent("tool_categories");
      String scopePrefix = "tool_categories/";
      TreeSet<String> distinctNames = new TreeSet<>();

      record Layer(String name, Resource resource, int depth, Map<String, LinkedHashMap<String, Integer>> entries) {
      }

      fs.walk("", 0).forEach(res -> {
         String relPath = res.relPath();
         if (relPath.startsWith(scopePrefix) && relPath.endsWith(".json")) {
            String leaf = relPath.substring(scopePrefix.length());
            if (!leaf.startsWith("_")) {
               distinctNames.add(leaf);
            }
         }
      });
      if (distinctNames.isEmpty()) {
         LOGGER.error("No tool_categories/*.json found in the content overlay (expected at least the shipped default.json)");
      } else {
         List<Layer> layers = new ArrayList<>();

         for (String name : distinctNames) {
            List<Resource> found = fs.findAll(name);

            for (int depth = 0; depth < found.size(); depth++) {
               Resource resource = found.get(depth);
               Map<String, LinkedHashMap<String, Integer>> parsed = parseFile(resource);
               if (parsed != null) {
                  layers.add(new Layer(name, resource, depth, parsed));
               }
            }
         }

         layers.sort(
            Comparator.<Layer, SourceKind>comparing(l -> l.resource().kind()).thenComparing(Layer::name).thenComparing(Layer::depth, Comparator.reverseOrder())
         );
         Map<String, LinkedHashMap<String, Integer>> merged = new LinkedHashMap<>();

         for (Layer layer : layers) {
            int items = 0;

            for (Entry<String, LinkedHashMap<String, Integer>> cat : layer.entries().entrySet()) {
               LinkedHashMap<String, Integer> categoryMap = merged.computeIfAbsent(cat.getKey(), k -> new LinkedHashMap<>());

               for (Entry<String, Integer> item : cat.getValue().entrySet()) {
                  if (item.getValue() <= 0) {
                     categoryMap.remove(item.getKey());
                  } else {
                     categoryMap.put(item.getKey(), item.getValue());
                  }

                  items++;
               }
            }

            LOGGER.info(
               "[Millenaire] Loaded tool_categories file '{}' from {}: {} item(s) across {} categor{}",
               new Object[]{layer.name(), layer.resource().source().displayName(), items, layer.entries().size(), layer.entries().size() == 1 ? "y" : "ies"}
            );
         }

         Map<String, ToolCategory> newCategories = new HashMap<>();

         for (Entry<String, LinkedHashMap<String, Integer>> catEntry : merged.entrySet()) {
            String categoryId = catEntry.getKey();
            List<ToolCategory.ToolEntry> items = new ArrayList<>();

            for (Entry<String, Integer> itemEntry : catEntry.getValue().entrySet()) {
               String itemId = itemEntry.getKey();
               int priority = itemEntry.getValue();
               Item resolved = ItemHelper.resolve(itemId);
               items.add(new ToolCategory.ToolEntry(itemId, resolved, priority));
               if (resolved == null) {
                  LOGGER.debug("Item {} not resolved for category {} — will be ignored at runtime", itemId, categoryId);
               }
            }

            items.sort(Comparator.comparingInt(ToolCategory.ToolEntry::priority).reversed());
            newCategories.put(categoryId, new ToolCategory(categoryId, List.copyOf(items)));
         }

         CATEGORIES = Map.copyOf(newCategories);
         LOGGER.info(
            "{} tool categories loaded from {} file(s) ({} items total)",
            new Object[]{CATEGORIES.size(), layers.size(), CATEGORIES.values().stream().mapToInt(c -> c.items().size()).sum()}
         );
      }
   }

   @Nullable
   private static Map<String, LinkedHashMap<String, Integer>> parseFile(Resource resource) {
      try (InputStream is = resource.open()) {
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

         JsonObject root;
         try {
            root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         } catch (Throwable var14) {
            try {
               reader.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }

         reader.close();
         if (root == null) {
            throw new IllegalStateException("empty file");
         }

         Map<String, LinkedHashMap<String, Integer>> parsed = new LinkedHashMap<>();

         for (Entry<String, JsonElement> entry : root.entrySet()) {
            String categoryId = entry.getKey();
            if (!categoryId.startsWith("_")) {
               JsonArray itemsArray = GsonHelper.convertToJsonArray(entry.getValue(), categoryId);
               LinkedHashMap<String, Integer> categoryMap = new LinkedHashMap<>();

               for (JsonElement elem : itemsArray) {
                  JsonObject itemObj = GsonHelper.convertToJsonObject(elem, categoryId + "[]");
                  categoryMap.put(GsonHelper.getAsString(itemObj, "item"), GsonHelper.getAsInt(itemObj, "priority"));
               }

               parsed.put(categoryId, categoryMap);
            }
         }

         return parsed;
      } catch (Exception e) {
         LOGGER.error("Error loading {} from {} — file ignored: {}", new Object[]{resource.relPath(), resource.source().displayName(), e.getMessage()});
         return null;
      }
   }

   @Nullable
   public static ToolCategory get(String categoryId) {
      return CATEGORIES.get(categoryId);
   }

   public static List<String> expandToolClass(String toolClass) {
      return switch (toolClass.toLowerCase(Locale.ROOT)) {
         case "armour" -> List.of("armourshelmet", "armourschestplate", "armoursleggings", "armoursboots");
         case "meleeweapons" -> {
            if (warnedMeleeWeapons.compareAndSet(false, true)) {
               LOGGER.warn("Deprecated usage of 'meleeweapons' — use 'toolssword' (further occurrences silenced)");
            }

            yield List.of("weaponshandtohand");
         }
         case "toolssword" -> List.of("toolssword");
         case "rangedweapons" -> List.of("weaponsranged");
         case "pickaxes" -> List.of("toolspickaxe");
         case "axes" -> List.of("toolsaxe");
         case "shovels" -> List.of("toolsshovel");
         case "hoes" -> List.of("toolshoe");
         default -> {
            LOGGER.warn("Unknown tool class: {}", toolClass);
            yield List.of();
         }
      };
   }
}
