package org.millenaire.faction.military;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Data-driven military unit definition. Culture binding is optional; null means universal.
 */
public record UnitTypeDefinition(
   ResourceLocation id,
   String displayName,
   UnitCategory category,
   @Nullable ResourceLocation cultureId,
   int defaultMaxStrength,
   int manpowerCostPerSoldier,
   int baseStrengthPerSoldier,
   int foodMaintenancePerSoldier,
   int equipmentValuePerSoldier,
   EquipmentLevel defaultEquipmentLevel
) {
   public UnitTypeDefinition {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(defaultEquipmentLevel, "defaultEquipmentLevel");
      if (defaultMaxStrength < 1) {
         throw new IllegalArgumentException("defaultMaxStrength");
      }

      if (manpowerCostPerSoldier < 1) {
         throw new IllegalArgumentException("manpowerCostPerSoldier");
      }
   }

   public boolean matchesCulture(@Nullable ResourceLocation culture) {
      return this.cultureId == null || culture != null && this.cultureId.equals(culture);
   }
}
