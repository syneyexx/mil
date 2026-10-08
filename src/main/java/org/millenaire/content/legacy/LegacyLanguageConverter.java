package org.millenaire.content.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.millenaire.content.CultureIdPolicy;
import org.slf4j.Logger;

public final class LegacyLanguageConverter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   static final Pattern STRIP_VARIANT_LEVEL = Pattern.compile("_[a-z]\\d+$");
   static final String SENTENCES_SUFFIX = "_sentences.txt";
   static final String DIALOGUES_SUFFIX = "_dialogues.txt";
   static final String BUILDINGS_SUFFIX = "_buildings.txt";
   static final String STRINGS_SUFFIX = "_strings.txt";
   public static final String TRAVELBOOK_SUFFIX = "_travelbook.txt";
   static final String QUEST_LANG_FILE = "quest_lang.json";
   public static final String TRAVELBOOK_KEY_PREFIX = "travelbook.";
   static final String DESC_SUFFIX = ".desc";
   public static final String DESC_NS_PREFIX = "travelbook.millenaire.";
   private static final LegacyConversionReport.Kind KIND = LegacyConversionReport.Kind.LANGUAGE;
   private final Path sourceRoot;
   private final Path outputRoot;
   private final LegacyLayoutDetector.Detection detection;
   private final LegacyConversionReport report;
   private final ConverterOutputManifest manifest;
   private final Set<String> shippedPaths;
   private final Map<String, LegacyLanguageConverter.CultureIndex> indexes = new HashMap<>();

   public LegacyLanguageConverter(
      Path sourceRoot, Path outputRoot, LegacyLayoutDetector.Detection detection, LegacyConversionReport report, ConverterOutputManifest manifest
   ) {
      this.sourceRoot = sourceRoot;
      this.outputRoot = outputRoot;
      this.detection = detection;
      this.report = report;
      this.manifest = manifest;
      this.shippedPaths = collectShippedPaths(sourceRoot, detection);
   }

   public void convert() {
      try {
         this.doConvert();
      } catch (RuntimeException e) {
         LOGGER.error("Language conversion aborted for {}", this.sourceRoot, e);
         this.report.recordSkipped(null, KIND, "languages/", "language conversion aborted: " + e);
      }
   }

   private void doConvert() {
      for (LegacyLayoutDetector.IgnoredLanguageFile ignored : this.detection.ignoredLanguageFiles()) {
         this.report.recordSkipped(null, KIND, this.relPath(ignored.path()), ignored.reason());
      }

      List<LegacyLanguageConverter.LangFile> files = new ArrayList<>();

      for (Path p : this.detection.languageFiles()) {
         String folder = p.getParent().getFileName().toString().toLowerCase(Locale.ROOT);
         String locale = LegacyLocaleMap.toLocale(folder).orElse(null);
         if (locale == null) {
            this.report.recordSkipped(null, KIND, this.relPath(p), "unknown locale folder '" + folder + "'");
         } else {
            files.add(new LegacyLanguageConverter.LangFile(p, folder, locale, p.getFileName().toString().toLowerCase(Locale.ROOT)));
         }
      }

      if (!files.isEmpty()) {
         for (String culture : this.detection.culturesWithLegacyContent()) {
            if (!CultureIdPolicy.PATTERN.matcher(culture).matches()) {
               for (LegacyLanguageConverter.LangFile f : files) {
                  if (f.cultureOf().equals(culture)) {
                     this.report.recordSkipped(culture, KIND, this.relPath(f.path()), "culture id '" + culture + "' is invalid — culture not converted");
                  }
               }
            } else {
               this.convertSpeech(culture, files, "_sentences.txt");
               this.convertSpeech(culture, files, "_dialogues.txt");
               this.convertNames(culture, files);
            }
         }

         this.convertQuestTexts(files);
      }
   }

   private void convertSpeech(String culture, List<LegacyLanguageConverter.LangFile> files, String suffix) {
      List<LegacyLanguageConverter.LangFile> inputs = new ArrayList<>();

      for (LegacyLanguageConverter.LangFile f : files) {
         if (f.is(culture, suffix)) {
            inputs.add(f);
         }
      }

      if (!inputs.isEmpty()) {
         inputs.sort((a, b) -> {
            boolean aEn = "en".equals(a.folder());
            boolean bEn = "en".equals(b.folder());
            if (aEn != bEn) {
               return aEn ? -1 : 1;
            } else {
               return a.folder().compareTo(b.folder());
            }
         });
         boolean dialogue = "_dialogues.txt".equals(suffix);
         String kind = dialogue ? "dialogues" : "sentences";
         String fileName = culture + suffix;
         String nativeLogical = "languages/native/" + fileName;
         boolean nativeDone = false;
         if (this.shippedPaths.contains(nativeLogical)) {
            this.report.recordPreserved(culture, KIND, nativeLogical, "shipped by the pack in 9.0 form — not regenerated from legacy " + kind);
            nativeDone = true;
         }

         for (LegacyLanguageConverter.LangFile in : inputs) {
            List<String> lines;
            try {
               lines = LegacyDataParser.readLinesLenient(in.path());
            } catch (IOException e) {
               this.report.recordSkipped(culture, KIND, this.relPath(in.path()), "read failed: " + e.getMessage());
               continue;
            }

            List<String> nativeLines = new ArrayList<>(lines.size());
            List<String> translatedLines = new ArrayList<>(lines.size());

            for (String line : lines) {
               String[] split = dialogue ? splitDialogueLine(line) : splitSentenceLine(line);
               nativeLines.add(split[0]);
               translatedLines.add(split[1]);
            }

            if (!nativeDone) {
               Path nativeTarget = this.outputRoot.resolve("languages").resolve("native").resolve(fileName);
               if (this.writeLines(culture, nativeTarget, nativeLines)) {
                  this.report.recordLanguageNote(culture + ": native " + kind + " taken from " + in.folder() + "/" + in.path().getFileName());
                  nativeDone = true;
               }
            }

            String localeLogical = "languages/" + in.locale() + "/" + fileName;
            if (this.shippedPaths.contains(localeLogical)) {
               this.report
                  .recordPreserved(
                     culture,
                     KIND,
                     localeLogical,
                     "shipped by the pack in 9.0 form — legacy " + in.folder() + "/" + in.path().getFileName() + " not converted over it"
                  );
            } else {
               Path localeTarget = this.outputRoot.resolve("languages").resolve(in.locale()).resolve(fileName);
               this.writeLines(culture, localeTarget, translatedLines);
            }
         }
      }
   }

   private void convertNames(String culture, List<LegacyLanguageConverter.LangFile> files) {
      TreeMap<String, List<LegacyLanguageConverter.LangFile>> byFolder = new TreeMap<>();

      for (LegacyLanguageConverter.LangFile f : files) {
         if (f.is(culture, "_buildings.txt") || f.is(culture, "_strings.txt") || f.is(culture, "_travelbook.txt")) {
            byFolder.computeIfAbsent(f.folder(), k -> new ArrayList<>()).add(f);
         }
      }

      if (!byFolder.isEmpty()) {
         LegacyLanguageConverter.CultureIndex index = this.indexFor(culture);

         for (Entry<String, List<LegacyLanguageConverter.LangFile>> e : byFolder.entrySet()) {
            String locale = e.getValue().get(0).locale();
            Path target = this.outputRoot
               .resolve("cultures")
               .resolve(culture)
               .resolve("resourcepack")
               .resolve("assets")
               .resolve("millenaire")
               .resolve("lang")
               .resolve(locale + ".json");

            LinkedHashMap<String, String> json;
            try {
               json = Files.isRegularFile(target) ? readJsonStrings(target) : new LinkedHashMap<>();
            } catch (IOException ex) {
               this.report
                  .recordSkipped(
                     culture,
                     KIND,
                     this.outRel(target),
                     "existing lang JSON unreadable (" + ex.getMessage() + ") — legacy " + e.getKey() + "/ names not merged"
                  );
               continue;
            }

            LinkedHashMap<String, String> additions = new LinkedHashMap<>();

            for (LegacyLanguageConverter.LangFile f : e.getValue()) {
               LinkedHashMap<String, String> kv;
               try {
                  kv = readKeyValues(f.path());
               } catch (IOException ex) {
                  this.report.recordSkipped(culture, KIND, this.relPath(f.path()), "read failed: " + ex.getMessage());
                  continue;
               }

               if (f.is(culture, "_buildings.txt")) {
                  this.collectBuildingNames(culture, f, kv, index, additions);
               } else if (f.is(culture, "_strings.txt")) {
                  this.collectStringNames(culture, f, kv, index, additions);
               } else {
                  this.collectTravelBookDescs(culture, f, kv, index, additions);
               }
            }

            int added = 0;

            for (Entry<String, String> a : additions.entrySet()) {
               if (!json.containsKey(a.getKey())) {
                  json.put(a.getKey(), a.getValue());
                  added++;
               }
            }

            if (added == 0) {
               this.report.recordLanguageNote(culture + ": " + e.getKey() + "/ names — nothing to add to " + this.outRel(target));
            } else if (this.writeJsonStrings(culture, target, json)) {
               this.report.recordLanguageNote(culture + ": " + added + " key(s) from " + e.getKey() + "/ merged into " + this.outRel(target));
            }
         }
      }
   }

   private void collectBuildingNames(
      String culture, LegacyLanguageConverter.LangFile f, Map<String, String> kv, LegacyLanguageConverter.CultureIndex index, Map<String, String> out
   ) {
      LinkedHashMap<String, String> byId = new LinkedHashMap<>();

      for (Entry<String, String> entry : kv.entrySet()) {
         String id = stripVariantLevel(entry.getKey());
         byId.remove(id);
         byId.put(id, entry.getValue().trim());
      }

      List<String> unknown = new ArrayList<>();

      for (Entry<String, String> entry : byId.entrySet()) {
         String id = entry.getKey();
         String value = entry.getValue();
         if (index.buildingsValidatable && !index.buildingNatives.containsKey(id)) {
            unknown.add(id);
         } else {
            if (value.isEmpty()) {
               String nat = index.buildingNatives.get(id);
               if (nat == null || nat.isEmpty()) {
                  continue;
               }

               value = nat;
            }

            out.put(buildingKey(culture, id), value);
         }
      }

      if (!unknown.isEmpty()) {
         this.report
            .recordSkipped(
               culture,
               KIND,
               this.relPath(f.path()),
               unknown.size() + " building id(s) not in the converted plan set, not ported: " + String.join(", ", unknown)
            );
      }
   }

   private void collectStringNames(
      String culture, LegacyLanguageConverter.LangFile f, Map<String, String> kv, LegacyLanguageConverter.CultureIndex index, Map<String, String> out
   ) {
      LinkedHashMap<String, String> byRole = new LinkedHashMap<>();
      int villageKeys = 0;

      for (Entry<String, String> entry : kv.entrySet()) {
         String key = entry.getKey().toLowerCase(Locale.ROOT);
         if (key.startsWith("villager.")) {
            String role = key.substring("villager.".length());
            byRole.remove(role);
            byRole.put(role, entry.getValue().trim());
         } else if (key.equals("culture." + culture)) {
            String value = entry.getValue().trim();
            if (!value.isEmpty()) {
               out.put(cultureKey(culture), value);
            }
         } else if (key.startsWith("village.")) {
            villageKeys++;
         }
      }

      List<String> unknown = new ArrayList<>();

      for (Entry<String, String> entry : byRole.entrySet()) {
         String role = entry.getKey();
         String value = entry.getValue();
         String simple;
         if (index.villagersValidatable) {
            simple = resolveAgainst(culture, role, index.roleNatives::containsKey);
            if (simple == null) {
               unknown.add(role);
               continue;
            }
         } else {
            String stripped = stripCulturePrefix(culture, role);
            simple = stripped != null ? stripped : role;
         }

         if (value.isEmpty()) {
            String nat = index.roleNatives.get(simple);
            if (nat == null || nat.isEmpty()) {
               continue;
            }

            value = nat;
         }

         out.put(roleKey(culture, simple), value);
      }

      if (!unknown.isEmpty()) {
         this.report
            .recordSkipped(
               culture,
               KIND,
               this.relPath(f.path()),
               unknown.size() + " villager role(s) not in the converted villager types, not ported: " + String.join(", ", unknown)
            );
      }

      if (villageKeys > 0) {
         this.report
            .recordLanguageNote(culture + ": " + villageKeys + " village.* key(s) in " + this.relPath(f.path()) + " have no 9.0 target and were not ported");
      }
   }

   private void collectTravelBookDescs(
      String culture, LegacyLanguageConverter.LangFile f, Map<String, String> kv, LegacyLanguageConverter.CultureIndex index, Map<String, String> out
   ) {
      List<String> orphans = new ArrayList<>();

      for (Entry<String, String> entry : kv.entrySet()) {
         if (entry.getKey().startsWith("travelbook.")) {
            String rewriteKey = resolveRewriteKey(entry.getKey(), culture, index.inventory());
            if (rewriteKey == null) {
               orphans.add(entry.getKey());
            } else {
               out.put(rewriteKey, entry.getValue().trim());
            }
         }
      }

      if (!orphans.isEmpty()) {
         this.report
            .recordSkipped(
               culture, KIND, this.relPath(f.path()), orphans.size() + " description(s) with no converted target, not ported: " + String.join(", ", orphans)
            );
      }
   }

   private void convertQuestTexts(List<LegacyLanguageConverter.LangFile> files) {
      TreeMap<String, List<LegacyLanguageConverter.LangFile>> byCode = new TreeMap<>();

      for (LegacyLanguageConverter.LangFile f : files) {
         if (f.lowerName().startsWith("quests_") && f.lowerName().endsWith(".txt")) {
            String code = LegacyLocaleMap.toQuestTextCode(f.folder()).orElse(f.folder());
            byCode.computeIfAbsent(code, k -> new ArrayList<>()).add(f);
         }
      }

      for (Entry<String, List<LegacyLanguageConverter.LangFile>> e : byCode.entrySet()) {
         String code = e.getKey();
         String logical = "languages/" + code + "/quest_lang.json";
         if (this.shippedPaths.contains(logical)) {
            this.report.recordPreserved(null, KIND, logical, "shipped by the pack — legacy quests_*.txt not converted over it");
         } else {
            Map<String, String> texts = new TreeMap<>();
            List<LegacyLanguageConverter.LangFile> inputs = new ArrayList<>(e.getValue());
            inputs.sort((a, b) -> a.path().toString().compareTo(b.path().toString()));

            for (LegacyLanguageConverter.LangFile f : inputs) {
               try {
                  QuestTextConverter.parseQuestTextFile(f.path(), texts);
               } catch (IOException ex) {
                  this.report.recordSkipped(null, KIND, this.relPath(f.path()), "read failed: " + ex.getMessage());
               }
            }

            if (!texts.isEmpty()) {
               Path target = this.outputRoot.resolve("languages").resolve(code).resolve("quest_lang.json");
               if (this.writeJsonStrings(null, target, texts)) {
                  this.report
                     .recordLanguageNote(
                        code + "/: " + texts.size() + " quest text(s) from " + inputs.size() + " quests_*.txt merged into " + this.outRel(target)
                     );
               }
            }
         }
      }
   }

   public static String[] splitBilingualValue(String value) {
      String[] parts = value.split("/");
      if (parts.length > 1) {
         String nativeText = parts[0].trim();
         String translated = parts[1].trim();
         if (translated.isEmpty()) {
            translated = nativeText;
         }

         return new String[]{nativeText, translated};
      } else {
         return null;
      }
   }

   public static String[] splitSentenceLine(String line) {
      if (!line.isBlank() && !line.startsWith("//")) {
         int eqIdx = line.indexOf(61);
         if (eqIdx < 0) {
            return new String[]{line, line};
         }

         String key = line.substring(0, eqIdx + 1);
         String[] split = splitBilingualValue(line.substring(eqIdx + 1));
         return split == null ? new String[]{line, line} : new String[]{key + split[0], key + split[1]};
      } else {
         return new String[]{line, line};
      }
   }

   public static String extractTranslationFromSentenceLine(String line) {
      return splitSentenceLine(line)[1];
   }

   public static String[] splitDialogueLine(String line) {
      if (!line.isBlank() && !line.startsWith("//")) {
         String trimmed = line.trim();
         if (trimmed.startsWith("newchat;")) {
            return new String[]{line, line};
         }

         String[] parts = trimmed.split(";");
         if (parts.length != 3) {
            return new String[]{line, line};
         }

         String speaker = "v2".equals(parts[0].trim()) ? "v2" : "v1";
         String prefix = speaker + ";" + parts[1] + ";";
         String[] split = splitBilingualValue(parts[2]);
         return split == null ? new String[]{prefix + parts[2], prefix + parts[2]} : new String[]{prefix + split[0], prefix + split[1]};
      } else {
         return new String[]{line, line};
      }
   }

   public static String extractTranslationFromDialogueLine(String line) {
      return splitDialogueLine(line)[1];
   }

   public static String stripVariantLevel(String legacyPlanName) {
      return STRIP_VARIANT_LEVEL.matcher(legacyPlanName.toLowerCase(Locale.ROOT)).replaceAll("");
   }

   public static String buildingKey(String culture, String buildingId) {
      return "building.millenaire." + culture + "." + buildingId;
   }

   public static String roleKey(String culture, String role) {
      return "role.millenaire." + culture + "_" + role;
   }

   public static String cultureKey(String culture) {
      return "culture.millenaire." + culture;
   }

   public static String[] parseTypeAndName(String legacyKey) {
      if (!legacyKey.startsWith("travelbook.")) {
         return null;
      }

      String body = legacyKey.substring("travelbook.".length());
      if (body.endsWith(".desc")) {
         body = body.substring(0, body.length() - ".desc".length());
      }

      int dot = body.indexOf(46);
      return dot < 0 ? new String[]{body, ""} : new String[]{body.substring(0, dot), body.substring(dot + 1)};
   }

   public static String resolveSimpleName(LegacyLanguageConverter.RewriteInventory inv, String culture, String type, String name) {
      return "culture".equals(type) ? "" : resolveAgainst(culture, name, simple -> inv.has(culture, type, simple));
   }

   static String resolveAgainst(String culture, String name, Predicate<String> exists) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (exists.test(lower)) {
         return lower;
      }

      String stripped = stripCulturePrefix(culture, lower);
      return stripped != null && exists.test(stripped) ? stripped : null;
   }

   static String stripCulturePrefix(String culture, String lowerName) {
      String prefix = culture + "_";
      return lowerName.startsWith(prefix) ? lowerName.substring(prefix.length()) : null;
   }

   public static String buildRewriteKey(String culture, String type, String simpleName) {
      String base = "travelbook.millenaire." + culture + "." + type;
      return simpleName.isEmpty() ? base + ".desc" : base + "." + simpleName + ".desc";
   }

   public static String resolveRewriteKey(String legacyKey, String culture, LegacyLanguageConverter.RewriteInventory inv) {
      String[] typeAndName = parseTypeAndName(legacyKey);
      if (typeAndName == null) {
         return null;
      }

      String resolved = resolveSimpleName(inv, culture, typeAndName[0], typeAndName[1]);
      return resolved == null ? null : buildRewriteKey(culture, typeAndName[0], resolved);
   }

   public static String cultureFromTravelbookFileName(String fileName) {
      return !fileName.endsWith("_travelbook.txt") ? null : fileName.substring(0, fileName.length() - "_travelbook.txt".length());
   }

   private LegacyLanguageConverter.CultureIndex indexFor(String culture) {
      return this.indexes.computeIfAbsent(culture, this::loadCultureIndex);
   }

   private LegacyLanguageConverter.CultureIndex loadCultureIndex(String culture) {
      LegacyLanguageConverter.CultureIndex index = new LegacyLanguageConverter.CultureIndex(culture);
      Path cultureRoot = this.outputRoot.resolve("cultures").resolve(culture);
      List<String> failures = new ArrayList<>();

      for (Path p : listJsonFiles(cultureRoot.resolve("buildings"), failures)) {
         try {
            JsonObject obj = readJsonObject(p);
            if (obj.has("building_id")) {
               String id = obj.get("building_id").getAsString().toLowerCase(Locale.ROOT);
               String nat = obj.has("native_name") ? obj.get("native_name").getAsString() : "";
               index.buildingNatives.put(id, nat);
            }
         } catch (IOException | RuntimeException e) {
            failures.add(this.outRel(p) + " (" + e.getMessage() + ")");
         }
      }

      if (!failures.isEmpty()) {
         index.buildingsValidatable = false;
         LOGGER.warn("[{}] building index incomplete, id validation disabled: {}", culture, failures);
         this.report.recordLanguageNote(culture + ": building plan JSON unreadable, building-name id validation disabled: " + String.join("; ", failures));
      }

      failures = new ArrayList<>();

      for (Path p : listJsonFiles(cultureRoot.resolve("villagers"), failures)) {
         String fileName = p.getFileName().toString();
         String role = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT);

         try {
            JsonObject obj = readJsonObject(p);
            String nat = obj.has("native_name") ? obj.get("native_name").getAsString() : "";
            index.roleNatives.put(role, nat);
         } catch (IOException | RuntimeException e) {
            failures.add(this.outRel(p) + " (" + e.getMessage() + ")");
         }
      }

      if (!failures.isEmpty()) {
         index.villagersValidatable = false;
         LOGGER.warn("[{}] villager index incomplete, role validation disabled: {}", culture, failures);
         this.report.recordLanguageNote(culture + ": villager type JSON unreadable, role-name id validation disabled: " + String.join("; ", failures));
      }

      for (Path p : listJsonFiles(cultureRoot.resolve("villages"), new ArrayList<>())) {
         String fileName = p.getFileName().toString();
         index.villageIds.add(fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT));
      }

      Path tradedGoods = cultureRoot.resolve("traded_goods.json");
      if (Files.isRegularFile(tradedGoods)) {
         try {
            index.goodIds.addAll(readGoodIds(tradedGoods));
         } catch (IOException | RuntimeException e) {
            this.report
               .recordLanguageNote(culture + ": " + this.outRel(tradedGoods) + " unreadable (" + e.getMessage() + ") — trade-good descriptions not ported");
         }
      }

      return index;
   }

   private static List<Path> listJsonFiles(Path dir, List<String> failures) {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      try (Stream<Path> stream = Files.walk(dir)) {
         return stream.filter(x$0 -> Files.isRegularFile(x$0))
            .filter(p -> p.getFileName().toString().endsWith(".json"))
            .filter(p -> !p.getFileName().toString().startsWith("_manifest"))
            .sorted()
            .toList();
      } catch (IOException e) {
         failures.add(dir + " (" + e.getMessage() + ")");
         return List.of();
      }
   }

   public static Set<String> readGoodIds(Path path) throws IOException {
      Set<String> out = new HashSet<>();
      JsonObject obj = readJsonObject(path);
      if (obj.has("goods") && obj.get("goods").isJsonArray()) {
         for (JsonElement e : obj.getAsJsonArray("goods")) {
            if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
               out.add(e.getAsJsonObject().get("id").getAsString().toLowerCase(Locale.ROOT));
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public static LinkedHashMap<String, String> readKeyValues(Path file) throws IOException {
      LinkedHashMap<String, String> map = new LinkedHashMap<>();

      for (String line : LegacyDataParser.readLinesLenient(file)) {
         String stripped = line.strip();
         if (!stripped.isEmpty() && !stripped.startsWith("//") && !stripped.startsWith("#")) {
            int eq = stripped.indexOf(61);
            if (eq > 0) {
               String key = stripped.substring(0, eq).strip();
               map.remove(key);
               map.put(key, stripped.substring(eq + 1));
            }
         }
      }

      return map;
   }

   public static LinkedHashMap<String, String> readJsonStrings(Path path) throws IOException {
      JsonObject obj = readJsonObject(path);
      LinkedHashMap<String, String> map = new LinkedHashMap<>();

      for (String key : obj.keySet()) {
         JsonElement v = obj.get(key);
         if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
            throw new IOException("malformed lang JSON: key '" + key + "' is not a string");
         }

         map.put(key, v.getAsString());
      }

      return map;
   }

   private static JsonObject readJsonObject(Path path) throws IOException {
      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         JsonElement el = JsonParser.parseReader(reader);
         if (el == null || !el.isJsonObject()) {
            throw new IOException("malformed JSON: not an object");
         } else {
            return el.getAsJsonObject();
         }
      } catch (RuntimeException e) {
         throw new IOException("malformed JSON: " + e.getMessage(), e);
      }
   }

   private boolean writeLines(String culture, Path target, List<String> lines) {
      return this.writeAndRecord(culture, target, String.join("\n", lines));
   }

   private boolean writeJsonStrings(String culture, Path target, Map<String, String> map) {
      JsonObject obj = new JsonObject();

      for (Entry<String, String> entry : new TreeMap<>(map).entrySet()) {
         obj.addProperty(entry.getKey(), entry.getValue());
      }

      return this.writeAndRecord(culture, target, GSON.toJson(obj) + "\n");
   }

   private boolean writeAndRecord(String culture, Path target, String content) {
      try {
         LegacyConversionEngine.writeAtomically(target, content);
         this.manifest.recordFile(this.outputRoot, target, null);
         this.report.recordConverted(culture, KIND);
         return true;
      } catch (IOException e) {
         LOGGER.warn("Failed to write {}: {}", target, e.getMessage());
         this.report.recordSkipped(culture, KIND, this.outRel(target), "write failed: " + e.getMessage());
         return false;
      }
   }

   private static Set<String> collectShippedPaths(Path sourceRoot, LegacyLayoutDetector.Detection detection) {
      Set<String> out = new TreeSet<>();

      for (Path p : detection.preservedPaths()) {
         Path rel;
         try {
            rel = sourceRoot.relativize(p);
         } catch (IllegalArgumentException e) {
            continue;
         }

         Path logical = LegacyConversionDriver.stripWrapperPrefix(rel);
         out.add(logical.toString().replace('\\', '/').toLowerCase(Locale.ROOT));
      }

      return out;
   }

   private String relPath(Path p) {
      try {
         return this.sourceRoot.relativize(p).toString().replace('\\', '/');
      } catch (IllegalArgumentException e) {
         return p.toString();
      }
   }

   private String outRel(Path p) {
      try {
         return this.outputRoot.relativize(p).toString().replace('\\', '/');
      } catch (IllegalArgumentException e) {
         return p.toString();
      }
   }

   static final class CultureIndex {
      final Map<String, String> buildingNatives = new HashMap<>();
      final Map<String, String> roleNatives = new HashMap<>();
      final Set<String> villageIds = new HashSet<>();
      final Set<String> goodIds = new HashSet<>();
      boolean buildingsValidatable = true;
      boolean villagersValidatable = true;
      private final String culture;

      CultureIndex(String culture) {
         this.culture = culture;
      }

      LegacyLanguageConverter.RewriteInventory inventory() {
         Map<String, Set<String>> map = new HashMap<>();
         map.put(this.culture + "/building", this.buildingNatives.keySet());
         map.put(this.culture + "/villager", this.roleNatives.keySet());
         map.put(this.culture + "/village", this.villageIds);
         map.put(this.culture + "/trade_good", this.goodIds);
         return new LegacyLanguageConverter.RewriteInventory(map);
      }
   }

   private record LangFile(Path path, String folder, String locale, String lowerName) {
      boolean is(String culture, String suffix) {
         return this.lowerName.equals(culture + suffix);
      }

      String cultureOf() {
         for (String suffix : LegacyLayoutDetector.CULTURE_LANGUAGE_SUFFIXES) {
            if (this.lowerName.endsWith(suffix)) {
               return this.lowerName.substring(0, this.lowerName.length() - suffix.length());
            }
         }

         return "";
      }
   }

   public static final class RewriteInventory {
      private final Map<String, Set<String>> byCultureType;

      public RewriteInventory(Map<String, Set<String>> byCultureType) {
         this.byCultureType = byCultureType;
      }

      public boolean has(String culture, String type, String name) {
         Set<String> names = this.byCultureType.get(culture + "/" + type);
         return names != null && names.contains(name);
      }

      public int size() {
         return this.byCultureType.values().stream().mapToInt(Set::size).sum();
      }

      public int groups() {
         return this.byCultureType.size();
      }
   }
}
