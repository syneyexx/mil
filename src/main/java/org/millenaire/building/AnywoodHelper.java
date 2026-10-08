package org.millenaire.building;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class AnywoodHelper {
   public static final ResourceLocation ANYWOOD_LOG = ResourceLocation.parse("millenaire:anywood_log");
   public static final TagKey<Item> LOGS_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:logs"));

   private AnywoodHelper() {
   }

   public static boolean isAnywood(ResourceLocation key) {
      return ANYWOOD_LOG.equals(key);
   }

   public static boolean isLogResourceLocation(ResourceLocation key) {
      if (isAnywood(key)) {
         return false;
      }

      String path = key.getPath();
      return path.endsWith("_log");
   }

   public static Set<Item> collectSpecificLogs(Map<ResourceLocation, Integer> requiredResources) {
      Set<Item> result = new HashSet<>();

      for (ResourceLocation key : requiredResources.keySet()) {
         if (isLogResourceLocation(key)) {
            Item item = (Item)BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            if (item != null) {
               result.add(item);
            }
         }
      }

      return result;
   }

   public static int sumConsumableSpecificLogs(Map<ResourceLocation, Integer> requiredResources, ToIntFunction<ResourceLocation> stockOf) {
      int total = 0;

      for (Entry<ResourceLocation, Integer> entry : requiredResources.entrySet()) {
         if (isLogResourceLocation(entry.getKey())) {
            total += Math.min(entry.getValue(), stockOf.applyAsInt(entry.getKey()));
         }
      }

      return total;
   }

   public static int anywoodAvailable(int rawLogCount, Map<ResourceLocation, Integer> requiredResources, ToIntFunction<ResourceLocation> stockOf) {
      return Math.max(0, rawLogCount - sumConsumableSpecificLogs(requiredResources, stockOf));
   }
}
