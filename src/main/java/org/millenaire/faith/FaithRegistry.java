package org.millenaire.faith;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class FaithRegistry {
   private static final Map<ResourceLocation, FaithDefinition> BY_ID = new ConcurrentHashMap<>();
   private static final Map<ResourceLocation, ResourceLocation> CULTURE_DEFAULT = new ConcurrentHashMap<>();

   private FaithRegistry() {
   }

   public static void clear() {
      BY_ID.clear();
      CULTURE_DEFAULT.clear();
   }

   public static void register(FaithDefinition definition) {
      BY_ID.put(definition.id(), definition);
      if (definition.cultureId() != null && !definition.isPlayerCustom()) {
         CULTURE_DEFAULT.put(definition.cultureId(), definition.id());
      }
   }

   @Nullable
   public static FaithDefinition get(ResourceLocation id) {
      return id == null ? null : BY_ID.get(id);
   }

   @Nullable
   public static FaithDefinition getDefaultForCulture(ResourceLocation cultureId) {
      ResourceLocation faithId = CULTURE_DEFAULT.get(cultureId);
      return faithId == null ? null : BY_ID.get(faithId);
   }

   @Nullable
   public static ResourceLocation defaultFaithIdForCulture(ResourceLocation cultureId) {
      return CULTURE_DEFAULT.get(cultureId);
   }

   public static Collection<FaithDefinition> all() {
      return Collections.unmodifiableCollection(BY_ID.values());
   }

   public static int size() {
      return BY_ID.size();
   }

   public static ResourceLocation playerCustomId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "player_custom");
   }

   /** Cenobites / De Orde faith — culture default for {@code millenaire:orde}, also selectable at founding. */
   public static ResourceLocation ordeId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "orde");
   }
}
