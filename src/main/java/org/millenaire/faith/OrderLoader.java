package org.millenaire.faith;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.culture.JsonLoaderUtils;
import org.slf4j.Logger;

public final class OrderLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String[] BUILTIN = {
      "last_flame", "closed_eye", "root_circle", "orde"
   };

   private OrderLoader() {
   }

   public static void loadAll() {
      OrderRegistry.clear();
      for (String name : BUILTIN) {
         loadOne("/millenaire/orders/" + name + ".json");
      }
      LOGGER.info("[Millenaire] Loaded {} order definitions", OrderRegistry.size());
   }

   private static void loadOne(String resourcePath) {
      try (InputStream in = OrderLoader.class.getResourceAsStream(resourcePath)) {
         if (in == null) {
            LOGGER.debug("[Millenaire] Order resource missing: {}", resourcePath);
            return;
         }
         try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            OrderDefinition def = parse(root);
            if (def != null) {
               if (def.minVillagePop() < 0 || def.spawnChance() < 0.0 || def.spawnChance() > 1.0) {
                  LOGGER.warn("[Millenaire] Skipping invalid order {}: bad pop/chance", def.id());
                  return;
               }
               OrderRegistry.register(def);
            }
         }
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Failed to load order {}", resourcePath, e);
      }
   }

   @Nullable
   private static OrderDefinition parse(JsonObject root) {
      String idStr = GsonHelper.getAsString(root, "id", null);
      if (idStr == null || idStr.isBlank()) {
         return null;
      }
      ResourceLocation id = ResourceLocation.parse(idStr);
      String displayName = GsonHelper.getAsString(root, "display_name", id.getPath());
      List<ResourceLocation> cultures = new ArrayList<>();
      if (root.has("allowed_cultures") && root.get("allowed_cultures").isJsonArray()) {
         for (JsonElement el : root.getAsJsonArray("allowed_cultures")) {
            if (el.isJsonPrimitive()) {
               cultures.add(ResourceLocation.parse(el.getAsString()));
            }
         }
      }
      int minPop = GsonHelper.getAsInt(root, "min_village_pop", 8);
      double chance = root.has("spawn_chance") ? root.get("spawn_chance").getAsDouble() : 0.25;
      int secrecy = GsonHelper.getAsInt(root, "secrecy_default", 70);
      List<String> buildingTags = parseStringList(root, "building_tags");
      List<String> clergy = parseStringList(root, "clergy_preference_tags");
      List<String> rituals = parseStringList(root, "rituals");
      Map<String, String> themes = new HashMap<>();
      if (root.has("themes") && root.get("themes").isJsonObject()) {
         JsonObject t = root.getAsJsonObject("themes");
         for (String key : t.keySet()) {
            themes.put(key, GsonHelper.getAsString(t, key, ""));
         }
      }
      boolean allows = GsonHelper.getAsBoolean(root, "allows_orders", true);
      return new OrderDefinition(id, displayName, cultures, minPop, chance, secrecy, buildingTags, clergy, rituals, themes, allows);
   }

   private static List<String> parseStringList(JsonObject root, String key) {
      List<String> out = new ArrayList<>();
      if (!root.has(key) || !root.get(key).isJsonArray()) {
         return out;
      }
      JsonArray arr = root.getAsJsonArray(key);
      for (JsonElement el : arr) {
         if (el.isJsonPrimitive()) {
            out.add(el.getAsString());
         }
      }
      return out;
   }
}
