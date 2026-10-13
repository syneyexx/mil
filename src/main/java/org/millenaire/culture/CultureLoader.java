package org.millenaire.culture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;
import org.millenaire.building.BlockCostRegistry;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.content.BuiltInCultures;
import org.millenaire.content.ContentFs;
import org.millenaire.content.ContentLoadReport;
import org.millenaire.content.ContentStatsReporter;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.dialogue.DialogueLoader;
import org.millenaire.dialogue.SentenceLoader;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.gathering.GatheringHandlerRegistry;
import org.slf4j.Logger;

public final class CultureLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final Gson GSON = JsonLoaderUtils.GSON;
   public static final List<String> BUILTIN_CULTURES = BuiltInCultures.IDS;
   static volatile List<String> activeCultures = BUILTIN_CULTURES;
   private static volatile List<String> cachedPlayerLanguages = null;

   private CultureLoader() {
   }

   public static List<String> discoverCultures() {
      List<String> result = new ArrayList<>(BUILTIN_CULTURES);
      Set<String> custom = CustomContentIndex.current().customCultureIds();
      result.addAll(custom);
      if (!custom.isEmpty()) {
         LOGGER.info("Discovered {} custom culture(s): {}", custom.size(), custom);
      }

      return result;
   }

   public static void loadAll() {
      ModCultures.clear();
      SentenceLoader.clear();
      DialogueLoader.clear();
      cachedPlayerLanguages = null;
      GatheringHandlerRegistry.clear();
      GatheringHandlerRegistry.registerDefaults();
      TradeGoodsLoader.clear();
      ShopProfileLoader.clear();
      BlockCostRegistry.clear();
      ContentLoadReport.clear();
      BuildingPlanSetLoader.resetCrossFolderTracking();
      VillagerTypeLoader.resetCrossFolderTracking();
      VillageTypeLoader.resetCrossFolderTracking();
      CustomContentIndex.rebuild(BUILTIN_CULTURES);
      WallTypeLoader.loadAll();
      BlockCostRegistry.loadAll();
      List<String> cultures = discoverCultures();
      activeCultures = List.copyOf(cultures);

      for (String cultureName : cultures) {
         loadCultureContent(cultureName);
      }

      resolveVillagerTypeGoods();
      LOGGER.info(
         "Millénaire content loaded: {} cultures, {} plans ({} sets), {} villager types, {} village types",
         new Object[]{
            ModCultures.getAllCultures().size(),
            ModCultures.getAllBuildingPlans().size(),
            ModCultures.getAllBuildingPlanSets().size(),
            ModCultures.getAllVillagerTypes().size(),
            ModCultures.getAllVillageTypes().size()
         }
      );
      ContentStatsReporter.capture(activeCultures, new HashSet<>(BUILTIN_CULTURES));
      org.millenaire.village.PlayerBuildingPlans.registerAll();
   }

   private static void resolveVillagerTypeGoods() {
      for (Entry<ResourceLocation, VillagerType> entry : ModCultures.getAllVillagerTypes().entrySet()) {
         VillagerType resolved = entry.getValue().withResolvedGoods();
         ModCultures.registerVillagerType(resolved);
      }
   }

   static void loadCultureContent(String cultureName) {
      try {
         loadCulture(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         CultureContentScanner.scan("building_plan", cultureName);
         CultureContentScanner.scan("villager_type", cultureName);
         CultureContentScanner.scan("village_type", cultureName);
      } catch (CrossFolderConflictException e) {
         ResourceLocation cultureRl = e.culture() != null ? e.culture() : ResourceLocation.fromNamespaceAndPath("millenaire", cultureName);
         ModCultures.unregisterCulture(cultureRl);
         LOGGER.error(
            "[Millenaire] Culture '{}' failed to load due to logical-id collision: {}. Registry rolled back; aborting remaining loaders for this culture.",
            cultureRl,
            e.getMessage()
         );
         return;
      }

      try {
         CultureMetadataLoader.loadReputationLabels(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading reputation labels {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         CultureMetadataLoader.loadCultureReputationLabels(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading culture reputation labels {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         CultureMetadataLoader.loadNameLists(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading namelists {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      ResourceLocation cultureRl = ResourceLocation.fromNamespaceAndPath("millenaire", cultureName);

      try {
         loadTradedGoods(cultureName);
         loadShopProfiles(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading commerce data for culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         loadSentencesAndDialogues(cultureName);
      } catch (RuntimeException e) {
         LOGGER.error("Error loading sentences/dialogues for culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         injectExtraBuildings(cultureRl);
      } catch (RuntimeException e) {
         LOGGER.error("Error injecting EXTRA buildings for culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         TravelBookCategoryAssigner.autoAssign(cultureRl);
      } catch (RuntimeException e) {
         LOGGER.error("Error assigning travel book categories for culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }

      try {
         validateNameListReferences(cultureRl);
      } catch (RuntimeException e) {
         LOGGER.error("Error validating namelist references for culture {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }
   }

   private static void loadTradedGoods(String cultureName) {
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureName);

      try {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(cultureName);
         List<Resource> resources = cultureFs.findAll("traded_goods.json");
         if (!resources.isEmpty()) {
            TradeGoodsLoader.loadFromResources(cultureId, resources);
         }
      } catch (Exception e) {
         LOGGER.error("Error loading traded_goods {}: {}", new Object[]{cultureName, e.getMessage(), e});
      }
   }

   private static void loadShopProfiles(String cultureName) {
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureName);
      Set<String> shopNames = new TreeSet<>();

      for (BuildingPlan bp : ModCultures.getAllBuildingPlans().values()) {
         if (bp.shopId() != null && bp.culture().equals(cultureId)) {
            shopNames.add(bp.shopId());
         }
      }

      if (!shopNames.isEmpty()) {
         ContentFs shopsFs = CustomContentIndex.current().forCulture(cultureName).sub("shops");
         int loaded = 0;

         for (String shopName : shopNames) {
            Optional<Resource> res = shopsFs.findFirst(shopName + ".json");
            if (!res.isEmpty()) {
               try (InputStream spStream = res.get().open()) {
                  ShopProfileLoader.load(cultureId, shopName, spStream);
                  loaded++;
               } catch (Exception e) {
                  LOGGER.error("Error loading shop profile {}: {}", new Object[]{shopName, e.getMessage(), e});
               }
            }
         }

         if (loaded > 0) {
            LOGGER.info("[Millenaire] Loaded {} shop profiles for culture {}", loaded, cultureName);
         }
      }
   }

   public static void resetPlayerLanguageCacheForTesting() {
      cachedPlayerLanguages = null;
   }

   static List<String> discoverPlayerLanguages() {
      if (cachedPlayerLanguages != null) {
         return cachedPlayerLanguages;
      }

      LinkedHashSet<String> languages = new LinkedHashSet<>();
      boolean fromManifest = readPlayerLanguagesManifest(languages);
      if (!fromManifest && languages.isEmpty()) {
         languages.add("en_us");
         languages.add("fr_fr");
      }

      addSubmodPlayerLanguages(languages);
      LOGGER.info("Loaded {} player languages: {}", languages.size(), languages);
      cachedPlayerLanguages = List.copyOf(languages);
      return cachedPlayerLanguages;
   }

   private static boolean readPlayerLanguagesManifest(LinkedHashSet<String> out) {
      ContentFs languagesFs = CustomContentIndex.current().forGlobalContent("languages");
      Optional<Resource> manifest = languagesFs.findFirst("_manifest.json");
      if (manifest.isEmpty()) {
         LOGGER.warn("Could not find languages/_manifest.json on the ContentFs overlay, falling back to en_us, fr_fr");
         return false;
      }

      try (
         InputStream is = manifest.get().open();
         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      ) {
         JsonObject obj = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         JsonArray arr = obj != null ? obj.getAsJsonArray("languages") : null;
         if (arr == null || arr.isEmpty()) {
            LOGGER.warn("Language manifest is empty, falling back to en_us, fr_fr");
            return false;
         }

         for (JsonElement e : arr) {
            String lang = e.getAsString();
            if (!"native".equals(lang)) {
               out.add(lang);
            }
         }

         return true;
      } catch (Exception e) {
         LOGGER.warn("Error reading language manifest, falling back to en_us, fr_fr: {}", e.getMessage());
         return false;
      }
   }

   private static void addSubmodPlayerLanguages(LinkedHashSet<String> out) {
      ContentFs languagesFs = CustomContentIndex.current().forGlobalContent("languages");
      languagesFs.walk("", Integer.MAX_VALUE).forEach(res -> {
         String relPath = res.relPath();
         String head = "languages/";
         if (relPath.startsWith(head)) {
            String tail = relPath.substring(head.length());
            int slash = tail.indexOf(47);
            if (slash > 0) {
               String locale = tail.substring(0, slash);
               if (!locale.isEmpty() && !locale.startsWith("_") && !"native".equals(locale)) {
                  out.add(locale);
               }
            }
         }
      });
   }

   private static void loadSentencesAndDialogues(String cultureName) {
      SentenceLoader.loadSentences(cultureName, "native");
      DialogueLoader.loadDialogues(cultureName, "native");

      for (String lang : discoverPlayerLanguages()) {
         SentenceLoader.loadSentences(cultureName, lang);
         DialogueLoader.loadDialogues(cultureName, lang);
      }
   }

   public static void validateAll(GoalRegistry goalRegistry) {
      int warnings = 0;
      int checks = 0;
      Set<ResourceLocation> villageTypesToDisable = new LinkedHashSet<>();

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (vt.weight() > 0 && vt.biomeTags().isEmpty()) {
            LOGGER.error(
               "VillageType {} has weight={} but no biomeTags — will never spawn naturally (biome pre-filter requires at least one tag). Add biomeTags or set weight=0.",
               vt.id(),
               vt.weight()
            );
            warnings++;
         }

         checks++;
      }

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         for (VillageType.LayoutSlot slot : vt.layout()) {
            checks++;
            if (ModCultures.getBuildingPlanSet(slot.plan()) == null) {
               LOGGER.error(
                  "VillageType {}: layout slot references non-existent BuildingPlanSet {} — DISABLING this village type (would NPE at spawn)",
                  vt.id(),
                  slot.plan()
               );
               villageTypesToDisable.add(vt.id());
               warnings++;
            }
         }

         for (ResourceLocation pbId : vt.playerBuildings()) {
            checks++;
            if (ModCultures.getBuildingPlanSet(pbId) == null) {
               LOGGER.error("VillageType {}: playerBuilding references non-existent BuildingPlanSet: {}", vt.id(), pbId);
               warnings++;
            }
         }

         for (ResourceLocation hamletId : vt.hamlets()) {
            checks++;
            if (ModCultures.getVillageType(hamletId) == null) {
               LOGGER.error("VillageType {}: hamlets references non-existent VillageType: {}", vt.id(), hamletId);
               warnings++;
            }
         }
      }

      for (VillagerType villagerType : ModCultures.getAllVillagerTypes().values()) {
         for (ResourceLocation goalId : villagerType.goals()) {
            checks++;
            if (goalRegistry.get(goalId) == null) {
               LOGGER.warn("VillagerType {}: missing goal in GoalRegistry: {}", villagerType.id(), goalId);
               warnings++;
            }
         }
      }

      for (BuildingPlanSet set : ModCultures.getAllBuildingPlanSets().values()) {
         for (List<BuildingPlanSet.LevelDef> levels : set.variants().values()) {
            for (BuildingPlanSet.LevelDef levelDef : levels) {
               checks++;
               if (ModCultures.getBuildingPlan(levelDef.planId()) == null) {
                  LOGGER.warn("BuildingPlanSet {}: LevelDef references non-existent BuildingPlan: {}", set.id(), levelDef.planId());
                  warnings++;
               }
            }
         }
      }

      for (BuildingPlan plan : ModCultures.getAllBuildingPlans().values()) {
         String shopId = plan.shopId();
         if (shopId != null && !shopId.isEmpty()) {
            checks++;
            if (ShopProfileLoader.getProfile(plan.culture(), shopId) == null) {
               LOGGER.error("BuildingPlan {}: shopId '{}' has no loaded ShopProfile for culture {}", new Object[]{plan.id(), shopId, plan.culture()});
               warnings++;
            }
         }
      }

      for (BuildingPlanSet set : ModCultures.getAllBuildingPlanSets().values()) {
         if (set.isSubBuilding()) {
            checks++;
            warnings += validateSubBuildingParentRef(set);
         }
      }

      for (ResourceLocation id : villageTypesToDisable) {
         ModCultures.unregisterVillageType(id);
      }

      if (!villageTypesToDisable.isEmpty()) {
         LOGGER.error(
            "Cross-reference validation DISABLED {} village type(s) with broken critical refs: {}", villageTypesToDisable.size(), villageTypesToDisable
         );
      }

      LOGGER.info("Cross-reference validation: {} warnings out of {} checks", warnings, checks);
   }

   static int validateSubBuildingParentRef(BuildingPlanSet set) {
      BuildingPlanSet.ParentRef ref = set.parentBuilding();
      if (ref == null) {
         LOGGER.warn(
            "BuildingPlanSet {} is a sub-building but has no parent_building reference. This will make it impossible to import and export it correctly using an Import Table.",
            set.id()
         );
         return 1;
      }

      ResourceLocation parentId = ResourceLocation.fromNamespaceAndPath(set.culture().getNamespace(), set.culture().getPath() + "/" + ref.id());
      BuildingPlanSet parent = ModCultures.getBuildingPlanSet(parentId);
      if (parent == null) {
         LOGGER.warn(
            "BuildingPlanSet {}: unknown parent building plan {} (variant {}, level {}).", new Object[]{set.id(), parentId, ref.variant(), ref.level()}
         );
         return 1;
      }

      int warnings = 0;
      BuildingPlanSet.LevelDef parentLevel = parent.getLevel(ref.variant(), ref.level());
      if (parentLevel == null) {
         LOGGER.warn(
            "BuildingPlanSet {}: unknown level or variant for parent building plan {} (variant {}, level {}).",
            new Object[]{set.id(), parentId, ref.variant(), ref.level()}
         );
         warnings++;
      }

      String subId = set.buildingId();
      boolean parentReferencesSub = parent.startingSubBuildings().contains(subId);
      if (!parentReferencesSub) {
         for (List<BuildingPlanSet.LevelDef> levels : parent.variants().values()) {
            for (BuildingPlanSet.LevelDef ld : levels) {
               if (ld.subBuildings().contains(subId)) {
                  parentReferencesSub = true;
                  break;
               }
            }

            if (parentReferencesSub) {
               break;
            }
         }
      }

      if (!parentReferencesSub) {
         LOGGER.warn(
            "BuildingPlanSet {}: parent building plan {} does not list this sub-building in starting_sub_buildings or any levels[].sub_buildings.",
            set.id(),
            parentId
         );
         warnings++;
      }

      if (parentLevel != null) {
         BuildingPlanSet.LevelDef subLevel0 = firstLevel0(set);
         if (subLevel0 != null && (parentLevel.width() != subLevel0.width() || parentLevel.depth() != subLevel0.depth())) {
            LOGGER.warn(
               "BuildingPlanSet {}: footprint of parent building plan {} (variant {}, level {}) [{}x{}] does not match that of the sub-building [{}x{}].",
               new Object[]{set.id(), parentId, ref.variant(), ref.level(), parentLevel.width(), parentLevel.depth(), subLevel0.width(), subLevel0.depth()}
            );
            warnings++;
         }
      }

      return warnings;
   }

   @Nullable
   private static BuildingPlanSet.LevelDef firstLevel0(BuildingPlanSet set) {
      for (List<BuildingPlanSet.LevelDef> levels : set.variants().values()) {
         if (!levels.isEmpty()) {
            return levels.getFirst();
         }
      }

      return null;
   }

   private static void loadCulture(String name) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", name);
      ContentFs cultureFs = CustomContentIndex.current().forCulture(name);
      List<Resource> allCultureJsons = cultureFs.findAll("culture.json");
      JsonObject json;
      if (allCultureJsons.isEmpty()) {
         String path = "/millenaire/cultures/" + name + "/culture.json";
         json = readJson(path);
      } else {
         json = mergeCultureJsons(allCultureJsons);
      }

      if (json != null) {
         String displayName;
         if (json.has("display_name")) {
            displayName = GsonHelper.getAsString(json, "display_name");
         } else {
            displayName = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
            LOGGER.warn("Culture '{}': missing 'display_name', falling back to '{}'", name, displayName);
         }

         ResourceLocation panelTexture = json.has("panel_texture")
            ? ResourceLocation.parse(GsonHelper.getAsString(json, "panel_texture"))
            : Culture.DEFAULT_PANEL_TEXTURE;
         String nativeLanguage = GsonHelper.getAsString(json, "native_language", "en");
         TravelBookCategories travelBookCategories = TravelBookCategories.EMPTY;
         if (json.has("travel_book")) {
            travelBookCategories = parseTravelBookCategories(GsonHelper.getAsJsonObject(json, "travel_book"));
         }

         List<String> knownCrops = json.has("known_crops") ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "known_crops")) : List.of();
         List<String> knownHuntingDrops = json.has("known_hunting_drops")
            ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "known_hunting_drops"))
            : List.of();
         Optional<ResourceLocation> mapIcon = parseMapIcon(json);
         String cultureBannerNbt = json.has("culture_banner_nbt") ? GsonHelper.getAsString(json, "culture_banner_nbt") : null;
         ModCultures.registerCulture(
            new Culture(id, displayName, panelTexture, nativeLanguage, travelBookCategories, knownCrops, knownHuntingDrops, mapIcon, cultureBannerNbt)
         );
      }
   }

   static Optional<ResourceLocation> parseMapIcon(JsonObject json) {
      if (!json.has("map_icon")) {
         return Optional.empty();
      }

      String raw = GsonHelper.getAsString(json, "map_icon");
      return Optional.of(ResourceLocation.parse(raw));
   }

   @Nullable
   private static JsonObject mergeCultureJsons(List<Resource> resources) {
      JsonObject merged = null;

      for (int i = resources.size() - 1; i >= 0; i--) {
         Resource res = resources.get(i);

         JsonObject layer;
         try (InputStream is = res.open()) {
            layer = (JsonObject)GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
         } catch (Exception e) {
            LOGGER.error("Error reading {} from {}: {}", new Object[]{res.relPath(), res.source().displayName(), e.getMessage(), e});
            continue;
         }

         if (layer != null) {
            if (merged == null) {
               merged = layer;
            } else {
               for (Entry<String, JsonElement> e : layer.entrySet()) {
                  merged.add(e.getKey(), e.getValue());
               }
            }
         }
      }

      return merged;
   }

   static void injectExtraBuildings(ResourceLocation cultureId) {
      Set<String> excludedCategories = Set.of("townhalls", "lone", "loneold", "player", "walls");
      List<BuildingPlanSet> cultureSets = new ArrayList<>();

      for (BuildingPlanSet set : ModCultures.getAllBuildingPlanSets().values()) {
         if (set.culture().equals(cultureId) && !excludedCategories.contains(set.category()) && !set.isSubBuilding() && !set.isTownHall()) {
            cultureSets.add(set);
         }
      }

      if (!cultureSets.isEmpty()) {
         for (VillageType vt : new ArrayList<>(ModCultures.getAllVillageTypes().values())) {
            if (vt.culture().equals(cultureId) && vt.allowExtraBuildings()) {
               Map<ResourceLocation, Integer> existingCounts = new HashMap<>();

               for (VillageType.LayoutSlot slot : vt.layout()) {
                  existingCounts.merge(slot.plan(), 1, Integer::sum);
               }

               List<VillageType.LayoutSlot> extraSlots = new ArrayList<>();

               for (BuildingPlanSet set : cultureSets) {
                  if (!vt.neverBuildings().contains(set.buildingId())) {
                     int existing = existingCounts.getOrDefault(set.id(), 0);
                     if (set.maxCount() != 0) {
                        int slotsToAdd = set.maxCount() - existing;
                        if (slotsToAdd > 0) {
                           for (int i = 0; i < slotsToAdd; i++) {
                              extraSlots.add(
                                 new VillageType.LayoutSlot(
                                    set.id(), null, null, "extra", -1.0, -1.0, -1, Map.of(), Map.of(), MillenaireServerConfig.getDefaultClearMargin(), null
                                 )
                              );
                           }
                        }
                     }
                  }
               }

               if (!extraSlots.isEmpty()) {
                  List<VillageType.LayoutSlot> fullLayout = new ArrayList<>(vt.layout());
                  fullLayout.addAll(extraSlots);
                  ModCultures.registerVillageType(vt.withLayout(fullLayout));
                  LOGGER.debug("Injected {} extra building slots into {}", extraSlots.size(), vt.id());
               }
            }
         }
      }
   }

   private static void validateNameListReferences(ResourceLocation cultureId) {
      NameLists nameLists = ModCultures.getNameLists(cultureId);

      for (VillageType vt : ModCultures.getAllVillageTypes().values()) {
         if (vt.culture().equals(cultureId)) {
            String nameListKey = vt.nameList();
            if (nameListKey != null) {
               if (nameLists == null) {
                  LOGGER.error(
                     "[Millenaire] VillageType {} references nameList '{}' but culture {} has no namelists loaded!",
                     new Object[]{vt.id(), nameListKey, cultureId}
                  );
               } else if (nameLists.randomFrom(nameListKey) == null) {
                  LOGGER.error(
                     "[Millenaire] VillageType {} references nameList '{}' but it is missing or empty in culture {}",
                     new Object[]{vt.id(), nameListKey, cultureId}
                  );
               }
            }
         }
      }
   }

   static List<BrickColourTheme.WeightedColor> parseWeightedColorList(JsonArray array) {
      List<BrickColourTheme.WeightedColor> list = new ArrayList<>();

      for (JsonElement e : array) {
         JsonObject obj = e.getAsJsonObject();
         DyeColor color = parseDyeColor(GsonHelper.getAsString(obj, "color"));
         int w = GsonHelper.getAsInt(obj, "weight");
         if (color != null) {
            list.add(new BrickColourTheme.WeightedColor(color, w));
         }
      }

      return list;
   }

   static DyeColor parseDyeColor(String name) {
      if ("silver".equalsIgnoreCase(name)) {
         return DyeColor.LIGHT_GRAY;
      }

      for (DyeColor color : DyeColor.values()) {
         if (color.getSerializedName().equals(name)) {
            return color;
         }
      }

      LOGGER.warn("Unknown DyeColor: {}", name);
      return null;
   }

   @Nullable
   static String pluralTypeName(String contentType) {
      return switch (contentType) {
         case "building_plan" -> "buildings";
         case "villager_type" -> "villagers";
         case "village_type" -> "villages";
         default -> null;
      };
   }

   static Set<String> resolveDisabledIds(String culture, String contentType) {
      String pluralType = pluralTypeName(contentType);
      if (pluralType == null) {
         return Set.of();
      }

      ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
      List<Resource> manifests = cultureFs.findAll(pluralType + "/_disabled.json");
      if (manifests.isEmpty()) {
         return Set.of();
      }

      Set<String> out = new HashSet<>();

      for (int i = manifests.size() - 1; i >= 0; i--) {
         Resource res = manifests.get(i);

         try (InputStream in = res.open()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray()) {
               for (JsonElement e : root.getAsJsonArray()) {
                  if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                     out.add(e.getAsString());
                  }
               }
            } else {
               LOGGER.warn("{} from {} must be a JSON array of strings; ignoring", res.relPath(), res.source().displayName());
            }
         } catch (Exception e) {
            LOGGER.warn("Could not parse {} from {}: {}", new Object[]{res.relPath(), res.source().displayName(), e.getMessage()});
         }
      }

      return out;
   }

   static String formatSimpleName(String contentId) {
      String path = contentId;
      int underscore = path.indexOf(95);
      if (underscore >= 0) {
         path = path.substring(underscore + 1);
      }

      String formatted = path.replace('_', ' ');
      return formatted.isEmpty() ? contentId : Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
   }

   @Nullable
   static JsonObject readJsonFromContentFs(ContentFs fs, String relPath) {
      if (fs != null && relPath != null) {
         Optional<Resource> res = fs.findFirst(relPath);
         if (res.isEmpty()) {
            return null;
         }

         try (InputStream is = res.get().open()) {
            return (JsonObject)GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
         } catch (Exception e) {
            LOGGER.error("Error reading {} from {}: {}", new Object[]{res.get().relPath(), res.get().source().displayName(), e.getMessage(), e});
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   static JsonObject readJson(String classpathPath) {
      if (classpathPath == null) {
         return null;
      }

      String relPath = classpathPathToRelPath(classpathPath);
      if (relPath == null) {
         LOGGER.error("Content file path does not start with /millenaire/: {}", classpathPath);
         return null;
      }

      ContentFs root = CustomContentIndex.current().root();
      Optional<Resource> res = root.findFirst(relPath);
      if (res.isPresent()) {
         try (InputStream is = res.get().open()) {
            return (JsonObject)GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
         } catch (Exception e) {
            LOGGER.error("Error reading {} from {}: {}", new Object[]{res.get().relPath(), res.get().source().displayName(), e.getMessage(), e});
            return null;
         }
      } else {
         String customCulture = extractCustomCultureFromPath(classpathPath);
         if (customCulture != null) {
            ContentLoadReport.recordMissingClasspathFile(customCulture, classpathPath);
            LOGGER.debug("Content file not found on overlay (custom culture '{}'): {}", customCulture, classpathPath);
         } else {
            LOGGER.error("Content file not found: {}", classpathPath);
         }

         return null;
      }
   }

   @Nullable
   static String classpathPathToRelPath(String classpathPath) {
      if (classpathPath == null) {
         return null;
      }

      String tail = classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
      return !tail.startsWith("millenaire/") ? null : tail.substring("millenaire/".length());
   }

   @Nullable
   static String extractCustomCultureFromPath(String classpathPath) {
      if (classpathPath == null) {
         return null;
      }

      String tail = classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
      if (!tail.startsWith("millenaire/")) {
         return null;
      }

      tail = tail.substring("millenaire/".length());
      int firstSlash = tail.indexOf(47);
      if (firstSlash < 0) {
         return null;
      }

      String type = tail.substring(0, firstSlash);
      String rest = tail.substring(firstSlash + 1);

      String candidate = switch (type) {
         case "cultures" -> {
            int sep = rest.indexOf(47);
            yield sep < 0 ? rest : rest.substring(0, sep);
         }
         default -> null;
      };
      if (candidate == null || candidate.isEmpty()) {
         return null;
      } else if (BUILTIN_CULTURES.contains(candidate)) {
         return null;
      } else {
         return activeCultures.contains(candidate) ? candidate : null;
      }
   }

   private static TravelBookCategories parseTravelBookCategories(JsonObject json) {
      List<String> villagerCategories = json.has("villager_categories")
         ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "villager_categories"))
         : List.of();
      List<String> buildingCategories = json.has("building_categories")
         ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "building_categories"))
         : List.of();
      List<String> tradeGoodCategories = json.has("trade_good_categories")
         ? JsonLoaderUtils.parseStringList(GsonHelper.getAsJsonArray(json, "trade_good_categories"))
         : List.of();
      Map<String, String> categoryIcons = new HashMap<>();
      if (json.has("category_icons")) {
         JsonObject iconsObj = GsonHelper.getAsJsonObject(json, "category_icons");

         for (Entry<String, JsonElement> entry : iconsObj.entrySet()) {
            categoryIcons.put(entry.getKey(), entry.getValue().getAsString());
         }
      }

      Map<String, String> categoryNames = new HashMap<>();
      if (json.has("category_names")) {
         JsonObject namesObj = GsonHelper.getAsJsonObject(json, "category_names");

         for (Entry<String, JsonElement> entry : namesObj.entrySet()) {
            categoryNames.put(entry.getKey(), entry.getValue().getAsString());
         }
      }

      return new TravelBookCategories(villagerCategories, buildingCategories, tradeGoodCategories, categoryIcons, categoryNames);
   }

   static List<String> parseStringOrArray(JsonElement element) {
      return element.isJsonArray() ? JsonLoaderUtils.parseStringList(element.getAsJsonArray()) : List.of(element.getAsString());
   }

   static Map<ResourceLocation, Integer> parseResourceIntMap(JsonObject obj) {
      Map<ResourceLocation, Integer> map = new HashMap<>();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         map.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsInt());
      }

      return map;
   }

   static List<ResourceLocation> parseResourceLocationList(JsonObject parent, String key) {
      List<ResourceLocation> list = new ArrayList<>();
      if (parent.has(key)) {
         for (JsonElement e : GsonHelper.getAsJsonArray(parent, key)) {
            list.add(ResourceLocation.parse(e.getAsString()));
         }
      }

      return list;
   }
}
