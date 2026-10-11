package org.millenaire.culture;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record Culture(
   ResourceLocation id,
   String displayName,
   ResourceLocation panelTexture,
   String nativeLanguage,
   TravelBookCategories travelBookCategories,
   List<String> knownCrops,
   List<String> knownHuntingDrops,
   Optional<ResourceLocation> mapIcon,
   @Nullable String cultureBannerNbt,
   boolean excludeFromTrains,
   CulturalTemperament temperament
) {
   public static final ResourceLocation DEFAULT_PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/panels/default.png");

   /** Back-compat for call sites that omit temperament. */
   public Culture(
      ResourceLocation id,
      String displayName,
      ResourceLocation panelTexture,
      String nativeLanguage,
      TravelBookCategories travelBookCategories,
      List<String> knownCrops,
      List<String> knownHuntingDrops,
      Optional<ResourceLocation> mapIcon,
      @Nullable String cultureBannerNbt,
      boolean excludeFromTrains
   ) {
      this(
         id,
         displayName,
         panelTexture,
         nativeLanguage,
         travelBookCategories,
         knownCrops,
         knownHuntingDrops,
         mapIcon,
         cultureBannerNbt,
         excludeFromTrains,
         CulturalTemperament.DEFAULT
      );
   }

   /** Back-compat for call sites that omit train exclusion and temperament. */
   public Culture(
      ResourceLocation id,
      String displayName,
      ResourceLocation panelTexture,
      String nativeLanguage,
      TravelBookCategories travelBookCategories,
      List<String> knownCrops,
      List<String> knownHuntingDrops,
      Optional<ResourceLocation> mapIcon,
      @Nullable String cultureBannerNbt
   ) {
      this(
         id,
         displayName,
         panelTexture,
         nativeLanguage,
         travelBookCategories,
         knownCrops,
         knownHuntingDrops,
         mapIcon,
         cultureBannerNbt,
         false,
         CulturalTemperament.DEFAULT
      );
   }
}
