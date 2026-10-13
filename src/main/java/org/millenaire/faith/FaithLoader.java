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
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.culture.JsonLoaderUtils;
import org.slf4j.Logger;

public final class FaithLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   /** Builtin fallback list when directory discovery is unavailable. */
   private static final String[] KNOWN_FAITHS = {
      "norman", "byzantines", "indian", "mayan", "japanese", "seljuk", "inuits", "nederland", "illagers", "player_custom"
   };

   private FaithLoader() {
   }

   public static void loadAll() {
      FaithRegistry.clear();
      for (String path : KNOWN_FAITHS) {
         loadOne("/millenaire/faiths/" + path + ".json");
      }
      LOGGER.info("[Millenaire] Loaded {} faith definitions", FaithRegistry.size());
   }

   private static void loadOne(String resourcePath) {
      try (InputStream in = FaithLoader.class.getResourceAsStream(resourcePath)) {
         if (in == null) {
            LOGGER.debug("[Millenaire] Faith resource missing: {}", resourcePath);
            return;
         }
         try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            FaithDefinition def = parse(root);
            if (def != null) {
               FaithRegistry.register(def);
            }
         }
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Failed to load faith {}", resourcePath, e);
      }
   }

   @Nullable
   private static FaithDefinition parse(JsonObject root) {
      String idStr = GsonHelper.getAsString(root, "id", null);
      if (idStr == null || idStr.isBlank()) {
         return null;
      }
      ResourceLocation id = ResourceLocation.parse(idStr);
      String displayName = GsonHelper.getAsString(root, "display_name", id.getPath());
      ResourceLocation cultureId = null;
      if (root.has("culture") && !root.get("culture").isJsonNull()) {
         String c = GsonHelper.getAsString(root, "culture");
         if (c != null && !c.isBlank()) {
            cultureId = ResourceLocation.parse(c);
         }
      }
      List<String> clergy = parseStringList(root, "clergy_tags");
      List<String> churches = parseStringList(root, "church_building_tags");
      String holyShop = root.has("holy_shop") && !root.get("holy_shop").isJsonNull()
         ? GsonHelper.getAsString(root, "holy_shop")
         : null;
      String festivalName = GsonHelper.getAsString(root, "festival_name", "Festival");
      int duration = GsonHelper.getAsInt(root, "festival_duration_ticks", 4000);
      ResourceLocation shrine = null;
      if (root.has("preferred_shrine") && !root.get("preferred_shrine").isJsonNull()) {
         String s = GsonHelper.getAsString(root, "preferred_shrine");
         if (s != null && !s.isBlank()) {
            shrine = ResourceLocation.parse(s);
         }
      }
      return new FaithDefinition(id, displayName, cultureId, clergy, churches, holyShop, festivalName, duration, shrine);
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
