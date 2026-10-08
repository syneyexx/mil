package org.millenaire.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceElection;
import org.millenaire.content.SourceKind;
import org.slf4j.Logger;

public final class ImportTablePlanResolver {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ImportTablePlanResolver() {
   }

   public static Optional<ImportTablePlanResolver.PlanView> resolvePlan(String cultureKey, String buildingId) {
      return ImportBuildingResolver.resolveBuilding(cultureKey, buildingId)
         .map(rb -> parsePlan(rb.effectiveJson(), rb.cultureKey(), rb.buildingId(), rb.category(), rb.dir()));
   }

   public static List<ImportTablePlanResolver.CultureListingEntry> listCulture(String cultureKey) {
      List<ImportBuildingResolver.ResolvedBuilding> resolved = ImportBuildingResolver.resolveCulture(cultureKey);
      List<ImportTablePlanResolver.CultureListingEntry> out = new ArrayList<>(resolved.size());

      for (ImportBuildingResolver.ResolvedBuilding rb : resolved) {
         out.add(toListingEntry(rb));
      }

      return out;
   }

   public static ImportTablePlanResolver.CultureListingEntry toListingEntry(ImportBuildingResolver.ResolvedBuilding rb) {
      List<String> shadowed = new ArrayList<>(rb.shadowed().size());

      for (Resource res : rb.shadowed()) {
         shadowed.add(res.source().displayName() + ":" + res.relPath());
      }

      return parseListingEntry(rb.unionPlan(), rb.buildingId(), rb.category(), rb.registered(), rb.provenance().badge(), shadowed);
   }

   static int winnerIndex(List<SourceKind> kinds) {
      int idx = SourceElection.crossSourceWinnerIndex(kinds);
      return idx == -1 ? 0 : idx;
   }

   static ImportTablePlanResolver.CultureListingEntry parseListingEntry(
      JsonObject root, String buildingId, String category, boolean registered, String provenance, List<String> shadowedPaths
   ) {
      boolean isSub = root.has("is_sub_building") && root.get("is_sub_building").getAsBoolean();
      LinkedHashSet<String> subIds = new LinkedHashSet<>();
      if (root.has("starting_sub_buildings") && root.get("starting_sub_buildings").isJsonArray()) {
         for (JsonElement e : root.getAsJsonArray("starting_sub_buildings")) {
            if (e.isJsonPrimitive()) {
               subIds.add(e.getAsString());
            }
         }
      }

      Map<String, List<Integer>> variantLevels = new TreeMap<>(ImportBuildingResolver.variantLevels(root));
      JsonArray variantsArray = root.has("variants") && root.get("variants").isJsonArray() ? root.getAsJsonArray("variants") : null;
      if (variantsArray != null) {
         for (JsonElement ve : variantsArray) {
            if (ve.isJsonObject()) {
               JsonObject variantObj = ve.getAsJsonObject();
               JsonArray levelsArray = variantObj.has("levels") && variantObj.get("levels").isJsonArray() ? variantObj.getAsJsonArray("levels") : null;
               if (levelsArray != null) {
                  for (JsonElement le : levelsArray) {
                     if (le.isJsonObject()) {
                        JsonObject levelObj = le.getAsJsonObject();
                        if (levelObj.has("sub_buildings") && levelObj.get("sub_buildings").isJsonArray()) {
                           for (JsonElement se : levelObj.getAsJsonArray("sub_buildings")) {
                              if (se.isJsonPrimitive()) {
                                 subIds.add(se.getAsString());
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      BuildingPlanSet.ParentRef parentRef = parseParentRef(root);
      String parentId = parentRef != null ? parentRef.id() : "";
      String parentVariant = parentRef != null ? parentRef.variant() : "";
      int parentTriggerLevel = parentRef != null ? parentRef.level() : -1;
      return new ImportTablePlanResolver.CultureListingEntry(
         buildingId,
         category,
         isSub,
         registered,
         Map.copyOf(variantLevels),
         new ArrayList<>(subIds),
         parentId,
         parentVariant,
         parentTriggerLevel,
         provenance,
         List.copyOf(shadowedPaths)
      );
   }

   public static Optional<String> resolveCategory(String cultureKey, String buildingId) {
      return ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).map(ImportBuildingResolver.ResolvedBuilding::category);
   }

   public static Optional<ImportTablePlanResolver.LevelView> resolveLevel(String cultureKey, String buildingId, String variant, int level) {
      return resolvePlan(cultureKey, buildingId).flatMap(plan -> {
         ImportTablePlanResolver.VariantView v = plan.variants().get(variant);
         return v == null ? Optional.empty() : Optional.ofNullable(v.levels().get(level));
      });
   }

   public static Optional<ImportTablePlanResolver.VariantView> resolveVariant(String cultureKey, String buildingId, String variant) {
      return resolvePlan(cultureKey, buildingId).flatMap(plan -> Optional.ofNullable(plan.variants().get(variant)));
   }

   public static boolean isServedFromExports(String cultureKey, String buildingId) {
      return ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).map(ImportBuildingResolver.ResolvedBuilding::fromExports).orElse(false);
   }

   public static Optional<String> formatFromExportsMessage(String tag, List<String> names) {
      return names != null && !names.isEmpty()
         ? Optional.of("§e[" + tag + "] From your exports: " + String.join(", ", names) + " (" + names.size() + ")")
         : Optional.empty();
   }

   static ImportTablePlanResolver.PlanView parsePlan(JsonObject root, String cultureKey, String buildingId, String category, String parentDir) {
      int planSetOrientation = root.has("building_orientation") ? root.get("building_orientation").getAsInt() : 1;
      Map<String, ImportTablePlanResolver.VariantView> variants = new TreeMap<>();
      JsonArray variantsArray = root.has("variants") ? root.getAsJsonArray("variants") : null;
      if (variantsArray != null) {
         for (JsonElement ve : variantsArray) {
            JsonObject variantObj = ve.getAsJsonObject();
            if (variantObj.has("variant")) {
               String variant = variantObj.get("variant").getAsString();
               int variantOrientation = variantObj.has("building_orientation") ? variantObj.get("building_orientation").getAsInt() : planSetOrientation;
               int variantGroundLevel = variantObj.has("ground_level") ? variantObj.get("ground_level").getAsInt() : 0;
               Map<Integer, ImportTablePlanResolver.LevelView> levels = new LinkedHashMap<>();
               JsonArray levelsArray = variantObj.has("levels") ? variantObj.getAsJsonArray("levels") : null;
               if (levelsArray != null) {
                  for (JsonElement le : levelsArray) {
                     JsonObject levelObj = le.getAsJsonObject();
                     if (levelObj.has("level")) {
                        int level = levelObj.get("level").getAsInt();
                        int width = 0;
                        int height = 0;
                        int depth = 0;
                        if (levelObj.has("footprint")) {
                           JsonObject fp = levelObj.getAsJsonObject("footprint");
                           width = fp.has("width") ? fp.get("width").getAsInt() : 0;
                           height = fp.has("height") ? fp.get("height").getAsInt() : 0;
                           depth = fp.has("depth") ? fp.get("depth").getAsInt() : 0;
                        }

                        int groundLevel = levelObj.has("ground_level") ? levelObj.get("ground_level").getAsInt() : variantGroundLevel;
                        String nbtPath = parentDir + "/" + buildingId + "_" + variant + "_" + level;
                        levels.put(level, new ImportTablePlanResolver.LevelView(level, width, height, depth, groundLevel, variantOrientation, nbtPath));
                     }
                  }
               }

               variants.put(variant, new ImportTablePlanResolver.VariantView(variant, variantOrientation, levels));
            }
         }
      }

      return new ImportTablePlanResolver.PlanView(cultureKey, buildingId, category, variants, parseParentRef(root));
   }

   @Nullable
   static BuildingPlanSet.ParentRef parseParentRef(JsonObject root) {
      if (root != null && root.has("parent_building") && root.get("parent_building").isJsonObject()) {
         JsonObject obj = root.getAsJsonObject("parent_building");
         if (obj.has("id") && obj.has("variant") && obj.has("level")) {
            try {
               String id = obj.get("id").getAsString();
               String variant = obj.get("variant").getAsString().toLowerCase(Locale.ROOT);
               int level = obj.get("level").getAsInt();
               return !id.isEmpty() && !variant.isEmpty() ? new BuildingPlanSet.ParentRef(id, variant, level) : null;
            } catch (RuntimeException e) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public record CultureListingEntry(
      String buildingId,
      String category,
      boolean isSubBuilding,
      boolean registered,
      Map<String, List<Integer>> variantLevels,
      List<String> referencedSubBuildingIds,
      String parentBuildingId,
      String parentVariant,
      int parentTriggerLevel,
      String provenance,
      List<String> shadowedPaths
   ) {
   }

   public record LevelView(int level, int width, int height, int depth, int groundLevel, int buildingOrientation, String nbtPath) {
   }

   public record PlanView(
      String culture,
      String buildingId,
      String category,
      Map<String, ImportTablePlanResolver.VariantView> variants,
      @Nullable BuildingPlanSet.ParentRef parentBuilding
   ) {
   }

   public record VariantView(String variant, int buildingOrientation, Map<Integer, ImportTablePlanResolver.LevelView> levels) {
   }
}
