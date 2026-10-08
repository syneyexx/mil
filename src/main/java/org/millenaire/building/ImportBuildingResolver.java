package org.millenaire.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceElection;
import org.millenaire.content.SourceKind;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class ImportBuildingResolver {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_JSON_BYTES = 2097152;
   private static final int MAX_LISTING_ENTRIES = 4096;
   private static final Set<String> FRAGMENT_HONOURED_KEYS = Set.of("culture", "building_id", "variants", "fragment");

   private ImportBuildingResolver() {
   }

   public static Optional<ImportBuildingResolver.ResolvedBuilding> resolveBuilding(String cultureKey, String buildingId) {
      if (!isBlank(cultureKey) && !isBlank(buildingId)) {
         String culturePath = culturePathOf(cultureKey);
         if (culturePath == null) {
            return Optional.empty();
         }

         CustomContentIndex snapshot = CustomContentIndex.current();
         return resolveBuilding(cultureKey, buildingId, exportedStratum(snapshot, culturePath), installedStratum(snapshot, culturePath));
      } else {
         return Optional.empty();
      }
   }

   public static Optional<ImportBuildingResolver.ResolvedBuilding> resolveBuilding(
      String cultureKey, String buildingId, ContentFs exportedFs, ContentFs installedFs
   ) {
      if (!isBlank(cultureKey) && !isBlank(buildingId)) {
         ImportBuildingResolver.Scan exported = scan(exportedFs, true);
         ImportBuildingResolver.Scan installed = scan(installedFs, false);
         return build(cultureKey, buildingId.toLowerCase(Locale.ROOT), exported, installed);
      } else {
         return Optional.empty();
      }
   }

   public static List<ImportBuildingResolver.ResolvedBuilding> resolveCulture(String cultureKey) {
      if (isBlank(cultureKey)) {
         return List.of();
      }

      String culturePath = culturePathOf(cultureKey);
      if (culturePath == null) {
         return List.of();
      }

      CustomContentIndex snapshot = CustomContentIndex.current();
      return resolveCulture(cultureKey, exportedStratum(snapshot, culturePath), installedStratum(snapshot, culturePath));
   }

   public static List<ImportBuildingResolver.ResolvedBuilding> resolveCulture(String cultureKey, ContentFs exportedFs, ContentFs installedFs) {
      if (isBlank(cultureKey)) {
         return List.of();
      }

      ImportBuildingResolver.Scan exported = scan(exportedFs, true);
      ImportBuildingResolver.Scan installed = scan(installedFs, false);
      Set<String> ids = new TreeSet<>();
      ids.addAll(exported.basesById.keySet());
      ids.addAll(installed.basesById.keySet());
      List<ImportBuildingResolver.ResolvedBuilding> out = new ArrayList<>(Math.min(ids.size(), 4096));

      for (String id : ids) {
         if (out.size() >= 4096) {
            LOGGER.warn("ImportBuildingResolver: culture {} listing truncated at {} entries", cultureKey, 4096);
            break;
         }

         build(cultureKey, id, exported, installed).ifPresent(out::add);
      }

      return out;
   }

   public static ContentFs exportedStratum(CustomContentIndex snapshot, String culturePath) {
      return snapshot.exportedFs().sub("cultures/" + culturePath);
   }

   public static ContentFs installedStratum(CustomContentIndex snapshot, String culturePath) {
      return snapshot.forCulture(culturePath);
   }

   private static Optional<ImportBuildingResolver.ResolvedBuilding> build(
      String cultureKey, String buildingId, ImportBuildingResolver.Scan exported, ImportBuildingResolver.Scan installed
   ) {
      ImportBuildingResolver.Elected fromExport = elect(exported, buildingId);
      ImportBuildingResolver.Elected fromInstalled = elect(installed, buildingId);
      ImportBuildingResolver.Elected winner = fromExport != null ? fromExport : fromInstalled;
      if (winner == null) {
         return Optional.empty();
      }

      JsonObject effective = winner.effectiveJson;
      if (effective == null) {
         return Optional.empty();
      }

      JsonObject union = effective;
      if (fromExport != null && fromInstalled != null && fromInstalled.effectiveJson != null) {
         union = unionOf(effective, fromInstalled.effectiveJson);
      }

      List<Resource> shadowed = new ArrayList<>();
      collectShadowed(fromExport, winner, shadowed);
      collectShadowed(fromInstalled, winner, shadowed);
      if (!shadowed.isEmpty()) {
         for (Resource s : shadowed) {
            LOGGER.debug(
               "ImportBuildingResolver: '{}' at '{}' ({}) shadowed by '{}' ({})",
               new Object[]{buildingId, s.relPath(), s.source().displayName(), winner.base.relPath(), winner.base.source().displayName()}
            );
         }
      }

      ImportBuildingResolver.Provenance provenance = winner.stratumExported
         ? ImportBuildingResolver.Provenance.EXPORTED
         : ImportBuildingResolver.Provenance.ofInstalled(winner.base.kind());
      ImportBuildingResolver.InstalledView installedView = winner != fromInstalled && fromInstalled != null
         ? new ImportBuildingResolver.InstalledView(fromInstalled.fs, fromInstalled.category)
         : null;
      return Optional.of(
         new ImportBuildingResolver.ResolvedBuilding(
            cultureKey,
            buildingId,
            winner.base,
            effective,
            union,
            winner.category,
            provenance,
            List.copyOf(shadowed),
            winner.fs,
            installedView,
            isRegistered(cultureKey, buildingId)
         )
      );
   }

   private static void collectShadowed(@Nullable ImportBuildingResolver.Elected candidate, ImportBuildingResolver.Elected winner, List<Resource> out) {
      if (candidate != null) {
         if (candidate != winner) {
            out.add(candidate.base);
         }

         for (Resource loser : candidate.losers) {
            out.add(loser);
         }
      }
   }

   @Nullable
   private static ImportBuildingResolver.Elected elect(ImportBuildingResolver.Scan scan, String buildingId) {
      List<Resource> hits = scan.basesById.get(buildingId);
      if (hits != null && !hits.isEmpty()) {
         int idx = SourceElection.servedIndexOf(hits);
         List<Integer> order = new ArrayList<>(hits.size());
         order.add(idx);

         for (int i = hits.size() - 1; i >= 0; i--) {
            if (i != idx) {
               order.add(i);
            }
         }

         for (int candidate : order) {
            Resource base = hits.get(candidate);
            String category = categoryOf(base.relPath());
            if (category != null) {
               JsonObject root = readJson(base);
               if (root != null) {
                  if (candidate != idx) {
                     LOGGER.warn(
                        "Building '{}': the elected file {} is unusable — falling back to {}",
                        new Object[]{buildingId, hits.get(idx).relPath(), base.relPath()}
                     );
                  }

                  List<Resource> losers = new ArrayList<>(hits.size() - 1);

                  for (int i = 0; i < hits.size(); i++) {
                     if (i != candidate) {
                        losers.add(hits.get(i));
                     }
                  }

                  String dir = base.relPath().substring(0, base.relPath().lastIndexOf(47));
                  List<Resource> fragments = scan.fragmentsByDirAndBase.get(dir + "/" + buildingId);
                  JsonObject effective = mergeFragments(root, buildingId, fragments);
                  return new ImportBuildingResolver.Elected(base, losers, category, scan.fs, scan.exported, effective);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static ImportBuildingResolver.Scan scan(ContentFs fs, boolean exported) {
      Set<String> disabled = readDisabledIds(fs);
      Map<String, List<Resource>> bases = new LinkedHashMap<>();
      Map<String, List<Resource>> fragments = new LinkedHashMap<>();

      try (Stream<Resource> walk = fs.walk("buildings", 4)) {
         for (Resource res : walk.toList()) {
            String relPath = res.relPath();
            if (relPath.endsWith(".json")) {
               int slash = relPath.lastIndexOf(47);
               if (slash >= 0) {
                  String fileName = relPath.substring(slash + 1);
                  if (!fileName.startsWith("_") && !fileName.endsWith("_special_points.json")) {
                     String[] parts = relPath.split("/");
                     if (parts.length >= 3 && "buildings".equals(parts[parts.length - 3])) {
                        String stem = fileName.substring(0, fileName.length() - ".json".length());
                        if (!stem.isEmpty()) {
                           int dot = stem.indexOf(46);
                           if (dot > 0) {
                              if (!disabled.contains(stem)) {
                                 String baseId = stem.substring(0, dot);
                                 if (!disabled.contains(baseId)) {
                                    String dir = relPath.substring(0, slash);
                                    fragments.computeIfAbsent(dir + "/" + baseId, k -> new ArrayList<>()).add(res);
                                 }
                              }
                           } else if (!disabled.contains(stem)) {
                              bases.computeIfAbsent(stem, k -> new ArrayList<>()).add(res);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return new ImportBuildingResolver.Scan(fs, exported, bases, fragments);
   }

   private static Set<String> readDisabledIds(ContentFs fs) {
      List<Resource> manifests = fs.findAll("buildings/_disabled.json");
      if (manifests.isEmpty()) {
         return Set.of();
      }

      Set<String> out = new HashSet<>();

      for (Resource res : manifests) {
         JsonElement root = readJsonElement(res);
         if (root != null && root.isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray()) {
               if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                  out.add(e.getAsString().toLowerCase(Locale.ROOT));
               }
            }
         }
      }

      return out;
   }

   private static JsonObject mergeFragments(JsonObject base, String buildingId, @Nullable List<Resource> fragments) {
      if (fragments != null && !fragments.isEmpty()) {
         List<Resource> ordered = new ArrayList<>(fragments);
         ordered.sort(Comparator.<Resource>comparingInt(r -> r.kind().ordinal()).thenComparing(Resource::relPath));
         String declaredCulture = base.has("culture") && base.get("culture").isJsonPrimitive() ? base.get("culture").getAsString() : null;
         Map<String, JsonObject> byLetter = new LinkedHashMap<>();
         if (base.has("variants") && base.get("variants").isJsonArray()) {
            for (JsonElement el : base.getAsJsonArray("variants")) {
               if (el.isJsonObject()) {
                  JsonObject variantObj = el.getAsJsonObject();
                  if (variantObj.has("variant")) {
                     byLetter.put(variantObj.get("variant").getAsString(), variantObj);
                  }
               }
            }
         }

         int applied = 0;

         for (Resource fragment : ordered) {
            JsonObject fragmentJson = readJson(fragment);
            if (fragmentJson != null) {
               if (fragmentJson.has("fragment") && fragmentJson.get("fragment").isJsonPrimitive() && fragmentJson.get("fragment").getAsBoolean()) {
                  String fragId = fragmentJson.has("building_id") ? fragmentJson.get("building_id").getAsString() : null;
                  if (fragId != null && fragId.equalsIgnoreCase(buildingId)) {
                     String fragCulture = fragmentJson.has("culture") ? fragmentJson.get("culture").getAsString() : null;
                     if (declaredCulture != null && (fragCulture == null || !fragCulture.equals(declaredCulture))) {
                        LOGGER.warn(
                           "[Millenaire] Variant fragment '{}' declares culture '{}' but extends a '{}' plan set — ignored.",
                           new Object[]{fragment.relPath(), fragCulture, declaredCulture}
                        );
                     } else if (fragmentJson.has("variants") && fragmentJson.get("variants").isJsonArray()) {
                        for (String key : fragmentJson.keySet()) {
                           if (!FRAGMENT_HONOURED_KEYS.contains(key)) {
                              LOGGER.warn(
                                 "[Millenaire] Variant fragment '{}' declares plan-set field '{}', which fragments cannot override — ignored.",
                                 fragment.relPath(),
                                 key
                              );
                           }
                        }

                        for (JsonElement el : fragmentJson.getAsJsonArray("variants")) {
                           if (el.isJsonObject()) {
                              JsonObject variantObj = el.getAsJsonObject();
                              if (!variantObj.has("variant")) {
                                 LOGGER.warn(
                                    "[Millenaire] Variant fragment '{}' has a variant entry with no 'variant' letter — the whole fragment is ignored.",
                                    fragment.relPath()
                                 );
                                 break;
                              }

                              byLetter.put(variantObj.get("variant").getAsString(), variantObj);
                              applied++;
                           }
                        }
                     } else {
                        LOGGER.warn("[Millenaire] Variant fragment '{}' has no 'variants' array — ignored.", fragment.relPath());
                     }
                  } else {
                     LOGGER.warn(
                        "[Millenaire] Variant fragment '{}' declares building_id '{}' but sits next to '{}' — ignored.",
                        new Object[]{fragment.relPath(), fragId, buildingId}
                     );
                  }
               } else {
                  LOGGER.warn(
                     "[Millenaire] '{}' is named like a variant fragment of '{}' but does not declare \"fragment\": true — ignored.",
                     fragment.relPath(),
                     buildingId
                  );
               }
            }
         }

         if (applied == 0) {
            return base;
         }

         JsonObject merged = base.deepCopy();
         JsonArray variants = new JsonArray();

         for (JsonObject variantObj : byLetter.values()) {
            variants.add(variantObj);
         }

         merged.add("variants", variants);
         return merged;
      } else {
         return base;
      }
   }

   static JsonObject unionOf(JsonObject elected, JsonObject installed) {
      Map<String, JsonObject> byLetter = new LinkedHashMap<>();

      for (JsonElement el : arrayOf(elected, "variants")) {
         if (el.isJsonObject()) {
            JsonObject v = el.getAsJsonObject();
            if (v.has("variant")) {
               byLetter.put(v.get("variant").getAsString(), v);
            }
         }
      }

      boolean changed = false;

      for (JsonElement el : arrayOf(installed, "variants")) {
         if (el.isJsonObject()) {
            JsonObject installedVariant = el.getAsJsonObject();
            if (installedVariant.has("variant")) {
               String letter = installedVariant.get("variant").getAsString();
               JsonObject electedVariant = byLetter.get(letter);
               if (electedVariant == null) {
                  byLetter.put(letter, installedVariant.deepCopy());
                  changed = true;
               } else {
                  Set<Integer> known = new LinkedHashSet<>();
                  JsonArray electedLevels = arrayOf(electedVariant, "levels");

                  for (JsonElement le : electedLevels) {
                     if (le.isJsonObject() && le.getAsJsonObject().has("level")) {
                        known.add(le.getAsJsonObject().get("level").getAsInt());
                     }
                  }

                  JsonArray mergedLevels = null;

                  for (JsonElement le : arrayOf(installedVariant, "levels")) {
                     if (le.isJsonObject()) {
                        JsonObject levelObj = le.getAsJsonObject();
                        if (levelObj.has("level") && !known.contains(levelObj.get("level").getAsInt())) {
                           if (mergedLevels == null) {
                              mergedLevels = electedLevels.deepCopy();
                           }

                           mergedLevels.add(levelObj.deepCopy());
                        }
                     }
                  }

                  if (mergedLevels != null) {
                     JsonObject copy = electedVariant.deepCopy();
                     copy.add("levels", sortLevels(mergedLevels));
                     byLetter.put(letter, copy);
                     changed = true;
                  }
               }
            }
         }
      }

      if (!changed) {
         return elected;
      }

      JsonObject union = elected.deepCopy();
      JsonArray variants = new JsonArray();

      for (JsonObject v : byLetter.values()) {
         variants.add(v);
      }

      union.add("variants", variants);
      return union;
   }

   private static JsonArray sortLevels(JsonArray levels) {
      List<JsonObject> objs = new ArrayList<>(levels.size());

      for (JsonElement e : levels) {
         if (e.isJsonObject()) {
            objs.add(e.getAsJsonObject());
         }
      }

      objs.sort(Comparator.comparingInt(ox -> ox.has("level") ? ox.get("level").getAsInt() : 0));
      JsonArray out = new JsonArray();

      for (JsonObject o : objs) {
         out.add(o);
      }

      return out;
   }

   private static JsonArray arrayOf(JsonObject root, String field) {
      return root != null && root.has(field) && root.get(field).isJsonArray() ? root.getAsJsonArray(field) : new JsonArray();
   }

   public static Map<String, List<Integer>> variantLevels(JsonObject plan) {
      Map<String, List<Integer>> out = new TreeMap<>();

      for (JsonElement ve : arrayOf(plan, "variants")) {
         if (ve.isJsonObject()) {
            JsonObject variantObj = ve.getAsJsonObject();
            if (variantObj.has("variant")) {
               List<Integer> levels = new ArrayList<>();

               for (JsonElement le : arrayOf(variantObj, "levels")) {
                  if (le.isJsonObject()) {
                     JsonObject levelObj = le.getAsJsonObject();
                     if (levelObj.has("level")) {
                        levels.add(levelObj.get("level").getAsInt());
                     }
                  }
               }

               levels.sort(Integer::compareTo);
               out.put(variantObj.get("variant").getAsString(), List.copyOf(levels));
            }
         }
      }

      return out;
   }

   @Nullable
   static String categoryOf(String relPath) {
      String[] parts = relPath.split("/");
      return parts.length >= 3 && "buildings".equals(parts[parts.length - 3]) ? parts[parts.length - 2] : null;
   }

   @Nullable
   static String culturePathOf(String cultureKey) {
      ResourceLocation rl = ResourceLocation.tryParse(cultureKey);
      return rl == null ? null : rl.getPath();
   }

   private static boolean isRegistered(String cultureKey, String buildingId) {
      ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
      return planSetId != null && ModCultures.getBuildingPlanSet(planSetId) != null;
   }

   @Nullable
   private static JsonObject readJson(Resource res) {
      JsonElement el = readJsonElement(res);
      return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
   }

   @Nullable
   private static JsonElement readJsonElement(Resource res) {
      try (InputStream is = res.open()) {
         byte[] bytes = is.readNBytes(2097153);
         if (bytes.length > 2097152) {
            LOGGER.warn("ImportBuildingResolver: {} exceeds {} bytes, skipped", res.relPath(), 2097152);
            return null;
         } else {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
         }
      } catch (Exception e) {
         LOGGER.warn("ImportBuildingResolver: failed to read {}: {}", res.relPath(), e.getMessage());
         return null;
      }
   }

   private static boolean isBlank(@Nullable String s) {
      return s == null || s.isEmpty();
   }

   private static final class Elected {
      final Resource base;
      final List<Resource> losers;
      final String category;
      final ContentFs fs;
      final boolean stratumExported;
      @Nullable
      final JsonObject effectiveJson;

      Elected(Resource base, List<Resource> losers, String category, ContentFs fs, boolean stratumExported, @Nullable JsonObject effectiveJson) {
         this.base = base;
         this.losers = losers;
         this.category = category;
         this.fs = fs;
         this.stratumExported = stratumExported;
         this.effectiveJson = effectiveJson;
      }
   }

   public record InstalledView(ContentFs fs, String category) {
   }

   public enum Provenance {
      EXPORTED,
      SUBMOD,
      STANDARD,
      JAR;

      static ImportBuildingResolver.Provenance ofInstalled(SourceKind kind) {
         return switch (kind) {
            case CLASSPATH -> JAR;
            case STANDARD -> STANDARD;
            case SUBMOD -> SUBMOD;
         };
      }

      public String badge() {
         return switch (this) {
            case EXPORTED -> "export";
            case SUBMOD -> "sub-mod";
            case STANDARD -> "standard";
            case JAR -> "built-in";
         };
      }
   }

   public record ResolvedBuilding(
      String cultureKey,
      String buildingId,
      Resource json,
      JsonObject effectiveJson,
      JsonObject unionPlan,
      String category,
      ImportBuildingResolver.Provenance provenance,
      List<Resource> shadowed,
      ContentFs stratum,
      @Nullable ImportBuildingResolver.InstalledView installed,
      boolean registered
   ) {
      public String dir() {
         return "buildings/" + this.category;
      }

      public String nbtPath(String variant, int level) {
         return this.dir() + "/" + this.buildingId + "_" + variant + "_" + level;
      }

      public Optional<Resource> nbt(String variant, int level) {
         return this.stratum.findFirst(this.nbtPath(variant, level) + ".nbt");
      }

      public Optional<Resource> seedNbt(String variant, int level) {
         Optional<Resource> hit = this.nbt(variant, level);
         return !hit.isPresent() && this.installed != null
            ? this.installed.fs().findFirst("buildings/" + this.installed.category() + "/" + this.buildingId + "_" + variant + "_" + level + ".nbt")
            : hit;
      }

      public boolean fromExports() {
         return this.provenance == ImportBuildingResolver.Provenance.EXPORTED;
      }
   }

   private record Scan(ContentFs fs, boolean exported, Map<String, List<Resource>> basesById, Map<String, List<Resource>> fragmentsByDirAndBase) {
   }
}
