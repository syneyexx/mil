package org.millenaire.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.Millenaire;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.encyclopedia.EncyclopediaExporter;
import org.millenaire.goal.gathering.GatheringGoal;
import org.slf4j.Logger;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class EncyclopediaExportGameTests {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Path OUTPUT_DIR = Path.of("build", "encyclopedia");
   private static final List<String> DATA_LOCALES = List.of(
      "en_us",
      "fr_fr",
      "zh_cn",
      "ru_ru",
      "es_es",
      "pt_pt",
      "ja_jp",
      "pl_pl",
      "tr_tr",
      "de_de",
      "uk_ua",
      "it_it",
      "ar_sa",
      "cs_cz",
      "da_dk",
      "et_ee",
      "hi_in",
      "hu_hu",
      "ko_kr",
      "nb_no",
      "nl_nl",
      "sl_si",
      "sv_se",
      "th_th"
   );
   private static final String ITEM_REF_SHAPE = "^[a-z0-9]+-(cultures|villages|villagers|buildings|tradegoods)-[a-z0-9_]+$";
   private static final String EXPECTED_SIGNATURE_GOLDEN = "{\n  \"byzantines\": {\n    \"byzantine_chestplate\": 4,\n    \"byzantine_fresco\": 1,\n    \"byzantine_mace\": 1,\n    \"byzantine_mosaic_red\": 1,\n    \"byzantine_pattern\": 3,\n    \"byzantine_pickaxe\": 4,\n    \"byzantine_tiles\": 1,\n    \"clothes_byz_silk\": 2,\n    \"feta\": 1,\n    \"olive_tree_sapling\": 1,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"silk\": 1,\n    \"souvlaki\": 1,\n    \"stained_glass_white\": 1,\n    \"wall_byzantine_icon_large\": 3,\n    \"winebasic\": 1,\n    \"winefancy\": 1\n  },\n  \"indian\": {\n    \"brick_mould\": 1,\n    \"charpoy\": 1,\n    \"chickencurry\": 1,\n    \"cotton\": 1,\n    \"decorated_brick_white\": 3,\n    \"indian_statue\": 1,\n    \"indpattern\": 6,\n    \"mud_brick\": 1,\n    \"rasgulla\": 1,\n    \"rice\": 1,\n    \"sandstone_carved\": 3,\n    \"thatch\": 1,\n    \"turmeric\": 1,\n    \"vegcurry\": 1,\n    \"wooden_bars\": 3\n  },\n  \"inuits\": {\n    \"bearmeat_cooked\": 1,\n    \"bearmeat_raw\": 1,\n    \"fire_pit\": 1,\n    \"furplate\": 4,\n    \"hidehanging\": 1,\n    \"icebrick\": 1,\n    \"inuitbow\": 1,\n    \"inuitcarving\": 1,\n    \"inuitmeatystew\": 3,\n    \"inuittrident\": 1,\n    \"inupattern\": 5,\n    \"seafood_cooked\": 1,\n    \"seafood_raw\": 1,\n    \"snowbrick\": 1,\n    \"snowwall\": 1,\n    \"ulu\": 1,\n    \"wolfmeat_cooked\": 1,\n    \"wolfmeat_raw\": 1\n  },\n  \"japanese\": {\n    \"ikayaki\": 1,\n    \"japanese_tachi\": 1,\n    \"japaneseblueplate\": 4,\n    \"japaneseguardplate\": 4,\n    \"japaneseredplate\": 4,\n    \"jappattern\": 5,\n    \"paper_wall\": 1,\n    \"rice\": 1,\n    \"sake\": 1,\n    \"thatch\": 1,\n    \"timber_frame_plain\": 1,\n    \"udon\": 1,\n    \"yumibow\": 1\n  },\n  \"mayan\": {\n    \"cacauhaa\": 1,\n    \"maize\": 1,\n    \"masa\": 1,\n    \"mayan_gold_block\": 1,\n    \"mayan_mace\": 1,\n    \"mayan_pattern\": 5,\n    \"mayan_pickaxe\": 4,\n    \"mayan_statue\": 1,\n    \"wah\": 1\n  },\n  \"norman\": {\n    \"boudin\": 1,\n    \"calva\": 1,\n    \"cider\": 1,\n    \"cider_apple\": 1,\n    \"norman_chestplate\": 4,\n    \"norman_pickaxe\": 4,\n    \"norman_sword\": 1,\n    \"norpattern\": 1,\n    \"rosette\": 1,\n    \"sapling_appletree\": 1,\n    \"stained_glass_red_blue\": 4,\n    \"stained_glass_white\": 1,\n    \"tapestry\": 1,\n    \"timber_frame_plain\": 2,\n    \"tripes\": 1\n  },\n  \"seljuk\": {\n    \"ayran\": 1,\n    \"brick_mould\": 1,\n    \"clothes_seljuk_cotton\": 2,\n    \"helva\": 1,\n    \"lokum\": 1,\n    \"mud_brick\": 4,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"pide\": 1,\n    \"pistachios\": 1,\n    \"seljukbow\": 1,\n    \"seljukplate\": 4,\n    \"seljukscimitar\": 1,\n    \"seljukturban\": 1,\n    \"sjkmpattern\": 3,\n    \"wall_carpet_large\": 3,\n    \"yogurt\": 1\n  }\n}\n";
   private static final Set<String> GATHERING_UNSOLD_ALLOWLIST = Set.of("millenaire:obsidian_flake", "millenaire:tannedhide", "millenaire:path_ochre_tiles");

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 400)
   public static void exportAllCulturesEncyclopedia(GameTestHelper helper) {
      List<String> cultureKeys = ModCultures.getAllCultures().keySet().stream().<String>map(ResourceLocation::getPath).sorted().toList();
      if (cultureKeys.isEmpty()) {
         helper.fail("No cultures loaded — content registries not initialised?");
      } else {
         EncyclopediaExporter.exportAll(OUTPUT_DIR, cultureKeys, DATA_LOCALES, null, null);
         JsonObject index = readJsonObject(helper, OUTPUT_DIR.resolve("index.json"));
         if (index != null) {
            JsonArray items = index.getAsJsonArray("items");
            if (items != null && !items.isEmpty()) {
               if (hasType(items, "CULTURES") && hasType(items, "VILLAGERS") && hasType(items, "BUILDINGS")) {
                  List<String> itemRefs = new ArrayList<>();

                  for (JsonElement el : items) {
                     JsonObject entry = el.getAsJsonObject();
                     String itemRef = entry.get("itemRef").getAsString();
                     itemRefs.add(itemRef);
                     if (!itemRef.matches("^[a-z0-9]+-(cultures|villages|villagers|buildings|tradegoods)-[a-z0-9_]+$")) {
                        helper.fail("Index item ref does not match the website shape: " + itemRef);
                        return;
                     }

                     Path structureFile = OUTPUT_DIR.resolve("generated").resolve("structure").resolve(itemRef + ".json");
                     if (!Files.exists(structureFile)) {
                        helper.fail("Missing structure file for index item: " + itemRef);
                        return;
                     }

                     if ("VILLAGES".equals(entry.get("type").getAsString()) && !assertVillageData(helper, itemRef)) {
                        return;
                     }

                     if ("BUILDINGS".equals(entry.get("type").getAsString()) && !assertBuildingCost(helper, itemRef)) {
                        return;
                     }

                     JsonObject structure = readJsonObject(helper, structureFile);
                     if (structure == null) {
                        return;
                     }

                     JsonArray pages = structure.getAsJsonArray("pages");
                     if (pages != null && !pages.isEmpty()) {
                        JsonArray lines = pages.get(0).getAsJsonObject().getAsJsonArray("lines");
                        if (lines != null && !lines.isEmpty()) {
                           continue;
                        }

                        helper.fail("Structure " + itemRef + " has an empty first page");
                        return;
                     }

                     helper.fail("Structure " + itemRef + " has no pages");
                     return;
                  }

                  JsonObject langEn = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("lang").resolve("en_us.json"));
                  JsonObject textEn = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("text").resolve("en_us.json"));
                  if (langEn != null && textEn != null) {
                     List<String> sample = itemRefs.stream().sorted().limit(20L).collect(Collectors.toCollection(ArrayList::new));
                     String abbot = "norman-villagers-abbot";
                     if (itemRefs.contains(abbot) && !sample.contains(abbot)) {
                        sample.add(abbot);
                     }

                     for (String itemRef : sample) {
                        JsonObject structure = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("structure").resolve(itemRef + ".json"));
                        if (structure == null) {
                           return;
                        }

                        JsonObject textForItem = textEn.getAsJsonObject(itemRef);

                        for (JsonElement lineEl : structure.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("lines")) {
                           if (!assertLineResolvable(helper, itemRef, lineEl.getAsJsonObject(), langEn, textForItem)) {
                              return;
                           }
                        }
                     }

                     if (itemRefs.contains(abbot) && !assertAbbotSample(helper, abbot)) {
                        return;
                     }

                     if (!assertSignatureFeature(helper, items)) {
                        return;
                     }

                     LOGGER.info(
                        "Encyclopedia export: {} items across {} cultures written to {}",
                        new Object[]{itemRefs.size(), cultureKeys.size(), OUTPUT_DIR.toAbsolutePath()}
                     );
                     helper.succeed();
                     return;
                  } else {
                     return;
                  }
               } else {
                  helper.fail("index.json is missing one of the required types CULTURES/VILLAGERS/BUILDINGS");
               }
            } else {
               helper.fail("index.json has no items (content not loaded?)");
            }
         }
      }
   }

   private static boolean assertSignatureFeature(GameTestHelper helper, JsonArray items) {
      boolean hard = "{\n  \"byzantines\": {\n    \"byzantine_chestplate\": 4,\n    \"byzantine_fresco\": 1,\n    \"byzantine_mace\": 1,\n    \"byzantine_mosaic_red\": 1,\n    \"byzantine_pattern\": 3,\n    \"byzantine_pickaxe\": 4,\n    \"byzantine_tiles\": 1,\n    \"clothes_byz_silk\": 2,\n    \"feta\": 1,\n    \"olive_tree_sapling\": 1,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"silk\": 1,\n    \"souvlaki\": 1,\n    \"stained_glass_white\": 1,\n    \"wall_byzantine_icon_large\": 3,\n    \"winebasic\": 1,\n    \"winefancy\": 1\n  },\n  \"indian\": {\n    \"brick_mould\": 1,\n    \"charpoy\": 1,\n    \"chickencurry\": 1,\n    \"cotton\": 1,\n    \"decorated_brick_white\": 3,\n    \"indian_statue\": 1,\n    \"indpattern\": 6,\n    \"mud_brick\": 1,\n    \"rasgulla\": 1,\n    \"rice\": 1,\n    \"sandstone_carved\": 3,\n    \"thatch\": 1,\n    \"turmeric\": 1,\n    \"vegcurry\": 1,\n    \"wooden_bars\": 3\n  },\n  \"inuits\": {\n    \"bearmeat_cooked\": 1,\n    \"bearmeat_raw\": 1,\n    \"fire_pit\": 1,\n    \"furplate\": 4,\n    \"hidehanging\": 1,\n    \"icebrick\": 1,\n    \"inuitbow\": 1,\n    \"inuitcarving\": 1,\n    \"inuitmeatystew\": 3,\n    \"inuittrident\": 1,\n    \"inupattern\": 5,\n    \"seafood_cooked\": 1,\n    \"seafood_raw\": 1,\n    \"snowbrick\": 1,\n    \"snowwall\": 1,\n    \"ulu\": 1,\n    \"wolfmeat_cooked\": 1,\n    \"wolfmeat_raw\": 1\n  },\n  \"japanese\": {\n    \"ikayaki\": 1,\n    \"japanese_tachi\": 1,\n    \"japaneseblueplate\": 4,\n    \"japaneseguardplate\": 4,\n    \"japaneseredplate\": 4,\n    \"jappattern\": 5,\n    \"paper_wall\": 1,\n    \"rice\": 1,\n    \"sake\": 1,\n    \"thatch\": 1,\n    \"timber_frame_plain\": 1,\n    \"udon\": 1,\n    \"yumibow\": 1\n  },\n  \"mayan\": {\n    \"cacauhaa\": 1,\n    \"maize\": 1,\n    \"masa\": 1,\n    \"mayan_gold_block\": 1,\n    \"mayan_mace\": 1,\n    \"mayan_pattern\": 5,\n    \"mayan_pickaxe\": 4,\n    \"mayan_statue\": 1,\n    \"wah\": 1\n  },\n  \"norman\": {\n    \"boudin\": 1,\n    \"calva\": 1,\n    \"cider\": 1,\n    \"cider_apple\": 1,\n    \"norman_chestplate\": 4,\n    \"norman_pickaxe\": 4,\n    \"norman_sword\": 1,\n    \"norpattern\": 1,\n    \"rosette\": 1,\n    \"sapling_appletree\": 1,\n    \"stained_glass_red_blue\": 4,\n    \"stained_glass_white\": 1,\n    \"tapestry\": 1,\n    \"timber_frame_plain\": 2,\n    \"tripes\": 1\n  },\n  \"seljuk\": {\n    \"ayran\": 1,\n    \"brick_mould\": 1,\n    \"clothes_seljuk_cotton\": 2,\n    \"helva\": 1,\n    \"lokum\": 1,\n    \"mud_brick\": 4,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"pide\": 1,\n    \"pistachios\": 1,\n    \"seljukbow\": 1,\n    \"seljukplate\": 4,\n    \"seljukscimitar\": 1,\n    \"seljukturban\": 1,\n    \"sjkmpattern\": 3,\n    \"wall_carpet_large\": 3,\n    \"yogurt\": 1\n  }\n}\n"
         != null;
      Map<String, List<String>> familyMembers = new TreeMap<>();
      Map<String, List<String>> familyPrimaries = new TreeMap<>();
      Map<String, Map<String, Integer>> golden = new TreeMap<>();

      for (JsonElement el : items) {
         JsonObject entry = el.getAsJsonObject();
         if ("TRADEGOODS".equals(entry.get("type").getAsString())) {
            boolean signature = entry.has("signature") && entry.get("signature").getAsBoolean();
            boolean hasFamily = entry.has("signatureFamily");
            boolean primary = entry.has("signaturePrimary") && entry.get("signaturePrimary").getAsBoolean();
            String itemKey = entry.get("itemKey").getAsString();
            String culture = entry.get("cultureSlug").getAsString();
            if ((hasFamily || primary) && !signature && hard) {
               helper.fail("Signature: '" + itemKey + "' carries family/primary without signature=true");
               return false;
            }

            if (primary && !hasFamily && hard) {
               helper.fail("Signature: '" + itemKey + "' is signaturePrimary without a signatureFamily");
               return false;
            }

            if (signature) {
               if (hasFamily) {
                  String family = entry.get("signatureFamily").getAsString();
                  familyMembers.computeIfAbsent(family, k -> new ArrayList<>()).add(itemKey);
                  if (primary) {
                     familyPrimaries.computeIfAbsent(family, k -> new ArrayList<>()).add(itemKey);
                  }
               } else {
                  golden.computeIfAbsent(culture, k -> new TreeMap<>()).put(itemKey, 1);
               }
            }
         }
      }

      for (Entry<String, List<String>> fam : familyMembers.entrySet()) {
         String family = fam.getKey();
         List<String> members = fam.getValue();
         List<String> primaries = familyPrimaries.getOrDefault(family, List.of());
         if (hard && members.size() < 2) {
            helper.fail("Signature family '" + family + "' has only " + members.size() + " member(s); a family must fold ≥2");
            return false;
         }

         if (hard && primaries.size() != 1) {
            helper.fail("Signature family '" + family + "' must have exactly one primary, found " + primaries.size() + " " + primaries);
            return false;
         }

         String rep = primaries.isEmpty() ? members.stream().sorted().findFirst().orElse(family) : primaries.get(0);
         String culture = family.substring(0, family.indexOf(58));
         golden.computeIfAbsent(culture, k -> new TreeMap<>()).put(rep, members.size());
      }

      try {
         Files.createDirectories(OUTPUT_DIR);
         Files.writeString(OUTPUT_DIR.resolve("signature_goods.json"), GSON.toJson(golden), StandardCharsets.UTF_8);
      } catch (Exception e) {
         helper.fail("Failed to write signature_goods.json: " + e.getMessage());
         return false;
      }

      if (hard) {
         JsonElement expected = JsonParser.parseString(
            "{\n  \"byzantines\": {\n    \"byzantine_chestplate\": 4,\n    \"byzantine_fresco\": 1,\n    \"byzantine_mace\": 1,\n    \"byzantine_mosaic_red\": 1,\n    \"byzantine_pattern\": 3,\n    \"byzantine_pickaxe\": 4,\n    \"byzantine_tiles\": 1,\n    \"clothes_byz_silk\": 2,\n    \"feta\": 1,\n    \"olive_tree_sapling\": 1,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"silk\": 1,\n    \"souvlaki\": 1,\n    \"stained_glass_white\": 1,\n    \"wall_byzantine_icon_large\": 3,\n    \"winebasic\": 1,\n    \"winefancy\": 1\n  },\n  \"indian\": {\n    \"brick_mould\": 1,\n    \"charpoy\": 1,\n    \"chickencurry\": 1,\n    \"cotton\": 1,\n    \"decorated_brick_white\": 3,\n    \"indian_statue\": 1,\n    \"indpattern\": 6,\n    \"mud_brick\": 1,\n    \"rasgulla\": 1,\n    \"rice\": 1,\n    \"sandstone_carved\": 3,\n    \"thatch\": 1,\n    \"turmeric\": 1,\n    \"vegcurry\": 1,\n    \"wooden_bars\": 3\n  },\n  \"inuits\": {\n    \"bearmeat_cooked\": 1,\n    \"bearmeat_raw\": 1,\n    \"fire_pit\": 1,\n    \"furplate\": 4,\n    \"hidehanging\": 1,\n    \"icebrick\": 1,\n    \"inuitbow\": 1,\n    \"inuitcarving\": 1,\n    \"inuitmeatystew\": 3,\n    \"inuittrident\": 1,\n    \"inupattern\": 5,\n    \"seafood_cooked\": 1,\n    \"seafood_raw\": 1,\n    \"snowbrick\": 1,\n    \"snowwall\": 1,\n    \"ulu\": 1,\n    \"wolfmeat_cooked\": 1,\n    \"wolfmeat_raw\": 1\n  },\n  \"japanese\": {\n    \"ikayaki\": 1,\n    \"japanese_tachi\": 1,\n    \"japaneseblueplate\": 4,\n    \"japaneseguardplate\": 4,\n    \"japaneseredplate\": 4,\n    \"jappattern\": 5,\n    \"paper_wall\": 1,\n    \"rice\": 1,\n    \"sake\": 1,\n    \"thatch\": 1,\n    \"timber_frame_plain\": 1,\n    \"udon\": 1,\n    \"yumibow\": 1\n  },\n  \"mayan\": {\n    \"cacauhaa\": 1,\n    \"maize\": 1,\n    \"masa\": 1,\n    \"mayan_gold_block\": 1,\n    \"mayan_mace\": 1,\n    \"mayan_pattern\": 5,\n    \"mayan_pickaxe\": 4,\n    \"mayan_statue\": 1,\n    \"wah\": 1\n  },\n  \"norman\": {\n    \"boudin\": 1,\n    \"calva\": 1,\n    \"cider\": 1,\n    \"cider_apple\": 1,\n    \"norman_chestplate\": 4,\n    \"norman_pickaxe\": 4,\n    \"norman_sword\": 1,\n    \"norpattern\": 1,\n    \"rosette\": 1,\n    \"sapling_appletree\": 1,\n    \"stained_glass_red_blue\": 4,\n    \"stained_glass_white\": 1,\n    \"tapestry\": 1,\n    \"timber_frame_plain\": 2,\n    \"tripes\": 1\n  },\n  \"seljuk\": {\n    \"ayran\": 1,\n    \"brick_mould\": 1,\n    \"clothes_seljuk_cotton\": 2,\n    \"helva\": 1,\n    \"lokum\": 1,\n    \"mud_brick\": 4,\n    \"oliveoil\": 1,\n    \"olives\": 1,\n    \"pide\": 1,\n    \"pistachios\": 1,\n    \"seljukbow\": 1,\n    \"seljukplate\": 4,\n    \"seljukscimitar\": 1,\n    \"seljukturban\": 1,\n    \"sjkmpattern\": 3,\n    \"wall_carpet_large\": 3,\n    \"yogurt\": 1\n  }\n}\n"
         );
         JsonElement actual = JsonParser.parseString(GSON.toJson(golden));
         if (!expected.equals(actual)) {
            helper.fail("Signature golden drift — see build/encyclopedia/signature_goods.json vs SignatureGolden.JSON. Computed:\n" + GSON.toJson(golden));
            return false;
         }
      }

      return assertGatheringOutputsSold(helper, hard);
   }

   private static boolean assertGatheringOutputsSold(GameTestHelper helper, boolean hard) {
      Set<String> sold = new HashSet<>();

      for (ResourceLocation cultureId : ModCultures.getAllCultures().keySet()) {
         Map<String, ShopProfile> profiles = ShopProfileLoader.getProfiles(cultureId);
         if (profiles != null) {
            for (ShopProfile profile : profiles.values()) {
               for (String goodId : profile.sells()) {
                  TradeGood good = TradeGoodsLoader.getGoodById(cultureId, goodId);
                  if (good != null) {
                     ResourceLocation loc = good.itemLocation();
                     if ("millenaire".equals(loc.getNamespace())) {
                        sold.add(loc.toString());
                     }
                  }
               }
            }
         }
      }

      Set<String> unsold = new TreeSet<>();

      for (GatheringGoal goal : Millenaire.getGoalRegistry().getGatheringGoals()) {
         JsonObject params = goal.getGatheringType().handlerParams();
         if (params != null) {
            JsonArray outputs = params.getAsJsonArray("outputs");
            if (outputs != null) {
               for (JsonElement el : outputs) {
                  JsonObject out = el.getAsJsonObject();
                  if (out.has("item")) {
                     String item = out.get("item").getAsString();
                     if (item.startsWith("millenaire:") && !sold.contains(item) && !GATHERING_UNSOLD_ALLOWLIST.contains(item)) {
                        unsold.add(item);
                     }
                  }
               }
            }
         }
      }

      try {
         Files.createDirectories(OUTPUT_DIR);
         Files.writeString(OUTPUT_DIR.resolve("gathering_unsold.json"), GSON.toJson(unsold), StandardCharsets.UTF_8);
      } catch (Exception e) {
         helper.fail("Failed to write gathering_unsold.json: " + e.getMessage());
         return false;
      }

      if (hard && !unsold.isEmpty()) {
         helper.fail("Gathering produces millenaire items no culture sells (add to allowlist if legitimate): " + unsold);
         return false;
      } else {
         return true;
      }
   }

   private static boolean assertLineResolvable(GameTestHelper helper, String itemRef, JsonObject line, JsonObject langEn, JsonObject textForItem) {
      if (!assertTextResolvable(helper, itemRef, line.getAsJsonObject("text"), langEn, textForItem)) {
         return false;
      }

      if (!assertTextResolvable(helper, itemRef, line.getAsJsonObject("referenceButtonLabel"), langEn, textForItem)) {
         return false;
      }

      JsonArray columns = line.getAsJsonArray("columns");
      if (columns != null) {
         for (JsonElement colEl : columns) {
            JsonObject col = colEl.getAsJsonObject();
            if (!assertTextResolvable(helper, itemRef, col.getAsJsonObject("text"), langEn, textForItem)) {
               return false;
            }

            if (!assertTextResolvable(helper, itemRef, col.getAsJsonObject("referenceButtonLabel"), langEn, textForItem)) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean assertTextResolvable(GameTestHelper helper, String itemRef, JsonObject node, JsonObject langEn, JsonObject textForItem) {
      if (node == null) {
         return true;
      }

      if (node.has("key")) {
         String key = node.get("key").getAsString();
         if (!langEn.has(key)) {
            helper.fail("Item " + itemRef + " references key '" + key + "' absent from lang/en_us");
            return false;
         }
      } else if (node.has("id")) {
         String id = node.get("id").getAsString();
         if (textForItem == null || !textForItem.has(id)) {
            helper.fail("Item " + itemRef + " references slot id '" + id + "' absent from text/en_us[" + itemRef + "]");
            return false;
         }
      }

      return true;
   }

   private static boolean assertAbbotSample(GameTestHelper helper, String abbot) {
      JsonObject structure = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("structure").resolve(abbot + ".json"));
      JsonObject textFr = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("text").resolve("fr_fr.json"));
      JsonObject langFr = readJsonObject(helper, OUTPUT_DIR.resolve("generated").resolve("lang").resolve("fr_fr.json"));
      if (structure == null || textFr == null || langFr == null) {
         return false;
      }

      if (langFr.size() == 0) {
         helper.fail("lang/fr_fr is empty — the mod lang file was not copied");
         return false;
      }

      JsonArray lines = structure.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("lines");
      if (!hasKeyLine(lines)) {
         helper.fail("abbot structure has no key-based ossature line (expected at least one {key:…} text node)");
         return false;
      }

      List<String> slotIds = new ArrayList<>();
      collectSlotIds(lines, slotIds);
      JsonObject abbotFr = textFr.getAsJsonObject(abbot);

      for (String id : slotIds) {
         if (abbotFr == null || !abbotFr.has(id)) {
            helper.fail("abbot slot id '" + id + "' not populated in text/fr_fr[" + abbot + "]");
            return false;
         }
      }

      return true;
   }

   private static boolean hasKeyLine(JsonArray lines) {
      for (JsonElement lineEl : lines) {
         JsonObject line = lineEl.getAsJsonObject();
         if (nodeHasKey(line.getAsJsonObject("text"))) {
            return true;
         }

         if (nodeHasKey(line.getAsJsonObject("referenceButtonLabel"))) {
            return true;
         }

         JsonArray columns = line.getAsJsonArray("columns");
         if (columns != null) {
            for (JsonElement colEl : columns) {
               JsonObject col = colEl.getAsJsonObject();
               if (nodeHasKey(col.getAsJsonObject("text"))) {
                  return true;
               }

               if (nodeHasKey(col.getAsJsonObject("referenceButtonLabel"))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean nodeHasKey(JsonObject node) {
      return node != null && node.has("key");
   }

   private static void collectSlotIds(JsonArray lines, List<String> out) {
      for (JsonElement lineEl : lines) {
         JsonObject line = lineEl.getAsJsonObject();
         collectSlotId(line.getAsJsonObject("text"), out);
         collectSlotId(line.getAsJsonObject("referenceButtonLabel"), out);
         JsonArray columns = line.getAsJsonArray("columns");
         if (columns != null) {
            for (JsonElement colEl : columns) {
               JsonObject col = colEl.getAsJsonObject();
               collectSlotId(col.getAsJsonObject("text"), out);
               collectSlotId(col.getAsJsonObject("referenceButtonLabel"), out);
            }
         }
      }
   }

   private static void collectSlotId(JsonObject node, List<String> out) {
      if (node != null && node.has("id")) {
         out.add(node.get("id").getAsString());
      }
   }

   private static boolean assertVillageData(GameTestHelper helper, String itemRef) {
      Path villageFile = OUTPUT_DIR.resolve("generated").resolve("villages").resolve(itemRef + ".json");
      if (!Files.exists(villageFile)) {
         helper.fail("Missing village data file for index item: " + itemRef);
         return false;
      }

      JsonObject village = readJsonObject(helper, villageFile);
      if (village == null) {
         return false;
      }

      if (village.has("type") && village.has("population") && village.has("composition")) {
         return true;
      }

      helper.fail("Village data " + itemRef + " is missing type/population/composition");
      return false;
   }

   private static boolean assertBuildingCost(GameTestHelper helper, String itemRef) {
      Path costFile = OUTPUT_DIR.resolve("generated").resolve("buildingcost").resolve(itemRef + ".json");
      if (!Files.exists(costFile)) {
         helper.fail("Missing building-cost file for index item: " + itemRef);
         return false;
      }

      JsonObject cost = readJsonObject(helper, costFile);
      if (cost == null) {
         return false;
      }

      JsonArray variants = cost.getAsJsonArray("variants");
      if (variants != null && !variants.isEmpty()) {
         JsonArray levels = variants.get(0).getAsJsonObject().getAsJsonArray("levels");
         if (levels != null && !levels.isEmpty()) {
            if (!"initial".equals(levels.get(0).getAsJsonObject().get("kind").getAsString())) {
               helper.fail("Building cost " + itemRef + " first level must be the initial build");
               return false;
            } else {
               return true;
            }
         } else {
            helper.fail("Building cost " + itemRef + " variant has no levels");
            return false;
         }
      } else {
         helper.fail("Building cost " + itemRef + " has no variants");
         return false;
      }
   }

   private static boolean hasType(JsonArray items, String type) {
      for (JsonElement el : items) {
         if (type.equals(el.getAsJsonObject().get("type").getAsString())) {
            return true;
         }
      }

      return false;
   }

   private static JsonObject readJsonObject(GameTestHelper helper, Path path) {
      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         return (JsonObject)GSON.fromJson(reader, JsonObject.class);
      } catch (Exception e) {
         helper.fail("Failed to read JSON file " + path + ": " + e.getMessage());
         return null;
      }
   }
}
