package org.millenaire.encyclopedia;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.DisplayUtils;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.TravelBookContentBuilder;
import org.millenaire.village.TravelBookLine;
import org.millenaire.village.TravelBookScreenState;
import org.slf4j.Logger;

public final class EncyclopediaExporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Set<String> VANILLA_LANG_LOCALES = Set.of("en_us", "fr_fr");

   private EncyclopediaExporter() {
   }

   public static String segment(TravelBookScreenState state) {
      return switch (state) {
         case CULTURE -> "cultures";
         case VILLAGER_DETAIL -> "villagers";
         case VILLAGE_DETAIL -> "villages";
         case BUILDING_DETAIL -> "buildings";
         case TRADE_GOOD_DETAIL -> "tradegoods";
         default -> throw new IllegalArgumentException("Not an exportable detail state: " + state);
      };
   }

   public static String typeName(TravelBookScreenState state) {
      return segment(state).toUpperCase(Locale.ROOT);
   }

   public static String simpleName(@Nullable String itemKey) {
      if (itemKey == null) {
         return "";
      }

      int slash = itemKey.lastIndexOf(47);
      return slash >= 0 ? itemKey.substring(slash + 1) : itemKey;
   }

   public static String itemRef(String cultureSlug, TravelBookScreenState state, String itemKey) {
      return cultureSlug + "-" + segment(state) + "-" + simpleName(itemKey);
   }

   private static boolean resolves(UnaryOperator<String> resolve, String key) {
      if (key != null && !key.isEmpty()) {
         String v = resolve.apply(key);
         return !v.equals(key) && !v.isEmpty();
      } else {
         return false;
      }
   }

   public static LocalizedText labelForCulture(UnaryOperator<String> resolve, String cultureKey, String displayName) {
      String key = "culture.millenaire." + cultureKey;
      return resolves(resolve, key) ? LocalizedText.key(key) : LocalizedText.lit(displayName);
   }

   public static EncyclopediaExporter.VillagerLabel labelForVillager(boolean roleKeyResolves, String roleKey, String nativeName) {
      return roleKeyResolves
         ? new EncyclopediaExporter.VillagerLabel(LocalizedText.key(roleKey), nativeName)
         : new EncyclopediaExporter.VillagerLabel(LocalizedText.lit(nativeName), null);
   }

   public static LocalizedText labelForBuilding(@Nullable String translationKey, String simpleName) {
      return translationKey != null && !translationKey.isEmpty() ? LocalizedText.key(translationKey) : LocalizedText.lit(simpleName);
   }

   public static LocalizedText labelForVillage(String name) {
      return LocalizedText.lit(name);
   }

   public static LocalizedText labelForTradeGood(ResourceLocation itemLocation, @Nullable String descriptionId) {
      return descriptionId != null ? LocalizedText.key(descriptionId) : LocalizedText.lit(TravelBookContentBuilder.extractSimpleName(itemLocation));
   }

   @Nullable
   public static LocalizedText categoryLabel(Map<String, String> categoryNames, @Nullable String category) {
      if (category == null) {
         return null;
      }

      String i18nKey = categoryNames.getOrDefault(category, category);
      return resolves(k -> i18nKey, category) ? LocalizedText.key(i18nKey) : LocalizedText.lit(TravelBookContentBuilder.formatCategoryName(category));
   }

   public static EncyclopediaExporter.ItemAssembly buildItem(TravelBookScreenState state, String cultureKey, @Nullable String itemKey, List<String> dataLocales) {
      String ref = itemRef(cultureKey, state, simpleName(itemKey));
      Map<String, List<ExportLine>> skeletons = new LinkedHashMap<>();
      Map<String, SlotText> slotsPerLocale = new LinkedHashMap<>();

      for (String locale : dataLocales) {
         UnaryOperator<String> resolve = LangFiles.resolver(locale);
         List<TravelBookLine> lines = TravelBookContentBuilder.exportItemDetail(state, cultureKey, itemKey, resolve);
         SlotText slots = new SlotText();
         List<ExportLine> skeleton = new ArrayList<>(lines.size());

         for (int i = 0; i < lines.size(); i++) {
            skeleton.add(EncyclopediaLineMapper.map(lines.get(i), i, resolve, slots));
         }

         skeletons.put(locale, skeleton);
         slotsPerLocale.put(locale, slots);
      }

      List<ExportLine> structure = StructureInvariance.validate(ref, skeletons);
      return new EncyclopediaExporter.ItemAssembly(structure, slotsPerLocale);
   }

   private static List<EncyclopediaExporter.ItemDescriptor> enumerate(
      List<String> cultureKeys, UnaryOperator<String> resolve, Map<ResourceLocation, Set<String>> offerCounts
   ) {
      List<String> sortedKeys = cultureKeys.stream().sorted().toList();
      List<EncyclopediaExporter.ItemDescriptor> out = new ArrayList<>();

      for (String cultureKey : sortedKeys) {
         enumerateCulture(out, cultureKey, resolve, offerCounts);
      }

      return out;
   }

   private static Map<ResourceLocation, Set<String>> buildOfferCounts(List<String> cultureKeys) {
      Map<ResourceLocation, Set<String>> out = new LinkedHashMap<>();

      for (String cultureKey : cultureKeys) {
         ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey);
         Map<String, ShopProfile> profiles = ShopProfileLoader.getProfiles(cultureId);
         if (profiles != null) {
            for (ShopProfile profile : profiles.values()) {
               for (String goodId : profile.sells()) {
                  TradeGood good = TradeGoodsLoader.getGoodById(cultureId, goodId);
                  if (good == null) {
                     LOGGER.warn("[Millenaire] Encyclopedia signature: shop of {} sells unknown good id '{}', skipping", cultureKey, goodId);
                  } else {
                     out.computeIfAbsent(good.itemLocation(), k -> new HashSet<>()).add(cultureKey);
                  }
               }
            }
         }
      }

      return out;
   }

   private static void enumerateCulture(
      List<EncyclopediaExporter.ItemDescriptor> out, String cultureKey, UnaryOperator<String> resolve, Map<ResourceLocation, Set<String>> offerCounts
   ) {
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey);
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null) {
         Map<String, String> categoryNames = culture.travelBookCategories().categoryNames();
         LocalizedText cultureLabel = labelForCulture(resolve, cultureKey, culture.displayName());
         addDescriptor(
            out, TravelBookScreenState.CULTURE, cultureKey, cultureKey, cultureKey, cultureLabel, null, "millenaire:" + cultureKey, null, categoryNames
         );

         for (VillagerType vt : ModCultures.getAllVillagerTypes()
            .values()
            .stream()
            .filter(vtx -> vtx.culture().equals(cultureId))
            .filter(VillagerType::travelBookDisplay)
            .sorted(Comparator.comparing(VillagerType::nativeName))
            .toList()) {
            String path = vt.id().getPath();
            String roleKey = DisplayUtils.resolveRoleKey(vt.id());
            EncyclopediaExporter.VillagerLabel vl = labelForVillager(resolves(resolve, roleKey), roleKey, vt.nativeName());
            addDescriptor(
               out,
               TravelBookScreenState.VILLAGER_DETAIL,
               cultureKey,
               path,
               simpleName(path),
               vl.label(),
               vl.nativePrefix(),
               vt.id().toString(),
               vt.travelBookCategory(),
               categoryNames
            );
         }

         for (VillageType vt : ModCultures.getAllVillageTypes()
            .values()
            .stream()
            .filter(vtx -> vtx.culture().equals(cultureId))
            .filter(VillageType::travelBookDisplay)
            .sorted(Comparator.comparing(VillageType::name))
            .toList()) {
            String path = vt.id().getPath();
            String iconKey = vt.icon() != null ? vt.icon() : vt.id().toString();
            IndexEntry entry = indexEntry(
               TravelBookScreenState.VILLAGE_DETAIL,
               cultureKey,
               simpleName(path),
               labelForVillage(vt.name()),
               null,
               iconKey,
               null,
               categoryNames,
               out.size(),
               null
            );
            out.add(
               new EncyclopediaExporter.ItemDescriptor(TravelBookScreenState.VILLAGE_DETAIL, cultureKey, path, entry, buildVillageData(cultureKey, vt), null)
            );
         }

         for (BuildingPlanSet bps : ModCultures.getAllBuildingPlanSets()
            .values()
            .stream()
            .filter(bpsx -> bpsx.culture().equals(cultureId))
            .filter(BuildingPlanSet::travelBookDisplay)
            .filter(EncyclopediaExporter::isEncyclopediaBuilding)
            .sorted(Comparator.comparing(BuildingPlanSet::nativeName))
            .toList()) {
            String path = bps.id().getPath();
            String simpleKey = simpleName(path);
            LocalizedText label = labelForBuilding(BuildingNameHelper.getTranslationKey(bps.id()), simpleKey);
            String iconKey = bps.icon() != null ? bps.icon() : bps.id().toString();
            List<String> residents = new ArrayList<>(bps.maleResidents());
            residents.addAll(bps.femaleResidents());
            IndexEntry entry = indexEntry(
               TravelBookScreenState.BUILDING_DETAIL,
               cultureKey,
               simpleKey,
               label,
               null,
               iconKey,
               bps.travelBookCategory(),
               categoryNames,
               out.size(),
               residents.isEmpty() ? null : residents
            );
            out.add(new EncyclopediaExporter.ItemDescriptor(TravelBookScreenState.BUILDING_DETAIL, cultureKey, path, entry, null, buildBuildingCost(bps)));
         }

         List<TradeGood> goods = TradeGoodsLoader.getGoods(cultureId)
            .stream()
            .filter(TradeGood::travelBookDisplay)
            .sorted(Comparator.comparing(TradeGood::id))
            .toList();
         Map<String, SignatureGoods.SignatureInfo> signatures = SignatureGoods.computeForCulture(cultureKey, goods, offerCounts);

         for (TradeGood good : goods) {
            Item item = ItemHelper.resolve(good.itemLocation());
            String descriptionId = item != null ? item.getDescriptionId() : null;
            LocalizedText label = labelForTradeGood(good.itemLocation(), descriptionId);
            SignatureGoods.SignatureInfo info = signatures.get(good.id());
            Boolean signature = info != null ? Boolean.TRUE : null;
            String signatureFamily = info != null ? info.family() : null;
            Boolean signaturePrimary = info != null && info.primary() ? Boolean.TRUE : null;
            IndexEntry entry = tradeGoodEntry(
               cultureKey,
               good.id(),
               label,
               good.itemLocation().toString(),
               good.category(),
               categoryNames,
               out.size(),
               signature,
               signatureFamily,
               signaturePrimary
            );
            out.add(new EncyclopediaExporter.ItemDescriptor(TravelBookScreenState.TRADE_GOOD_DETAIL, cultureKey, good.id(), entry, null, null));
         }
      }
   }

   private static boolean isEncyclopediaBuilding(BuildingPlanSet bps) {
      return !bps.isSubBuilding() && !bps.isWallSegment() && !bps.isBorderBuilding() && !bps.tags().contains("worldquest");
   }

   private static void addDescriptor(
      List<EncyclopediaExporter.ItemDescriptor> out,
      TravelBookScreenState state,
      String cultureKey,
      String builderItemKey,
      String simpleKey,
      LocalizedText label,
      @Nullable String nativePrefix,
      String iconKey,
      @Nullable String category,
      Map<String, String> categoryNames
   ) {
      addDescriptor(out, state, cultureKey, builderItemKey, simpleKey, label, nativePrefix, iconKey, category, categoryNames, null);
   }

   private static void addDescriptor(
      List<EncyclopediaExporter.ItemDescriptor> out,
      TravelBookScreenState state,
      String cultureKey,
      String builderItemKey,
      String simpleKey,
      LocalizedText label,
      @Nullable String nativePrefix,
      String iconKey,
      @Nullable String category,
      Map<String, String> categoryNames,
      @Nullable List<String> residents
   ) {
      IndexEntry entry = indexEntry(state, cultureKey, simpleKey, label, nativePrefix, iconKey, category, categoryNames, out.size(), residents);
      out.add(new EncyclopediaExporter.ItemDescriptor(state, cultureKey, builderItemKey, entry, null, null));
   }

   private static IndexEntry indexEntry(
      TravelBookScreenState state,
      String cultureKey,
      String simpleKey,
      LocalizedText label,
      @Nullable String nativePrefix,
      String iconKey,
      @Nullable String category,
      Map<String, String> categoryNames,
      int displayOrder,
      @Nullable List<String> residents
   ) {
      return new IndexEntry(
         itemRef(cultureKey, state, simpleKey),
         cultureKey,
         typeName(state),
         simpleKey,
         label,
         nativePrefix,
         iconKey,
         category,
         categoryLabel(categoryNames, category),
         displayOrder,
         residents,
         null,
         null,
         null
      );
   }

   private static IndexEntry tradeGoodEntry(
      String cultureKey,
      String goodId,
      LocalizedText label,
      String iconKey,
      @Nullable String category,
      Map<String, String> categoryNames,
      int displayOrder,
      @Nullable Boolean signature,
      @Nullable String signatureFamily,
      @Nullable Boolean signaturePrimary
   ) {
      return new IndexEntry(
         itemRef(cultureKey, TravelBookScreenState.TRADE_GOOD_DETAIL, goodId),
         cultureKey,
         typeName(TravelBookScreenState.TRADE_GOOD_DETAIL),
         goodId,
         label,
         null,
         iconKey,
         category,
         categoryLabel(categoryNames, category),
         displayOrder,
         null,
         signature,
         signatureFamily,
         signaturePrimary
      );
   }

   private static VillageData buildVillageData(String cultureKey, VillageType vt) {
      String type = vt.loneBuilding() ? "lone" : (vt.isMarvel() ? "marvel" : (vt.isHamlet() ? "hameau" : "regular"));
      Map<String, Map<ResourceLocation, Integer>> byRole = new LinkedHashMap<>();

      for (VillageType.LayoutSlot slot : vt.layout()) {
         byRole.computeIfAbsent(slot.role(), k -> new LinkedHashMap<>()).merge(slot.plan(), 1, Integer::sum);
      }

      int male = 0;
      int female = 0;
      List<VillageData.RoleGroup> composition = new ArrayList<>();

      for (Entry<String, Map<ResourceLocation, Integer>> roleEntry : byRole.entrySet()) {
         List<VillageData.Building> buildings = new ArrayList<>();

         for (Entry<ResourceLocation, Integer> planEntry : roleEntry.getValue().entrySet()) {
            int count = planEntry.getValue();
            BuildingPlanSet bps = ModCultures.getBuildingPlanSet(planEntry.getKey());
            int residents = bps != null ? bps.maleResidents().size() + bps.femaleResidents().size() : 0;
            if (bps != null) {
               male += bps.maleResidents().size() * count;
               female += bps.femaleResidents().size() * count;
            }

            buildings.add(new VillageData.Building(simpleName(planEntry.getKey().getPath()), count, residents));
         }

         composition.add(new VillageData.RoleGroup(roleEntry.getKey(), buildings));
      }

      VillageData.Population population = new VillageData.Population(male + female, male, female);
      VillageData.Walls walls = vt.innerWallType() == null && vt.outerWallType() == null
         ? null
         : new VillageData.Walls(
            vt.innerWallType() != null ? vt.innerWallType().getPath() : null,
            vt.outerWallType() != null ? vt.outerWallType().getPath() : null,
            vt.innerWallRadius()
         );
      VillageData.Economy economy = vt.sellingPriceOverrides().isEmpty() && vt.buyingPriceOverrides().isEmpty()
         ? null
         : new VillageData.Economy(sortedOrNull(vt.sellingPriceOverrides()), sortedOrNull(vt.buyingPriceOverrides()));
      Map<String, String> terrain = new LinkedHashMap<>();
      putIfPresent(terrain, "forest", vt.forestQualifier());
      putIfPresent(terrain, "hill", vt.hillQualifier());
      putIfPresent(terrain, "mountain", vt.mountainQualifier());
      putIfPresent(terrain, "desert", vt.desertQualifier());
      putIfPresent(terrain, "lava", vt.lavaQualifier());
      putIfPresent(terrain, "lake", vt.lakeQualifier());
      putIfPresent(terrain, "ocean", vt.oceanQualifier());
      List<String> hamlets = null;
      if (!vt.hamlets().isEmpty()) {
         hamlets = new ArrayList<>(vt.hamlets().size());

         for (ResourceLocation h : vt.hamlets()) {
            hamlets.add(itemRef(cultureKey, TravelBookScreenState.VILLAGE_DETAIL, simpleName(h.getPath())));
         }
      }

      List<String> biomes = vt.biomeTags().isEmpty() ? null : vt.biomeTags().stream().map(tag -> tag.location().getPath()).toList();
      return new VillageData(
         type,
         vt.radius(),
         vt.weight(),
         vt.playerControlled(),
         vt.carriesRaid(),
         population,
         composition,
         walls,
         economy,
         vt.qualifiers().isEmpty() ? null : List.copyOf(vt.qualifiers()),
         terrain.isEmpty() ? null : terrain,
         hamlets,
         biomes
      );
   }

   private static BuildingCostData buildBuildingCost(BuildingPlanSet bps) {
      List<BuildingCostData.Variant> variants = new ArrayList<>();

      for (String variantKey : bps.variants().keySet().stream().sorted().toList()) {
         List<BuildingCostData.Level> levels = new ArrayList<>();

         for (BuildingPlanSet.LevelDef level : bps.variants().get(variantKey)) {
            String kind = level.level() == 0 ? "initial" : "upgrade";
            String size = level.width() + "x" + level.depth();
            List<BuildingCostData.Material> materials = level.requiredResources()
               .entrySet()
               .stream()
               .sorted(Comparator.comparing(e -> e.getKey().toString()))
               .map(res -> buildMaterial(res.getKey(), res.getValue()))
               .toList();
            levels.add(new BuildingCostData.Level(level.level(), kind, size, materials));
         }

         variants.add(new BuildingCostData.Variant(variantKey, levels));
      }

      return new BuildingCostData(variants);
   }

   private static BuildingCostData.Material buildMaterial(ResourceLocation resource, int qty) {
      if (AnywoodHelper.isAnywood(resource)) {
         return new BuildingCostData.Material(resource.toString(), LocalizedText.key("item.millenaire.anywood_log"), qty);
      }

      Item item = ItemHelper.resolve(resource);
      LocalizedText label = item != null ? LocalizedText.key(item.getDescriptionId()) : LocalizedText.lit(TravelBookContentBuilder.extractSimpleName(resource));
      return new BuildingCostData.Material(resource.toString(), label, qty);
   }

   @Nullable
   private static Map<String, Integer> sortedOrNull(Map<String, Integer> map) {
      return map.isEmpty() ? null : new TreeMap<>(map);
   }

   private static void putIfPresent(Map<String, String> map, String key, @Nullable String value) {
      if (value != null) {
         map.put(key, value);
      }
   }

   public static void exportAll(Path outDir, List<String> cultureKeys, List<String> dataLocales, String modVersion, String generatedAt) {
      UnaryOperator<String> refResolve = LangFiles.resolver("en_us");
      Map<ResourceLocation, Set<String>> offerCounts = buildOfferCounts(cultureKeys);
      List<EncyclopediaExporter.ItemDescriptor> descriptors = enumerate(cultureKeys, refResolve, offerCounts);
      List<IndexEntry> entries = new ArrayList<>(descriptors.size());
      Map<String, VillageData> villageData = new LinkedHashMap<>();
      Map<String, BuildingCostData> buildingCost = new LinkedHashMap<>();

      for (EncyclopediaExporter.ItemDescriptor d : descriptors) {
         entries.add(d.entry());
         if (d.villageData() != null) {
            villageData.put(d.entry().itemRef(), d.villageData());
         }

         if (d.buildingCost() != null) {
            buildingCost.put(d.entry().itemRef(), d.buildingCost());
         }
      }

      EncyclopediaIndex index = new EncyclopediaIndex(1, modVersion, generatedAt, entries);
      Map<String, List<ExportLine>> structures = new LinkedHashMap<>();
      Map<String, Map<String, Map<String, String>>> textByLocale = new LinkedHashMap<>();

      for (String locale : dataLocales) {
         textByLocale.put(locale, new LinkedHashMap<>());
      }

      Set<String> vanillaKeys = new LinkedHashSet<>();
      collectVanillaKeysFromEntries(entries, vanillaKeys);
      collectVanillaKeysFromBuildingCost(buildingCost.values(), vanillaKeys);

      for (EncyclopediaExporter.ItemDescriptor d : descriptors) {
         EncyclopediaExporter.ItemAssembly assembly = buildItem(d.state(), d.cultureKey(), d.builderItemKey(), dataLocales);
         String ref = d.entry().itemRef();
         structures.put(ref, assembly.structure());
         collectVanillaKeysFromLines(assembly.structure(), vanillaKeys);

         for (String locale : dataLocales) {
            SlotText slots = assembly.slotsPerLocale().get(locale);
            textByLocale.get(locale).put(ref, slots.values());
         }
      }

      Map<String, Map<String, String>> langByLocale = new LinkedHashMap<>();

      for (String locale : LangFiles.locales()) {
         Map<String, String> lang = new LinkedHashMap<>(LangFiles.modKeys(locale));
         if (VANILLA_LANG_LOCALES.contains(locale)) {
            UnaryOperator<String> resolve = LangFiles.resolver(locale);

            for (String key : vanillaKeys) {
               String value = resolve.apply(key);
               if (!value.equals(key)) {
                  lang.put(key, value);
               }
            }
         }

         langByLocale.put(locale, lang);
      }

      EncyclopediaWriter.write(outDir, index, structures, textByLocale, langByLocale, villageData, buildingCost);
   }

   private static void collectVanillaKeysFromBuildingCost(Iterable<BuildingCostData> cards, Set<String> out) {
      for (BuildingCostData card : cards) {
         for (BuildingCostData.Variant variant : card.variants()) {
            for (BuildingCostData.Level level : variant.levels()) {
               for (BuildingCostData.Material material : level.materials()) {
                  collectVanillaKey(material.label(), out);
               }
            }
         }
      }
   }

   private static void collectVanillaKeysFromEntries(List<IndexEntry> entries, Set<String> out) {
      for (IndexEntry e : entries) {
         collectVanillaKey(e.label(), out);
         collectVanillaKey(e.categoryLabel(), out);
      }
   }

   private static void collectVanillaKeysFromLines(List<ExportLine> lines, Set<String> out) {
      for (ExportLine line : lines) {
         collectVanillaKey(line.text(), out);
         collectVanillaKey(line.referenceButtonLabel(), out);
         if (line.columns() != null) {
            for (ExportColumn col : line.columns()) {
               collectVanillaKey(col.text(), out);
               collectVanillaKey(col.referenceButtonLabel(), out);
            }
         }
      }
   }

   private static void collectVanillaKey(@Nullable LocalizedText node, Set<String> out) {
      if (node != null && node.key() != null) {
         String key = node.key();
         if ((key.startsWith("item.") || key.startsWith("block.")) && !key.contains(".millenaire.")) {
            out.add(key);
         }
      }
   }

   public record ItemAssembly(List<ExportLine> structure, Map<String, SlotText> slotsPerLocale) {
   }

   private record ItemDescriptor(
      TravelBookScreenState state,
      String cultureKey,
      String builderItemKey,
      IndexEntry entry,
      @Nullable VillageData villageData,
      @Nullable BuildingCostData buildingCost
   ) {
   }

   public record VillagerLabel(LocalizedText label, String nativePrefix) {
   }
}
