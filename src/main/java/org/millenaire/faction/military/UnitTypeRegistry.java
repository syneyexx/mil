package org.millenaire.faction.military;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.Millenaire;

/**
 * Built-in unit type definitions. Culture-specific lookups prefer matching culture ids,
 * falling back to universal types. No hard-coded culture name branches in call sites.
 */
public final class UnitTypeRegistry {
   private static final Map<ResourceLocation, UnitTypeDefinition> DEFINITIONS = new LinkedHashMap<>();

   static {
      register(universal("militia", "Militia", UnitCategory.MILITIA, 20, 1, 6, EquipmentLevel.BASIC));
      register(universal("infantry", "Infantry", UnitCategory.INFANTRY, 24, 1, 8, EquipmentLevel.STANDARD));
      register(universal("archers", "Archers", UnitCategory.RANGED, 20, 1, 9, EquipmentLevel.STANDARD));
      register(universal("guards", "Guards", UnitCategory.GUARD, 16, 1, 7, EquipmentLevel.STANDARD));
      register(universal("cavalry", "Cavalry", UnitCategory.CAVALRY, 16, 1, 11, EquipmentLevel.GOOD));
      register(universal("specialists", "Specialists", UnitCategory.SPECIAL, 12, 1, 12, EquipmentLevel.GOOD));
   }

   private UnitTypeRegistry() {
   }

   private static UnitTypeDefinition universal(String path, String name, UnitCategory category, int max, int manpower, int strength, EquipmentLevel equipment) {
      return new UnitTypeDefinition(
         ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, "unit/" + path),
         name,
         category,
         null,
         max,
         manpower,
         strength,
         MilitaryConfig.FOOD_PER_SOLDIER_MAINTENANCE,
         MilitaryConfig.EQUIPMENT_VALUE_PER_SOLDIER,
         equipment
      );
   }

   private static void register(UnitTypeDefinition definition) {
      DEFINITIONS.put(definition.id(), definition);
   }

   /**
    * Registers an additional culture-bound or universal unit definition at runtime/mod init.
    * Existing ids are not overwritten.
    */
   public static boolean registerDefinition(UnitTypeDefinition definition) {
      if (definition == null || DEFINITIONS.containsKey(definition.id())) {
         return false;
      }

      DEFINITIONS.put(definition.id(), definition);
      return true;
   }

   @Nullable
   public static UnitTypeDefinition get(ResourceLocation id) {
      return id == null ? null : DEFINITIONS.get(id);
   }

   public static Collection<UnitTypeDefinition> getAll() {
      return Collections.unmodifiableCollection(DEFINITIONS.values());
   }

   public static UnitTypeDefinition resolveDefault(@Nullable ResourceLocation cultureId, UnitCategory category) {
      UnitTypeDefinition cultureMatch = null;
      UnitTypeDefinition universal = null;

      for (UnitTypeDefinition definition : DEFINITIONS.values()) {
         if (definition.category() != category) {
            continue;
         }

         if (definition.cultureId() == null) {
            universal = definition;
         } else if (definition.matchesCulture(cultureId)) {
            cultureMatch = definition;
            break;
         }
      }

      if (cultureMatch != null) {
         return cultureMatch;
      }

      if (universal != null) {
         return universal;
      }

      return DEFINITIONS.values().iterator().next();
   }

   public static UnitTypeDefinition defaultInfantry(@Nullable ResourceLocation cultureId) {
      return resolveDefault(cultureId, UnitCategory.INFANTRY);
   }
}
