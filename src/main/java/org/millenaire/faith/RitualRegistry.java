package org.millenaire.faith;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class RitualRegistry {
   private static final Map<ResourceLocation, RitualDefinition> BY_ID = new ConcurrentHashMap<>();

   private RitualRegistry() {
   }

   public static void clear() {
      BY_ID.clear();
   }

   public static void register(RitualDefinition definition) {
      BY_ID.put(definition.id(), definition);
   }

   @Nullable
   public static RitualDefinition get(ResourceLocation id) {
      return id == null ? null : BY_ID.get(id);
   }

   public static Collection<RitualDefinition> all() {
      return Collections.unmodifiableCollection(BY_ID.values());
   }

   public static int size() {
      return BY_ID.size();
   }
}
