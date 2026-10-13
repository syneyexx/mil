package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class CraftingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public String id() {
      return "crafting";
   }

   @Override
   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      errors.addAll(this.validateItemList(type, "inputs", false));
      errors.addAll(this.validateItemList(type, "outputs", false));
      return errors;
   }

   @Override
   public boolean canStart(GoalContext ctx, GatheringType type) {
      List<CraftingHandler.CraftingInput> inputs = parseCraftingInputs(type.handlerParams(), "inputs");
      boolean freeRecipe = inputs.isEmpty();
      ServerLevel level = ctx.level();
      VillagerInventory villagerInv = ctx.villager().getInventory();

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         if (this.hasCraftingPos(building) && (freeRecipe || this.hasAllInputsCombined(building, level, villagerInv, inputs))) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   @Override
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      List<CraftingHandler.CraftingInput> inputs = parseCraftingInputs(type.handlerParams(), "inputs");
      boolean freeRecipe = inputs.isEmpty();
      ServerLevel level = ctx.level();
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      VillagerInventory villagerInv = ctx.villager().getInventory();
      List<BlockPos> candidates = new ArrayList<>();

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         BlockPos craftingPos = this.getCraftingPos(building);
         if (craftingPos != null && (freeRecipe || this.hasAllInputsCombined(building, level, villagerInv, inputs))) {
            candidates.add(craftingPos);
         }
      }

      BlockPos bestPos = findClosestBlock(candidates, reference, lastTarget, type.batchRadius());
      return bestPos != null ? new GatheringTarget.BlockTarget(bestPos) : null;
   }

   @Override
   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      List<CraftingHandler.CraftingInput> inputs = parseCraftingInputs(type.handlerParams(), "inputs");
      Map<Item, Integer> outputs = this.parseItemList(type.handlerParams(), "outputs");
      BuildingInstance craftingBuilding = null;

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         BlockPos craftingPos = this.getCraftingPos(building);
         if (craftingPos != null && craftingPos.equals(target.navigationPos())) {
            craftingBuilding = building;
            break;
         }
      }

      if (craftingBuilding == null) {
         return true;
      }

      BuildingInventory inv = craftingBuilding.getInventory();
      if (inv == null) {
         return true;
      }

      ServerLevel level = ctx.level();
      VillagerInventory villagerInv = ctx.villager().getInventory();
      if (!inputs.isEmpty() && !this.hasAllInputsCombined(craftingBuilding, level, villagerInv, inputs)) {
         return true;
      }

      if (!this.hasSpaceForOutputs(inv, level, outputs)) {
         return true;
      }

      for (CraftingHandler.CraftingInput input : inputs) {
         int needed = input.count();
         switch (input) {
            case CraftingHandler.CraftingInput.Concrete c:
               int takenFromVillagerx = villagerInv.remove(c.item(), needed);
               int remainingx = needed - takenFromVillagerx;
               if (remainingx > 0) {
                  inv.remove(level, c.item(), remainingx);
               }
               break;
            case CraftingHandler.CraftingInput.Tagged t:
               int takenFromVillager = villagerInv.removeByTag(t.tag(), needed);
               int remaining = needed - takenFromVillager;
               if (remaining > 0) {
                  inv.removeByTag(level, t.tag(), remaining, Set.of());
               }
               break;
            default:
               throw new MatchException(null, null);
         }
      }

      for (Entry<Item, Integer> entry : outputs.entrySet()) {
         inv.add(level, entry.getKey(), entry.getValue());
      }

      LOGGER.debug("Crafting performed at building {} at {}", craftingBuilding.getPlanId(), target.navigationPos());
      org.millenaire.village.VillagerXpService.awardCraft(ctx.villager(), ctx.village());
      return true;
   }

   @Nullable
   @Override
   public BuildingInstance resolveBuildingLimitTarget(GoalContext ctx, GatheringType type) {
      List<BuildingInstance> buildings = this.resolveTargetBuildings(ctx, type);
      return buildings.isEmpty() ? null : buildings.getFirst();
   }

   private boolean hasCraftingPos(BuildingInstance building) {
      return this.getCraftingPos(building) != null;
   }

   @Nullable
   private BlockPos getCraftingPos(BuildingInstance building) {
      BlockPos pos = building.getFirstPointPos("craftingPos");
      if (pos != null) {
         return pos;
      }

      pos = building.getFirstPointPos("sellingPos");
      return pos != null ? pos : building.getFirstPointPos("sleepingPos");
   }

   private boolean hasSpaceForOutputs(BuildingInventory inv, ServerLevel level, Map<Item, Integer> outputs) {
      Map<Item, Integer> cache = inv.getCachedContents();
      if (cache == null) {
         inv.scanChests(level);
         cache = inv.getCachedContents();
      }

      if (cache == null) {
         return false;
      }

      int totalItems = cache.values().stream().mapToInt(Integer::intValue).sum();
      int totalSlots = inv.getChestCount() * 27;
      int totalOutputCount = outputs.values().stream().mapToInt(Integer::intValue).sum();
      int freeSpace = totalSlots * 64 - totalItems;
      return freeSpace >= totalOutputCount;
   }

   private boolean hasAllInputsCombined(BuildingInstance building, ServerLevel level, VillagerInventory villagerInv, List<CraftingHandler.CraftingInput> inputs) {
      BuildingInventory buildingInv = building.getInventory();
      if (buildingInv == null) {
         return false;
      }

      for (CraftingHandler.CraftingInput input : inputs) {
         int available = switch (input) {
            case CraftingHandler.CraftingInput.Concrete c -> villagerInv.getCount(c.item()) + buildingInv.getCount(level, c.item());
            case CraftingHandler.CraftingInput.Tagged t -> villagerInv.getCountByTag(t.tag()) + buildingInv.getCountByTag(level, t.tag());
            default -> throw new MatchException(null, null);
         };
         if (available < input.count()) {
            return false;
         }
      }

      return true;
   }

   static List<CraftingHandler.CraftingInput> parseCraftingInputs(JsonObject params, String key) {
      Map<Object, CraftingHandler.CraftingInput> merged = new LinkedHashMap<>();
      if (!params.has(key)) {
         return new ArrayList<>();
      }

      for (JsonElement elem : params.getAsJsonArray(key)) {
         JsonObject obj = elem.getAsJsonObject();
         String itemId = GsonHelper.getAsString(obj, "item");
         int count = GsonHelper.getAsInt(obj, "count", 1);
         if (itemId.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(itemId.substring(1));
            if (tagId != null) {
               TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
               CraftingHandler.CraftingInput existing = merged.get(itemId);
               int total = count + (existing != null ? existing.count() : 0);
               merged.put(itemId, new CraftingHandler.CraftingInput.Tagged(tag, itemId, total));
            }
         } else {
            Item item = ItemHelper.resolve(itemId);
            if (item != null && item != Items.AIR) {
               CraftingHandler.CraftingInput existing = merged.get(item);
               int total = count + (existing != null ? existing.count() : 0);
               merged.put(item, new CraftingHandler.CraftingInput.Concrete(item, total));
            }
         }
      }

      return new ArrayList<>(merged.values());
   }

   sealed interface CraftingInput permits CraftingHandler.CraftingInput.Concrete, CraftingHandler.CraftingInput.Tagged {
      int count();

      record Concrete(Item item, int count) implements CraftingHandler.CraftingInput {
      }

      record Tagged(TagKey<Item> tag, String tagId, int count) implements CraftingHandler.CraftingInput {
      }
   }
}
