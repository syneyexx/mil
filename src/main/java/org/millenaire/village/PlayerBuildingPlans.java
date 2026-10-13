package org.millenaire.village;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingPlan;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.culture.ModCultures;

/**
 * Registers synthetic building plans and shop profiles for player-designated buildings.
 * Called after {@link org.millenaire.culture.CultureLoader#loadAll()}.
 */
public final class PlayerBuildingPlans {
   private static final ResourceLocation PLAYER_CULTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "player");
   private static final ShopProfile PLAYER_SHOP = new ShopProfile(List.of("*"), List.of(), List.of(), List.of());

   private PlayerBuildingPlans() {
   }

   public static void registerAll() {
      for (PlayerBuildingRole role : PlayerBuildingRole.values()) {
         ModCultures.registerBuildingPlan(planFor(role));
      }

      for (ResourceLocation cultureId : ModCultures.getAllCultures().keySet()) {
         ShopProfileLoader.register(cultureId, "player_shop", PLAYER_SHOP);
         if (ShopProfileLoader.getProfile(cultureId, "armoury") == null) {
            ShopProfileLoader.register(
               cultureId,
               "armoury",
               new ShopProfile(List.of(), List.of(), List.of(), List.of())
            );
         }
      }
   }

   private static BuildingPlan planFor(PlayerBuildingRole role) {
      int size = PlayerBuildingRole.FOOTPRINT;
      return new BuildingPlan(
         role.planId(),
         PLAYER_CULTURE,
         "",
         size,
         PlayerBuildingRole.SCAN_Y_DOWN + PlayerBuildingRole.SCAN_Y_UP + 1,
         size,
         0,
         0,
         BlockPos.ZERO,
         role.tags(),
         "standard",
         "flat",
         List.of(),
         role.shopId()
      );
   }
}
