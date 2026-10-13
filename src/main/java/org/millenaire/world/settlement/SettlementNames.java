package org.millenaire.world.settlement;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.NameLists;
import org.millenaire.culture.VillageType;

public final class SettlementNames {
   private SettlementNames() {
   }

   public static String generate(SettlementId id, @Nullable ResourceLocation cultureId, long worldSeed) {
      long seed = worldSeed ^ id.value().hashCode() * 0x9E3779B97F4A7C15L;
      Random random = new Random(seed);
      if (cultureId != null) {
         NameLists lists = ModCultures.getNameLists(cultureId);
         VillageType type = pickAnyType(cultureId);
         if (lists != null && type != null && type.nameList() != null) {
            // Use a seeded pick from the list
            List<String> names = lists.lists().get(type.nameList());
            if (names != null && !names.isEmpty()) {
               return names.get(random.nextInt(names.size()));
            }
            String fromApi = lists.randomFrom(type.nameList());
            if (fromApi != null) {
               return fromApi;
            }
         }
      }
      return "Settlement-" + Integer.toHexString(id.value().hashCode() & 0xffff);
   }

   @Nullable
   private static VillageType pickAnyType(ResourceLocation cultureId) {
      for (VillageType type : ModCultures.getAllVillageTypes().values()) {
         if (type.culture().equals(cultureId) && !type.loneBuilding() && type.nameList() != null) {
            return type;
         }
      }
      return null;
   }
}
