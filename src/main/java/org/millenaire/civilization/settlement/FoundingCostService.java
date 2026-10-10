package org.millenaire.civilization.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.Village;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementPlan;

/**
 * Authoritative founding-cost computation and escrow coverage for colonization.
 * Execution, player contributions, and presentation must all use this service.
 *
 * <p>Exact item lines consume matching items first; only explicit Anywood lines may accept
 * different log species. One stack count is never attributed to two cost lines.
 */
public final class FoundingCostService {
   public static final int FALLBACK_WOOD_COST = 32;
   public static final int FALLBACK_COBBLE_COST = 32;

   private FoundingCostService() {
   }

   public record CostComputation(Map<ResourceLocation, Integer> costs, @Nullable String blockReason) {
      public boolean ok() {
         return this.blockReason == null || this.blockReason.isBlank();
      }
   }

   public record Coverage(
      Map<ResourceLocation, Integer> costs,
      Map<ResourceLocation, Integer> missing,
      boolean fullyCovered,
      @Nullable String blockReason
   ) {
      public boolean ok() {
         return (this.blockReason == null || this.blockReason.isBlank()) && this.fullyCovered;
      }
   }

   public record ReserveResult(boolean fullyCovered, List<ItemStack> taken, List<ItemStack> rollbackLeftover, @Nullable String reason) {
   }

   public static CostComputation computeFoundingCosts(@Nullable SettlementPlan plan) {
      Map<ResourceLocation, Integer> costs = new LinkedHashMap<>();
      if (plan == null) {
         return new CostComputation(costs, "plan_missing");
      }
      if (plan.buildings().isEmpty()) {
         // Layout not ready — caller should wait, not treat as free founding.
         return new CostComputation(costs, "layout_empty");
      }
      for (PlannedBuilding building : plan.buildings()) {
         if (building.planSetId() == null) {
            return new CostComputation(Map.of(), "missing_building_content");
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(building.planSetId());
         if (set == null) {
            return new CostComputation(Map.of(), "missing_building_content");
         }
         BuildingPlanSet.LevelDef level0 = set.getLevel(building.variant(), Math.max(0, building.level()));
         if (level0 == null) {
            return new CostComputation(Map.of(), "missing_building_content");
         }
         if (level0.requiredResources() == null) {
            continue;
         }
         for (Entry<ResourceLocation, Integer> e : level0.requiredResources().entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
               continue;
            }
            costs.merge(e.getKey(), e.getValue(), Integer::sum);
         }
      }
      if (costs.isEmpty()) {
         // Documented minimum levy when content defines no costs — founding is never free.
         costs.put(AnywoodHelper.ANYWOOD_LOG, FALLBACK_WOOD_COST);
         costs.put(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), FALLBACK_COBBLE_COST);
      }
      // Validate exact item keys resolve; unknown ids are an explicit block.
      for (ResourceLocation key : costs.keySet()) {
         if (AnywoodHelper.isAnywood(key)) {
            continue;
         }
         Item item = BuiltInRegistries.ITEM.get(key);
         if (item == null || item == Items.AIR) {
            return new CostComputation(Map.of(), "missing_required_item:" + key);
         }
      }
      return new CostComputation(costs, null);
   }

   public static Coverage assessCoverage(@Nullable SettlementPlan plan, @Nullable List<ItemStack> escrow) {
      CostComputation computation = computeFoundingCosts(plan);
      if (!computation.ok()) {
         return new Coverage(computation.costs(), Map.of(), false, computation.blockReason());
      }
      Map<ResourceLocation, Integer> missing = missingAfterEscrow(computation.costs(), escrow);
      return new Coverage(computation.costs(), missing, missing.isEmpty(), null);
   }

   public static boolean isFullyCovered(@Nullable SettlementPlan plan, @Nullable List<ItemStack> escrow) {
      return assessCoverage(plan, escrow).ok();
   }

   public static boolean isOperationFullyCovered(ServerLevel level, ColonizationOperation op, SettlementPlan plan) {
      if (op == null) {
         return false;
      }
      if (op.costsConsumed()) {
         return true;
      }
      if (op.decodeUnresolved()) {
         return false;
      }
      return isFullyCovered(plan, op.reservedStacks());
   }

   /**
    * Allocate escrow stacks to cost lines without double-counting.
    * Exact lines first, then Anywood from remaining logs.
    */
   public static Map<ResourceLocation, Integer> missingAfterEscrow(
      Map<ResourceLocation, Integer> costs,
      @Nullable List<ItemStack> escrow
   ) {
      Map<ResourceLocation, Integer> missing = new LinkedHashMap<>();
      if (costs == null || costs.isEmpty()) {
         return missing;
      }
      List<ItemStack> pool = copyPool(escrow);
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         if (AnywoodHelper.isAnywood(e.getKey())) {
            continue;
         }
         int need = Math.max(0, e.getValue());
         int took = consumeExact(pool, e.getKey(), need);
         int left = need - took;
         if (left > 0) {
            missing.put(e.getKey(), left);
         }
      }
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         if (!AnywoodHelper.isAnywood(e.getKey())) {
            continue;
         }
         int need = Math.max(0, e.getValue());
         int took = consumeAnywood(pool, need);
         int left = need - took;
         if (left > 0) {
            missing.put(e.getKey(), left);
         }
      }
      return missing;
   }

   /**
    * Pull only the remaining deficit from the parent village into {@code taken}.
    * Existing escrow is credited first. On failure, taken stacks are returned for rollback.
    */
   public static ReserveResult reserveDeficitFromVillage(
      ServerLevel level,
      Village parent,
      SettlementPlan plan,
      List<ItemStack> existingEscrow
   ) {
      Coverage coverage = assessCoverage(plan, existingEscrow);
      if (!coverage.ok() && coverage.blockReason() != null) {
         return new ReserveResult(false, List.of(), List.of(), coverage.blockReason());
      }
      if (coverage.fullyCovered()) {
         return new ReserveResult(true, List.of(), List.of(), null);
      }
      List<ItemStack> taken = new ArrayList<>();
      for (Entry<ResourceLocation, Integer> e : coverage.missing().entrySet()) {
         int need = e.getValue();
         if (need <= 0) {
            continue;
         }
         BuildingInventory.RemoveStacksResult result;
         if (AnywoodHelper.isAnywood(e.getKey())) {
            result = removeAnyLogsExact(level, parent, need);
         } else {
            Item item = BuiltInRegistries.ITEM.get(e.getKey());
            if (item == null || item == Items.AIR) {
               List<ItemStack> leftover = rollbackStacksExact(level, parent, taken);
               return new ReserveResult(false, taken, leftover, "missing_required_item:" + e.getKey());
            }
            result = removeItemExact(level, parent, item, need);
         }
         taken.addAll(result.stacks());
         if (!result.fullySatisfied()) {
            List<ItemStack> leftover = rollbackStacksExact(level, parent, taken);
            return new ReserveResult(false, taken, leftover, "insufficient_resources");
         }
      }
      // Verify combined escrow + taken fully covers (guards against allocator bugs).
      List<ItemStack> combined = new ArrayList<>();
      if (existingEscrow != null) {
         for (ItemStack s : existingEscrow) {
            if (s != null && !s.isEmpty()) {
               combined.add(s.copy());
            }
         }
      }
      for (ItemStack s : taken) {
         if (s != null && !s.isEmpty()) {
            combined.add(s.copy());
         }
      }
      if (!isFullyCovered(plan, combined)) {
         List<ItemStack> leftover = rollbackStacksExact(level, parent, taken);
         return new ReserveResult(false, taken, leftover, "insufficient_resources");
      }
      return new ReserveResult(true, taken, List.of(), null);
   }

   private static List<ItemStack> copyPool(@Nullable List<ItemStack> escrow) {
      List<ItemStack> pool = new ArrayList<>();
      if (escrow == null) {
         return pool;
      }
      for (ItemStack stack : escrow) {
         if (stack != null && !stack.isEmpty()) {
            pool.add(stack.copy());
         }
      }
      return pool;
   }

   private static int consumeExact(List<ItemStack> pool, ResourceLocation itemId, int need) {
      int left = need;
      Item item = BuiltInRegistries.ITEM.get(itemId);
      if (item == null || item == Items.AIR || left <= 0) {
         return 0;
      }
      for (ItemStack stack : pool) {
         if (left <= 0) {
            break;
         }
         if (stack.isEmpty() || stack.getItem() != item) {
            continue;
         }
         int take = Math.min(left, stack.getCount());
         stack.shrink(take);
         left -= take;
      }
      return need - left;
   }

   private static int consumeAnywood(List<ItemStack> pool, int need) {
      int left = need;
      for (ItemStack stack : pool) {
         if (left <= 0) {
            break;
         }
         if (stack.isEmpty() || !stack.is(AnywoodHelper.LOGS_TAG)) {
            continue;
         }
         int take = Math.min(left, stack.getCount());
         stack.shrink(take);
         left -= take;
      }
      return need - left;
   }

   private static BuildingInventory.RemoveStacksResult removeAnyLogsExact(ServerLevel level, Village village, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         BuildingInventory.RemoveStacksResult part = inv.removeByTagExact(level, AnywoodHelper.LOGS_TAG, left, java.util.Set.of());
         taken.addAll(part.stacks());
         left = part.unsatisfied();
      }
      int removed = taken.stream().mapToInt(ItemStack::getCount).sum();
      return new BuildingInventory.RemoveStacksResult(removed, Math.max(0, amount - removed), taken);
   }

   private static BuildingInventory.RemoveStacksResult removeItemExact(ServerLevel level, Village village, Item item, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         BuildingInventory.RemoveStacksResult part = inv.removeByItemExact(level, item, left);
         taken.addAll(part.stacks());
         left = part.unsatisfied();
      }
      int removed = taken.stream().mapToInt(ItemStack::getCount).sum();
      return new BuildingInventory.RemoveStacksResult(removed, Math.max(0, amount - removed), taken);
   }

   private static List<ItemStack> rollbackStacksExact(ServerLevel level, Village village, List<ItemStack> taken) {
      List<ItemStack> leftover = new ArrayList<>();
      for (ItemStack stack : taken) {
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         ItemStack remaining = stack.copy();
         for (var building : village.getBuildings()) {
            if (remaining.isEmpty()) {
               break;
            }
            BuildingInventory inv = building.getInventory();
            if (inv == null) {
               continue;
            }
            BuildingInventory.AddStackResult result = inv.addExactStack(level, remaining);
            remaining = result.remainder();
         }
         if (!remaining.isEmpty()) {
            leftover.add(remaining);
         }
      }
      return leftover;
   }
}
