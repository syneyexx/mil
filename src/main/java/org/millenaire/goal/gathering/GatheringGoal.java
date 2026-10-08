package org.millenaire.goal.gathering;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalUtils;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class GatheringGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final PerVillagerThrottle canStartThrottle = new PerVillagerThrottle(40);
   private final GatheringType type;
   private final GatheringHandler handler;

   public GatheringGoal(GatheringType type, GatheringHandler handler) {
      this.type = type;
      this.handler = handler;
   }

   public GatheringType getGatheringType() {
      return this.type;
   }

   public GatheringHandler getHandler() {
      return this.handler;
   }

   @Override
   public ResourceLocation id() {
      return this.type.id();
   }

   @Override
   public boolean isLeisure() {
      return this.type.leisure();
   }

   @Override
   public long reoccurDelayTicks() {
      return this.type.reoccurDelay();
   }

   @Override
   public int computePriority(GoalContext context) {
      int base = this.type.priority();
      if (this.type.priorityInvPenaltyItems() != null) {
         int invCount = this.countPenaltyItemsInInventory(context);
         base = Math.max(10, this.type.priorityInvPenaltyBase() - invCount);
      }

      int random = this.type.priorityRandom() > 0 ? ThreadLocalRandom.current().nextInt(this.type.priorityRandom()) : 0;
      return base + random;
   }

   private int countPenaltyItemsInInventory(GoalContext context) {
      VillagerInventory inventory = context.villager().getInventory();
      Map<Item, Integer> all = inventory.getAll();
      int total = 0;

      for (String key : this.type.priorityInvPenaltyItems()) {
         if (key.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.parse(key.substring(1));
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);

            for (Entry<Item, Integer> e : all.entrySet()) {
               if (e.getKey().builtInRegistryHolder().is(tag)) {
                  total += e.getValue();
               }
            }
         } else {
            Item item = ItemHelper.resolve(key);
            if (item != null) {
               total += inventory.getCount(item);
            }
         }
      }

      return total;
   }

   @Override
   public boolean canStart(GoalContext context) {
      UUID uuid = context.villager().getUUID();
      long gameTime = context.gameTime();
      if (this.canStartThrottle.isThrottled(uuid, gameTime)) {
         return false;
      }

      long dayTime = context.dayTime();
      if (this.type.minimumHour() >= 0 && dayTime < this.type.minimumHour()) {
         return false;
      }

      if (this.type.maximumHour() >= 0 && dayTime > this.type.maximumHour()) {
         return false;
      }

      if (this.type.buildingLimit() != null) {
         BuildingInstance limitTarget = this.handler.resolveBuildingLimitTarget(context, this.type);
         if (limitTarget == null) {
            limitTarget = context.resolveHomeBuilding().orElse(null);
         }

         if (limitTarget != null && !checkStockLimit(context, limitTarget, this.type.buildingLimit())) {
            return false;
         }
      }

      if (this.type.townhallLimit() != null) {
         BuildingInstance townhall = context.village().getTownhall();
         if (townhall != null && !checkStockLimit(context, townhall, this.type.townhallLimit())) {
            return false;
         }
      }

      if (this.type.villageLimit() != null && !this.checkVillageStockLimit(context, this.type.villageLimit())) {
         return false;
      }

      if (this.type.itemsBalance() != null && !this.checkItemsBalance(context, this.type.itemsBalance())) {
         return false;
      }

      if (this.type.maxSimultaneousTotal() > 0) {
         int total = GoalUtils.countSimultaneous(context, this.type.id());
         if (total >= this.type.maxSimultaneousTotal()) {
            return false;
         }
      }

      boolean result = this.handler.canStart(context, this.type);
      this.canStartThrottle.record(uuid, gameTime);
      return result;
   }

   static boolean checkStockLimit(GoalContext context, BuildingInstance building, Map<String, Integer> limits) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return true;
      }

      if (context.level() instanceof ServerLevel sl) {
         ResourceLocation var13 = context.village().getCultureId();

         for (Entry<String, Integer> entry : limits.entrySet()) {
            String key = entry.getKey();
            int maxQty = entry.getValue();
            if (key.startsWith("#")) {
               ResourceLocation tagId = ResourceLocation.parse(key.substring(1));
               TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
               int total = availableForTag(inv, context, sl, building, var13, tag);
               if (total > maxQty) {
                  return false;
               }
            } else {
               Item item = ItemHelper.resolve(key);
               if (item != null) {
                  int current = GoodAvailabilityHelper.availableAgainstStockLimit(building, item, sl, context.village(), var13);
                  if (current > maxQty) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return true;
      }
   }

   private static int availableForTag(
      BuildingInventory inv, GoalContext context, ServerLevel level, BuildingInstance building, ResourceLocation cultureId, TagKey<Item> tag
   ) {
      Map<Item, Integer> cache = inv.getCachedContents();
      if (cache == null) {
         inv.scanChests(level);
         cache = inv.getCachedContents();
      }

      if (cache == null) {
         return 0;
      }

      int total = 0;

      for (Entry<Item, Integer> e : cache.entrySet()) {
         if (e.getKey().builtInRegistryHolder().is(tag)) {
            total += GoodAvailabilityHelper.availableAgainstStockLimit(building, e.getKey(), level, context.village(), cultureId);
         }
      }

      return total;
   }

   private boolean checkVillageStockLimit(GoalContext context, Map<String, Integer> limits) {
      if (!(context.level() instanceof ServerLevel sl)) {
         return true;
      } else {
         for (Entry<String, Integer> entry : limits.entrySet()) {
            String key = entry.getKey();
            int maxQty = entry.getValue();
            int current;
            if (key.startsWith("#")) {
               ResourceLocation tagId = ResourceLocation.parse(key.substring(1));
               TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
               current = context.village().getVillageTagCount(sl, tag);
            } else {
               Item item = ItemHelper.resolve(key);
               if (item == null) {
                  continue;
               }

               current = context.village().getVillageItemCount(sl, item);
            }

            if (current >= maxQty) {
               return false;
            }
         }

         return true;
      }
   }

   private boolean checkItemsBalance(GoalContext context, Map<String, String> itemsBalance) {
      if (context.level() instanceof ServerLevel sl) {
         BuildingInstance townhall = context.village().getTownhall();
         if (townhall == null) {
            return true;
         }

         ResourceLocation cultureId = context.village().getCultureId();

         for (Entry<String, String> entry : itemsBalance.entrySet()) {
            Item input = ItemHelper.resolve(entry.getKey());
            Item output = ItemHelper.resolve(entry.getValue());
            if (input != null && output != null) {
               int inputCount = GoodAvailabilityHelper.nbGoodAvailable(townhall, input, sl, context.village(), cultureId, false);
               int outputCount = GoodAvailabilityHelper.nbGoodAvailable(townhall, output, sl, context.village(), cultureId, false);
               if (inputCount < outputCount) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return true;
      }
   }

   @Override
   public VillagerTask start(GoalContext context) {
      LOGGER.debug("Starting goal {} for villager {}", this.type.id(), context.villager().getVillagerTypeId());
      return new GatheringTask(this.type, this.handler);
   }
}
