package org.millenaire.building;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.culture.ModCultures;

/**
 * Registers synthetic plan sets used by player-designated buildings.
 * Footprint size on the stub plan is a placeholder; runtime bounds come from
 * {@link BuildingInstance} designation dimensions.
 */
public final class DesignatedBuildingPlans {
   public static final ResourceLocation CULTURE_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "designated");

   private static final Map<BuildingPurpose, ResourceLocation> PLAN_SET_IDS = new EnumMap<>(BuildingPurpose.class);
   private static final Map<BuildingPurpose, ResourceLocation> PLAN_IDS = new EnumMap<>(BuildingPurpose.class);

   static {
      for (BuildingPurpose purpose : BuildingPurpose.values()) {
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", "designated/" + purpose.id());
         PLAN_SET_IDS.put(purpose, id);
         PLAN_IDS.put(purpose, id);
      }
   }

   private DesignatedBuildingPlans() {
   }

   public static void register() {
      registerPurpose(BuildingPurpose.HOUSE, "Civilian House", "houses", List.of("designated_house", "house"), null, false, "house");
      registerPurpose(BuildingPurpose.FARMLAND, "Farmland", "agriculture", List.of("designated_farmland", "wheat", "farm"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.ARMORY, "Armory", "military", List.of("designated_armory", "armoury", "armory"), "armoury", false, "military");
      registerPurpose(BuildingPurpose.TOWN_HALL, "Town Hall", "townhalls", List.of("designated_town_hall", "townhall"), "townhall", true, "townhall");
      registerPurpose(BuildingPurpose.KING_HOUSE, "King's House", "houses", List.of("designated_king_house", "king_house"), null, false, "house");
      registerPurpose(BuildingPurpose.SHEEP_PEN, "Sheep Pasture", "agriculture", List.of("designated_sheep_pen", "sheeps"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.CATTLE_PEN, "Cattle Pasture", "agriculture", List.of("designated_cattle_pen", "cattle"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.HORSE_STABLE, "Horse Stable", "agriculture", List.of("designated_horse_stable", "horses"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.CHICKEN_COOP, "Chicken Coop", "agriculture", List.of("designated_chicken_coop", "chicken"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.PIG_PEN, "Pig Pen", "agriculture", List.of("designated_pig_pen", "pigs"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.GROVE, "Lumber Grove", "agriculture", List.of("designated_grove", "grove"), null, false, "agriculture");
      registerPurpose(BuildingPurpose.WAREHOUSE, "Warehouse", "commerce", List.of("designated_warehouse", "warehouse"), null, false, "commerce");
      registerPurpose(BuildingPurpose.JAIL, "Jail", "military", List.of("designated_jail", "jail"), null, false, "military");
      registerPurpose(BuildingPurpose.SHOP, "Shop", "commerce", List.of("designated_shop", "shop", "market"), "player_shop", false, "commerce");
      registerPurpose(BuildingPurpose.GUEST_QUARTER, "Guest Quarter", "houses", List.of("designated_guest_quarter", "guest_quarter", "inn"), null, false, "house");
      registerPurpose(
         BuildingPurpose.SHRINE,
         "Shrine",
         "religious",
         List.of("designated_shrine", "praying", "shrine", "temple"),
         null,
         false,
         "othervillage"
      );
   }

   public static ResourceLocation planSetId(BuildingPurpose purpose) {
      return PLAN_SET_IDS.get(purpose);
   }

   public static ResourceLocation planId(BuildingPurpose purpose) {
      return PLAN_IDS.get(purpose);
   }

   public static boolean isDesignatedPlanSet(@Nullable ResourceLocation planSetId) {
      return planSetId != null && PLAN_SET_IDS.containsValue(planSetId);
   }

   private static void registerPurpose(
      BuildingPurpose purpose,
      String nativeName,
      String category,
      List<String> tags,
      @Nullable String shopId,
      boolean isTownHall,
      String travelBookCategory
   ) {
      ResourceLocation planSetId = planSetId(purpose);
      ResourceLocation planId = planId(purpose);
      BuildingPlan plan = new BuildingPlan(
         planId,
         CULTURE_ID,
         "",
         1,
         1,
         1,
         0,
         0,
         BlockPos.ZERO,
         List.copyOf(tags),
         "standard",
         "standard",
         List.of(),
         shopId
      );
      BuildingPlanSet.LevelDef level0 = new BuildingPlanSet.LevelDef(
         0,
         planId,
         "",
         1,
         1,
         1,
         0,
         0,
         nativeName,
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         List.of(),
         Map.of(),
         List.of(),
         null,
         0,
         false,
         0,
         Map.of(),
         0
      );
      BuildingPlanSet planSet = new BuildingPlanSet(
         planSetId,
         CULTURE_ID,
         purpose.id(),
         category,
         nativeName,
         0,
         0.0,
         0.0,
         List.of(),
         List.of(),
         0,
         List.copyOf(tags),
         "standard",
         "standard",
         Map.of("0", List.of(level0)),
         List.of(),
         null,
         ClearMargins.symmetric(0),
         0,
         0,
         Map.of(),
         List.of(),
         travelBookCategory,
         false,
         false,
         isTownHall,
         Map.of(),
         Map.of(),
         null,
         false,
         false,
         0,
         false,
         List.of(),
         null
      );
      ModCultures.registerBuildingPlan(plan);
      ModCultures.registerBuildingPlanSet(planSet);
   }
}
