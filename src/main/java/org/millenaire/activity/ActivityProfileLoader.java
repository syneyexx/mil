package org.millenaire.activity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.millenaire.content.Resource;
import org.millenaire.culture.JsonLoaderUtils;
import org.slf4j.Logger;

public final class ActivityProfileLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = JsonLoaderUtils.GSON;
   private static final Map<ResourceLocation, List<ActivityProfile>> BY_CULTURE = new ConcurrentHashMap<>();
   private static final Map<String, ActivityProfile> BY_ID = new ConcurrentHashMap<>();

   private ActivityProfileLoader() {}

   public static void clear() {
      BY_CULTURE.clear();
      BY_ID.clear();
   }

   public static void loadFromResources(ResourceLocation cultureId, List<Resource> resources) {
      Map<String, ActivityProfile> byId = new LinkedHashMap<>();
      if (resources != null) {
         for (int i = resources.size() - 1; i >= 0; i--) {
            Resource res = resources.get(i);
            try (InputStream stream = res.open()) {
               for (ActivityProfile p : parse(stream)) {
                  byId.put(p.id(), p);
               }
            } catch (IOException e) {
               LOGGER.error("[Millenaire] Error reading activity_profiles {} for {}: {}", res.relPath(), cultureId, e.getMessage());
            }
         }
      }
      // Ensure builtins if culture has no file
      if (byId.isEmpty()) {
         for (ActivityProfile p : builtinForCulture(cultureId.getPath())) {
            byId.put(p.id(), p);
         }
      }
      List<ActivityProfile> list = Collections.unmodifiableList(new ArrayList<>(byId.values()));
      BY_CULTURE.put(cultureId, list);
      for (ActivityProfile p : list) {
         BY_ID.put(p.id(), p);
      }
      LOGGER.info("[Millenaire] Loaded {} activity profiles for {}", list.size(), cultureId);
   }

   public static void ensureDefaultsLoaded() {
      if (!BY_ID.isEmpty()) {
         return;
      }
      for (String culture : List.of("norman", "illagers", "seljuk", "inuits", "nederland", "mayan", "japanese", "indian", "byzantines")) {
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", culture);
         if (!BY_CULTURE.containsKey(id)) {
            List<ActivityProfile> list = builtinForCulture(culture);
            BY_CULTURE.put(id, list);
            for (ActivityProfile p : list) {
               BY_ID.put(p.id(), p);
            }
         }
      }
   }

   public static List<ActivityProfile> forCulture(ResourceLocation cultureId) {
      ensureDefaultsLoaded();
      return BY_CULTURE.getOrDefault(cultureId, List.of());
   }

   @Nullable
   public static ActivityProfile byId(String id) {
      ensureDefaultsLoaded();
      return BY_ID.get(id);
   }

   private static List<ActivityProfile> parse(InputStream stream) throws IOException {
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         JsonObject root = GSON.fromJson(reader, JsonObject.class);
         if (root == null) {
            return List.of();
         }
         JsonArray arr = root.getAsJsonArray("profiles");
         if (arr == null) {
            return List.of();
         }
         List<ActivityProfile> out = new ArrayList<>();
         for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            ActivityProfile.Builder b = ActivityProfile.builder(GsonHelper.getAsString(o, "id"));
            readStrings(o, "eligibleRoles", b::roles);
            readStrings(o, "eligibleTags", b::tags);
            if (o.has("allowedActivityKinds")) {
               for (JsonElement k : o.getAsJsonArray("allowedActivityKinds")) {
                  b.kinds(ActivityKind.fromId(k.getAsString()));
               }
            }
            readStrings(o, "huntingPreyTags", b::prey);
            b.size(GsonHelper.getAsInt(o, "groupSizeMin", 2), GsonHelper.getAsInt(o, "groupSizeMax", 3));
            b.aggression(GsonHelper.getAsFloat(o, "aggression", 0.3f));
            b.risk(GsonHelper.getAsFloat(o, "riskTolerance", 0.4f));
            b.retreat(GsonHelper.getAsFloat(o, "retreatThreshold", 0.35f));
            readStrings(o, "biomePreferences", b::biomes);
            readStrings(o, "routePreferences", b::routes);
            b.range(GsonHelper.getAsInt(o, "activityRange", 256));
            b.homeDefense(GsonHelper.getAsInt(o, "minHomeDefense", 2));
            b.awayFraction(GsonHelper.getAsFloat(o, "maxAwayFraction", 0.20f));
            b.cooldown(GsonHelper.getAsInt(o, "cooldowns", 6000));
            b.loot(GsonHelper.getAsString(o, "lootPolicy", "real_drops"));
            b.org(GsonHelper.getAsString(o, "organizationId", ""));
            readStrings(o, "rivalOrganizations", b::rivals);
            b.bandit(GsonHelper.getAsBoolean(o, "bandit", false));
            b.hunter(GsonHelper.getAsBoolean(o, "hunter", false));
            b.patrol(GsonHelper.getAsBoolean(o, "patrol", false));
            out.add(b.build());
         }
         return out;
      }
   }

   private interface StringSink { void accept(String... v); }

   private static void readStrings(JsonObject o, String key, StringSink sink) {
      if (!o.has(key)) return;
      List<String> vals = new ArrayList<>();
      for (JsonElement e : o.getAsJsonArray(key)) {
         vals.add(e.getAsString());
      }
      sink.accept(vals.toArray(String[]::new));
   }

   static List<ActivityProfile> builtinForCulture(String culture) {
      List<ActivityProfile> out = new ArrayList<>();
      out.add(ActivityProfile.builder(culture + "_hunters")
         .roles("hunter", "lumberman", "forester", "jager", "chasse")
         .tags("hunter", "lumberman")
         .kinds(ActivityKind.HUNT)
         .prey("animal", "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken", "minecraft:rabbit", "minecraft:deer")
         .size(2, 3).range(256).homeDefense(2).awayFraction(0.20f).cooldown(8000L)
         .hunter(true).org(culture + "_village").build());
      out.add(ActivityProfile.builder(culture + "_patrol")
         .roles("guard", "soldier", "patrol", "garde", "wacht")
         .tags("helpInAttacks", "defensive")
         .kinds(ActivityKind.PATROL, ActivityKind.RESPONSE)
         .size(2, 4).range(160).homeDefense(2).awayFraction(0.20f).cooldown(4000L)
         .patrol(true).retreat(0.40f).org(culture + "_village").build());
      if ("illagers".equals(culture) || "norman".equals(culture) || "seljuk".equals(culture) || "mayan".equals(culture)
         || "japanese".equals(culture) || "inuits".equals(culture)) {
         String orgA = culture + "_bandits_a";
         String orgB = culture + "_bandits_b";
         out.add(ActivityProfile.builder(culture + "_bandits_opportunist")
            .roles("bandit", "raider", "pillager", "outlaw")
            .tags("hostile", "raider")
            .kinds(ActivityKind.BANDIT_ROAM, ActivityKind.AMBUSH, ActivityKind.VILLAGE_RAID, ActivityKind.SKIRMISH)
            .size(3, 5).range(320).homeDefense(1).awayFraction(0.50f).cooldown(12000L)
            .aggression(0.7f).risk(0.6f).retreat(0.35f)
            .bandit(true).org(orgA).rivals(orgB).routes("road", "edge", "cover").build());
         out.add(ActivityProfile.builder(culture + "_bandits_rival")
            .roles("bandit", "raider", "pillager", "outlaw")
            .tags("hostile", "raider")
            .kinds(ActivityKind.BANDIT_ROAM, ActivityKind.AMBUSH, ActivityKind.SKIRMISH)
            .size(3, 5).range(320).homeDefense(1).awayFraction(0.50f).cooldown(12000L)
            .aggression(0.75f).risk(0.55f).retreat(0.35f)
            .bandit(true).org(orgB).rivals(orgA).routes("road", "edge", "cover").build());
      }
      return Collections.unmodifiableList(out);
   }
}
