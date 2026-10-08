package org.millenaire.goal.gathering.handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class BreedingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SCAN_RADIUS_XZ = 15;
   private static final int SCAN_RADIUS_Y = 10;
   private static final int MIN_ADULTS_FOR_BREEDING = 2;

   public static boolean isAdult(Animal animal) {
      return animal.isAlive() && animal.getAge() == 0;
   }

   public static boolean isBreedingCandidate(Animal animal) {
      return isAdult(animal) && !animal.isInLove();
   }

   public static boolean hasBreedingQuorum(Collection<? extends Animal> animalsInRange) {
      int adults = 0;

      for (Animal animal : animalsInRange) {
         if (isAdult(animal)) {
            adults++;
         }
      }

      return adults >= 2;
   }

   @Override
   public String id() {
      return "breeding";
   }

   @Override
   public Item getDefaultHeldItem(GatheringType type) {
      return Items.WHEAT;
   }

   @Override
   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      if (this.getBuildingTag(type) == null) {
         errors.add("missing required handlerParam 'buildingTag'");
      }

      errors.addAll(validateItemIdList(type, "foodItems", true));
      String animalId = GsonHelper.getAsString(type.handlerParams(), "animalType", null);
      if (animalId == null) {
         errors.add("missing required handlerParam 'animalType'");
      } else if (EntityType.byString(animalId).isEmpty()) {
         errors.add("unresolvable entity type '" + animalId + "' in 'animalType'");
      }

      return errors;
   }

   @Override
   public boolean canStart(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      if (buildingTag == null) {
         return false;
      }

      EntityType<?> animalType = this.getAnimalType(type);
      if (animalType == null) {
         return false;
      }

      List<Item> foodItems = this.getFoodItems(type);
      if (foodItems.isEmpty()) {
         return false;
      }

      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      if (farms.isEmpty()) {
         return false;
      }

      ServerLevel level = ctx.level();

      for (BuildingInstance farm : farms) {
         if (this.hasFoodInBuilding(farm, level, foodItems) && this.canBreedAtFarm(this.findAnimalsAround(level, farm, animalType), farm, foodItems)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   @Override
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      String buildingTag = this.getBuildingTag(type);
      if (buildingTag == null) {
         return null;
      }

      EntityType<?> animalType = this.getAnimalType(type);
      if (animalType == null) {
         return null;
      }

      List<Item> foodItems = this.getFoodItems(type);
      if (foodItems.isEmpty()) {
         return null;
      }

      ServerLevel level = ctx.level();
      List<BuildingInstance> farms = this.findBuildingsWithTag(ctx.village(), buildingTag);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      List<Animal> breedable = new ArrayList<>();

      for (BuildingInstance farm : farms) {
         if (this.hasFoodInBuilding(farm, level, foodItems)) {
            List<Animal> nearby = this.findAnimalsAround(level, farm, animalType);
            if (this.canBreedAtFarm(nearby, farm, foodItems)) {
               breedable.addAll(selectBreedable(nearby, foodItems));
            }
         }
      }

      Animal bestAnimal = findClosestEntity(breedable, reference, lastTarget, type.batchRadius());
      return bestAnimal != null ? new GatheringTarget.EntityTarget(bestAnimal) : null;
   }

   @Override
   public boolean isTargetStillValid(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.EntityTarget entityTarget) {
         return entityTarget.entity() instanceof Animal animal ? isBreedingCandidate(animal) : false;
      } else {
         return true;
      }
   }

   @Override
   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (!(target instanceof GatheringTarget.EntityTarget entityTarget)) {
         return true;
      } else if (entityTarget.entity() instanceof Animal animal) {
         if (!isBreedingCandidate(animal)) {
            return true;
         }

         List<Item> foodItems = this.getFoodItems(type);
         String buildingTag = this.getBuildingTag(type);
         Item usedFood = null;
         BuildingInstance foodSource = null;
         if (buildingTag != null) {
            for (BuildingInstance farm : this.findBuildingsWithTag(ctx.village(), buildingTag)) {
               BuildingInventory inv = farm.getInventory();
               if (inv != null) {
                  for (Item food : foodItems) {
                     if (inv.getCount(ctx.level(), food) >= 1) {
                        usedFood = food;
                        foodSource = farm;
                        break;
                     }
                  }

                  if (usedFood != null) {
                     break;
                  }
               }
            }
         }

         if (usedFood != null && foodSource != null) {
            if (!animal.isFood(new ItemStack(usedFood))) {
               return true;
            }

            foodSource.getInventory().remove(ctx.level(), usedFood, 1);
            animal.setInLove(null);
            LOGGER.debug("Breeding: animal {} put in love mode at {}", animal.getType().getDescriptionId(), animal.blockPosition());
            return true;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private boolean hasFoodInBuilding(BuildingInstance building, ServerLevel level, List<Item> foodItems) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return false;
      }

      for (Item food : foodItems) {
         if (inv.getCount(level, food) >= 1) {
            return true;
         }
      }

      return false;
   }

   private List<Item> getFoodItems(GatheringType type) {
      JsonObject params = type.handlerParams();
      List<Item> items = new ArrayList<>();
      if (!params.has("foodItems")) {
         return items;
      }

      for (JsonElement elem : params.getAsJsonArray("foodItems")) {
         items.addAll(ItemHelper.resolveAll(elem.getAsString()));
      }

      return items;
   }

   public static List<Animal> selectBreedable(List<? extends Animal> nearby, List<Item> foodItems) {
      List<Animal> result = new ArrayList<>();

      for (Animal animal : nearby) {
         if (isBreedingCandidate(animal)) {
            for (Item food : foodItems) {
               if (animal.isFood(new ItemStack(food))) {
                  result.add(animal);
                  break;
               }
            }
         }
      }

      return result;
   }

   private List<Animal> findAnimalsOfType(ServerLevel level, AABB box, EntityType<?> animalType) {
      List<Animal> result = new ArrayList<>();

      for (Entity e : level.getEntities((Entity)null, box, ex -> ex.getType() == animalType && ex instanceof Animal && ex.isAlive())) {
         result.add((Animal)e);
      }

      return result;
   }

   private List<Animal> findAnimalsAround(ServerLevel level, BuildingInstance farm, EntityType<?> animalType) {
      AABB searchBox = scanBoxAround(farm.getOrigin(), 15, 10);
      return this.findAnimalsOfType(level, searchBox, animalType);
   }

   private boolean canBreedAtFarm(List<Animal> nearby, BuildingInstance farm, List<Item> foodItems) {
      return canBreed(nearby, countAnimalSpawnPoints(farm), foodItems);
   }

   public static boolean canBreed(List<Animal> nearby, int spawnCount, List<Item> foodItems) {
      if (!hasBreedingQuorum(nearby)) {
         return false;
      } else {
         return nearby.size() >= spawnCount * 2 ? false : !selectBreedable(nearby, foodItems).isEmpty();
      }
   }

   @Nullable
   private EntityType<?> getAnimalType(GatheringType type) {
      String animalId = GsonHelper.getAsString(type.handlerParams(), "animalType", null);
      if (animalId == null) {
         return null;
      }

      Optional<EntityType<?>> opt = EntityType.byString(animalId);
      return opt.orElse(null);
   }
}
