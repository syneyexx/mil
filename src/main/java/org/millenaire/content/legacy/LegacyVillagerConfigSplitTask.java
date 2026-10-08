package org.millenaire.content.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;

final class LegacyVillagerConfigSplitTask {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final String DEFAULT_STEM = "default";
   private static final Map<String, String> TOOL_CATEGORY_KEYS = Map.ofEntries(
      Map.entry("weaponhandtohandpriority", "weaponshandtohand"),
      Map.entry("weaponrangedpriority", "weaponsranged"),
      Map.entry("toolswordpriority", "toolssword"),
      Map.entry("toolaxepriority", "toolsaxe"),
      Map.entry("toolpickaxepriority", "toolspickaxe"),
      Map.entry("toolhoepriority", "toolshoe"),
      Map.entry("toolshovelpriority", "toolsshovel"),
      Map.entry("armourhelmetpriority", "armourshelmet"),
      Map.entry("armourchestplatepriority", "armourschestplate"),
      Map.entry("armourleggingspriority", "armoursleggings"),
      Map.entry("armourbootspriority", "armoursboots")
   );
   private static final Map<String, String> VILLAGER_CONFIG_KEYS = Map.of(
      "foodgrowthvalue", "food_growth_overrides", "foodconceptionvalue", "food_conception_overrides"
   );
   private final ItemIdMapper items;
   private final LegacyConversionReport report;
   private final ConverterOutputManifest manifest;

   LegacyVillagerConfigSplitTask(ItemIdMapper items, LegacyConversionReport report, ConverterOutputManifest manifest) {
      this.items = items;
      this.report = report;
      this.manifest = manifest;
   }

   void convert(Path writeRoot, LegacyLayoutDetector.Detection detection) {
      List<Path> files = detection.villagerConfigTxts();
      if (!files.isEmpty()) {
         LOGGER.info("=== LegacyVillagerConfigSplitTask ===");

         for (Path file : files) {
            this.processFile(file, writeRoot);
         }

         LOGGER.info("=== LegacyVillagerConfigSplitTask done ===");
      }
   }

   private void processFile(Path file, Path writeRoot) {
      String fileName = file.getFileName().toString();
      String stem = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - ".txt".length()) : fileName;
      String context = "villagerconfig:" + stem;
      boolean weaponsAllowed = "default".equalsIgnoreCase(stem);
      Map<String, List<Entry<String, Integer>>> toolBucket = new LinkedHashMap<>();
      Map<String, Map<String, Integer>> configBucket = new LinkedHashMap<>();
      boolean weaponKeysDropped = false;

      List<String> lines;
      try {
         lines = LegacyDataParser.readLinesLenient(file);
      } catch (IOException e) {
         LOGGER.error("Error reading {}: {}", file, e.getMessage());
         this.report.recordSkipped(null, LegacyConversionReport.Kind.VILLAGER_CONFIG, file.toString(), "unreadable: " + e.getMessage());
         return;
      }

      for (int i = 0; i < lines.size(); i++) {
         String rawLine = lines.get(i);
         int lineNo = i + 1;
         String line = rawLine.strip();
         if (!line.isEmpty() && !line.startsWith("//")) {
            int eq = line.indexOf(61);
            if (eq <= 0) {
               LOGGER.warn("[villagerconfig] {}:{}: no '=' separator — {}", new Object[]{file, lineNo, rawLine});
               this.report.recordUnparseableLine(null, file.toString(), lineNo, rawLine);
            } else {
               String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
               String value = line.substring(eq + 1);
               int comma = value.indexOf(44);
               if (comma < 0) {
                  LOGGER.warn("[villagerconfig] {}:{}: malformed line (expected item,value) — {}", new Object[]{file, lineNo, rawLine});
                  this.report.recordMalformedRow(null, file.toString(), lineNo, "expected 'item,value' after '='");
               } else {
                  String legacyItem = value.substring(0, comma).strip();
                  String numberPart = value.substring(comma + 1).strip();

                  int numericValue;
                  try {
                     numericValue = Integer.parseInt(numberPart);
                  } catch (NumberFormatException e) {
                     LOGGER.warn("[villagerconfig] {}:{}: non-numeric value '{}' — {}", new Object[]{file, lineNo, numberPart, rawLine});
                     this.report.recordInvalidNumeric(null, file.toString(), key, numberPart, "integer");
                     continue;
                  }

                  String toolCategory = TOOL_CATEGORY_KEYS.get(key);
                  String configKey = VILLAGER_CONFIG_KEYS.get(key);
                  if (toolCategory != null) {
                     if (!weaponsAllowed) {
                        weaponKeysDropped = true;
                     } else {
                        String resolved = this.items.resolveOrWarn(null, legacyItem, context).orElse(null);
                        if (resolved != null) {
                           toolBucket.computeIfAbsent(toolCategory, k -> new ArrayList<>()).add(Map.entry(resolved, numericValue));
                        }
                     }
                  } else if (configKey != null) {
                     String resolved = this.items.resolveOrWarn(null, legacyItem, context).orElse(null);
                     if (resolved != null) {
                        configBucket.computeIfAbsent(configKey, k -> new LinkedHashMap<>()).put(resolved, numericValue);
                     }
                  } else {
                     LOGGER.warn("[villagerconfig] {}:{}: unrecognised key '{}' — skipped", new Object[]{file, lineNo, key});
                     this.report.recordUnknownKey(null, file.toString(), key);
                  }
               }
            }
         }
      }

      if (weaponKeysDropped) {
         LOGGER.warn(
            "[villagerconfig] {}: weapon/tool/armour priority keys are only supported in default.txt (Millénaire 9 has one global tool-category list, not one per named config) — dropped",
            file
         );
         this.report
            .recordSkipped(
               null,
               LegacyConversionReport.Kind.TOOL_CATEGORIES,
               file.toString(),
               "weapon/tool/armour priorities in a named config are not supported (global tool_categories only, see default.txt)"
            );
      }

      if (!toolBucket.isEmpty()) {
         this.writeToolCategories(writeRoot, file, stem, toolBucket);
      }

      if (!configBucket.isEmpty()) {
         this.writeVillagerConfig(writeRoot, file, stem, configBucket);
      }

      if (toolBucket.isEmpty() && configBucket.isEmpty()) {
         LOGGER.info("  [SKIP] {}: no recognised keys produced output", file);
         if (!weaponKeysDropped) {
            this.report.recordSkipped(null, LegacyConversionReport.Kind.VILLAGER_CONFIG, file.toString(), "no recognised keys produced output");
         }
      }
   }

   private void writeToolCategories(Path writeRoot, Path source, String stem, Map<String, List<Entry<String, Integer>>> bucket) {
      JsonObject root = new JsonObject();

      for (Entry<String, List<Entry<String, Integer>>> category : bucket.entrySet()) {
         JsonArray arr = new JsonArray();

         for (Entry<String, Integer> entry : category.getValue()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("item", entry.getKey());
            obj.addProperty("priority", entry.getValue());
            arr.add(obj);
         }

         root.add(category.getKey(), arr);
      }

      Path out = writeRoot.resolve("tool_categories").resolve(stem + ".json");
      this.writeAndRecord(writeRoot, out, source, root, LegacyConversionReport.Kind.TOOL_CATEGORIES);
   }

   private void writeVillagerConfig(Path writeRoot, Path source, String stem, Map<String, Map<String, Integer>> bucket) {
      JsonObject root = new JsonObject();

      for (Entry<String, Map<String, Integer>> overlay : bucket.entrySet()) {
         JsonObject obj = new JsonObject();

         for (Entry<String, Integer> entry : overlay.getValue().entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
         }

         root.add(overlay.getKey(), obj);
      }

      Path out = writeRoot.resolve("villager_config").resolve(stem + ".json");
      this.writeAndRecord(writeRoot, out, source, root, LegacyConversionReport.Kind.VILLAGER_CONFIG);
   }

   private void writeAndRecord(Path writeRoot, Path out, Path source, JsonObject root, LegacyConversionReport.Kind kind) {
      try {
         LegacyConversionEngine.writeAtomically(out, GSON.toJson(root) + "\n");
         this.manifest.recordFile(writeRoot, out, null);
         this.report.recordConverted(null, kind);
         LOGGER.info("  [CREATED] {}", out);
      } catch (IOException e) {
         LOGGER.error("Error writing {}: {}", out, e.getMessage());
         this.report.recordSkipped(null, kind, source.toString(), "write failed: " + e.getMessage());
      }
   }
}
