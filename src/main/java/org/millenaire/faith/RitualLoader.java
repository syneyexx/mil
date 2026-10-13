package org.millenaire.faith;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.culture.JsonLoaderUtils;
import org.slf4j.Logger;

public final class RitualLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final String[] BUILTIN = {
      "initiation", "night_wake", "relic_restore", "reconciliation",
      "closed_eye_initiation", "root_circle_vigil"
   };

   private RitualLoader() {
   }

   public static void loadAll() {
      RitualRegistry.clear();
      for (String name : BUILTIN) {
         loadOne("/millenaire/rituals/" + name + ".json");
      }
      LOGGER.info("[Millenaire] Loaded {} ritual definitions", RitualRegistry.size());
   }

   private static void loadOne(String resourcePath) {
      try (InputStream in = RitualLoader.class.getResourceAsStream(resourcePath)) {
         if (in == null) {
            LOGGER.debug("[Millenaire] Ritual resource missing: {}", resourcePath);
            return;
         }
         try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            RitualDefinition def = parse(root);
            if (def != null) {
               if (def.capacity() <= 0 || def.durationTicks() <= 0 || def.cooldownDays() < 0) {
                  LOGGER.warn("[Millenaire] Skipping invalid ritual {}", def.id());
                  return;
               }
               RitualRegistry.register(def);
            }
         }
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Failed to load ritual {}", resourcePath, e);
      }
   }

   @Nullable
   private static RitualDefinition parse(JsonObject root) {
      String idStr = GsonHelper.getAsString(root, "id", null);
      if (idStr == null || idStr.isBlank()) {
         return null;
      }
      ResourceLocation id = ResourceLocation.parse(idStr);
      String displayName = GsonHelper.getAsString(root, "display_name", id.getPath());
      int capacity = GsonHelper.getAsInt(root, "capacity", 4);
      int duration = GsonHelper.getAsInt(root, "duration_ticks", 2000);
      int cooldown = GsonHelper.getAsInt(root, "cooldown_days", 3);
      int winMin = GsonHelper.getAsInt(root, "start_window_min", 13000);
      int winMax = GsonHelper.getAsInt(root, "start_window_max", 23000);
      int gatherDl = GsonHelper.getAsInt(root, "gather_deadline_ticks", 6000);
      double radius = root.has("presence_radius") ? root.get("presence_radius").getAsDouble() : 8.0;
      Map<String, Integer> goods = new LinkedHashMap<>();
      if (root.has("required_goods") && root.get("required_goods").isJsonObject()) {
         JsonObject g = root.getAsJsonObject("required_goods");
         for (String key : g.keySet()) {
            goods.put(key, GsonHelper.getAsInt(g, key, 1));
         }
      }
      int devotion = GsonHelper.getAsInt(root, "devotion_reward", 10);
      int cohesion = GsonHelper.getAsInt(root, "cohesion_reward", 5);
      int secrecy = GsonHelper.getAsInt(root, "secrecy_delta", 0);
      int tension = GsonHelper.getAsInt(root, "tension_delta", 0);
      List<String> tags = new ArrayList<>();
      if (root.has("order_tags") && root.get("order_tags").isJsonArray()) {
         for (JsonElement el : root.getAsJsonArray("order_tags")) {
            if (el.isJsonPrimitive()) {
               tags.add(el.getAsString());
            }
         }
      }
      return new RitualDefinition(
         id, displayName, capacity, duration, cooldown, winMin, winMax, gatherDl, radius, goods,
         devotion, cohesion, secrecy, tension, tags
      );
   }
}
