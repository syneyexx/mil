package org.millenaire.building;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Player-declared purpose for a designated (player-built) building.
 * Purpose is a contract: systems treat the structure accordingly.
 */
public enum BuildingPurpose {
   HOUSE(16, false, true, false),
   FARMLAND(8, true, false, false),
   ARMORY(4, false, false, true),
   TOWN_HALL(1, false, false, true),
   KING_HOUSE(1, false, true, false),
   SHEEP_PEN(8, true, false, false),
   CATTLE_PEN(8, true, false, false),
   HORSE_STABLE(8, true, false, false),
   CHICKEN_COOP(8, true, false, false),
   PIG_PEN(8, true, false, false),
   GROVE(8, true, false, false),
   WAREHOUSE(8, false, false, true),
   JAIL(2, false, true, true),
   SHOP(4, false, false, true),
   GUEST_QUARTER(4, false, true, false),
   /** Player-designated religious site (shrine / chapel / prayer hall). */
   SHRINE(4, false, false, false);

   private final int maxCount;
   private final boolean openAir;
   private final boolean requiresBed;
   private final boolean requiresChest;

   BuildingPurpose(int maxCount, boolean openAir, boolean requiresBed, boolean requiresChest) {
      this.maxCount = maxCount;
      this.openAir = openAir;
      this.requiresBed = requiresBed;
      this.requiresChest = requiresChest;
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public int maxCount() {
      return this.maxCount;
   }

   public boolean isOpenAir() {
      return this.openAir;
   }

   public boolean requiresBed() {
      return this.requiresBed;
   }

   public boolean requiresChest() {
      return this.requiresChest;
   }

   /** Counts toward immigrant housing capacity (beds). */
   public boolean providesImmigrantHousing() {
      return this == HOUSE || this == GUEST_QUARTER;
   }

   /** Crop soils on this volume are usable by HOUSE residents via the farm bridge. */
   public boolean isFarmland() {
      return this == FARMLAND;
   }

   /** Animal-pen style purpose that synthesizes animalSpawn points. */
   @Nullable
   public String animalSpawnSubtype() {
      return switch (this) {
         case SHEEP_PEN -> "sheep";
         case CATTLE_PEN -> "cow";
         case HORSE_STABLE -> "horse";
         case CHICKEN_COOP -> "chicken";
         case PIG_PEN -> "pig";
         default -> null;
      };
   }

   public boolean isWorkAttraction() {
      return switch (this) {
         case FARMLAND, SHEEP_PEN, CATTLE_PEN, HORSE_STABLE, CHICKEN_COOP, PIG_PEN, GROVE, WAREHOUSE, ARMORY, SHOP -> true;
         case HOUSE, TOWN_HALL, KING_HOUSE, JAIL, GUEST_QUARTER, SHRINE -> false;
      };
   }

   public String fallbackDisplayName() {
      return switch (this) {
         case HOUSE -> "Civilian House";
         case FARMLAND -> "Farmland";
         case ARMORY -> "Armory";
         case TOWN_HALL -> "Town Hall";
         case KING_HOUSE -> "King's House";
         case SHEEP_PEN -> "Sheep Pasture";
         case CATTLE_PEN -> "Cattle Pasture";
         case HORSE_STABLE -> "Horse Stable";
         case CHICKEN_COOP -> "Chicken Coop";
         case PIG_PEN -> "Pig Pen";
         case GROVE -> "Lumber Grove";
         case WAREHOUSE -> "Warehouse";
         case JAIL -> "Jail";
         case SHOP -> "Shop";
         case GUEST_QUARTER -> "Guest Quarter";
         case SHRINE -> "Shrine";
      };
   }

   @Nullable
   public static BuildingPurpose fromId(@Nullable String id) {
      if (id == null || id.isEmpty()) {
         return null;
      }

      try {
         return BuildingPurpose.valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
