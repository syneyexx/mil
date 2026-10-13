package org.millenaire.faith;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class OrderRegistry {
   private static final Map<ResourceLocation, OrderDefinition> BY_ID = new ConcurrentHashMap<>();
   private static int contentGeneration;

   private OrderRegistry() {
   }

   public static void clear() {
      BY_ID.clear();
      contentGeneration++;
   }

   public static int contentGeneration() {
      return contentGeneration;
   }

   public static void register(OrderDefinition definition) {
      BY_ID.put(definition.id(), definition);
   }

   @Nullable
   public static OrderDefinition get(ResourceLocation id) {
      return id == null ? null : BY_ID.get(id);
   }

   public static Collection<OrderDefinition> all() {
      return Collections.unmodifiableCollection(BY_ID.values());
   }

   public static int size() {
      return BY_ID.size();
   }

   public static ResourceLocation lastFlameId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "order_last_flame");
   }

   public static ResourceLocation closedEyeId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "order_closed_eye");
   }

   public static ResourceLocation rootCircleId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "order_root_circle");
   }
}
