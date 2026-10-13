package org.millenaire.village;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Concrete work roles a player can assign to a building in their controlled city.
 */
public enum PlayerBuildingRole {
   FARM(
      "farm",
      BuildingPurpose.PRODUCTION,
      List.of(),
      null,
      List.of("farmer"),
      List.of(),
      1,
      true,
      false,
      false,
      false
   ),
   LUMBERMAN(
      "lumberman",
      BuildingPurpose.PRODUCTION,
      List.of("grove"),
      null,
      List.of("lumberman"),
      List.of(),
      1,
      false,
      true,
      false,
      false
   ),
   GUARD_STATION(
      "guard_station",
      BuildingPurpose.MILITARY,
      List.of("patrol"),
      null,
      List.of("guard"),
      List.of(),
      1,
      false,
      false,
      false,
      false
   ),
   GUARD_BARRACKS(
      "guard_barracks",
      BuildingPurpose.MILITARY,
      List.of("patrol"),
      null,
      List.of("guard"),
      List.of(),
      5,
      false,
      false,
      false,
      false
   ),
   ARMORY(
      "armory",
      BuildingPurpose.MILITARY,
      List.of("armoury"),
      "armoury",
      List.of("smith"),
      List.of(),
      1,
      false,
      false,
      true,
      false
   ),
   BAKERY(
      "bakery",
      BuildingPurpose.PRODUCTION,
      List.of("bakery"),
      null,
      List.of(),
      List.of("wife"),
      1,
      false,
      false,
      false,
      true
   ),
   SHOP(
      "shop",
      BuildingPurpose.TRADE,
      List.of("player_shop"),
      "player_shop",
      List.of(),
      List.of("wife"),
      1,
      false,
      false,
      true,
      false
   ),
   BANK(
      "bank",
      BuildingPurpose.TRADE,
      List.of("bank", "treasury"),
      null,
      List.of("merchant"),
      List.of("wife"),
      1,
      false,
      false,
      true,
      false
   ),
   FISHERMAN(
      "fisherman",
      BuildingPurpose.PRODUCTION,
      List.of("fishingspot"),
      null,
      List.of("fisherman"),
      List.of(),
      1,
      false,
      false,
      false,
      false
   ),
   KINGS_HOUSE(
      "kings_house",
      BuildingPurpose.HOUSING,
      List.of("kings_house", "player_house"),
      null,
      List.of(),
      List.of(),
      0,
      false,
      false,
      false,
      false
   ),
   HOUSING(
      "housing",
      BuildingPurpose.HOUSING,
      List.of("housing"),
      null,
      List.of(),
      List.of(),
      0,
      false,
      false,
      false,
      false
   );

   public static final int FOOTPRINT = 11;
   public static final int HALF = 5;
   public static final int SCAN_Y_DOWN = 4;
   public static final int SCAN_Y_UP = 8;
   public static final int WATER_RANGE = 8;

   private final String id;
   private final BuildingPurpose purpose;
   private final List<String> tags;
   @Nullable
   private final String shopId;
   private final List<String> maleRoles;
   private final List<String> femaleRoles;
   private final int workerCount;
   private final boolean requireFarmland;
   private final boolean requireTrees;
   private final boolean requireChest;
   private final boolean requireCrafting;

   PlayerBuildingRole(
      String id,
      BuildingPurpose purpose,
      List<String> tags,
      @Nullable String shopId,
      List<String> maleRoles,
      List<String> femaleRoles,
      int workerCount,
      boolean requireFarmland,
      boolean requireTrees,
      boolean requireChest,
      boolean requireCrafting
   ) {
      this.id = id;
      this.purpose = purpose;
      this.tags = List.copyOf(tags);
      this.shopId = shopId;
      this.maleRoles = List.copyOf(maleRoles);
      this.femaleRoles = List.copyOf(femaleRoles);
      this.workerCount = workerCount;
      this.requireFarmland = requireFarmland;
      this.requireTrees = requireTrees;
      this.requireChest = requireChest;
      this.requireCrafting = requireCrafting;
   }

   public String id() {
      return this.id;
   }

   public BuildingPurpose purpose() {
      return this.purpose;
   }

   public List<String> tags() {
      return this.tags;
   }

   @Nullable
   public String shopId() {
      return this.shopId;
   }

   public List<String> maleRoles() {
      return this.maleRoles;
   }

   public List<String> femaleRoles() {
      return this.femaleRoles;
   }

   public int workerCount() {
      return this.workerCount;
   }

   public boolean requireFarmland() {
      return this.requireFarmland;
   }

   public boolean requireTrees() {
      return this.requireTrees;
   }

   public boolean requireChest() {
      return this.requireChest;
   }

   public boolean requireCrafting() {
      return this.requireCrafting;
   }

   public boolean requireWater() {
      return this == FISHERMAN;
   }

   /** Beds are for the player; NPCs must not claim them. */
   public boolean reservesBedsForPlayer() {
      return this == KINGS_HOUSE;
   }

   /** At least one bed required in the footprint. */
   public boolean requireBed() {
      return this == HOUSING;
   }

   public boolean registersNpcBeds() {
      return this != KINGS_HOUSE;
   }

   public ResourceLocation planId() {
      return ResourceLocation.fromNamespaceAndPath("millenaire", "player/" + this.id);
   }

   public static PlayerBuildingRole fromId(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      String key = raw.trim().toLowerCase(Locale.ROOT);
      for (PlayerBuildingRole role : values()) {
         if (role.id.equals(key) || role.name().equalsIgnoreCase(key)) {
            return role;
         }
      }
      return null;
   }
}
