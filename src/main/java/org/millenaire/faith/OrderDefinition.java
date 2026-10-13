package org.millenaire.faith;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Content template for a secret order. Not every village receives an instance.
 */
public record OrderDefinition(
   ResourceLocation id,
   String displayName,
   List<ResourceLocation> allowedCultures,
   int minVillagePop,
   double spawnChance,
   int secrecyDefault,
   List<String> buildingTags,
   List<String> clergyPreferenceTags,
   List<String> ritualIds,
   Map<String, String> themes,
   boolean allowsOrders
) {
   public OrderDefinition {
      allowedCultures = List.copyOf(allowedCultures);
      buildingTags = List.copyOf(buildingTags);
      clergyPreferenceTags = List.copyOf(clergyPreferenceTags);
      ritualIds = List.copyOf(ritualIds);
      themes = Map.copyOf(themes);
   }

   public boolean allowsCulture(@Nullable ResourceLocation cultureId) {
      if (this.allowedCultures.isEmpty()) {
         return true;
      }
      if (cultureId == null) {
         return false;
      }
      return this.allowedCultures.contains(cultureId);
   }
}
