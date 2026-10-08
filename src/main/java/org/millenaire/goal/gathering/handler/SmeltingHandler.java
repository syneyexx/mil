package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.millenaire.block.FirePitBlockEntity;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class SmeltingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int DEFAULT_MINIMUM_TO_COOK = 3;
   private static final int MINIMUM_FUEL = 4;
   private static final int MAX_FURNACE_INPUT = 32;
   static final int MAX_FUEL_PER_FURNACE = 64;
   static final int FUEL_REFILL_THRESHOLD = 32;
   private static final Map<Item, ItemStack> LOG_TO_PLANKS = new ConcurrentHashMap<>();
   private static final Map<Item, Item> PLANK_TO_LOG = new ConcurrentHashMap<>();
   @Nullable
   private static List<Item> fuelItemsCache;

   private int getMinimumToCook(GatheringType type) {
      return GsonHelper.getAsInt(type.handlerParams(), "minimumToCook", 3);
   }

   @Override
   public String id() {
      return "smelting";
   }

   @Override
   public void onClear() {
      fuelItemsCache = null;
   }

   @Override
   public List<String> validate(GatheringType type) {
      return this.validateItemList(type, "inputs", true);
   }

   @Override
   public boolean supportsRemoteAction() {
      return true;
   }

   @Override
   public boolean canStart(GoalContext ctx, GatheringType type) {
      SmeltingHandler.SmeltingInput input = resolveInput(type);
      if (input == null) {
         return false;
      }

      boolean firepitBurnable = this.isFirePitBurnable(input, ctx.level());

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         if (building.getInventory() != null) {
            for (BlockPos furnacePos : building.getFurnacePositions()) {
               if (ctx.level().getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace
                  && this.furnaceNeedsAttention(ctx, type, building, furnace, input)) {
                  return true;
               }
            }

            if (firepitBurnable) {
               for (BlockPos fpPos : building.getFirePitPositions()) {
                  if (ctx.level().getBlockEntity(fpPos) instanceof FirePitBlockEntity firePit
                     && this.firePitNeedsAttention(ctx, type, building, firePit, input)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @Nullable
   @Override
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      SmeltingHandler.SmeltingInput input = resolveInput(type);
      if (input == null) {
         return null;
      }

      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos bestPos = null;
      double bestDistSq = Double.MAX_VALUE;
      boolean firepitBurnable = this.isFirePitBurnable(input, ctx.level());

      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         for (BlockPos furnacePos : building.getFurnacePositions()) {
            if (ctx.level().getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace
               && this.furnaceNeedsAttention(ctx, type, building, furnace, input)) {
               double distSq = reference.distSqr(furnacePos);
               if (distSq < bestDistSq) {
                  bestDistSq = distSq;
                  bestPos = furnacePos;
               }
            }
         }

         if (firepitBurnable) {
            for (BlockPos fpPos : building.getFirePitPositions()) {
               if (ctx.level().getBlockEntity(fpPos) instanceof FirePitBlockEntity firePit && this.firePitNeedsAttention(ctx, type, building, firePit, input)) {
                  double distSq = reference.distSqr(fpPos);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     bestPos = fpPos;
                  }
               }
            }
         }
      }

      return bestPos != null ? new GatheringTarget.BlockTarget(bestPos) : null;
   }

   @Override
   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      SmeltingHandler.SmeltingInput input = resolveInput(type);
      if (input == null) {
         return true;
      }

      int minimumToCook = this.getMinimumToCook(type);
      BlockPos pos = target.navigationPos();
      BuildingInstance ownerBuilding = this.findBuildingOwning(ctx, type, pos);
      if (ownerBuilding == null) {
         return true;
      }

      BuildingInventory inv = ownerBuilding.getInventory();
      if (inv == null) {
         return true;
      }

      ServerLevel level = ctx.level();

      for (BlockPos furnacePos : ownerBuilding.getFurnacePositions()) {
         if (level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
            this.performFurnaceAction(ctx, ownerBuilding, furnace, furnacePos, input, minimumToCook, level);
         }
      }

      if (this.isFirePitBurnable(input, level)) {
         for (BlockPos fpPos : ownerBuilding.getFirePitPositions()) {
            if (level.getBlockEntity(fpPos) instanceof FirePitBlockEntity firePit) {
               this.performActionFirePit(ctx, ownerBuilding, input, minimumToCook, fpPos, firePit);
            }
         }
      }

      return true;
   }

   private void performFurnaceAction(
      GoalContext ctx,
      BuildingInstance ownerBuilding,
      AbstractFurnaceBlockEntity furnace,
      BlockPos pos,
      SmeltingHandler.SmeltingInput input,
      int minimumToCook,
      ServerLevel level
   ) {
      BuildingInventory inv = ownerBuilding.getInventory();
      if (inv != null) {
         ItemStack outputSlot = furnace.getItem(2);
         if (!outputSlot.isEmpty()) {
            inv.add(level, outputSlot.getItem(), outputSlot.getCount());
            furnace.setItem(2, ItemStack.EMPTY);
            furnace.setChanged();
            LOGGER.debug("Retrieved {} {} from furnace at {}", new Object[]{outputSlot.getCount(), outputSlot.getItem(), pos});
         }

         ItemStack inputSlot = furnace.getItem(0);
         Item placement = input.placementItem(inputSlot);
         boolean accepts = placement != null && applianceAccepts(level, furnace, placement);
         if (accepts) {
            int totalAvailable = this.countInputAvailable(ctx, ownerBuilding, input);
            if (totalAvailable >= minimumToCook) {
               if (inputSlot.isEmpty()) {
                  int toPlace = Math.min(64, totalAvailable);
                  int removed = this.removeInputFromSources(ctx, ownerBuilding, input, toPlace, level);
                  if (removed > 0) {
                     furnace.setItem(0, new ItemStack(placement, removed));
                     furnace.setChanged();
                  }
               } else if (input.matches(inputSlot) && inputSlot.getCount() < 64) {
                  int toAdd = Math.min(64 - inputSlot.getCount(), totalAvailable);
                  int removed = this.removeInputFromSources(ctx, ownerBuilding, input, toAdd, level);
                  if (removed > 0) {
                     inputSlot.grow(removed);
                     furnace.setItem(0, inputSlot);
                     furnace.setChanged();
                  }
               }
            }
         }

         if (accepts) {
            this.addFuelIfNeeded(ctx, ownerBuilding, furnace, level);
         }
      }
   }

   private void performActionFirePit(
      GoalContext ctx, BuildingInstance ownerBuilding, SmeltingHandler.SmeltingInput input, int minimumToCook, BlockPos pos, FirePitBlockEntity firePit
   ) {
      BuildingInventory inv = ownerBuilding.getInventory();
      if (inv != null) {
         ServerLevel level = ctx.level();

         for (int lane = 0; lane < 3; lane++) {
            ItemStack laneInput = firePit.getInputItem(lane);
            Item placement = input.placementItem(laneInput);
            if (placement == null) {
               break;
            }

            int countGoods = this.countInputAvailable(ctx, ownerBuilding, input);
            if (laneInput.isEmpty() && countGoods >= minimumToCook) {
               int nb = Math.min(64, countGoods);
               int removed = this.removeInputFromSources(ctx, ownerBuilding, input, nb, level);
               if (removed > 0) {
                  firePit.setItem(0 + lane, new ItemStack(placement, removed));
               }
            } else if (input.matches(laneInput) && laneInput.getCount() < 64 && countGoods > 0) {
               int nb = Math.min(64 - laneInput.getCount(), countGoods);
               int removed = this.removeInputFromSources(ctx, ownerBuilding, input, nb, level);
               if (removed > 0) {
                  laneInput.grow(removed);
                  firePit.setItem(0 + lane, laneInput);
               }
            }
         }

         for (int lane = 0; lane < 3; lane++) {
            ItemStack laneOutput = firePit.getOutputItem(lane);
            if (!laneOutput.isEmpty()) {
               inv.add(level, laneOutput.getItem(), laneOutput.getCount());
               firePit.setItem(4 + lane, ItemStack.EMPTY);
               LOGGER.debug("Retrieved {} {} from fire pit lane {} at {}", new Object[]{laneOutput.getCount(), laneOutput.getItem(), lane, pos});
            }
         }

         this.addFuelToFirePitIfNeeded(ctx, ownerBuilding, firePit, level);
         firePit.setChanged();
      }
   }

   @Nullable
   @Override
   public BuildingInstance resolveBuildingLimitTarget(GoalContext ctx, GatheringType type) {
      List<BuildingInstance> buildings = this.resolveTargetBuildings(ctx, type);
      return buildings.isEmpty() ? null : buildings.getFirst();
   }

   @Nullable
   static SmeltingHandler.SmeltingInput resolveInput(GatheringType type) {
      List<CraftingHandler.CraftingInput> declared = CraftingHandler.parseCraftingInputs(type.handlerParams(), "inputs");
      if (declared.isEmpty()) {
         return null;
      }

      return switch ((CraftingHandler.CraftingInput)declared.getFirst()) {
         case CraftingHandler.CraftingInput.Concrete c -> new SmeltingHandler.SmeltingInput(c.item(), null, null);
         case CraftingHandler.CraftingInput.Tagged t -> new SmeltingHandler.SmeltingInput(null, t.tag(), t.tagId());
         default -> throw new MatchException(null, null);
      };
   }

   private boolean isFirePitBurnable(SmeltingHandler.SmeltingInput input, ServerLevel level) {
      Item representative = input.placementItem(ItemStack.EMPTY);
      return representative != null && FirePitBlockEntity.isFirePitBurnable(new ItemStack(representative), level);
   }

   private int countInputAvailable(GoalContext ctx, BuildingInstance smeltingBuilding, SmeltingHandler.SmeltingInput input) {
      int total = 0;
      ServerLevel level = ctx.level();
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null) {
         total += input.tag() != null ? inv.getCountByTag(level, input.tag()) : inv.getCount(level, input.item());
      }

      total += input.tag() != null ? ctx.villager().getInventory().getCountByTag(input.tag()) : ctx.villager().getInventory().getCount(input.item());
      BuildingInstance home = this.getVillagerHome(ctx);
      if (home != null && home != smeltingBuilding) {
         BuildingInventory homeInv = home.getInventory();
         if (homeInv != null) {
            total += input.tag() != null ? homeInv.getCountByTag(level, input.tag()) : homeInv.getCount(level, input.item());
         }
      }

      return total;
   }

   private int removeInputFromSources(GoalContext ctx, BuildingInstance smeltingBuilding, SmeltingHandler.SmeltingInput input, int amount, ServerLevel level) {
      int remaining = amount;
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null && remaining > 0) {
         remaining -= input.tag() != null ? inv.removeByTag(level, input.tag(), remaining, Set.of()) : inv.remove(level, input.item(), remaining);
      }

      if (remaining > 0) {
         if (input.tag() != null) {
            remaining -= ctx.villager().getInventory().removeByTag(input.tag(), remaining);
         } else {
            int removed = Math.min(remaining, ctx.villager().getInventory().getCount(input.item()));
            ctx.villager().getInventory().remove(input.item(), removed);
            remaining -= removed;
         }
      }

      if (remaining > 0) {
         BuildingInstance home = this.getVillagerHome(ctx);
         if (home != null && home != smeltingBuilding) {
            BuildingInventory homeInv = home.getInventory();
            if (homeInv != null) {
               remaining -= input.tag() != null ? homeInv.removeByTag(level, input.tag(), remaining, Set.of()) : homeInv.remove(level, input.item(), remaining);
            }
         }
      }

      return amount - remaining;
   }

   private boolean furnaceNeedsAttention(
      GoalContext ctx, GatheringType type, BuildingInstance building, AbstractFurnaceBlockEntity furnace, SmeltingHandler.SmeltingInput input
   ) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return false;
      }

      int minimumToCook = this.getMinimumToCook(type);
      ItemStack outputSlot = furnace.getItem(2);
      if (!outputSlot.isEmpty() && outputSlot.getCount() >= minimumToCook) {
         return true;
      }

      Item placement = input.placementItem(furnace.getItem(0));
      boolean accepts = placement != null && applianceAccepts(ctx.level(), furnace, placement);
      if (accepts) {
         int totalAvailable = this.countInputAvailable(ctx, building, input);
         ItemStack inputSlot = furnace.getItem(0);
         if (totalAvailable >= minimumToCook && (inputSlot.isEmpty() || input.matches(inputSlot) && inputSlot.getCount() < 32)) {
            return true;
         }
      }

      ItemStack inputSlot = furnace.getItem(0);
      if (accepts && input.matches(inputSlot)) {
         ItemStack fuelSlot = furnace.getItem(1);
         if ((fuelSlot.isEmpty() || fuelSlot.getCount() < 32)
            && this.countFuelAvailable(ctx, building) >= 4
            && this.planFueling(ctx, building, fuelSlot, ctx.level()) != null) {
            return true;
         }
      }

      return false;
   }

   static boolean applianceAccepts(ServerLevel level, AbstractFurnaceBlockEntity furnace, Item input) {
      RecipeType<? extends AbstractCookingRecipe> recipeType = furnace instanceof BlastFurnaceBlockEntity
         ? RecipeType.BLASTING
         : (furnace instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING);
      return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(new ItemStack(input)), level).isPresent();
   }

   private int countFuelAvailable(GoalContext ctx, BuildingInstance building) {
      int count = 0;

      for (Item fuel : getAllFuelItems()) {
         count += this.countFuelInSource(ctx, building, fuel);
      }

      return count;
   }

   private int countFuelInSource(GoalContext ctx, BuildingInstance smeltingBuilding, Item fuel) {
      int count = 0;
      ServerLevel level = ctx.level();
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null) {
         count += inv.getCount(level, fuel);
      }

      count += ctx.villager().getInventory().getCount(fuel);
      BuildingInstance home = this.getVillagerHome(ctx);
      if (home != null && home != smeltingBuilding) {
         BuildingInventory homeInv = home.getInventory();
         if (homeInv != null) {
            count += homeInv.getCount(level, fuel);
         }
      }

      return count;
   }

   private static boolean isLog(Item item) {
      return new ItemStack(item).is(ItemTags.LOGS);
   }

   @Nullable
   private static ItemStack resolveLogPlanks(ServerLevel level, Item log) {
      ItemStack cached = LOG_TO_PLANKS.get(log);
      if (cached != null) {
         return cached;
      } else {
         CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(log)));
         RecipeHolder<CraftingRecipe> holder = (RecipeHolder<CraftingRecipe>)level.getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level)
            .orElse(null);
         if (holder == null) {
            return null;
         } else {
            ItemStack result = ((CraftingRecipe)holder.value()).getResultItem(level.registryAccess());
            if (!result.isEmpty() && result.is(ItemTags.PLANKS)) {
               ItemStack planks = result.copy();
               LOG_TO_PLANKS.put(log, planks);
               PLANK_TO_LOG.put(planks.getItem(), log);
               return planks;
            } else {
               return null;
            }
         }
      }
   }

   @Nullable
   private SmeltingHandler.FuelOp planFueling(GoalContext ctx, BuildingInstance building, ItemStack currentFuel, ServerLevel level) {
      int current = currentFuel.getCount();
      int capacity = 64 - current;
      if (capacity <= 0) {
         return null;
      }

      Item sourceItem;
      Item placedItem;
      int ratio;
      if (currentFuel.isEmpty()) {
         Item best = this.findBestFuel(ctx, building);
         if (best == null) {
            return null;
         }

         ItemStack planks = isLog(best) ? resolveLogPlanks(level, best) : null;
         if (planks != null) {
            sourceItem = best;
            placedItem = planks.getItem();
            ratio = planks.getCount();
         } else {
            sourceItem = best;
            placedItem = best;
            ratio = 1;
         }
      } else {
         placedItem = currentFuel.getItem();
         Item log = PLANK_TO_LOG.get(placedItem);
         if (log != null && this.countFuelInSource(ctx, building, log) > 0) {
            sourceItem = log;
            ratio = LOG_TO_PLANKS.get(log).getCount();
         } else {
            sourceItem = placedItem;
            ratio = 1;
         }
      }

      int availableSource = this.countFuelInSource(ctx, building, sourceItem);
      int sourceConsumed = planFuelSourceAmount(capacity, availableSource, ratio);
      if (sourceConsumed <= 0) {
         return null;
      }

      int placeCount = sourceConsumed * ratio;
      return new SmeltingHandler.FuelOp(new ItemStack(placedItem, current + placeCount), sourceItem, sourceConsumed);
   }

   static int planFuelSourceAmount(int capacity, int availableSource, int ratio) {
      return capacity > 0 && availableSource > 0 && ratio > 0 ? Math.min(capacity, availableSource * ratio) / ratio : 0;
   }

   private void addFuelIfNeeded(GoalContext ctx, BuildingInstance building, AbstractFurnaceBlockEntity furnace, ServerLevel level) {
      SmeltingHandler.FuelOp op = this.planFueling(ctx, building, furnace.getItem(1), level);
      if (op != null) {
         furnace.setItem(1, op.slotStack());
         this.removeFuelFromSources(ctx, building, op.sourceItem(), op.sourceConsumed(), level);
         furnace.setChanged();
      }
   }

   private void removeFuelFromSources(GoalContext ctx, BuildingInstance smeltingBuilding, Item fuel, int amount, ServerLevel level) {
      int remaining = amount;
      BuildingInventory inv = smeltingBuilding.getInventory();
      if (inv != null && remaining > 0) {
         remaining -= inv.remove(level, fuel, remaining);
      }

      if (remaining > 0) {
         int removed = Math.min(remaining, ctx.villager().getInventory().getCount(fuel));
         ctx.villager().getInventory().remove(fuel, removed);
         remaining -= removed;
      }

      if (remaining > 0) {
         BuildingInstance home = this.getVillagerHome(ctx);
         if (home != null && home != smeltingBuilding) {
            BuildingInventory homeInv = home.getInventory();
            if (homeInv != null) {
               homeInv.remove(level, fuel, remaining);
            }
         }
      }
   }

   private static List<Item> getAllFuelItems() {
      if (fuelItemsCache != null) {
         return fuelItemsCache;
      }

      ArrayList<Item> list = new ArrayList<>();
      list.add(Items.COAL);
      list.add(Items.CHARCOAL);

      for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
         list.add((Item)holder.value());
      }

      for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS)) {
         list.add((Item)holder.value());
      }

      fuelItemsCache = List.copyOf(list);
      return fuelItemsCache;
   }

   @Nullable
   private Item findBestFuel(GoalContext ctx, BuildingInstance building) {
      Item best = null;
      int bestCount = 0;

      for (Item fuel : getAllFuelItems()) {
         int count = this.countFuelInSource(ctx, building, fuel);
         if (count > bestCount) {
            bestCount = count;
            best = fuel;
         }
      }

      return bestCount >= 4 ? best : null;
   }

   @Nullable
   private BuildingInstance findBuildingOwning(GoalContext ctx, GatheringType type, BlockPos furnacePos) {
      for (BuildingInstance building : this.resolveTargetBuildings(ctx, type)) {
         for (BlockPos pos : building.getFurnacePositions()) {
            if (pos.equals(furnacePos)) {
               return building;
            }
         }

         for (BlockPos pos : building.getFirePitPositions()) {
            if (pos.equals(furnacePos)) {
               return building;
            }
         }
      }

      return null;
   }

   @Nullable
   private BuildingInstance getVillagerHome(GoalContext ctx) {
      BuildingId homeId = ctx.villager().getHomeBuilding();
      return homeId == null ? null : ctx.village().getBuilding(homeId);
   }

   private boolean firePitNeedsAttention(
      GoalContext ctx, GatheringType type, BuildingInstance building, FirePitBlockEntity firePit, SmeltingHandler.SmeltingInput input
   ) {
      int minimumToCook = this.getMinimumToCook(type);

      for (int lane = 0; lane < 3; lane++) {
         ItemStack laneOutput = firePit.getOutputItem(lane);
         if (!laneOutput.isEmpty() && laneOutput.getCount() >= minimumToCook) {
            return true;
         }

         int totalAvailable = this.countInputAvailable(ctx, building, input);
         ItemStack laneInput = firePit.getInputItem(lane);
         if (totalAvailable >= minimumToCook && (laneInput.isEmpty() || input.matches(laneInput) && laneInput.getCount() < 32)) {
            return true;
         }
      }

      return this.hasFuelNeedForFirePit(firePit, ctx, building);
   }

   private boolean hasFuelNeedForFirePit(FirePitBlockEntity firePit, GoalContext ctx, BuildingInstance building) {
      boolean hasInput = false;

      for (int lane = 0; lane < 3; lane++) {
         if (!firePit.getInputItem(lane).isEmpty()) {
            hasInput = true;
            break;
         }
      }

      if (!hasInput) {
         return false;
      }

      ItemStack fuelSlot = firePit.getFuelItem();
      boolean thresholdMet = fuelSlot.isEmpty()
         ? this.countFuelAvailable(ctx, building) > 4
         : fuelSlot.getCount() < 32 && this.countFuelAvailable(ctx, building) >= 4;
      return thresholdMet && this.planFueling(ctx, building, fuelSlot, ctx.level()) != null;
   }

   private void addFuelToFirePitIfNeeded(GoalContext ctx, BuildingInstance building, FirePitBlockEntity firePit, ServerLevel level) {
      SmeltingHandler.FuelOp op = this.planFueling(ctx, building, firePit.getFuelItem(), level);
      if (op != null) {
         firePit.setItem(3, op.slotStack());
         this.removeFuelFromSources(ctx, building, op.sourceItem(), op.sourceConsumed(), level);
      }
   }

   private record FuelOp(ItemStack slotStack, Item sourceItem, int sourceConsumed) {
   }

   record SmeltingInput(@Nullable Item item, @Nullable TagKey<Item> tag, @Nullable String tagId) {
      boolean matches(ItemStack stack) {
         if (stack.isEmpty()) {
            return false;
         } else {
            return this.tag != null ? stack.is(this.tag) : stack.is(this.item);
         }
      }

      @Nullable
      Item placementItem(ItemStack slot) {
         if (this.tag == null) {
            return this.item;
         } else {
            return !slot.isEmpty() && slot.is(this.tag) ? slot.getItem() : ItemHelper.resolve(this.tagId);
         }
      }
   }
}
