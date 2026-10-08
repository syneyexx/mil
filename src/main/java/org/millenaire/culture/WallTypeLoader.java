package org.millenaire.culture;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.slf4j.Logger;

final class WallTypeLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WALL_TYPE_DIR = "/millenaire/wall_type/";
   private static final String WALL_TYPE_CONTENT_DIR = "wall_type";
   private static final String JSON_EXT = ".json";

   private WallTypeLoader() {
   }

   static void loadAll() {
      ContentFs fs = CustomContentIndex.current().forGlobalContent("wall_type");
      List<Resource> entries = fs.walk("", 1).toList();
      String scopePrefix = "wall_type/";
      int count = 0;

      for (Resource res : entries) {
         String full = res.relPath();
         if (full.endsWith(".json")) {
            String leaf = leafName(full);
            if (leaf != null && !leaf.startsWith("_") && full.startsWith(scopePrefix)) {
               String rel = full.substring(scopePrefix.length());
               if (rel.indexOf(47) >= 0) {
                  String contentId = rel.substring(0, rel.length() - ".json".length());
                  load(contentId);
                  count++;
               }
            }
         }
      }

      LOGGER.info("{} wall types loaded", count);
   }

   @Nullable
   private static String leafName(String relPath) {
      if (relPath != null && !relPath.isEmpty()) {
         int slash = relPath.lastIndexOf(47);
         return slash < 0 ? relPath : relPath.substring(slash + 1);
      } else {
         return null;
      }
   }

   static void load(String contentId) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", contentId);
      String path = "/millenaire/wall_type/" + contentId + ".json";
      JsonObject json = CultureLoader.readJson(path);
      if (json != null) {
         try {
            ResourceLocation culture = ResourceLocation.parse(GsonHelper.getAsString(json, "culture"));
            String key = GsonHelper.getAsString(json, "key", contentId);
            WallType wallType = new WallType(
               id,
               culture,
               key,
               optionalRL(json, "wall_plan_set"),
               optionalRL(json, "tower_plan_set"),
               ResourceLocation.parse(GsonHelper.getAsString(json, "gateway_plan_set")),
               optionalRL(json, "corner_plan_set"),
               optionalRL(json, "cap_right_plan_set"),
               optionalRL(json, "cap_left_plan_set"),
               optionalRL(json, "cap_both_plan_set"),
               optionalRL(json, "slope1_left_plan_set"),
               optionalRL(json, "slope1_right_plan_set"),
               optionalRL(json, "slope2_left_plan_set"),
               optionalRL(json, "slope2_right_plan_set"),
               optionalRL(json, "slope3_left_plan_set"),
               optionalRL(json, "slope3_right_plan_set"),
               GsonHelper.getAsBoolean(json, "wall_spawn", true),
               GsonHelper.getAsBoolean(json, "tower_spawn", true),
               GsonHelper.getAsBoolean(json, "gateway_spawn", true),
               GsonHelper.getAsBoolean(json, "corner_spawn", true),
               GsonHelper.getAsBoolean(json, "cap_spawn", true),
               GsonHelper.getAsInt(json, "walls_between_towers", 3),
               GsonHelper.getAsInt(json, "nb_smooth_runs", 3),
               GsonHelper.getAsInt(json, "max_y_delta", 15)
            );
            ModCultures.registerWallType(wallType);
            LOGGER.debug("Loaded wall type: {}", id);
         } catch (Exception e) {
            LOGGER.error("Error parsing wall type {}: {}", new Object[]{contentId, e.getMessage(), e});
         }
      }
   }

   @Nullable
   private static ResourceLocation optionalRL(JsonObject json, String field) {
      return json.has(field) ? ResourceLocation.parse(GsonHelper.getAsString(json, field)) : null;
   }

   static ResourceLocation resolveWallTypeRL(String wallTypeName, ResourceLocation culture) {
      return wallTypeName.contains(":")
         ? ResourceLocation.parse(wallTypeName)
         : ResourceLocation.fromNamespaceAndPath(culture.getNamespace(), culture.getPath() + "/" + wallTypeName.toLowerCase());
   }
}
