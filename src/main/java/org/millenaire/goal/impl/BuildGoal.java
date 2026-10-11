package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.block.mock.MockBlock;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.PlacementStep;
import org.millenaire.building.placement.BlockPlacementEngine;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.BlockHazards;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.BuildingFinalizer;
import org.millenaire.village.SubBuildingHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public class BuildGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "build");
   private static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 1500;
   }

   @Override
   public boolean canStart(GoalContext context) {
      BuildingId savedId = context.villager().getConstructionBuildingId();
      if (savedId != null) {
         BuildingInstance saved = context.village().getBuilding(savedId);
         if (saved != null
            && saved.isBeingBuilt()
            && saved.getConstructionTask() != null
            && (!saved.getConstructionTask().isReserved() || context.villager().getUUID().equals(saved.getConstructionTask().getReservedBuilder()))) {
            return true;
         }

         context.villager().setConstructionBuildingId(null);
      }

      return context.village().findUnreservedConstruction() != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      BuildingInstance building = null;
      BuildingId savedId = context.villager().getConstructionBuildingId();
      if (savedId != null) {
         BuildingInstance saved = context.village().getBuilding(savedId);
         if (saved != null
            && saved.isBeingBuilt()
            && saved.getConstructionTask() != null
            && (!saved.getConstructionTask().isReserved() || context.villager().getUUID().equals(saved.getConstructionTask().getReservedBuilder()))) {
            building = saved;
         }
      }

      if (building == null) {
         building = context.village().findUnreservedConstruction();
      }

      if (building == null) {
         context.villager().setConstructionBuildingId(null);
         return new IdleGoal().start(context);
      }

      ConstructionTask task = building.getConstructionTask();
      if (task == null) {
         context.villager().setConstructionBuildingId(null);
         return new IdleGoal().start(context);
      }

      task.reserve(context.villager().getUUID());
      context.villager().setConstructionBuildingId(building.getId());
      if (task.hasPendingLeafClear()) {
         preClearFootprintLeaves(context, building);
         task.clearPendingLeafClear();
      }

      LOGGER.info(
         "Builder starting construction of {} — {} steps total, progress {}%",
         new Object[]{building.getPlanId(), task.totalSteps(), String.format("%.0f", task.progress() * 100.0F)}
      );
      return new BuildGoal.BuildTask(building, task);
   }

   private static void preClearFootprintLeaves(GoalContext context, BuildingInstance building) {
      BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
      if (plan != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet != null) {
            ClearMargins margins = planSet.clearMargins();
            BlockPos origin = building.getOrigin();
            int baseY = origin.getY() - plan.groundLevel();
            TerrainPreparer.clearLeavesInFootprint(
               context.level(), origin, plan.width(), plan.height(), plan.depth(), building.getRotation(), baseY, plan.groundLevel(), margins, 2
            );
         }
      }
   }

   static int computeConstructionDuration(float shovelEfficiency, boolean isSoftBlock) {
      int baseDuration;
      if (shovelEfficiency > 8.0F) {
         baseDuration = 7;
      } else if (shovelEfficiency == 8.0F) {
         baseDuration = 8;
      } else if (shovelEfficiency >= 6.0F) {
         baseDuration = 10;
      } else if (shovelEfficiency >= 4.0F) {
         baseDuration = 12;
      } else if (shovelEfficiency >= 2.0F) {
         baseDuration = 14;
      } else {
         baseDuration = 16;
      }

      return isSoftBlock ? (int)(baseDuration / 4.0F) : baseDuration;
   }

   static class BuildTask implements VillagerTask {
      private static final double WALK_SPEED = 0.5;
      private static final double ARRIVE_DISTANCE_SQ = 25.0;
      private static final double REMOTE_PLACE_DISTANCE_SQ = 2500.0;
      private static final int PLACING_STUCK_TIMEOUT = 400;
      private static final int WALKING_STUCK_TIMEOUT = 1200;
      private static final double WALKING_PROGRESS_EPSILON_SQ = 1.0;
      private static final int SUFFOCATION_GRACE_TICKS = 10;
      private static final int NAVIGATE_STUCK_THRESHOLD = 100;
      private static final double HORIZONTAL_APPROACH_DISTANCE_SQ = 16.0;
      private static final int ROOF_STRAND_HEIGHT = 3;
      private static final int UPCOMING_STEPS_EXCLUSION = 10;
      private static final int DODGE_LOOKAHEAD_STEPS = 8;
      private static final int[][] DODGE_OFFSETS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
      private final BuildingInstance building;
      private final ConstructionTask constructionTask;
      private BuildGoal.BuildTask.State state = BuildGoal.BuildTask.State.FETCHING_RESOURCES;
      private int actionCooldown;
      private int placingStuckTicks;
      private final WalkStuckTracker walkStuckTracker = new WalkStuckTracker(1200, 1.0);
      private int navigateStuckTicks;
      private int navigateStuckStepIndex = -1;
      private boolean lastNavWasHorizontalApproach;
      private boolean progressFlag;
      private float cachedShovelEfficiency = 2.0F;
      @Nullable
      private VillagerInventory villagerInventory;
      @Nullable
      private List<ItemStack> travellingItems;
      @Nullable
      private List<ItemStack> destinationItems;
      private static final double ARRIVE_AT_BUILDING_DIST = 5.0;
      private static final int FETCHING_TIMEOUT = 600;
      private int fetchingTicks;
      private static final double BLOCK_REACH_DISTANCE_SQ = 25.0;

      BuildTask(BuildingInstance building, ConstructionTask constructionTask) {
         this.building = building;
         this.constructionTask = constructionTask;
         if (constructionTask.getNextStepIndex() > 0) {
            this.state = BuildGoal.BuildTask.State.WALKING_TO_SITE;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return BuildGoal.ID;
      }

      @Override
      public void reportProgress() {
         this.progressFlag = true;
      }

      @Override
      public boolean consumeProgress() {
         if (this.progressFlag) {
            this.progressFlag = false;
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void tick(GoalContext ctx) {
         if (this.villagerInventory == null) {
            this.villagerInventory = ctx.villager().getInventory();
            this.travellingItems = List.of(this.getBestToolStack("toolsshovel", Items.WOODEN_SHOVEL));
            this.destinationItems = List.of(this.getBestToolStack("toolsshovel", Items.WOODEN_SHOVEL));
            this.cachedShovelEfficiency = computeShovelEfficiency(this.villagerInventory);
         }

         switch (this.state) {
            case FETCHING_RESOURCES:
               this.tickFetching(ctx);
               break;
            case WALKING_TO_SITE:
               this.tickWalking(ctx);
               break;
            case PLACING:
               this.tickPlacing(ctx);
            case DONE:
         }
      }

      @Override
      public boolean isFinished() {
         return this.state == BuildGoal.BuildTask.State.DONE;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            if (reason == StopReason.INTERRUPTED) {
               this.constructionTask.resetReservationAge();
            } else {
               this.constructionTask.releaseReservation();
               ctx.villager().setConstructionBuildingId(null);
            }

            if (reason == StopReason.IMPOSSIBLE) {
               this.constructionTask.incrementFailedAttempts();
               BuildGoal.LOGGER
                  .warn("Construction impossible for building {} — attempt failed ({}/3)", this.building.getPlanId(), this.constructionTask.getFailedAttempts());
            }
         }
      }

      @Override
      public TravelPhase getTravelPhase() {
         return this.state != BuildGoal.BuildTask.State.WALKING_TO_SITE && this.state != BuildGoal.BuildTask.State.FETCHING_RESOURCES
            ? TravelPhase.AT_DESTINATION
            : TravelPhase.TRAVELLING;
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         if (phase == TravelPhase.TRAVELLING) {
            return this.travellingItems != null ? this.travellingItems : List.of(new ItemStack(Items.WOODEN_SHOVEL));
         } else {
            return this.destinationItems != null ? this.destinationItems : List.of(new ItemStack(Items.WOODEN_SHOVEL));
         }
      }

      private ItemStack getBestToolStack(String categoryId, Item fallback) {
         if (this.villagerInventory == null) {
            return new ItemStack(fallback);
         }

         ToolCategory category = ToolCategoryRegistry.get(categoryId);
         if (category == null) {
            return new ItemStack(fallback);
         }

         ToolCategory.ToolEntry best = category.getBestOwned(item -> this.villagerInventory.getCount(item) > 0);
         return best != null && best.item() != null ? new ItemStack(best.item()) : new ItemStack(fallback);
      }

      @Override
      public List<ItemStack> getOffHandItems(TravelPhase phase) {
         if (phase != TravelPhase.AT_DESTINATION) {
            return List.of();
         } else {
            PlacementStep step = this.constructionTask.currentStep();
            if (step != null && !step.blockState().isAir()) {
               ItemStack blockItem = new ItemStack(step.blockState().getBlock().asItem());
               return blockItem.isEmpty() ? List.of() : List.of(blockItem);
            } else {
               return List.of();
            }
         }
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         if (this.state == BuildGoal.BuildTask.State.FETCHING_RESOURCES) {
            return Component.translatable("goal.millenaire.build.fetching");
         } else {
            return this.state == BuildGoal.BuildTask.State.WALKING_TO_SITE
               ? Component.translatable("goal.millenaire.build.travelling")
               : Component.translatable("goal.millenaire.build");
         }
      }

      private void tickFetching(GoalContext ctx) {
         if (this.hasRequiredResources(ctx)) {
            BuildGoal.LOGGER.debug("Builder already has required resources — skip FETCHING");
            this.transitionToWalking();
            this.reportProgress();
         } else {
            BuildingInstance townhall = ctx.village().getTownhall();
            if (townhall == null) {
               BuildGoal.LOGGER.warn("No TownHall found for builder — skip FETCHING phase");
               this.transitionToWalking();
            } else {
               this.fetchingTicks++;
               if (this.fetchingTicks > 600) {
                  BuildGoal.LOGGER.debug("Builder FETCHING timeout ({} ticks) — skip to WALKING", this.fetchingTicks);
                  ctx.villager().getNavManager().stop(ctx.villager());
                  this.transitionToWalking();
                  this.reportProgress();
               } else {
                  BlockPos thTarget = resolveNavTarget(townhall);
                  VillagerNavDriver nav = ctx.villager().getNavManager();
                  if (nav.getDestination() == null) {
                     nav.navigateTo(ctx.villager(), thTarget, 0.5);
                  }

                  if (nav.isArrivedHorizontal(ctx.villager(), 5.0)) {
                     this.fetchResourcesFromTownhall(ctx, townhall);
                     nav.stop(ctx.villager());
                     this.transitionToWalking();
                     this.reportProgress();
                  } else if (nav.isAbandoned()) {
                     BuildGoal.LOGGER.debug("Builder cannot reach TownHall — skip FETCHING phase");
                     nav.stop(ctx.villager());
                     this.transitionToWalking();
                     this.reportProgress();
                  }
               }
            }
         }
      }

      private void fetchResourcesFromTownhall(GoalContext ctx, BuildingInstance townhall) {
         Map<ResourceLocation, Integer> required = this.getRequiredResources();
         if (required.isEmpty()) {
            BuildGoal.LOGGER.debug("Construction {}: no required resources", this.building.getPlanId());
         } else {
            BuildingInventory thInventory = townhall.getInventory();
            if (thInventory == null) {
               BuildGoal.LOGGER.warn("TownHall {} has no inventory — resources not transferred", townhall.getPlanId());
            } else {
               Set<Item> specificLogs = AnywoodHelper.collectSpecificLogs(required);

               for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
                  if (!AnywoodHelper.isAnywood(entry.getKey())) {
                     Item item = ItemHelper.resolve(entry.getKey());
                     if (item != null) {
                        int needed = entry.getValue();
                        int taken = thInventory.remove(ctx.level(), item, needed);
                        if (taken > 0) {
                           ctx.villager().getInventory().add(item, taken);
                        }
                     }
                  }
               }

               Integer anywoodNeeded = required.get(AnywoodHelper.ANYWOOD_LOG);
               if (anywoodNeeded != null && anywoodNeeded > 0) {
                  int taken = thInventory.removeByTag(ctx.level(), AnywoodHelper.LOGS_TAG, anywoodNeeded, specificLogs);
                  if (taken > 0) {
                     ctx.villager().getInventory().add(Items.OAK_LOG, taken);
                  }
               }

               BuildGoal.LOGGER.info("Builder fetched resources from TH for construction of {}", this.building.getPlanId());
            }
         }
      }

      private void deductResourcesFromInventory(GoalContext ctx) {
         Map<ResourceLocation, Integer> required = this.getRequiredResources();
         if (!required.isEmpty()) {
            VillagerInventory inventory = ctx.villager().getInventory();

            for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
               if (!AnywoodHelper.isAnywood(entry.getKey())) {
                  Item item = ItemHelper.resolve(entry.getKey());
                  if (item != null) {
                     inventory.remove(item, entry.getValue());
                  }
               }
            }

            Integer anywoodNeeded = required.get(AnywoodHelper.ANYWOOD_LOG);
            if (anywoodNeeded != null && anywoodNeeded > 0) {
               inventory.remove(Items.OAK_LOG, anywoodNeeded);
            }
         }
      }

      private Map<ResourceLocation, Integer> getRequiredResources() {
         if (this.building.getPlanSetId() != null && this.building.getVariant() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(this.building.getPlanSetId());
            if (planSet == null) {
               return Map.of();
            }

            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(this.building.getVariant(), this.building.getLevel());
            return levelDef == null ? Map.of() : levelDef.requiredResources();
         } else {
            return Map.of();
         }
      }

      private boolean hasRequiredResources(GoalContext ctx) {
         Map<ResourceLocation, Integer> required = this.getRequiredResources();
         if (required.isEmpty()) {
            return true;
         }

         for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
            if (!AnywoodHelper.isAnywood(entry.getKey())) {
               Item item = ItemHelper.resolve(entry.getKey());
               if (item != null && !ctx.villager().getInventory().has(item, entry.getValue())) {
                  return false;
               }
            }
         }

         Integer anywoodNeeded = required.get(AnywoodHelper.ANYWOOD_LOG);
         return anywoodNeeded == null || anywoodNeeded <= 0 || ctx.villager().getInventory().hasByTag(AnywoodHelper.LOGS_TAG, anywoodNeeded);
      }

      private void transitionToWalking() {
         this.state = BuildGoal.BuildTask.State.WALKING_TO_SITE;
         this.walkStuckTracker.reset();
      }

      private void tickWalking(GoalContext ctx) {
         PlacementStep step = this.constructionTask.currentStep();
         if (step == null) {
            this.finishConstruction(ctx);
         } else {
            BlockPos siteTarget = resolveNavTarget(this.building);
            VillagerNavDriver nav = ctx.villager().getNavManager();
            if (nav.getDestination() == null || nav.getDestination().distSqr(siteTarget) > 4.0) {
               nav.navigateTo(ctx.villager(), siteTarget, 0.5);
            }

            if (nav.isArrivedHorizontal(ctx.villager(), Math.sqrt(25.0))) {
               nav.stop(ctx.villager());
               this.state = BuildGoal.BuildTask.State.PLACING;
               this.actionCooldown = 0;
            } else {
               BlockPos current = ctx.villager().blockPosition();
               double dx = current.getX() - siteTarget.getX();
               double dz = current.getZ() - siteTarget.getZ();
               if (this.walkStuckTracker.update(dx * dx + dz * dz)) {
                  this.teleportToSafeNear(ctx, siteTarget);
                  BuildGoal.LOGGER.warn("Builder rescue-teleported to site {} (no walking progress for {} ticks)", this.building.getPlanId(), 1200);
               }

               if (nav.isAbandoned()) {
                  nav.navigateTo(ctx.villager(), siteTarget, 0.5);
                  this.reportProgress();
               }

               this.reportProgress();
            }
         }
      }

      private void tickPlacing(GoalContext ctx) {
         PlacementStep step = this.constructionTask.currentStep();
         if (step == null) {
            this.finishConstruction(ctx);
         } else if (step.blockState().getBlock() instanceof MockBlock) {
            this.advanceStep(ctx);
         } else {
            BlockPos absolutePos = this.building.getOrigin().offset(step.relativePos());
            ServerLevel level = ctx.level();
            BlockState stateToPlace = BuildingPlacer.applyFacingGuess(
               level, absolutePos, step.blockState(), step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
            );
            stateToPlace = BlockPlacementEngine.hydrateIfFarmland(stateToPlace);
            stateToPlace = BlockPlacementEngine.stabilizePathBlock(stateToPlace);
            stateToPlace = BuildingPlacer.computePaneConnections(level, absolutePos, stateToPlace);
            if (BlockPlacementEngine.isBlockAlreadySuitable(level.getBlockState(absolutePos), stateToPlace)) {
               this.advanceStep(ctx);
            } else {
               BlockPos currentPos = ctx.villager().blockPosition();
               this.placingStuckTicks++;
               if (this.placingStuckTicks > 400) {
                  BlockPos navTarget = resolveNavTarget(this.building);
                  this.teleportToSafeNear(ctx, navTarget);
                  this.placingStuckTicks = 0;
                  this.reportProgress();
                  BuildGoal.LOGGER.debug("Builder TP chantier {} (bloqué PLACING {}+ ticks)", this.building.getPlanId(), 400);
               }

               double distToBlockSq = currentPos.distSqr(absolutePos);
               int currentStepIdx = this.constructionTask.getNextStepIndex();
               if (currentStepIdx != this.navigateStuckStepIndex) {
                  this.navigateStuckTicks = 0;
                  this.navigateStuckStepIndex = currentStepIdx;
               }

               if (distToBlockSq > 25.0) {
                  boolean accessible = this.isBlockAccessible(ctx, absolutePos);
                  boolean withinRemoteRange = distToBlockSq <= 2500.0;
                  if (!accessible && withinRemoteRange) {
                     int dx = currentPos.getX() - absolutePos.getX();
                     int dz = currentPos.getZ() - absolutePos.getZ();
                     double horizontalDistSq = dx * dx + dz * dz;
                     if (horizontalDistSq > 16.0) {
                        if (!this.lastNavWasHorizontalApproach) {
                           this.navigateStuckTicks = 0;
                           this.lastNavWasHorizontalApproach = true;
                        }

                        this.navigateStuckTicks++;
                        if (this.navigateStuckTicks > 100) {
                           BuildGoal.LOGGER
                              .debug(
                                 "Builder places remotely (horizontal stuck {} ticks) step {} at {}",
                                 new Object[]{this.navigateStuckTicks, currentStepIdx, absolutePos.toShortString()}
                              );
                           this.remotePlaceBlock(ctx, step, absolutePos);
                        } else {
                           BlockPos approachPos = new BlockPos(absolutePos.getX(), currentPos.getY(), absolutePos.getZ());
                           this.navigateToBlock(ctx, approachPos);
                        }
                     } else {
                        this.actionCooldown++;
                        if (this.actionCooldown >= this.getActionDuration(step)) {
                           this.actionCooldown = 0;
                           this.remotePlaceBlock(ctx, step, absolutePos);
                        }
                     }
                  } else {
                     if (this.lastNavWasHorizontalApproach) {
                        this.navigateStuckTicks = 0;
                        this.lastNavWasHorizontalApproach = false;
                     }

                     this.navigateStuckTicks++;
                     if (this.navigateStuckTicks > 100) {
                        BuildGoal.LOGGER
                           .debug(
                              "Builder places remotely (nav stuck {} ticks) step {} at {}",
                              new Object[]{this.navigateStuckTicks, currentStepIdx, absolutePos.toShortString()}
                           );
                        this.remotePlaceBlock(ctx, step, absolutePos);
                     } else {
                        this.navigateToBlock(ctx, absolutePos);
                     }
                  }
               } else {
                  this.actionCooldown++;
                  if (this.actionCooldown >= this.getActionDuration(step)) {
                     this.actionCooldown = 0;
                     this.dodgeIfNeeded(ctx, absolutePos, stateToPlace);
                     this.displaceBystandersAt(ctx, absolutePos, stateToPlace);
                     ctx.villager().getLookControl().setLookAt(absolutePos.getX() + 0.5, absolutePos.getY() + 0.5, absolutePos.getZ() + 0.5);
                     BlockState oldState = level.getBlockState(absolutePos);
                     if (!oldState.isAir()) {
                        SoundType oldSound = oldState.getSoundType(level, absolutePos, null);
                        level.playSound(null, absolutePos, oldSound.getBreakSound(), SoundSource.BLOCKS, oldSound.getVolume() * 0.5F, oldSound.getPitch());
                     }

                     BlockPlacementEngine.clearBedIfPresent(level, absolutePos);
                     BlockPlacementEngine.clearDoorIfPresent(level, absolutePos);
                     level.setBlock(absolutePos, stateToPlace, BlockPlacementEngine.getPlacementFlags(stateToPlace));
                     BlockPlacementEngine.fixWaterlogging(level, absolutePos, step.blockState());
                     if (stateToPlace.getBlock() instanceof ChestBlock) {
                        BlockPlacementEngine.fixAdjacentChestType(level, absolutePos, stateToPlace);
                     }

                     BlockPos bedFoot = BlockPlacementEngine.generateBedFoot(level, absolutePos, stateToPlace);
                     if (bedFoot != null && this.building != null) {
                        this.building.getBedManager().add(bedFoot);
                     }

                     BuildingPlacer.refreshNeighborConnections(level, absolutePos);
                     BlockPlacementEngine.generateDoorUpper(level, absolutePos, stateToPlace);
                     SoundType soundType = step.blockState().getSoundType(level, absolutePos, null);
                     level.playSound(
                        null,
                        absolutePos,
                        soundType.getPlaceSound(),
                        SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F * 0.6F,
                        soundType.getPitch() * 0.8F
                     );
                     ctx.villager().swing(InteractionHand.MAIN_HAND);
                     ctx.villager().grantSuffocationGrace(10);
                     this.advanceStep(ctx);
                  }
               }
            }
         }
      }

      private void advanceStep(GoalContext ctx) {
         this.constructionTask.advance();
         this.placingStuckTicks = 0;
         this.navigateStuckTicks = 0;
         this.reportProgress();
         VillageSavedData.get(ctx.level()).setDirty();
         ctx.villager().getNavigation().stop();
         if (this.constructionTask.isComplete()) {
            this.finishConstruction(ctx);
         }
      }

      private void navigateToBlock(GoalContext ctx, BlockPos targetPos) {
         if (ctx.villager().getNavigation().isDone()) {
            ctx.villager().getNavigation().moveTo(targetPos.getX() + 0.5, ctx.villager().getY(), targetPos.getZ() + 0.5, 0.5);
         }

         ctx.villager().getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
      }

      private void remotePlaceBlock(GoalContext ctx, PlacementStep step, BlockPos absolutePos) {
         ServerLevel level = ctx.level();
         BlockState stateToPlace = BuildingPlacer.applyFacingGuess(
            level, absolutePos, step.blockState(), step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
         );
         stateToPlace = BlockPlacementEngine.hydrateIfFarmland(stateToPlace);
         stateToPlace = BlockPlacementEngine.stabilizePathBlock(stateToPlace);
         stateToPlace = BuildingPlacer.computePaneConnections(level, absolutePos, stateToPlace);
         BlockState oldState = level.getBlockState(absolutePos);
         if (!BlockPlacementEngine.isBlockAlreadySuitable(oldState, stateToPlace)) {
            this.displaceBystandersAt(ctx, absolutePos, stateToPlace);
            BlockPlacementEngine.clearBedIfPresent(level, absolutePos);
            BlockPlacementEngine.clearDoorIfPresent(level, absolutePos);
            if (!oldState.isAir()) {
               SoundType oldSound = oldState.getSoundType(level, absolutePos, null);
               level.playSound(null, absolutePos, oldSound.getBreakSound(), SoundSource.BLOCKS, oldSound.getVolume() * 0.5F, oldSound.getPitch());
            }

            ctx.villager().getLookControl().setLookAt(absolutePos.getX() + 0.5, absolutePos.getY() + 0.5, absolutePos.getZ() + 0.5);
            ctx.villager().swing(InteractionHand.MAIN_HAND);
            level.setBlock(absolutePos, stateToPlace, BlockPlacementEngine.getPlacementFlags(stateToPlace));
            BlockPlacementEngine.fixWaterlogging(level, absolutePos, step.blockState());
            if (stateToPlace.getBlock() instanceof ChestBlock) {
               BlockPlacementEngine.fixAdjacentChestType(level, absolutePos, stateToPlace);
            }

            BlockPos bedFoot = BlockPlacementEngine.generateBedFoot(level, absolutePos, stateToPlace);
            if (bedFoot != null && this.building != null) {
               this.building.getBedManager().add(bedFoot);
            }

            BlockPlacementEngine.generateDoorUpper(level, absolutePos, stateToPlace);
            SoundType soundType = step.blockState().getSoundType(level, absolutePos, null);
            level.playSound(
               null, absolutePos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F * 0.4F, soundType.getPitch() * 0.8F
            );
         }

         this.advanceStep(ctx);
      }

      private boolean isBlockAccessible(GoalContext ctx, BlockPos targetPos) {
         int builderY = ctx.villager().blockPosition().getY();
         int blockY = targetPos.getY();
         if (Math.abs(blockY - builderY) > 3) {
            return false;
         }

         BlockPos below = targetPos.below();
         BlockState belowState = ctx.level().getBlockState(below);
         return belowState.canOcclude();
      }

      private void dodgeIfNeeded(GoalContext ctx, BlockPos absolutePos, BlockState stateToPlace) {
         if (stateToPlace.isSuffocating(ctx.level(), absolutePos)) {
            BlockPos feetPos = ctx.villager().blockPosition();
            double hDist = Math.max(Math.abs(absolutePos.getX() - feetPos.getX()), Math.abs(absolutePos.getZ() - feetPos.getZ()));
            if (!(hDist >= 1.0)) {
               int blockY = absolutePos.getY();
               int feetY = feetPos.getY();
               if (blockY >= feetY && blockY <= feetY + 1) {
                  ServerLevel level = ctx.level();
                  List<BlockPos> upcoming = this.constructionTask.upcomingAbsolutePositions(this.building.getOrigin(), 8);
                  int[] best = null;
                  double bestStandY = 0.0;
                  double bestScore = Double.NEGATIVE_INFINITY;

                  for (int[] off : DODGE_OFFSETS) {
                     int nx = feetPos.getX() + off[0];
                     int nz = feetPos.getZ() + off[1];
                     BlockPos candidateFeet = new BlockPos(nx, feetY, nz);
                     BlockPos candidateHead = candidateFeet.above();
                     BlockPos belowPos = candidateFeet.below();
                     BlockState candidateState = level.getBlockState(candidateFeet);
                     if (!level.getBlockState(belowPos).getCollisionShape(level, belowPos).isEmpty()
                        && !candidateState.isSuffocating(level, candidateFeet)
                        && !level.getBlockState(candidateHead).isSuffocating(level, candidateHead)
                        && !BlockHazards.isHazardousAt(level, candidateFeet)) {
                        double score = this.upcomingClearance(upcoming, candidateFeet, candidateHead);
                        if (off[0] != 0 && off[1] != 0) {
                           score -= 0.5;
                        }

                        if (!(score <= bestScore)) {
                           bestScore = score;
                           bestStandY = feetY + NavigationHelperUtils.collisionTopOffset(level, candidateFeet, candidateState);
                           best = off;
                        }
                     }
                  }

                  if (best != null) {
                     int nx = feetPos.getX() + best[0];
                     int nz = feetPos.getZ() + best[1];
                     ctx.villager().teleportTo(nx + 0.5, bestStandY, nz + 0.5);
                     BuildGoal.LOGGER
                        .debug(
                           "Builder dodges to ({}, {}, {}) before placing a block at {} (clearance {})",
                           new Object[]{nx, bestStandY, nz, absolutePos.toShortString(), bestScore}
                        );
                  } else {
                     BuildGoal.LOGGER.warn("Builder cannot dodge the block at {} — suffocation risk", absolutePos.toShortString());
                  }
               }
            }
         }
      }

      private double upcomingClearance(List<BlockPos> upcoming, BlockPos feet, BlockPos head) {
         if (upcoming.isEmpty()) {
            return 8.0;
         }

         double nearest = 8.0;

         for (BlockPos next : upcoming) {
            if (next.equals(feet) || next.equals(head)) {
               return 0.0;
            }

            nearest = Math.min(nearest, Math.sqrt(feet.distSqr(next)));
         }

         return nearest;
      }

      private void displaceBystandersAt(GoalContext ctx, BlockPos absolutePos, BlockState stateToPlace) {
         ServerLevel level = ctx.level();
         if (stateToPlace.isSuffocating(level, absolutePos)) {
            AABB area = new AABB(
               absolutePos.getX(), absolutePos.getY(), absolutePos.getZ(), absolutePos.getX() + 1, absolutePos.getY() + 2, absolutePos.getZ() + 1
            );
            List<MillVillager> bystanders = level.getEntitiesOfClass(MillVillager.class, area, e -> e != ctx.villager());
            if (!bystanders.isEmpty()) {
               int[][] offsets = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

               for (MillVillager bystander : bystanders) {
                  BlockPos feetPos = bystander.blockPosition();
                  boolean displaced = false;

                  for (int[] off : offsets) {
                     BlockPos candidate = new BlockPos(feetPos.getX() + off[0], feetPos.getY(), feetPos.getZ() + off[1]);
                     BlockPos below = candidate.below();
                     BlockState candidateState = level.getBlockState(candidate);
                     if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                        && !candidateState.isSuffocating(level, candidate)
                        && !level.getBlockState(candidate.above()).isSuffocating(level, candidate.above())) {
                        double standY = candidate.getY() + NavigationHelperUtils.collisionTopOffset(level, candidate, candidateState);
                        bystander.teleportTo(candidate.getX() + 0.5, standY, candidate.getZ() + 0.5);
                        displaced = true;
                        break;
                     }
                  }

                  if (!displaced) {
                     int safeY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, feetPos.getX(), feetPos.getZ());
                     bystander.teleportTo(feetPos.getX() + 0.5, safeY, feetPos.getZ() + 0.5);
                  }
               }
            }
         }
      }

      private void teleportToSafeNear(GoalContext ctx, BlockPos target) {
         NavigationHelperUtils.teleportToSafeNearTarget(ctx.villager(), target, buildExcludedTilesPredicate(this.building));
      }

      private static Predicate<BlockPos> buildExcludedTilesPredicate(BuildingInstance site) {
         ConstructionTask task = site.getConstructionTask();
         if (task == null) {
            return p -> false;
         }

         List<BlockPos> upcoming = task.upcomingAbsolutePositions(site.getOrigin(), 10);
         if (upcoming.isEmpty()) {
            return p -> false;
         }

         Set<BlockPos> excluded = new HashSet<>(upcoming);
         return excluded::contains;
      }

      private int getActionDuration(PlacementStep step) {
         BlockState blockState = step.blockState();
         boolean isSoft = blockState.isAir() || blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND);
         return BuildGoal.computeConstructionDuration(this.cachedShovelEfficiency, isSoft);
      }

      private static float computeShovelEfficiency(VillagerInventory inventory) {
         ToolCategory category = ToolCategoryRegistry.get("toolsshovel");
         return category == null
            ? 2.0F
            : category.getBestDestroySpeed(item -> inventory.getCount(item) > 0, Blocks.DIRT.defaultBlockState(), Items.WOODEN_SHOVEL);
      }

      private void finishConstruction(GoalContext ctx) {
         ServerLevel level = (ServerLevel)ctx.villager().level();
         Village village = ctx.village();
         this.building.markComplete();
         this.deductResourcesFromInventory(ctx);
         BuildingPlan plan = ModCultures.getBuildingPlan(this.building.getPlanId());
         if (plan != null) {
            BuildingFinalizer.applyPostPlacement(level, village, this.building, plan);
            village.recordEvent(level, "Construction terminée : " + this.building.getPlanId().getPath() + " à " + this.building.getOrigin().toShortString());
            String chronicleName = this.building.getPlanId().getPath();
            if (this.building.getPlanSetId() != null) {
               BuildingPlanSet ps = ModCultures.getBuildingPlanSet(this.building.getPlanSetId());
               if (ps != null) {
                  BuildingPlanSet.LevelDef ld = ps.getLevel(this.building.getVariant(), this.building.getLevel());
                  if (ld != null && ld.nativeName() != null) {
                     chronicleName = ld.nativeName();
                  } else {
                     chronicleName = ps.nativeName();
                  }
               }
            }

            village.recordChronicleEvent(level, VillageEventType.BUILDING_COMPLETED, chronicleName, null);
            org.millenaire.religion.ReligionLifecycle.onReligiousBuildingCompleted(level, village, this.building);
            BuildGoal.LOGGER.info("Building {} completed — {} special points resolved", this.building.getPlanId(), this.building.getResolvedPoints().size());
         }

         if (village.getOwnerUUID() != null && this.building.isPlayerChosen()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(village.getOwnerUUID());
            if (owner != null) {
               MillAdvancements.grant(owner, MillAdvancements.CONTROLLED_BUILDER);
            }
         }

         if (this.building.getLevel() > 0 && this.building.getPlanSetId() != null && this.building.getVariant() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(this.building.getPlanSetId());
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(this.building.getVariant(), this.building.getLevel());
               if (levelDef != null) {
                  SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, levelDef, this.building, false);
               }
            }
         }

         BuildingFinalizer.applyCompletionEffects(level, village, this.building);
         BuildingPlacer.spawnWallDecorations(level, this.building.getResolvedPoints());
         BuildingFinalizer.applyVillageUpdates(level, village, this.building);
         if (this.building.getPlanSetId() != null && this.building.getVariant() != null) {
            BuildingPlanSet ps = ModCultures.getBuildingPlanSet(this.building.getPlanSetId());
            if (ps != null) {
               BuildingPlanSet.LevelDef currentLevelDef = ps.getLevel(this.building.getVariant(), this.building.getLevel());
               if (currentLevelDef != null) {
                  boolean shouldRecalculate = this.building.getLevel() == 0 || currentLevelDef.rebuildPath();
                  if (!shouldRecalculate && this.building.getLevel() > 0) {
                     BuildingPlanSet.LevelDef prevLevelDef = ps.getLevel(this.building.getVariant(), this.building.getLevel() - 1);
                     if (prevLevelDef != null && prevLevelDef.pathLevel() != currentLevelDef.pathLevel()) {
                        shouldRecalculate = true;
                     }
                  }

                  if (shouldRecalculate) {
                     ctx.village().getPathManager().markPathsDirty();
                     ctx.village().markDirty();
                  }
               }
            }
         }

         this.descendIfStrandedOnRoof(ctx);
         this.state = BuildGoal.BuildTask.State.DONE;
      }

      private void descendIfStrandedOnRoof(GoalContext ctx) {
         BlockPos navTarget = resolveNavTarget(this.building);
         if (navTarget != null) {
            int currentY = ctx.villager().blockPosition().getY();
            if (currentY - navTarget.getY() >= 3) {
               this.teleportToSafeNear(ctx, navTarget);
               BuildGoal.LOGGER
                  .debug(
                     "Builder TP down from finished structure {} (Y {} -> path-start {})",
                     new Object[]{this.building.getPlanId(), currentY, navTarget.toShortString()}
                  );
            }
         }
      }

      private static BlockPos resolveNavTarget(BuildingInstance building) {
         return building.getPathStartPos();
      }

      @Override
      public Map<String, String> getNavDebugInfo() {
         Map<String, String> info = new LinkedHashMap<>();
         info.put("buildState", this.state.name());
         info.put(
            "step",
            this.constructionTask.getNextStepIndex()
               + "/"
               + this.constructionTask.totalSteps()
               + " ("
               + Math.round(this.constructionTask.progress() * 100.0F)
               + "%)"
         );
         info.put("building", this.building.getPlanId().getPath() + " @ " + this.building.getOrigin().toShortString());
         info.put("placingStuckTicks", String.valueOf(this.placingStuckTicks));
         info.put("navigateStuckTicks", this.navigateStuckTicks + " (step " + this.navigateStuckStepIndex + ")");
         return info;
      }

      enum State {
         FETCHING_RESOURCES,
         WALKING_TO_SITE,
         PLACING,
         DONE;
      }
   }
}
