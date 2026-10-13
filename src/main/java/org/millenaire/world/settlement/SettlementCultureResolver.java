package org.millenaire.world.settlement;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;

public final class SettlementCultureResolver {
   private SettlementCultureResolver() {
   }

   @Nullable
   public static ResourceLocation resolveCulture(Holder<Biome> biome) {
      List<ResourceLocation> matches = new ArrayList<>();
      for (VillageType type : ModCultures.getAllVillageTypes().values()) {
         if (type.loneBuilding()) {
            continue;
         }
         for (TagKey<Biome> tag : type.biomeTags()) {
            if (biome.is(tag)) {
               matches.add(type.culture());
               break;
            }
         }
      }
      if (matches.isEmpty()) {
         try {
            String fallback = org.millenaire.config.MillenaireServerConfig.SERVER.settlementsFallbackCulture.get();
            return ResourceLocation.tryParse(fallback);
         } catch (Throwable t) {
            return ResourceLocation.tryParse("millenaire:norman");
         }
      }
      // Deterministic pick: first after sort
      matches.sort(ResourceLocation::compareNamespaced);
      return matches.get(0);
   }

   @Nullable
   public static VillageType pickVillageType(ResourceLocation cultureId, Holder<Biome> biome) {
      VillageType best = null;
      for (VillageType type : ModCultures.getAllVillageTypes().values()) {
         if (!type.culture().equals(cultureId) || type.loneBuilding()) {
            continue;
         }
         boolean ok = false;
         for (TagKey<Biome> tag : type.biomeTags()) {
            if (biome.is(tag)) {
               ok = true;
               break;
            }
         }
         if (ok && (best == null || type.radius() > best.radius())) {
            best = type;
         }
      }
      return best;
   }
}
