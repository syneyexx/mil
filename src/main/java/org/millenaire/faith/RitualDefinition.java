package org.millenaire.faith;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record RitualDefinition(
   ResourceLocation id,
   String displayName,
   int capacity,
   int durationTicks,
   int cooldownDays,
   int startWindowMin,
   int startWindowMax,
   int gatherDeadlineTicks,
   double presenceRadius,
   Map<String, Integer> requiredGoods,
   int devotionReward,
   int cohesionReward,
   int secrecyDelta,
   int tensionDelta,
   List<String> orderTags
) {
   public RitualDefinition {
      requiredGoods = Map.copyOf(requiredGoods);
      orderTags = List.copyOf(orderTags);
   }

   public static RitualDefinition defaults(ResourceLocation id, String name) {
      return new RitualDefinition(
         id,
         name,
         4,
         2000,
         3,
         13000,
         23000,
         6000,
         8.0,
         Map.of(),
         10,
         5,
         0,
         0,
         List.of()
      );
   }

   public Map<String, Integer> mutableGoodsCopy() {
      return new LinkedHashMap<>(this.requiredGoods);
   }
}
