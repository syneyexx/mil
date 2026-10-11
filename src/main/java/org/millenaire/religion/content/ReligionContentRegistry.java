package org.millenaire.religion.content;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * In-memory registry of loaded religion content. Cleared and rebuilt on content reload.
 */
public final class ReligionContentRegistry {
   private static final Map<ResourceLocation, ReligionDefinition> TRADITIONS = new LinkedHashMap<>();
   private static final Map<ResourceLocation, RitualDefinition> RITUALS = new LinkedHashMap<>();
   /** culture path (e.g. norman) -> tradition ids (may be empty = no organized religion). */
   private static final Map<String, List<ResourceLocation>> CULTURE_TRADITIONS = new LinkedHashMap<>();
   private static final Map<String, Boolean> CULTURE_EXPLICIT_NONE = new LinkedHashMap<>();

   private ReligionContentRegistry() {
   }

   public static void clear() {
      TRADITIONS.clear();
      RITUALS.clear();
      CULTURE_TRADITIONS.clear();
      CULTURE_EXPLICIT_NONE.clear();
   }

   public static void registerTradition(ReligionDefinition def) {
      if (def != null) {
         TRADITIONS.put(def.id(), def);
      }
   }

   public static void registerRitual(RitualDefinition def) {
      if (def != null) {
         RITUALS.put(def.id(), def);
      }
   }

   public static void bindCulture(String cultureKey, List<ResourceLocation> traditionIds, boolean explicitNone) {
      if (cultureKey == null || cultureKey.isEmpty()) {
         return;
      }
      String key = normalizeCulture(cultureKey);
      CULTURE_TRADITIONS.put(key, traditionIds == null ? List.of() : List.copyOf(traditionIds));
      CULTURE_EXPLICIT_NONE.put(key, explicitNone);
   }

   public static Optional<ReligionDefinition> getTradition(ResourceLocation id) {
      return Optional.ofNullable(TRADITIONS.get(id));
   }

   @Nullable
   public static ReligionDefinition traditionOrNull(@Nullable ResourceLocation id) {
      return id == null ? null : TRADITIONS.get(id);
   }

   public static Optional<RitualDefinition> getRitual(ResourceLocation id) {
      return Optional.ofNullable(RITUALS.get(id));
   }

   @Nullable
   public static RitualDefinition ritualOrNull(@Nullable ResourceLocation id) {
      return id == null ? null : RITUALS.get(id);
   }

   public static Collection<ReligionDefinition> allTraditions() {
      return Collections.unmodifiableCollection(TRADITIONS.values());
   }

   public static Collection<RitualDefinition> allRituals() {
      return Collections.unmodifiableCollection(RITUALS.values());
   }

   public static List<ResourceLocation> traditionsForCulture(String cultureKey) {
      List<ResourceLocation> list = CULTURE_TRADITIONS.get(normalizeCulture(cultureKey));
      return list == null ? List.of() : list;
   }

   public static boolean cultureHasExplicitNone(String cultureKey) {
      return Boolean.TRUE.equals(CULTURE_EXPLICIT_NONE.get(normalizeCulture(cultureKey)));
   }

   public static boolean cultureConfigured(String cultureKey) {
      String key = normalizeCulture(cultureKey);
      return CULTURE_TRADITIONS.containsKey(key) || CULTURE_EXPLICIT_NONE.containsKey(key);
   }

   private static String normalizeCulture(String cultureKey) {
      if (cultureKey == null) {
         return "";
      }
      if (cultureKey.contains(":")) {
         return ResourceLocation.parse(cultureKey).getPath();
      }
      return cultureKey.toLowerCase();
   }
}
