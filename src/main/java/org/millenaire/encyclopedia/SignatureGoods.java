package org.millenaire.encyclopedia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.commerce.TradeGood;

public final class SignatureGoods {
   public static final int MAX_SIGNATURE_CULTURES = 3;
   public static final List<String> EXCLUDED_ITEM_PATH_PREFIXES = List.of("path_", "sod_");
   private static final String MILLENAIRE_NAMESPACE = "millenaire";
   private static final Pattern ARMOR_SUFFIX = Pattern.compile("_?(chestplate|plate|boots|helmet|leggings|legs)$");
   private static final List<SignatureGoods.FamilyRule> FAMILIES = List.of(
      new SignatureGoods.FamilyRule(
         "armor", matches(".*(chestplate|plate|boots|helmet|leggings|legs)"), id -> ARMOR_SUFFIX.matcher(id).replaceFirst(""), matches(".*plate")
      ),
      new SignatureGoods.FamilyRule("tools", matches(".*_(axe|hoe|pickaxe|shovel)"), id -> "", matches(".*pickaxe")),
      new SignatureGoods.FamilyRule("banner", matches(".*pattern(_?\\d+)?"), id -> "", matches(".*pattern")),
      new SignatureGoods.FamilyRule("glass", matches("stained_glass_(?!white$).+"), id -> "", is("stained_glass_red_blue")),
      new SignatureGoods.FamilyRule("wallcarpet", matches("wall_carpet_.+"), id -> "", is("wall_carpet_large")),
      new SignatureGoods.FamilyRule("icon", matches("wall_byzantine_icon_.+"), id -> "", is("wall_byzantine_icon_large")),
      new SignatureGoods.FamilyRule("mudbrick", matches("mud(brick|_brick).*"), id -> "", is("mud_brick")),
      new SignatureGoods.FamilyRule("clothes", matches("clothes_.+"), id -> "", s -> s.endsWith("silk") || s.endsWith("cotton")),
      new SignatureGoods.FamilyRule("carvedstone", matches(".*sandstone_carved"), id -> "", is("sandstone_carved")),
      new SignatureGoods.FamilyRule("woodbars", matches("wooden_bars.*"), id -> "", is("wooden_bars")),
      new SignatureGoods.FamilyRule("decobrick", matches(".*_brick_white|paint_bucket_white"), id -> "", is("decorated_brick_white")),
      new SignatureGoods.FamilyRule("timberframe", matches("timber_frame_.+"), id -> "", is("timber_frame_plain")),
      new SignatureGoods.FamilyRule("stew", matches("inuit.*stew"), id -> "", is("inuitmeatystew"))
   );

   private SignatureGoods() {
   }

   public static boolean isSignature(String cultureId, TradeGood good, Map<ResourceLocation, Set<String>> offerCounts) {
      ResourceLocation item = good.itemLocation();
      if (!"millenaire".equals(item.getNamespace())) {
         return false;
      }

      String path = item.getPath();

      for (String prefix : EXCLUDED_ITEM_PATH_PREFIXES) {
         if (path.startsWith(prefix)) {
            return false;
         }
      }

      Set<String> offerers = offerCounts.get(item);
      return offerers != null && offerers.contains(cultureId) ? offerers.size() <= 3 : false;
   }

   public static Map<String, SignatureGoods.SignatureInfo> computeForCulture(
      String cultureId, List<TradeGood> goods, Map<ResourceLocation, Set<String>> offerCounts
   ) {
      List<TradeGood> signatures = new ArrayList<>();
      Map<String, List<String>> familyMembers = new LinkedHashMap<>();

      for (TradeGood good : goods) {
         if (isSignature(cultureId, good, offerCounts)) {
            signatures.add(good);
            familyKey(cultureId, good.id()).ifPresent(keyx -> familyMembers.computeIfAbsent(keyx, k -> new ArrayList<>()).add(good.id()));
         }
      }

      Map<String, String> familyPrimary = new LinkedHashMap<>();

      for (Entry<String, List<String>> family : familyMembers.entrySet()) {
         if (family.getValue().size() >= 2) {
            familyPrimary.put(family.getKey(), electPrimary(family.getValue()));
         }
      }

      Map<String, SignatureGoods.SignatureInfo> out = new LinkedHashMap<>();

      for (TradeGood good : signatures) {
         Optional<String> key = familyKey(cultureId, good.id());
         if (key.isPresent() && familyPrimary.containsKey(key.get())) {
            boolean primary = good.id().equals(familyPrimary.get(key.get()));
            out.put(good.id(), new SignatureGoods.SignatureInfo(key.get(), primary));
         } else {
            out.put(good.id(), new SignatureGoods.SignatureInfo(null, false));
         }
      }

      return out;
   }

   private static String electPrimary(List<String> members) {
      List<String> reps = members.stream().filter(SignatureGoods::isRepresentative).sorted().toList();
      return reps.size() == 1 ? reps.get(0) : members.stream().sorted().findFirst().orElseThrow();
   }

   public static Optional<String> familyKey(String cultureId, String goodId) {
      for (SignatureGoods.FamilyRule rule : FAMILIES) {
         if (rule.member.test(goodId)) {
            String suffix = rule.styleSuffix.apply(goodId);
            String name = suffix.isEmpty() ? rule.baseName : rule.baseName + "_" + suffix;
            return Optional.of(cultureId + ":" + name);
         }
      }

      return Optional.empty();
   }

   public static boolean isRepresentative(String goodId) {
      for (SignatureGoods.FamilyRule rule : FAMILIES) {
         if (rule.member.test(goodId)) {
            return rule.representative.test(goodId);
         }
      }

      return false;
   }

   private static Predicate<String> matches(String regex) {
      Pattern p = Pattern.compile(regex);
      return s -> p.matcher(s).matches();
   }

   private static Predicate<String> is(String id) {
      return id::equals;
   }

   private record FamilyRule(String baseName, Predicate<String> member, UnaryOperator<String> styleSuffix, Predicate<String> representative) {
   }

   public record SignatureInfo(@Nullable String family, boolean primary) {
   }
}
