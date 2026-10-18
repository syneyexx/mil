package org.millenaire.faith;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Data-driven faith / religion definition. Culture defaults and player_custom template.
 */
public record FaithDefinition(
   ResourceLocation id,
   String displayName,
   @Nullable ResourceLocation cultureId,
   List<String> clergyVillagerTags,
   List<String> churchBuildingTags,
   @Nullable String holyShopId,
   String festivalName,
   int festivalDurationTicks,
   @Nullable ResourceLocation preferredShrinePlan
) {
   public FaithDefinition {
      clergyVillagerTags = List.copyOf(clergyVillagerTags);
      churchBuildingTags = List.copyOf(churchBuildingTags);
   }

   public boolean isPlayerCustom() {
      return "player_custom".equals(this.id.getPath());
   }

   /** Cenobites / De Orde faith (culture default for the orde civilization). */
   public boolean isOrde() {
      return "orde".equals(this.id.getPath());
   }
}
