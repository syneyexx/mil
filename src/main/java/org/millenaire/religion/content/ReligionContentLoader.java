package org.millenaire.religion.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.culture.CultureLoader;
import org.millenaire.religion.model.RitualKind;
import org.slf4j.Logger;

/**
 * Loads tradition/ritual JSON and per-culture religion bindings. Soft-fails invalid files
 * so other cultures remain usable. Custom content overlays classpath via {@link CustomContentIndex}.
 */
public final class ReligionContentLoader {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ReligionContentLoader() {
   }

   public static void loadAll() {
      ReligionContentRegistry.clear();
      loadFromFs("religion/traditions", true);
      loadFromFs("religion/rituals", false);
      // Ensure built-in classpath files are present even if overlay missed them.
      ensureClasspathBuiltins();
      for (String culture : CultureLoader.discoverCultures()) {
         try {
            loadCultureBinding(culture);
         } catch (Exception e) {
            LOGGER.error("[Religion] Failed culture religion binding for {}: {}", culture, e.getMessage());
         }
      }
      ReligionContentValidator.validateAll();
      LOGGER.info(
         "[Religion] Loaded {} traditions, {} rituals",
         ReligionContentRegistry.allTraditions().size(),
         ReligionContentRegistry.allRituals().size()
      );
   }

   private static void loadFromFs(String contentPath, boolean traditions) {
      try {
         ContentFs fs = CustomContentIndex.current().root().sub(contentPath);
         List<Resource> entries = fs.walk("", Integer.MAX_VALUE)
            .filter(r -> {
               String leaf = leafName(r.relPath());
               return leaf != null && leaf.endsWith(".json") && !leaf.startsWith("_");
            })
            .sorted(Comparator.comparing(Resource::relPath))
            .toList();
         Set<String> seen = new HashSet<>();
         for (Resource res : entries) {
            String id = stripJson(leafName(res.relPath()));
            if (id == null || !seen.add(id)) {
               continue;
            }
            try (InputStream in = res.open()) {
               JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
               if (traditions) {
                  ReligionDefinition def = parseTradition(id, json);
                  if (def != null) {
                     ReligionContentRegistry.registerTradition(def);
                  }
               } else {
                  RitualDefinition def = parseRitual(id, json);
                  if (def != null) {
                     ReligionContentRegistry.registerRitual(def);
                  }
               }
            } catch (Exception e) {
               LOGGER.error("[Religion] Invalid {} {}: {}", traditions ? "tradition" : "ritual", res.relPath(), e.getMessage());
            }
         }
      } catch (Exception e) {
         LOGGER.warn("[Religion] ContentFs walk failed for {}: {}", contentPath, e.getMessage());
      }
   }

   private static void ensureClasspathBuiltins() {
      String[] traditionFiles = {
         "western_christianity", "eastern_christianity", "sanatana_dharma", "nihon_way",
         "sunni_islam", "maya_cosmovision", "angakkuq_way", "pulse_circle", "totem_shadow"
      };
      for (String name : traditionFiles) {
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", name);
         if (ReligionContentRegistry.traditionOrNull(id) != null) {
            continue;
         }
         loadClasspathFile("millenaire/religion/traditions/" + name + ".json", true, name);
      }
      String[] ritualFiles = {
         "norman_daily_prayer", "norman_sunday_mass", "norman_feast_assumption", "norman_memorial",
         "norman_consecration", "byz_daily_prayer", "byz_divine_liturgy", "byz_feast_pascha",
         "indian_daily_puja", "indian_communal_puja", "indian_festival_diwali", "indian_meditation",
         "japanese_daily_meditation", "japanese_kami_offering", "japanese_obon",
         "seljuk_daily_salah", "seljuk_jumuah", "seljuk_eid",
         "mayan_daily_offering", "mayan_community_rite", "mayan_memorial",
         "inuit_daily_propitiation", "inuit_community_kashim", "inuit_memorial",
         "gabber_daily_pulse", "gabber_circle_feast",
         "illager_daily_totem", "illager_shadow_gathering",
         "generic_family_blessing", "generic_pilgrimage"
      };
      for (String name : ritualFiles) {
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", name);
         if (ReligionContentRegistry.ritualOrNull(id) != null) {
            continue;
         }
         loadClasspathFile("millenaire/religion/rituals/" + name + ".json", false, name);
      }
   }

   private static void loadClasspathFile(String path, boolean tradition, String fallbackName) {
      try (InputStream in = ReligionContentLoader.class.getClassLoader().getResourceAsStream(path)) {
         if (in == null) {
            return;
         }
         JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
         if (tradition) {
            ReligionDefinition def = parseTradition(fallbackName, json);
            if (def != null) {
               ReligionContentRegistry.registerTradition(def);
            }
         } else {
            RitualDefinition def = parseRitual(fallbackName, json);
            if (def != null) {
               ReligionContentRegistry.registerRitual(def);
            }
         }
      } catch (Exception e) {
         LOGGER.error("[Religion] Classpath load failed for {}: {}", path, e.getMessage());
      }
   }

   private static void loadCultureBinding(String culture) {
      ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
      Optional<Resource> res = cultureFs.findFirst("religion.json");
      if (res.isEmpty()) {
         // Also try classpath directly
         try (InputStream in = ReligionContentLoader.class.getClassLoader()
            .getResourceAsStream("millenaire/cultures/" + culture + "/religion.json")) {
            if (in != null) {
               parseCultureBinding(culture, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
               return;
            }
         } catch (Exception e) {
            LOGGER.error("[Religion] Classpath religion.json for {}: {}", culture, e.getMessage());
         }
         List<ResourceLocation> inferred = new ArrayList<>();
         for (ReligionDefinition def : ReligionContentRegistry.allTraditions()) {
            if (def.supportsCulture(culture)) {
               inferred.add(def.id());
            }
         }
         ReligionContentRegistry.bindCulture(culture, inferred, false);
         return;
      }
      try (InputStream in = res.get().open()) {
         parseCultureBinding(
            culture,
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
         );
      } catch (Exception e) {
         LOGGER.error("[Religion] Bad religion.json for {}: {}", culture, e.getMessage());
         ReligionContentRegistry.bindCulture(culture, List.of(), false);
      }
   }

   private static void parseCultureBinding(String culture, JsonObject json) {
      boolean none = GsonHelper.getAsBoolean(json, "no_organized_religion", false);
      List<ResourceLocation> ids = new ArrayList<>();
      if (json.has("traditions")) {
         for (JsonElement el : json.getAsJsonArray("traditions")) {
            ids.add(parseId(el.getAsString()));
         }
      }
      ReligionContentRegistry.bindCulture(culture, ids, none);
   }

   static ReligionDefinition parseTradition(String fallbackName, JsonObject json) {
      ResourceLocation id = parseId(GsonHelper.getAsString(json, "id", "millenaire:" + fallbackName));
      List<String> cultures = stringList(json, "cultures");
      List<ReligionRoleDefinition> roles = new ArrayList<>();
      if (json.has("roles")) {
         for (JsonElement el : json.getAsJsonArray("roles")) {
            JsonObject r = el.getAsJsonObject();
            roles.add(
               new ReligionRoleDefinition(
                  GsonHelper.getAsString(r, "id"),
                  stringList(r, "villager_tags"),
                  stringList(r, "type_path_contains"),
                  GsonHelper.getAsBoolean(r, "clergy", false),
                  GsonHelper.getAsBoolean(r, "can_lead_ceremony", false),
                  GsonHelper.getAsString(r, "name_key", null)
               )
            );
         }
      }
      return new ReligionDefinition(
         id,
         GsonHelper.getAsString(json, "name_key", "religion.millenaire." + id.getPath() + ".name"),
         GsonHelper.getAsString(json, "description_key", "religion.millenaire." + id.getPath() + ".desc"),
         cultures,
         roles,
         stringList(json, "location_tags"),
         idList(json, "daily_rituals"),
         idList(json, "service_rituals"),
         idList(json, "feast_rituals"),
         idList(json, "memorial_rituals"),
         idList(json, "family_rituals"),
         idList(json, "consecration_rituals"),
         idList(json, "pilgrimage_rituals"),
         GsonHelper.getAsBoolean(json, "allows_multiple_affiliations", false),
         GsonHelper.getAsInt(json, "default_devotion", 40),
         GsonHelper.getAsString(json, "primary_clergy_role", null)
      );
   }

   @Nullable
   static RitualDefinition parseRitual(String fallbackName, JsonObject json) {
      ResourceLocation id = parseId(GsonHelper.getAsString(json, "id", "millenaire:" + fallbackName));
      RitualKind kind = RitualKind.fromId(GsonHelper.getAsString(json, "kind", "daily"));
      if (kind == null) {
         LOGGER.error("[Religion] Unknown ritual kind in {}", id);
         return null;
      }
      ResourceLocation traditionId = parseId(GsonHelper.getAsString(json, "tradition", "millenaire:unknown"));
      List<SupplyRequirement> supplies = new ArrayList<>();
      if (json.has("supplies")) {
         for (JsonElement el : json.getAsJsonArray("supplies")) {
            JsonObject s = el.getAsJsonObject();
            supplies.add(
               SupplyRequirement.parse(
                  GsonHelper.getAsString(s, "item"),
                  GsonHelper.getAsInt(s, "count", 1),
                  GsonHelper.getAsBoolean(s, "optional", false)
               )
            );
         }
      }
      return new RitualDefinition(
         id,
         kind,
         GsonHelper.getAsString(json, "name_key", "ritual.millenaire." + id.getPath() + ".name"),
         GsonHelper.getAsString(json, "description_key", "ritual.millenaire." + id.getPath() + ".desc"),
         traditionId,
         stringList(json, "location_tags"),
         stringList(json, "required_leader_roles"),
         GsonHelper.getAsBoolean(json, "requires_leader", kind != RitualKind.DAILY && kind != RitualKind.PILGRIMAGE),
         supplies,
         GsonHelper.getAsInt(json, "min_participants", kind == RitualKind.DAILY ? 1 : 3),
         GsonHelper.getAsInt(json, "max_participants", 24),
         GsonHelper.getAsInt(json, "gather_ticks", 600),
         GsonHelper.getAsInt(json, "active_ticks", 600),
         GsonHelper.getAsInt(json, "cooldown_days", kind == RitualKind.DAILY ? 0 : 3),
         GsonHelper.getAsInt(json, "priority", 40),
         GsonHelper.getAsInt(json, "day_modulo", 0),
         GsonHelper.getAsInt(json, "day_offset", 0),
         GsonHelper.getAsInt(json, "minimum_hour", -1),
         GsonHelper.getAsInt(json, "maximum_hour", -1),
         GsonHelper.getAsBoolean(json, "align_with_festival", kind == RitualKind.FEAST),
         GsonHelper.getAsString(json, "sound", null),
         GsonHelper.getAsString(json, "particle", null),
         stringList(json, "held_items"),
         GsonHelper.getAsString(json, "effects_profile", "default_communal")
      );
   }

   private static List<String> stringList(JsonObject json, String key) {
      List<String> out = new ArrayList<>();
      if (!json.has(key)) {
         return out;
      }
      JsonArray arr = json.getAsJsonArray(key);
      for (JsonElement el : arr) {
         out.add(el.getAsString());
      }
      return out;
   }

   private static List<ResourceLocation> idList(JsonObject json, String key) {
      List<ResourceLocation> out = new ArrayList<>();
      if (!json.has(key)) {
         return out;
      }
      for (JsonElement el : json.getAsJsonArray(key)) {
         out.add(parseId(el.getAsString()));
      }
      return out;
   }

   private static ResourceLocation parseId(String raw) {
      if (raw == null || raw.isEmpty()) {
         return ResourceLocation.fromNamespaceAndPath("millenaire", "unknown");
      }
      if (raw.contains(":")) {
         return ResourceLocation.parse(raw);
      }
      return ResourceLocation.fromNamespaceAndPath("millenaire", raw);
   }

   @Nullable
   private static String leafName(@Nullable String relPath) {
      if (relPath == null || relPath.isEmpty()) {
         return null;
      }
      int slash = relPath.lastIndexOf('/');
      return slash < 0 ? relPath : relPath.substring(slash + 1);
   }

   @Nullable
   private static String stripJson(@Nullable String filename) {
      if (filename == null || !filename.endsWith(".json")) {
         return filename;
      }
      return filename.substring(0, filename.length() - 5);
   }
}
