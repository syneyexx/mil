package org.millenaire.building;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class GoodAvailabilityHelper {
   private GoodAvailabilityHelper() {
   }

   public static int nbGoodAvailable(
      BuildingInstance building, Item item, ServerLevel level, Village village, ResourceLocation cultureId, boolean forShop, boolean forConstruction
   ) {
      return nbGoodAvailable(building, item, level, village, cultureId, forShop, forConstruction, false, null);
   }

   public static int nbGoodAvailable(
      BuildingInstance building,
      Item item,
      ServerLevel level,
      Village village,
      ResourceLocation cultureId,
      boolean forShop,
      boolean forConstruction,
      @Nullable Village.PendingProject excludeProject
   ) {
      return nbGoodAvailable(building, item, level, village, cultureId, forShop, forConstruction, false, excludeProject);
   }

   public static int nbGoodAvailable(BuildingInstance building, Item item, ServerLevel level, Village village, ResourceLocation cultureId, boolean forShop) {
      return nbGoodAvailable(building, item, level, village, cultureId, forShop, false);
   }

   public static int nbGoodAvailable(
      BuildingInstance building,
      Item item,
      ServerLevel level,
      Village village,
      ResourceLocation cultureId,
      boolean forShop,
      boolean forConstruction,
      boolean forExport,
      @Nullable Village.PendingProject excludeProject
   ) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return 0;
      }

      int nb = inv.getCount(level, item);
      if (nb <= 0) {
         return 0;
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      boolean isTownhall = plan != null && "townhall".equals(plan.shopId());
      String shopId = plan != null ? plan.shopId() : null;
      if (isTownhall) {
         nb -= getConstructionReservedQuantity(item, level, village, excludeProject);
      }

      if (!forConstruction) {
         boolean tradedHere = false;
         if (shopId != null) {
            ShopProfile profile = ShopProfileLoader.getProfile(cultureId, shopId);
            if (profile != null) {
               tradedHere = isItemSoldAt(item, profile, cultureId);
            }
         }

         nb -= reservedDeductionFor(item, cultureId, isTownhall, tradedHere, forExport);
         nb -= getResidentNeeds(building, item, village);
      }

      return Math.max(nb, 0);
   }

   static int reservedDeductionFor(Item item, ResourceLocation cultureId, boolean isTownhall, boolean tradedHere, boolean forExport) {
      if (!isTownhall && !tradedHere && !forExport) {
         return 0;
      } else {
         return forExport ? TradeGoodsLoader.getTargetQuantities(cultureId).getOrDefault(item, 0) : getReservedQuantity(item, cultureId);
      }
   }

   private static boolean isItemSoldAt(Item item, ShopProfile profile, ResourceLocation cultureId) {
      ItemStack stack = new ItemStack(item);

      for (String sellName : profile.sells()) {
         TradeGood good = TradeGoodsLoader.getGoodById(cultureId, sellName);
         if (good != null && good.matchesItem(stack)) {
            return true;
         }
      }

      return false;
   }

   private static int getReservedQuantity(Item item, ResourceLocation cultureId) {
      List<TradeGood> tradeGoods = TradeGoodsLoader.getGoods(cultureId);
      if (tradeGoods == null) {
         return 0;
      }

      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

      for (TradeGood good : tradeGoods) {
         if (!good.isTag() && good.itemLocation().equals(itemId)) {
            return good.reservedQuantity();
         }
      }

      return 0;
   }

   @Nullable
   private static BuildingPlanSet.LevelDef controlledQueueHeadDef(Village village) {
      ControlledQueuedProject head = village.controlledQueueHead();
      if (head == null) {
         return null;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(head.planSetId());
      return planSet == null ? null : planSet.getLevel(head.variant(), head.level());
   }

   public static int getConstructionReservedQuantity(Item item, ServerLevel level, Village village, @Nullable Village.PendingProject excludeProject) {
      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
      int reserved = 0;
      Village.PendingProject pending = village.getPendingProject();
      boolean pendingHandled = false;

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            UUID builderUuid = task.getReservedBuilder();
            if (builderUuid == null) {
               if (pending != null
                  && !pendingHandled
                  && building.getPlanSetId() != null
                  && building.getPlanSetId().equals(pending.planSetId())
                  && pending.variant().equals(building.getVariant())
                  && pending.level() == building.getLevel()
                  && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                  pendingHandled = true;
               }
            } else {
               BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
               if (levelDef != null) {
                  Map<ResourceLocation, Integer> resCost = levelDef.requiredResources();
                  if (resCost.containsKey(itemId)) {
                     int needed = resCost.get(itemId);
                     int builderHas = 0;
                     if (level.getEntity(builderUuid) instanceof MillVillager villager) {
                        builderHas = villager.getInventory().getCount(item);
                     }

                     if (builderHas < needed) {
                        reserved += needed - builderHas;
                     }
                  }

                  if (pending != null
                     && !pendingHandled
                     && building.getPlanSetId() != null
                     && building.getPlanSetId().equals(pending.planSetId())
                     && pending.variant().equals(building.getVariant())
                     && pending.level() == building.getLevel()
                     && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                     pendingHandled = true;
                  }
               }
            }
         }
      }

      if (pending != null && !pendingHandled && !pending.equals(excludeProject)) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            String variant = pending.variant();
            int pendingLevel = pending.level();
            BuildingPlanSet.LevelDef pendingDef = planSet.getLevel(variant, pendingLevel);
            if (pendingDef != null) {
               Map<ResourceLocation, Integer> resCost = pendingDef.requiredResources();
               if (resCost.containsKey(itemId)) {
                  reserved += resCost.get(itemId);
               }
            }
         }
      }

      BuildingPlanSet.LevelDef headDef = controlledQueueHeadDef(village);
      if (headDef != null) {
         Integer headNeed = headDef.requiredResources().get(itemId);
         if (headNeed != null) {
            reserved += headNeed;
         }
      }

      return reserved;
   }

   public static int getPlannedProjectQuantity(Item item, Village village) {
      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
      int planned = 0;
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            BuildingPlanSet.LevelDef def = planSet.getLevel(pending.variant(), pending.level());
            if (def != null) {
               Integer need = def.requiredResources().get(itemId);
               if (need != null) {
                  planned += need;
               }
            }
         }
      }

      BuildingPlanSet.LevelDef headDef = controlledQueueHeadDef(village);
      if (headDef != null) {
         Integer headNeed = headDef.requiredResources().get(itemId);
         if (headNeed != null) {
            planned += headNeed;
         }
      }

      return planned;
   }

   public static int nbGoodNeeded(BuildingInstance building, Item item, @Nullable ServerLevel level, Village village) {
      BuildingInventory inv = building.getInventory();
      int nb = inv != null ? inv.getCount(level, item) : 0;
      nb += builderCarriedForCurrentProject(item, level, village);
      int targetAmount = TradeGoodsLoader.getTargetQuantities(village.getCultureId()).getOrDefault(item, 0);
      int neededForProject = getPlannedProjectQuantity(item, village);
      return Math.max(neededForProject + targetAmount - nb, 0);
   }

   public static int nbGoodNeeded(@Nullable ServerLevel level, Village village, Item item) {
      BuildingInstance townhall = village.getTownhall();
      return townhall == null ? 0 : nbGoodNeeded(townhall, item, level, village);
   }

   private static int builderCarriedForCurrentProject(Item item, @Nullable ServerLevel level, Village village) {
      if (level == null) {
         return 0;
      }

      Village.PendingProject pending = village.getPendingProject();
      if (pending == null) {
         return 0;
      }

      int carried = 0;

      for (BuildingInstance building : village.getBuildings()) {
         if (building.isBeingBuilt() && building.getPlanSetId() != null && building.getPlanSetId().equals(pending.planSetId())) {
            ConstructionTask task = building.getConstructionTask();
            if (task != null) {
               UUID builderUuid = task.getReservedBuilder();
               if (builderUuid != null && level.getEntity(builderUuid) instanceof MillVillager villager) {
                  carried += villager.getInventory().getCount(item);
               }
            }
         }
      }

      return carried;
   }

   public static int availableAgainstStockLimit(BuildingInstance building, Item item, ServerLevel level, Village village, ResourceLocation cultureId) {
      int available = nbGoodAvailable(building, item, level, village, cultureId, false);
      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      boolean isTownhall = plan != null && "townhall".equals(plan.shopId());
      if (!isTownhall) {
         return available;
      }

      int reserved = getConstructionReservedQuantity(item, level, village, null);
      int planned = getPlannedProjectQuantity(item, village);
      return Math.max(0, available - Math.max(0, planned - reserved));
   }

   public static Set<ResourceLocation> collectConstructionNeedItems(Village village) {
      Set<ResourceLocation> items = new HashSet<>();
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(pending.variant(), pending.level());
            if (levelDef != null) {
               items.addAll(levelDef.requiredResources().keySet());
            }
         }
      }

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
            if (levelDef != null) {
               items.addAll(levelDef.requiredResources().keySet());
            }
         }
      }

      BuildingPlanSet.LevelDef headDef = controlledQueueHeadDef(village);
      if (headDef != null) {
         items.addAll(headDef.requiredResources().keySet());
      }

      return items;
   }

   public static int getAnywoodReservedQuantity(ServerLevel level, Village village, @Nullable Village.PendingProject excludeProject) {
      int reserved = 0;
      Village.PendingProject pending = village.getPendingProject();
      boolean pendingHandled = false;

      for (BuildingInstance building : village.getBuildings()) {
         ConstructionTask task = building.getConstructionTask();
         if (task != null && !task.isComplete() && building.isBeingBuilt()) {
            UUID builderUuid = task.getReservedBuilder();
            if (builderUuid == null) {
               if (pending != null
                  && !pendingHandled
                  && building.getPlanSetId() != null
                  && building.getPlanSetId().equals(pending.planSetId())
                  && pending.variant().equals(building.getVariant())
                  && pending.level() == building.getLevel()
                  && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                  pendingHandled = true;
               }
            } else {
               BuildingPlanSet.LevelDef levelDef = getLevelDefForBuilding(building);
               if (levelDef != null) {
                  Map<ResourceLocation, Integer> resCost = levelDef.requiredResources();
                  Integer anywoodNeeded = resCost.get(AnywoodHelper.ANYWOOD_LOG);
                  if (anywoodNeeded != null && anywoodNeeded > 0) {
                     int builderHas = 0;
                     if (level.getEntity(builderUuid) instanceof MillVillager villager) {
                        builderHas = villager.getInventory().getCountByTag(AnywoodHelper.LOGS_TAG);
                     }

                     if (builderHas < anywoodNeeded) {
                        reserved += anywoodNeeded - builderHas;
                     }
                  }

                  if (pending != null
                     && !pendingHandled
                     && building.getPlanSetId() != null
                     && building.getPlanSetId().equals(pending.planSetId())
                     && pending.variant().equals(building.getVariant())
                     && pending.level() == building.getLevel()
                     && (!pending.isUpgrade() || Objects.equals(pending.buildingId(), building.getId()))) {
                     pendingHandled = true;
                  }
               }
            }
         }
      }

      if (pending != null && !pendingHandled && !pending.equals(excludeProject)) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         if (planSet != null) {
            String variant = pending.variant();
            int pendingLevel = pending.level();
            BuildingPlanSet.LevelDef pendingDef = planSet.getLevel(variant, pendingLevel);
            if (pendingDef != null) {
               Integer anywoodNeeded = pendingDef.requiredResources().get(AnywoodHelper.ANYWOOD_LOG);
               if (anywoodNeeded != null) {
                  reserved += anywoodNeeded;
               }
            }
         }
      }

      BuildingPlanSet.LevelDef headDef = controlledQueueHeadDef(village);
      if (headDef != null) {
         Integer headAnywood = headDef.requiredResources().get(AnywoodHelper.ANYWOOD_LOG);
         if (headAnywood != null) {
            reserved += headAnywood;
         }
      }

      return reserved;
   }

   @Nullable
   private static BuildingPlanSet.LevelDef getLevelDefForBuilding(BuildingInstance building) {
      ResourceLocation planSetId = building.getPlanSetId();
      if (planSetId == null) {
         return null;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         return null;
      }

      String variant = building.getVariant();
      return variant == null ? null : planSet.getLevel(variant, building.getLevel());
   }

   public static int getSinkRequiredGoods(BuildingInstance building, Item item, Village village) {
      return getResidentNeeds(building, item, village);
   }

   public static int remainingSinkDemand(int demand, int currentStock, int noCapValue) {
      return demand <= 0 ? noCapValue : Math.max(0, demand - currentStock);
   }

   private static int getResidentNeeds(BuildingInstance building, Item item, Village village) {
      int needs = 0;

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         BuildingId homeId = record.getHomeBuilding();
         if (homeId != null && homeId.equals(building.getId())) {
            VillagerType vtype = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vtype != null) {
               Integer qty = vtype.resolvedRequiredGoods().get(item);
               if (qty != null) {
                  needs += qty;
               }
            }
         }
      }

      return needs;
   }
}
