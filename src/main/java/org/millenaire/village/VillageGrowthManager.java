package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.DisplayUtils;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.building.PlacementStep;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.item.ItemHelper;
import org.millenaire.world.BuildingLocationFinder;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.PlacedLocation;
import org.millenaire.world.PlacementConstraints;
import org.millenaire.world.TerrainPreparer;
import org.millenaire.world.TerrainReachability;
import org.millenaire.world.VillageTerrainMap;
import org.slf4j.Logger;

public final class VillageGrowthManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final int GROWTH_TICK_INTERVAL = 20;
   private static final long NO_PROJECTS_CACHE_TICKS = 600L;
   private static final long PLACEMENT_FAILURE_COOLDOWN_TICKS = 1600L;
   private static final Map<String, Integer> TIER_MULTIPLIERS = Map.of(
      "centre", 6, "townhall", 6, "start", 6, "player", 6, "core", 4, "secondary", 2, "extra", 1
   );
   /** Player layout slots stay player-driven; core/secondary may grow organically when the queue is idle. */
   private static final Set<String> CONTROLLED_AUTO_EXCLUDED_ROLES = Set.of("player");
   private static final List<String> TIER_ORDER = List.of("start", "player", "core", "secondary", "extra");

   private VillageGrowthManager() {
   }

   public static void evaluateGrowth(ServerLevel level, Village village) {
      long currentTick = level.getGameTime();
      if (currentTick % 600L == 0L) {
         LOGGER.debug("[Growth] {} — evaluateGrowth ENTRY, tick={}, typeId={}", new Object[]{village.getVillageName(), currentTick, village.getVillageTypeId()});
      }

      if (currentTick < village.getNoProjectsLeftUntil()) {
         if (currentTick % 600L == 0L) {
            LOGGER.debug(
               "[Growth] {} — cached no-projects until tick {} (now={})", new Object[]{village.getVillageName(), village.getNoProjectsLeftUntil(), currentTick}
            );
         }
      } else {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType == null) {
            LOGGER.warn("[Growth] {} — villageType is NULL for typeId={}", village.getVillageName(), village.getVillageTypeId());
         } else if (villageType.loneBuilding()) {
            LOGGER.debug("[Growth] {} — loneBuilding, delegating to evaluateLoneBuildingUpgrades", village.getVillageName());
            evaluateLoneBuildingUpgrades(level, village, villageType);
         } else if (!canStartNewConstruction(village, villageType)) {
            LOGGER.debug("[Growth] {} — max constructions reached", village.getVillageName());
         } else if (tryLaunchControlledQueueHead(level, village)) {
            LOGGER.debug("[Growth] {} — controlled queue head launched", village.getVillageName());
         } else if (tryLaunchPendingProject(level, village)) {
            LOGGER.debug("[Growth] {} — pending project launched", village.getVillageName());
         } else if (tryLaunchPlannedSubBuilding(level, village)) {
            LOGGER.debug("[Growth] {} — planned sub-building launched", village.getVillageName());
         } else if (village.getPendingProject() != null) {
            Village.PendingProject pp = village.getPendingProject();
            LOGGER.debug("[Growth] {} — pending {} lv{} waiting for resources", new Object[]{village.getVillageName(), pp.planSetId(), pp.level()});
            tryAffordableFallback(level, village, villageType, null);
         } else {
            List<VillageGrowthManager.GrowthCandidate> candidates = getAllCandidates(level, village, villageType);
            if (candidates.isEmpty()) {
               LOGGER.debug("[Growth] {} — no candidates, caching {}s", village.getVillageName(), 30L);
               village.setNoProjectsLeftUntil(currentTick + 600L);
            } else {
               List<Integer> weights = candidates.stream().map(VillageGrowthManager.GrowthCandidate::weight).toList();
               VillageGrowthManager.GrowthCandidate chosen = weightedPick(candidates, weights, ThreadLocalRandom.current());
               if (chosen == null) {
                  village.setNoProjectsLeftUntil(currentTick + 600L);
               } else {
                  LOGGER.debug(
                     "[Growth] {} — chosen: {} lv{} (upgrade={}), checking resources...",
                     new Object[]{village.getVillageName(), chosen.planSet().id(), chosen.levelDef().level(), chosen.isUpgrade()}
                  );
                  if (hasResourcesForProject(level, village, chosen.levelDef())) {
                     LOGGER.debug("[Growth] {} — resources OK, launching {}", village.getVillageName(), chosen.planSet().id());
                     launchCandidate(level, village, chosen, true);
                  } else {
                     LOGGER.debug("[Growth] {} — resources INSUFFICIENT for {}, storing as pending", village.getVillageName(), chosen.planSet().id());
                     storePending(village, chosen);
                     tryAffordableFallback(level, village, villageType, chosen);
                  }
               }
            }
         }
      }
   }

   private static void tryAffordableFallback(
      ServerLevel level, Village village, VillageType villageType, @Nullable VillageGrowthManager.GrowthCandidate exclude
   ) {
      List<VillageGrowthManager.GrowthCandidate> candidates = getAllCandidates(level, village, villageType, true);
      if (!candidates.isEmpty()) {
         List<VillageGrowthManager.GrowthCandidate> affordable = new ArrayList<>();
         List<Integer> fallbackWeights = new ArrayList<>();
         Village.PendingProject pending = village.getPendingProject();

         for (VillageGrowthManager.GrowthCandidate c : candidates) {
            if (c != exclude && (pending == null || !isPendingMatch(c, pending)) && hasResources(level, village, c.levelDef())) {
               affordable.add(c);
               fallbackWeights.add(c.weight());
            }
         }

         if (!affordable.isEmpty()) {
            VillageGrowthManager.GrowthCandidate fallback = weightedPick(affordable, fallbackWeights, ThreadLocalRandom.current());
            if (fallback != null) {
               LOGGER.debug("[Millenaire] Growth: fallback — {} (pending: {})", fallback.planSet().id(), pending != null ? pending.planSetId() : "none");
               launchCandidate(level, village, fallback, false);
            }
         }
      }
   }

   private static boolean isPendingMatch(VillageGrowthManager.GrowthCandidate c, Village.PendingProject pending) {
      if (!c.planSet().id().equals(pending.planSetId())) {
         return false;
      } else {
         return !c.variant().equals(pending.variant()) ? false : c.levelDef().level() == pending.level();
      }
   }

   public static void onBuildingCompleted(Village village) {
      village.setNoProjectsLeftUntil(0L);
   }

   private static void evaluateLoneBuildingUpgrades(ServerLevel level, Village village, VillageType villageType) {
      if (canStartNewConstruction(village, villageType)) {
         if (!tryLaunchPendingProject(level, village)) {
            if (village.getPendingProject() == null) {
               long currentTick = level.getGameTime();
               List<VillageGrowthManager.GrowthCandidate> candidates = new ArrayList<>();

               for (BuildingInstance b : village.getBuildings()) {
                  if (b.getStatus() == BuildingInstance.Status.COMPLETE && b.isUpgradesAllowed() && b.getPlanSetId() != null && b.getVariant() != null) {
                     BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
                     if (planSet != null && planSet.hasNextLevel(b.getVariant(), b.getLevel())) {
                        BuildingPlanSet.LevelDef nextLevel = planSet.getLevel(b.getVariant(), b.getLevel() + 1);
                        if (nextLevel != null && checkBuildConditions(village, b, nextLevel)) {
                           int weight = Math.max(1, nextLevel.priority());
                           candidates.add(new VillageGrowthManager.GrowthCandidate(planSet, nextLevel, b.getVariant(), weight, true, b, null));
                        }
                     }
                  }
               }

               if (candidates.isEmpty()) {
                  village.setNoProjectsLeftUntil(currentTick + 600L);
               } else {
                  int totalWeight = 0;

                  for (VillageGrowthManager.GrowthCandidate c : candidates) {
                     totalWeight += c.weight();
                  }

                  VillageGrowthManager.GrowthCandidate chosen = candidates.get(0);
                  if (totalWeight > 0) {
                     int roll = ThreadLocalRandom.current().nextInt(totalWeight);

                     for (VillageGrowthManager.GrowthCandidate c : candidates) {
                        roll -= c.weight();
                        if (roll < 0) {
                           chosen = c;
                           break;
                        }
                     }
                  }

                  if (hasResourcesForProject(level, village, chosen.levelDef())) {
                     launchCandidate(level, village, chosen, true);
                  } else {
                     storePending(village, chosen);
                  }
               }
            }
         }
      }
   }

   private static List<VillageGrowthManager.GrowthCandidate> getAllCandidates(ServerLevel level, Village village, VillageType villageType) {
      return getAllCandidates(level, village, villageType, false, false);
   }

   private static List<VillageGrowthManager.GrowthCandidate> getAllCandidates(
      ServerLevel level, Village village, VillageType villageType, boolean skipTierGating
   ) {
      return getAllCandidates(level, village, villageType, skipTierGating, false);
   }

   private static List<VillageGrowthManager.GrowthCandidate> getAllCandidates(
      ServerLevel level, Village village, VillageType villageType, boolean skipTierGating, boolean skipPlacementCooldown
   ) {
      long currentTick = level.getGameTime();
      List<VillageGrowthManager.GrowthCandidate> candidates = new ArrayList<>();
      String allowedTier = skipTierGating ? null : findLowestUnsatisfiedTier(village, villageType, currentTick);
      if (!"player".equals(allowedTier)) {
         for (VillageType.LayoutSlot slot : villageType.layout()) {
            if (!isCentreRole(slot.role())
               && !isAutoExcludedInControlledVillage(villageType.playerControlled(), slot.role())
               && (skipTierGating || (allowedTier != null ? allowedTier.equals(slot.role()) : !TIER_ORDER.contains(slot.role())))
               && slotIsBuildable(village, villageType, slot, currentTick, skipPlacementCooldown)) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
               String variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
               if (levelDef != null && checkNewBuildingConditions(village, levelDef)) {
                  int weight = computeLayoutSlotWeight(slot, levelDef);
                  candidates.add(new VillageGrowthManager.GrowthCandidate(planSet, levelDef, variant, weight, false, null, slot));
               }
            }
         }
      }

      if ("player".equals(allowedTier) || allowedTier == null || skipTierGating) {
         for (ResourceLocation playerPlanId : villageType.playerBuildings()) {
            if (!isPlayerBuildingCandidate(village, playerPlanId)) {
               if (village.isBuildingBought(playerPlanId)) {
                  LOGGER.debug(
                     "[Growth] Player building {} bought but NOT candidate (already built: {})", playerPlanId, countBuildingsOfType(village, playerPlanId)
                  );
               }
            } else {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(playerPlanId);
               if (planSet != null) {
                  if (!skipPlacementCooldown && isOnPlacementCooldown(village, planSet.id(), currentTick)) {
                     LOGGER.debug("[Growth] Player building {} on placement cooldown", playerPlanId);
                  } else {
                     String variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
                     BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
                     if (levelDef != null) {
                        int tierMultiplier = TIER_MULTIPLIERS.getOrDefault("player", 6);
                        int weight = Math.max(1, levelDef.priority() * tierMultiplier);
                        candidates.add(new VillageGrowthManager.GrowthCandidate(planSet, levelDef, variant, weight, false, null, null));
                     }
                  }
               }
            }
         }
      }

      for (BuildingInstance b : village.getBuildings()) {
         if (b.getStatus() == BuildingInstance.Status.COMPLETE
            && !b.isWallSegment()
            && b.isUpgradesAllowed()
            && b.getPlanSetId() != null
            && b.getVariant() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
            if (planSet != null && planSet.hasNextLevel(b.getVariant(), b.getLevel())) {
               BuildingPlanSet.LevelDef nextLevel = planSet.getLevel(b.getVariant(), b.getLevel() + 1);
               if (nextLevel != null && checkBuildConditions(village, b, nextLevel)) {
                  int weight = Math.max(1, nextLevel.priority());
                  candidates.add(new VillageGrowthManager.GrowthCandidate(planSet, nextLevel, b.getVariant(), weight, true, b, null));
               }
            }
         }
      }

      return candidates;
   }

   private static void launchCandidate(ServerLevel level, Village village, VillageGrowthManager.GrowthCandidate candidate, boolean clearPending) {
      if (clearPending) {
         village.setPendingProject(null);
      }

      if (candidate.isUpgrade()) {
         launchUpgrade(level, village, candidate);
      } else {
         launchNewBuilding(level, village, candidate);
      }
   }

   private static void storePending(Village village, VillageGrowthManager.GrowthCandidate candidate) {
      if (candidate.isUpgrade() && candidate.existingBuilding() != null) {
         village.setPendingProject(
            new Village.PendingProject(candidate.planSet().id(), candidate.variant(), candidate.levelDef().level(), true, candidate.existingBuilding().getId())
         );
      } else {
         village.setPendingProject(new Village.PendingProject(candidate.planSet().id(), candidate.variant(), candidate.levelDef().level(), false, null));
      }
   }

   private static void launchNewBuilding(ServerLevel level, Village village, VillageGrowthManager.GrowthCandidate candidate) {
      placeNewBuildingForConstruction(level, village, candidate.planSet(), candidate.levelDef(), candidate.variant(), candidate.slot());
   }

   private static boolean placeNewBuildingForConstruction(
      ServerLevel level, Village village, BuildingPlanSet planSet, BuildingPlanSet.LevelDef levelDef, String variant, @Nullable VillageType.LayoutSlot slot
   ) {
      return placeNewBuildingForConstruction(level, village, planSet, levelDef, variant, slot, null, false);
   }

   private static boolean placeNewBuildingForConstruction(
      ServerLevel level,
      Village village,
      BuildingPlanSet planSet,
      BuildingPlanSet.LevelDef levelDef,
      String variant,
      @Nullable VillageType.LayoutSlot slot,
      @Nullable PlacedLocation preResolvedLocation,
      boolean playerChosen
   ) {
      BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
      if (plan == null) {
         return false;
      }

      long currentTick = level.getGameTime();
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 90;
      PlacementConstraints constraints = PlacementConstraints.resolve(planSet, slot, radius);
      ClearMargins margins = planSet.clearMargins().atLeast(constraints.clearMargin());
      VillageTerrainMap terrainMap;
      PlacedLocation location = preResolvedLocation;
      if (location != null) {
         // Player/queue placement already validated: only need a local map for altitude.
         int localRadius = Math.max(plan.width(), plan.depth()) + margins.maxMargin() * 2 + 8;
         localRadius = Math.max(localRadius, 24);
         BlockPos localCenter = new BlockPos(location.position().getX(), village.getCenter().getY(), location.position().getZ());
         terrainMap = VillageTerrainMap.compute(level, localCenter, localRadius);
         markExistingBuildingsOccupied(terrainMap, village);
      } else {
         terrainMap = VillageTerrainMap.compute(level, village.getCenter(), radius);
         markExistingBuildingsOccupied(terrainMap, village);
         TerrainReachability reachability = TerrainReachability.compute(terrainMap, village.getCenter());
         location = BuildingLocationFinder.findLocation(
            terrainMap, plan, village.getCenter(), constraints, margins, new ArrayList<>(village.getBuildings()), reachability
         );
      }

      if (location == null) {
         LOGGER.warn("[Millenaire] Growth: no location found for {}, cooldown {}s", planSet.id(), 80L);
         addPlacementCooldown(village, planSet.id(), currentTick);
         return false;
      }

      BlockPos requestedOrigin = location.position();
      Rotation rotation = location.rotation();
      int baseY = terrainMap.computeAverageAltitude(requestedOrigin.getX(), requestedOrigin.getZ(), plan.width(), plan.depth(), margins, rotation);
      int placementY = baseY + plan.groundLevel();
      BlockPos adjustedOrigin = new BlockPos(requestedOrigin.getX(), placementY, requestedOrigin.getZ());
      BuildingId buildingId = BuildingId.random();
      BuildingInstance instance = new BuildingInstance(
         buildingId, plan.id(), adjustedOrigin, rotation, BuildingInstance.Status.UNDER_CONSTRUCTION, planSet.id(), variant, 0
      );
      instance.setPlayerChosen(playerChosen);
      initBrickColours(instance, planSet, village, level.getRandom());
      List<PlacementStep> terrainSteps = BuildingPlacer.compileTerrainPrepSteps(
         level, requestedOrigin, plan.width(), plan.height(), plan.depth(), baseY, adjustedOrigin, rotation, plan.groundLevel(), margins
      );
      List<PlacementStep> buildingSteps = BuildingPlacer.compilePlacementSteps(level, plan, adjustedOrigin, rotation, instance);
      List<PlacementStep> dedupedTerrainSteps = deduplicateTerrainSteps(terrainSteps, buildingSteps, plan.groundLevel());
      List<PlacementStep> allSteps = new ArrayList<>(dedupedTerrainSteps.size() + buildingSteps.size());
      allSteps.addAll(dedupedTerrainSteps);
      allSteps.addAll(buildingSteps);
      if (!allSteps.isEmpty()) {
         ConstructionTask task = new ConstructionTask(allSteps, 0);
         task.markPendingLeafClear();
         instance.setConstructionTask(task);
      }

      village.addBuilding(instance);
      if (allSteps.isEmpty()) {
         LOGGER.warn("Building {} has 0 placement steps — marking COMPLETE immediately", planSet.buildingId());
         completeImmediately(level, village, instance, plan, planSet);
      }

      SubBuildingHelper.spawnStartingSubBuildings(level, village, planSet, instance, false);
      SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, levelDef, instance, false);
      village.recordEvent(level, "New building: " + planSet.id().getPath() + " (" + variant + "L0) at " + adjustedOrigin.toShortString());
      village.recordChronicleEvent(level, VillageEventType.BUILDING_STARTED, planSet.nativeName(), null);
      LOGGER.info("[Millenaire] Growth: new building {} ({}L0) at {}", new Object[]{planSet.buildingId(), variant, adjustedOrigin.toShortString()});
      return true;
   }

   static void launchUpgrade(ServerLevel level, Village village, VillageGrowthManager.GrowthCandidate candidate) {
      BuildingInstance target = candidate.existingBuilding();
      if (target != null) {
         setupUpgradeConstruction(level, village, target, candidate.planSet(), candidate.levelDef());
      }
   }

   private static void setupUpgradeConstruction(
      ServerLevel level, Village village, BuildingInstance target, BuildingPlanSet planSet, BuildingPlanSet.LevelDef levelDef
   ) {
      BuildingPlan newPlan = ModCultures.getBuildingPlan(levelDef.planId());
      if (newPlan != null) {
         BlockPos upgradeOrigin = recalculateOriginForUpgrade(target, newPlan);
         target.startUpgrade(levelDef.planId(), levelDef.level());
         village.invalidateBuildingTagCache();
         if (!upgradeOrigin.equals(target.getOrigin())) {
            target.setOrigin(upgradeOrigin);
         }

         village.recordEvent(level, "Upgrade started: " + planSet.buildingId() + " → level " + levelDef.level());
         village.recordChronicleEvent(level, VillageEventType.UPGRADE_STARTED, planSet.nativeName(), String.valueOf(levelDef.level()));
         List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, newPlan, upgradeOrigin, target.getRotation(), target);
         if (!steps.isEmpty()) {
            target.setConstructionTask(new ConstructionTask(steps, 0));
         } else {
            LOGGER.warn("Upgrade {} → level {} has 0 placement steps — marking COMPLETE immediately", planSet.buildingId(), levelDef.level());
            completeImmediately(level, village, target, newPlan, planSet);
         }

         LOGGER.info("[Millenaire] Upgrade: {} → level {}", planSet.buildingId(), levelDef.level());
      }
   }

   private static void completeImmediately(ServerLevel level, Village village, BuildingInstance building, BuildingPlan plan, BuildingPlanSet planSet) {
      building.markComplete();
      BuildingFinalizer.applyPostPlacement(level, village, building, plan);
      String chronicleName = plan.id().getPath();
      if (building.getVariant() != null) {
         BuildingPlanSet.LevelDef ld = planSet.getLevel(building.getVariant(), building.getLevel());
         if (ld != null && ld.nativeName() != null) {
            chronicleName = ld.nativeName();
         } else {
            chronicleName = planSet.nativeName();
         }
      }

      village.recordChronicleEvent(level, VillageEventType.BUILDING_COMPLETED, chronicleName, null);
      BuildingFinalizer.applyCompletionEffects(level, village, building);
      BuildingPlacer.spawnWallDecorations(level, building.getResolvedPoints());
      BuildingFinalizer.applyVillageUpdates(level, village, building);
      if (building.getLevel() > 0 && building.getPlanSetId() != null && building.getVariant() != null) {
         BuildingPlanSet.LevelDef completedLevelDef = planSet.getLevel(building.getVariant(), building.getLevel());
         if (completedLevelDef != null) {
            SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, completedLevelDef, building, true);
         }
      }

      if (building.getLevel() == 0 && building.getVariant() != null) {
         BuildingPlanSet.LevelDef currentLevelDef = planSet.getLevel(building.getVariant(), building.getLevel());
         if (currentLevelDef != null) {
            village.getPathManager().markPathsDirty();
            village.markDirty();
         }
      }
   }

   public static int countTotalProjects(VillageType villageType) {
      int total = 0;

      for (VillageType.LayoutSlot slot : villageType.layout()) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
         if (planSet != null) {
            if (!isCentreRole(slot.role())) {
               total++;
            }

            int maxUpgrades = 0;

            for (String variant : planSet.variants().keySet()) {
               int levels = planSet.getLevelCount(variant);
               if (levels > 1) {
                  maxUpgrades = Math.max(maxUpgrades, levels - 1);
               }
            }

            total += maxUpgrades;
            Set<String> allSubKeys = new HashSet<>(planSet.startingSubBuildings());

            for (String v : planSet.variants().keySet()) {
               List<BuildingPlanSet.LevelDef> levels = planSet.variants().get(v);
               if (levels != null) {
                  for (BuildingPlanSet.LevelDef ld : levels) {
                     allSubKeys.addAll(ld.subBuildings());
                  }
               }
            }

            String culturePath = planSet.culture().getPath();

            for (String subKey : allSubKeys) {
               ResourceLocation subId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + subKey);
               BuildingPlanSet subPlanSet = ModCultures.getBuildingPlanSet(subId);
               if (subPlanSet != null) {
                  int maxSubLevels = 0;

                  for (String subVariant : subPlanSet.variants().keySet()) {
                     maxSubLevels = Math.max(maxSubLevels, subPlanSet.getLevelCount(subVariant));
                  }

                  total += maxSubLevels;
               }
            }
         }
      }

      return total;
   }

   public static boolean rushOneProject(ServerLevel level, Village village) {
      return rushOneProject(level, village, null);
   }

   public static boolean rushOneProject(ServerLevel level, Village village, @Nullable VillageTerrainMap terrainMap) {
      return rushOneProject(level, village, terrainMap, null);
   }

   public static boolean rushOneProject(
      ServerLevel level, Village village, @Nullable VillageTerrainMap terrainMap, @Nullable Set<ResourceLocation> rushExcluded
   ) {
      return rushOneProject(level, village, terrainMap, rushExcluded, null);
   }

   public static boolean rushOneProject(
      ServerLevel level,
      Village village,
      @Nullable VillageTerrainMap terrainMap,
      @Nullable Set<ResourceLocation> rushExcluded,
      @Nullable TerrainReachability reachability
   ) {
      return rushOneProject(level, village, terrainMap, rushExcluded, reachability, null);
   }

   public static boolean rushOneProject(
      ServerLevel level,
      Village village,
      @Nullable VillageTerrainMap terrainMap,
      @Nullable Set<ResourceLocation> rushExcluded,
      @Nullable TerrainReachability reachability,
      @Nullable VillageGrowthManager.RushDispersion dispersion
   ) {
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType == null) {
         return false;
      }

      List<VillageGrowthManager.GrowthCandidate> candidates = getAllCandidates(level, village, villageType, false, true);
      if (rushExcluded != null && !rushExcluded.isEmpty()) {
         candidates.removeIf(c -> rushExcluded.contains(c.planSet().id()));
      }

      if (candidates.isEmpty()) {
         return false;
      }

      if (dispersion != null) {
         List<VillageGrowthManager.GrowthCandidate> available;
         for (available = filterUnblocked(candidates, dispersion);
            available.isEmpty() && dispersion.hasActiveCooldowns();
            available = filterUnblocked(candidates, dispersion)
         ) {
            dispersion.tick();
         }

         if (!available.isEmpty()) {
            candidates = available;
         }
      }

      List<Integer> weights = candidates.stream().map(VillageGrowthManager.GrowthCandidate::weight).toList();
      VillageGrowthManager.GrowthCandidate chosen = weightedPick(candidates, weights, ThreadLocalRandom.current());
      if (chosen == null) {
         return false;
      }

      if (chosen.isUpgrade()) {
         boolean upgraded = executeRushUpgrade(level, village, chosen);
         if (upgraded) {
            if (rushExcluded != null) {
               rushExcluded.clear();
            }

            if (dispersion != null && chosen.existingBuilding() != null) {
               dispersion.tick();
               dispersion.occupy(chosen.existingBuilding().getId(), chosen.levelDef().width(), chosen.levelDef().depth());
            }
         }

         return upgraded;
      } else {
         BuildingInstance placed = executeRushPlacement(level, village, chosen, terrainMap, reachability);
         if (placed != null) {
            if (rushExcluded != null) {
               rushExcluded.clear();
            }

            if (dispersion != null) {
               dispersion.tick();
               dispersion.occupy(placed.getId(), chosen.levelDef().width(), chosen.levelDef().depth());
            }

            return true;
         } else {
            if (rushExcluded != null) {
               rushExcluded.add(chosen.planSet().id());
            }

            return false;
         }
      }
   }

   private static List<VillageGrowthManager.GrowthCandidate> filterUnblocked(
      List<VillageGrowthManager.GrowthCandidate> candidates, VillageGrowthManager.RushDispersion dispersion
   ) {
      List<VillageGrowthManager.GrowthCandidate> out = new ArrayList<>();

      for (VillageGrowthManager.GrowthCandidate c : candidates) {
         if (!c.isUpgrade() || c.existingBuilding() == null || !dispersion.isBlocked(c.existingBuilding().getId())) {
            out.add(c);
         }
      }

      return out;
   }

   private static BuildingInstance executeRushPlacement(
      ServerLevel level,
      Village village,
      VillageGrowthManager.GrowthCandidate candidate,
      @Nullable VillageTerrainMap sharedTerrainMap,
      @Nullable TerrainReachability reachability
   ) {
      BuildingPlanSet planSet = candidate.planSet();
      BuildingPlanSet.LevelDef levelDef = candidate.levelDef();
      String variant = candidate.variant();
      VillageType.LayoutSlot slot = candidate.slot();
      BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
      if (plan == null) {
         return null;
      }

      VillageTerrainMap terrainMap;
      if (sharedTerrainMap != null) {
         terrainMap = sharedTerrainMap;
      } else {
         VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
         int radius = vt != null ? vt.radius() : 90;
         terrainMap = VillageTerrainMap.compute(level, village.getCenter(), radius);
         markExistingBuildingsOccupied(terrainMap, village);
      }

      VillageType vt2 = ModCultures.getVillageType(village.getVillageTypeId());
      int rushRadius = vt2 != null ? vt2.radius() : 90;
      PlacementConstraints constraints = PlacementConstraints.resolve(planSet, slot, rushRadius);
      ClearMargins margins = slot != null ? planSet.clearMargins().atLeast(slot.clearMargin()) : planSet.clearMargins();
      PlacedLocation location = BuildingLocationFinder.findLocation(
         terrainMap, plan, village.getCenter(), constraints, margins, new ArrayList<>(village.getBuildings()), reachability
      );
      if (location == null) {
         LOGGER.warn("[Millenaire] Rush: no location found for {}", planSet.id());
         return null;
      }

      BlockPos requestedOrigin = location.position();
      Rotation rotation = location.rotation();
      int baseY = terrainMap.computeAverageAltitude(requestedOrigin.getX(), requestedOrigin.getZ(), plan.width(), plan.depth(), margins, rotation);
      boolean[][] snowMap = TerrainPreparer.checkForSnow(level, requestedOrigin, plan.width(), plan.depth(), rotation, margins);
      TerrainPreparer.clearAndFlattenAtY(level, requestedOrigin, plan.width(), plan.height(), plan.depth(), rotation, plan.groundLevel(), baseY, margins);
      TerrainPreparer.decayOrphanedLeaves(level, requestedOrigin, plan.width(), plan.depth(), rotation, baseY, margins);
      int placementY = baseY + plan.groundLevel();
      BlockPos adjustedOrigin = new BlockPos(requestedOrigin.getX(), placementY, requestedOrigin.getZ());
      BuildingId buildingId = BuildingId.random();
      BuildingInstance instance = new BuildingInstance(
         buildingId, plan.id(), adjustedOrigin, rotation, BuildingInstance.Status.COMPLETE, planSet.id(), variant, 0
      );
      initBrickColours(instance, planSet, village, level.getRandom());
      BuildingPlacer.placeInstantly(level, plan, adjustedOrigin, rotation, instance);
      TerrainPreparer.restoreSnow(level, requestedOrigin, plan.width(), plan.depth(), rotation, snowMap, margins);
      BuildingFinalizer.applyPostPlacement(level, village, instance, plan);
      village.addBuilding(instance);
      if (sharedTerrainMap != null) {
         sharedTerrainMap.markBuildingFootprint(requestedOrigin.getX(), requestedOrigin.getZ(), plan.width(), plan.depth(), margins, rotation, baseY);
      }

      BuildingFinalizer.applyCompletionEffects(level, village, instance);
      SubBuildingHelper.spawnStartingSubBuildings(level, village, planSet, instance, true);
      SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, levelDef, instance, true);
      spawnBuildingOccupants(level, village, planSet, instance);
      village.recordEvent(level, "Rush: new building " + planSet.id().getPath() + " (" + variant + "L0) at " + adjustedOrigin.toShortString());
      LOGGER.info("[Millenaire] Rush: new building {} ({}L0) at {}", new Object[]{planSet.buildingId(), variant, adjustedOrigin.toShortString()});
      return instance;
   }

   private static boolean executeRushUpgrade(ServerLevel level, Village village, VillageGrowthManager.GrowthCandidate candidate) {
      BuildingInstance target = candidate.existingBuilding();
      if (target == null) {
         return false;
      }

      BuildingPlanSet planSet = candidate.planSet();
      BuildingPlanSet.LevelDef nextLevel = candidate.levelDef();
      BuildingPlan newPlan = ModCultures.getBuildingPlan(nextLevel.planId());
      if (newPlan == null) {
         return false;
      }

      BlockPos currentOrigin = target.getOrigin();
      BlockPos upgradeOrigin = recalculateOriginForUpgrade(target, newPlan);
      if (!upgradeOrigin.equals(currentOrigin)) {
         target.setOrigin(upgradeOrigin);
      }

      BuildingPlacer.placeUpgradeInstantly(level, newPlan, upgradeOrigin, target.getRotation(), target);
      target.startUpgrade(nextLevel.planId(), nextLevel.level());
      village.invalidateBuildingTagCache();
      target.markComplete();
      BuildingFinalizer.applyPostPlacement(level, village, target, newPlan);
      SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, nextLevel, target, true);
      BuildingFinalizer.applyCompletionEffects(level, village, target);
      village.recordEvent(level, "Rush: upgrade " + planSet.buildingId() + " → level " + nextLevel.level());
      LOGGER.info("[Millenaire] Rush: upgrade {} → level {}", planSet.buildingId(), nextLevel.level());
      return true;
   }

   static boolean canStartNewConstruction(Village village, @Nullable VillageType villageType) {
      int maxSlots = villageType != null ? villageType.maxSimultaneousConstructions() : 1;
      int ongoing = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            ConstructionTask task = b.getConstructionTask();
            if ((task == null || !task.isBlocked()) && !b.isWallSegment()) {
               ongoing++;
            }
         }
      }

      return ongoing < maxSlots;
   }

   @Nullable
   static String findLowestUnsatisfiedTier(Village village, VillageType villageType, long currentTick) {
      for (String tier : TIER_ORDER) {
         if (hasPendingSlotsForTier(village, villageType, tier, currentTick)) {
            return tier;
         }
      }

      return null;
   }

   private static boolean hasPendingSlotsForTier(Village village, VillageType villageType, String tier, long currentTick) {
      if ("player".equals(tier)) {
         for (ResourceLocation playerPlanId : villageType.playerBuildings()) {
            if (isPlayerBuildingCandidate(village, playerPlanId)) {
               return true;
            }
         }

         return false;
      } else {
         for (VillageType.LayoutSlot slot : villageType.layout()) {
            if (tier.equals(slot.role()) && slotIsBuildable(village, villageType, slot, currentTick, true)) {
               return true;
            }
         }

         return false;
      }
   }

   static boolean slotIsBuildable(Village village, VillageType villageType, VillageType.LayoutSlot slot, long currentTick, boolean skipPlacementCooldown) {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
      if (planSet == null) {
         return false;
      }

      if (villageType.neverBuildings().contains(planSet.buildingId())) {
         return false;
      }

      int existingCount = countBuildingsOfType(village, planSet.id());
      int layoutCountSoFar = countPriorLayoutSlots(villageType, slot, planSet.id());
      if (existingCount > layoutCountSoFar) {
         return false;
      }

      if (!skipPlacementCooldown && isOnPlacementCooldown(village, planSet.id(), currentTick)) {
         return false;
      }

      for (String variant : planSet.variants().keySet()) {
         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
         if (levelDef != null && checkNewBuildingConditions(village, levelDef)) {
            return true;
         }
      }

      return false;
   }

   static boolean checkNewBuildingConditions(Village village, BuildingPlanSet.LevelDef levelDef) {
      if (!levelDef.requiredTags().isEmpty()) {
         return false;
      }

      for (String forbiddenTag : levelDef.forbiddenTagsInVillage()) {
         if (villageHasTag(village, forbiddenTag)) {
            return false;
         }
      }

      for (String requiredVillageTag : levelDef.requiredVillageTags()) {
         if (!villageHasTag(village, requiredVillageTag)) {
            return false;
         }
      }

      return true;
   }

   public static void spawnBuildingOccupants(ServerLevel level, Village village, BuildingPlanSet planSet, BuildingInstance building) {
      String sharedFamilyName = null;
      if (!planSet.maleResidents().isEmpty() && !planSet.femaleResidents().isEmpty()) {
         String culturePath = village.getCultureId().getPath();
         ResourceLocation firstTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + planSet.maleResidents().get(0));
         VillagerType firstType = ModCultures.getVillagerType(firstTypeId);
         if (firstType != null) {
            Set<String> namesTaken = new HashSet<>();

            for (VillagerRecord record : village.getVillagerRecords().values()) {
               String fam = record.getFamilyName();
               if (fam != null && !fam.isEmpty()) {
                  namesTaken.add(fam);
               }
            }

            sharedFamilyName = VillagerAppearanceFactory.generateUniqueFamilyName(firstType, namesTaken);
         }
      }

      MillVillager husband = planSet.maleResidents().isEmpty()
         ? null
         : spawnOccupant(level, village, planSet.maleResidents().get(0), building, sharedFamilyName);
      MillVillager wife = planSet.femaleResidents().isEmpty()
         ? null
         : spawnOccupant(level, village, planSet.femaleResidents().get(0), building, sharedFamilyName);
      if (husband != null && wife != null && !husband.isChild() && !wife.isChild()) {
         husband.setSpousesName(wife.getFirstName() + " " + wife.getFamilyName());
         wife.setSpousesName(husband.getFirstName() + " " + husband.getFamilyName());
      }

      for (int i = 1; i < planSet.maleResidents().size(); i++) {
         spawnOccupant(level, village, planSet.maleResidents().get(i), building, sharedFamilyName);
      }

      for (int i = 1; i < planSet.femaleResidents().size(); i++) {
         spawnOccupant(level, village, planSet.femaleResidents().get(i), building, sharedFamilyName);
      }
   }

   @Nullable
   private static MillVillager spawnOccupant(
      ServerLevel level, Village village, @Nullable String villagerName, BuildingInstance building, @Nullable String sharedFamilyName
   ) {
      if (villagerName == null) {
         return null;
      }

      String culturePath = village.getCultureId().getPath();
      ResourceLocation villagerTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + villagerName);
      BlockPos spawnPos = building.getPathStartPos();
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, villagerTypeId, spawnPos, building.getId());
      if (villager == null) {
         return null;
      }

      if (sharedFamilyName != null) {
         villager.setFamilyName(sharedFamilyName);
      }

      village.recordEvent(
         level, "New villager: " + villagerTypeId.getPath() + " [" + villager.getUUID().toString().substring(0, 8) + "] in " + building.getPlanId().getPath()
      );
      String roleName = DisplayUtils.resolveRoleName(villagerTypeId);
      village.recordChronicleEvent(level, VillageEventType.VILLAGER_SPAWNED, villager.getFirstName() + " " + villager.getFamilyName(), roleName);
      LOGGER.info("[Millenaire] New villager: {}", villagerTypeId.getPath());
      return villager;
   }

   private static boolean tryLaunchPendingProject(ServerLevel level, Village village) {
      Village.PendingProject pending = village.getPendingProject();
      if (pending == null) {
         return false;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
      if (planSet == null) {
         village.setPendingProject(null);
         return false;
      }

      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(pending.variant(), pending.level());
      if (levelDef == null) {
         village.setPendingProject(null);
         return false;
      }

      if (!hasResourcesForProject(level, village, levelDef)) {
         return false;
      }

      if (pending.isUpgrade()) {
         if (pending.buildingId() == null) {
            village.setPendingProject(null);
            return false;
         } else {
            BuildingInstance target = village.getBuilding(pending.buildingId());
            if (target != null && target.getStatus() == BuildingInstance.Status.COMPLETE) {
               village.setPendingProject(null);
               setupUpgradeConstruction(level, village, target, planSet, levelDef);
               return true;
            } else {
               village.setPendingProject(null);
               return false;
            }
         }
      } else {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType == null) {
            village.setPendingProject(null);
            return false;
         } else {
            VillageType.LayoutSlot slot = findSlotForPlanSet(villageType, pending.planSetId());
            PlacedLocation planned = pending.plannedLocation();
            village.setPendingProject(null);
            return placeNewBuildingForConstruction(level, village, planSet, levelDef, pending.variant(), slot, planned, false);
         }
      }
   }

   private static boolean tryLaunchControlledQueueHead(ServerLevel level, Village village) {
      ControlledQueuedProject head = village.controlledQueueHead();
      if (head == null) {
         return false;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(head.planSetId());
      if (planSet == null) {
         village.pollControlledQueueHead();
         notifyOwnerOfDroppedProject(level, village, head.planSetId().getPath());
         return false;
      }

      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(head.variant(), head.level());
      if (levelDef == null) {
         village.pollControlledQueueHead();
         dropQueuedProjectSigns(level, planSet, head);
         notifyOwnerOfDroppedProject(level, village, planSet.nativeName());
         return false;
      }

      if (!hasResourcesForProject(level, village, levelDef)) {
         return false;
      }

      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType == null) {
         return false;
      }

      VillageType.LayoutSlot slot = findSlotForPlanSet(villageType, head.planSetId());
      village.pollControlledQueueHead();
      boolean placed = placeNewBuildingForConstruction(level, village, planSet, levelDef, head.variant(), slot, head.plannedLocation(), true);
      if (!placed) {
         dropQueuedProjectSigns(level, planSet, head);
         notifyOwnerOfDroppedProject(level, village, planSet.nativeName());
      }

      return placed;
   }

   private static void dropQueuedProjectSigns(ServerLevel level, BuildingPlanSet planSet, ControlledQueuedProject project) {
      BuildingPlanSet.LevelDef def = planSet.getLevel(project.variant(), 0);
      BuildingPlan plan = def != null ? ModCultures.getBuildingPlan(def.planId()) : null;
      if (plan != null && project.plannedLocation() != null) {
         PlacementSignHelper.removeCornerSigns(level, plan, project.plannedLocation());
      }
   }

   private static void notifyOwnerOfDroppedProject(ServerLevel level, Village village, String name) {
      LOGGER.warn("[Millenaire] Controlled queue: dropped unresolvable project {} in village {}", name, village.getVillageName());
      if (village.getOwnerUUID() != null) {
         ServerPlayer owner = level.getServer().getPlayerList().getPlayer(village.getOwnerUUID());
         if (owner != null) {
            owner.sendSystemMessage(Component.translatable("gui.millenaire.building_project.dropped", new Object[]{name}));
         }
      }
   }

   private static boolean tryLaunchPlannedSubBuilding(ServerLevel level, Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building.isSubBuilding()
            && building.getStatus() == BuildingInstance.Status.PLANNED
            && building.getPlanSetId() != null
            && building.getVariant() != null) {
            BuildingId parentId = building.getParentBuildingId();
            if (parentId != null) {
               BuildingInstance parent = village.findBuildingById(parentId);
               if (parent != null && parent.isBeingBuilt()) {
                  continue;
               }
            }

            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(building.getVariant(), building.getLevel());
               if (levelDef != null && hasResourcesForProject(level, village, levelDef)) {
                  BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
                  if (plan != null) {
                     ConstructionTask task = building.getConstructionTask();
                     if (task == null) {
                        List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, plan, building.getOrigin(), building.getRotation());
                        if (steps.isEmpty()) {
                           completeImmediately(level, village, building, plan, planSet);
                           return true;
                        }

                        task = new ConstructionTask(steps, 0);
                        building.setConstructionTask(task);
                     }

                     building.markUnderConstruction();
                     village.recordEvent(
                        level, "Sub-building started: " + planSet.id().getPath() + " (" + building.getVariant() + "L" + building.getLevel() + ")"
                     );
                     village.recordChronicleEvent(level, VillageEventType.BUILDING_STARTED, planSet.nativeName(), null);
                     LOGGER.info("[Millenaire] Planned sub-building {} activated", planSet.id().getPath());
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @Nullable
   private static VillageType.LayoutSlot findSlotForPlanSet(VillageType villageType, ResourceLocation planSetId) {
      for (VillageType.LayoutSlot slot : villageType.layout()) {
         if (planSetId.equals(slot.plan())) {
            return slot;
         }
      }

      return null;
   }

   static boolean isCentreRole(String role) {
      return "centre".equals(role) || "townhall".equals(role);
   }

   static boolean isAutoExcludedInControlledVillage(boolean playerControlled, String role) {
      return playerControlled && CONTROLLED_AUTO_EXCLUDED_ROLES.contains(role);
   }

   static int computeLayoutSlotWeight(VillageType.LayoutSlot slot, BuildingPlanSet.LevelDef levelDef) {
      int basePriority = slot.hasPriorityOverride() ? slot.priority() : levelDef.priority();
      int tierMultiplier = TIER_MULTIPLIERS.getOrDefault(slot.role(), 1);
      return Math.max(1, basePriority * tierMultiplier);
   }

   static int countPriorLayoutSlots(VillageType villageType, VillageType.LayoutSlot targetSlot, ResourceLocation planSetId) {
      int count = 0;

      for (VillageType.LayoutSlot prev : villageType.layout()) {
         if (prev == targetSlot) {
            break;
         }

         BuildingPlanSet prevSet = ModCultures.getBuildingPlanSet(prev.plan());
         if (prevSet != null && prevSet.id().equals(planSetId)) {
            count++;
         }
      }

      return count;
   }

   static int countBuildingsOfType(Village village, ResourceLocation planSetId) {
      int count = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (planSetId.equals(b.getPlanSetId())) {
            count++;
         }
      }

      return count;
   }

   static boolean isPlayerBuildingCandidate(Village village, ResourceLocation planSetId) {
      return !village.isBuildingBought(planSetId) ? false : countBuildingsOfType(village, planSetId) == 0;
   }

   private static boolean hasResources(ServerLevel level, Village village, BuildingPlanSet.LevelDef levelDef) {
      return hasResources(level, village, levelDef, VillageGrowthManager.AffordabilityMode.RESERVATION_AWARE);
   }

   static boolean hasResourcesForProject(ServerLevel level, Village village, BuildingPlanSet.LevelDef levelDef) {
      return hasResources(level, village, levelDef, VillageGrowthManager.AffordabilityMode.RAW_COUNT);
   }

   static boolean hasResourcesForWall(ServerLevel level, Village village, BuildingPlanSet.LevelDef levelDef) {
      return hasResources(level, village, levelDef, VillageGrowthManager.AffordabilityMode.RESERVATION_AWARE);
   }

   private static boolean hasResources(ServerLevel level, Village village, BuildingPlanSet.LevelDef levelDef, VillageGrowthManager.AffordabilityMode mode) {
      if ((Boolean)MillenaireServerConfig.SERVER.ignoreResourceCost.get()) {
         return true;
      }

      Map<ResourceLocation, Integer> required = levelDef.requiredResources();
      if (required.isEmpty()) {
         return true;
      }

      BuildingInstance townhall = village.getTownhall();
      if (townhall == null) {
         return false;
      }

      BuildingInventory inv = townhall.getInventory();
      if (inv == null) {
         return false;
      }

      inv.invalidateCacheOncePerTick(level.getGameTime());

      for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
         if (!AnywoodHelper.isAnywood(entry.getKey())) {
            Item item = ItemHelper.resolve(entry.getKey());
            if (item == null) {
               LOGGER.warn("[Millenaire] Unknown resource: {}", entry.getKey());
               return false;
            }

            int available;
            if (mode == VillageGrowthManager.AffordabilityMode.RAW_COUNT) {
               available = inv.getCount(level, item);
            } else {
               available = GoodAvailabilityHelper.nbGoodAvailable(townhall, item, level, village, village.getCultureId(), false, true);
            }

            if (available < entry.getValue()) {
               LOGGER.debug("[Growth] hasResources FAIL: {} need={} have={} (mode={})", new Object[]{entry.getKey(), entry.getValue(), available, mode});
               return false;
            }
         }
      }

      Integer anywoodNeeded = required.get(AnywoodHelper.ANYWOOD_LOG);
      if (anywoodNeeded != null && anywoodNeeded > 0) {
         int rawCount = inv.getCountByTag(level, AnywoodHelper.LOGS_TAG);
         if (mode != VillageGrowthManager.AffordabilityMode.RAW_COUNT) {
            rawCount -= GoodAvailabilityHelper.getAnywoodReservedQuantity(level, village, null);
         }

         int available = AnywoodHelper.anywoodAvailable(rawCount, required, key -> {
            Item logItem = ItemHelper.resolve(key);
            return logItem == null ? 0 : inv.getCount(level, logItem);
         });
         if (available < anywoodNeeded) {
            LOGGER.debug("[Growth] hasResources FAIL: anywood need={} have={}", anywoodNeeded, available);
            return false;
         }
      }

      return true;
   }

   static boolean checkBuildConditions(Village village, BuildingInstance building, BuildingPlanSet.LevelDef levelDef) {
      for (String requiredTag : levelDef.requiredTags()) {
         if (!building.hasRuntimeTag(requiredTag)) {
            return false;
         }
      }

      for (String requiredParentTag : levelDef.requiredParentTags()) {
         if (building.getParentBuildingId() == null) {
            return false;
         }

         BuildingInstance parent = village.findBuildingById(building.getParentBuildingId());
         if (parent == null || !parent.hasRuntimeTag(requiredParentTag)) {
            return false;
         }
      }

      for (String forbiddenTag : levelDef.forbiddenTagsInVillage()) {
         if (villageHasTag(village, forbiddenTag)) {
            return false;
         }
      }

      for (String requiredVillageTag : levelDef.requiredVillageTags()) {
         if (!villageHasTag(village, requiredVillageTag)) {
            return false;
         }
      }

      return true;
   }

   private static boolean villageHasTag(Village village, String tag) {
      return !village.getBuildingsWithTag(tag).isEmpty();
   }

   @Nullable
   static <T> T weightedPick(List<T> candidates, List<Integer> weights, RandomGenerator rng) {
      if (candidates.isEmpty()) {
         return null;
      }

      if (candidates.size() == 1) {
         return candidates.getFirst();
      }

      int totalWeight = 0;

      for (int w : weights) {
         totalWeight += w;
      }

      if (totalWeight <= 0) {
         return null;
      }

      int roll = rng.nextInt(totalWeight);
      int cumulative = 0;

      for (int i = 0; i < candidates.size(); i++) {
         cumulative += weights.get(i);
         if (roll < cumulative) {
            return candidates.get(i);
         }
      }

      return candidates.getLast();
   }

   static BlockPos recalculateOriginForUpgrade(BuildingInstance target, BuildingPlan newPlan) {
      BlockPos currentOrigin = target.getOrigin();
      BuildingPlan currentPlan = ModCultures.getBuildingPlan(target.getPlanId());
      int oldGroundLevel = currentPlan != null ? currentPlan.groundLevel() : 0;
      int newGroundLevel = newPlan.groundLevel();
      if (newGroundLevel != oldGroundLevel) {
         int baseY = currentOrigin.getY() - oldGroundLevel;
         int newPlacementY = baseY + newGroundLevel;
         return new BlockPos(currentOrigin.getX(), newPlacementY, currentOrigin.getZ());
      } else {
         return currentOrigin;
      }
   }

   @Nullable
   public static PlacedLocation findLocationForNewBuilding(
      ServerLevel level, Village village, BuildingPlanSet planSet, BuildingPlan plan, @Nullable VillageType.LayoutSlot slot
   ) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 90;
      VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, village.getCenter(), radius);
      markExistingBuildingsOccupied(terrainMap, village);
      TerrainReachability reachability = TerrainReachability.compute(terrainMap, village.getCenter());
      PlacementConstraints constraints = PlacementConstraints.resolve(planSet, slot, radius);
      ClearMargins margins = planSet.clearMargins().atLeast(constraints.clearMargin());
      return BuildingLocationFinder.findLocation(
         terrainMap, plan, village.getCenter(), constraints, margins, new ArrayList<>(village.getBuildings()), reachability
      );
   }

   public static BuildingLocationFinder.AnchorEvaluation validateLocationAt(
      ServerLevel level,
      Village village,
      BuildingPlanSet planSet,
      BuildingPlan plan,
      @Nullable VillageType.LayoutSlot slot,
      BlockPos clickedPos,
      @Nullable Integer playerRotation
   ) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 90;
      PlacementConstraints constraints = PlacementConstraints.resolve(planSet, slot, radius);
      ClearMargins margins = planSet.clearMargins().atLeast(constraints.clearMargin());
      // Local map around the click — full village maps at large player-city radii are too expensive,
      // and path-reachability from the town hall is too strict for player-planned cities.
      int localRadius = Math.max(plan.width(), plan.depth()) + margins.maxMargin() * 2 + 16;
      localRadius = Math.max(localRadius, 32);
      BlockPos localCenter = new BlockPos(clickedPos.getX(), village.getCenter().getY(), clickedPos.getZ());
      VillageTerrainMap terrainMap = VillageTerrainMap.compute(level, localCenter, localRadius);
      markExistingBuildingsOccupied(terrainMap, village);
      return BuildingLocationFinder.evaluateAnchor(
         clickedPos.getX(),
         clickedPos.getZ(),
         terrainMap,
         plan,
         village.getCenter(),
         radius,
         constraints,
         margins,
         new ArrayList<>(village.getBuildings()),
         null,
         playerRotation
      );
   }

   static void markExistingBuildingsOccupied(VillageTerrainMap terrainMap, Village village) {
      for (BuildingInstance existing : village.getBuildings()) {
         if (!existing.isSubBuilding()) {
            BuildingPlan existingPlan = ModCultures.getBuildingPlan(existing.getPlanId());
            if (existingPlan != null) {
               int surfaceY = existing.getOrigin().getY() - existingPlan.groundLevel();
               BuildingPlanSet existingPlanSet = ModCultures.getBuildingPlanSet(existing.getPlanSetId());
               ClearMargins margins = existingPlanSet != null
                  ? existingPlanSet.clearMargins().atLeast(PlacementConstraints.getDefaultClearMargin())
                  : ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
               terrainMap.markBuildingFootprint(
                  existing.getOrigin().getX(),
                  existing.getOrigin().getZ(),
                  existingPlan.width(),
                  existingPlan.depth(),
                  margins,
                  existing.getRotation(),
                  surfaceY
               );
               if (existing.isWallSegment() && existingPlanSet != null) {
                  terrainMap.markOccupied(
                     existing.getOrigin().getX(),
                     existing.getOrigin().getZ(),
                     existingPlan.width(),
                     existingPlan.depth(),
                     existingPlanSet.clearMargins(),
                     existing.getRotation()
                  );
               }
            }
         }
      }

      for (ControlledQueuedProject q : village.getControlledQueue()) {
         markPlannedOccupied(terrainMap, ModCultures.getBuildingPlanSet(q.planSetId()), q.variant(), q.level(), q.plannedLocation());
      }

      Village.PendingProject pendingReserve = village.getPendingProject();
      if (pendingReserve != null && pendingReserve.plannedLocation() != null) {
         markPlannedOccupied(
            terrainMap,
            ModCultures.getBuildingPlanSet(pendingReserve.planSetId()),
            pendingReserve.variant(),
            pendingReserve.level(),
            pendingReserve.plannedLocation()
         );
      }
   }

   private static void markPlannedOccupied(VillageTerrainMap terrainMap, @Nullable BuildingPlanSet planSet, String variant, int level, PlacedLocation planned) {
      if (planSet != null && planned != null) {
         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, level);
         BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
         if (plan != null) {
            int surfaceY = planned.position().getY() - plan.groundLevel();
            ClearMargins margins = planSet.clearMargins().atLeast(PlacementConstraints.getDefaultClearMargin());
            terrainMap.markBuildingFootprint(
               planned.position().getX(), planned.position().getZ(), plan.width(), plan.depth(), margins, planned.rotation(), surfaceY
            );
         }
      }
   }

   static boolean isOnPlacementCooldown(Village village, ResourceLocation planSetId, long currentTick) {
      Long until = village.getPlacementCooldowns().get(planSetId);
      return until != null && currentTick < until;
   }

   private static void addPlacementCooldown(Village village, ResourceLocation planSetId, long currentTick) {
      village.addPlacementCooldown(planSetId, currentTick + 1600L);
   }

   static void initBrickColours(BuildingInstance instance, BuildingPlanSet planSet, Village village, RandomSource random) {
      if (!planSet.randomBrickColours().isEmpty()) {
         instance.initBrickColoursFromPlan(planSet.randomBrickColours(), random);
      } else if (village.getBrickTheme() != null) {
         instance.initBrickColours(village.getBrickTheme(), random);
      }
   }

   static List<PlacementStep> deduplicateTerrainSteps(List<PlacementStep> terrainSteps, List<PlacementStep> buildingSteps, int groundLevel) {
      Set<BlockPos> buildingPositions = new HashSet<>(buildingSteps.size());

      for (PlacementStep step : buildingSteps) {
         buildingPositions.add(step.relativePos());
      }

      int naturalSurfaceY = -groundLevel;
      List<PlacementStep> result = new ArrayList<>(terrainSteps.size());

      for (PlacementStep step : terrainSteps) {
         if (!buildingPositions.contains(step.relativePos())) {
            result.add(step);
         } else if (step.relativePos().getY() >= naturalSurfaceY) {
            result.add(step);
         }
      }

      return result;
   }

   enum AffordabilityMode {
      RAW_COUNT,
      RESERVATION_AWARE;
   }

   record GrowthCandidate(
      BuildingPlanSet planSet,
      BuildingPlanSet.LevelDef levelDef,
      String variant,
      int weight,
      boolean isUpgrade,
      @Nullable BuildingInstance existingBuilding,
      @Nullable VillageType.LayoutSlot slot
   ) {
   }

   public static final class RushDispersion {
      private static final int AREA_PER_COOLDOWN_TICK = 60;
      private final Map<BuildingId, Integer> cooldowns = new HashMap<>();

      boolean isBlocked(BuildingId id) {
         return this.cooldowns.getOrDefault(id, 0) > 0;
      }

      boolean hasActiveCooldowns() {
         return !this.cooldowns.isEmpty();
      }

      void tick() {
         this.cooldowns.replaceAll((id, remaining) -> remaining - 1);
         this.cooldowns.values().removeIf(remaining -> remaining <= 0);
      }

      void occupy(BuildingId id, int width, int depth) {
         int area = Math.max(1, width * depth);
         int ticks = Math.max(1, Math.round(area / 60.0F));
         this.cooldowns.put(id, ticks);
      }
   }
}
