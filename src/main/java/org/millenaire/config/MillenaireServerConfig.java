package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MillenaireServerConfig {
   public static final MillenaireServerConfig SERVER;
   public static final ModConfigSpec SPEC;
   private static final int DEFAULT_CLEAR_MARGIN = 5;
   public final BooleanValue generateVillages;
   public final BooleanValue generateLoneBuildings;
   public final BooleanValue generateHamlets;
   public final IntValue minVillageDistance;
   public final IntValue minLoneBuildingDistance;
   public final IntValue minVillageLoneBuildingDistance;
   public final IntValue spawnProtectionRadius;
   public final IntValue completionMaxPercentage;
   public final IntValue completionMinDistance;
   public final IntValue completionMaxDistance;
   public final BooleanValue logSpawnAttempts;
   public final BooleanValue worldgenEngineEnabled;
   public final BooleanValue worldgenLegacyFallback;
   public final IntValue worldgenRegionSizeChunks;
   public final DoubleValue worldgenSettlementDensity;
   public final DoubleValue worldgenLoneBuildingDensity;
   public final DoubleValue worldgenHamletDensity;
   public final IntValue worldgenMinimumSettlementSpacing;
   public final IntValue worldgenProtectedSpawnRadius;
   public final BooleanValue starterSettlementEnabled;
   public final ConfigValue<String> starterCulture;
   public final ConfigValue<String> starterVillageType;
   public final IntValue starterSearchRadius;
   public final IntValue nearSpawnBoostRadius;
   public final IntValue worldgenMaximumSettlementFootprint;
   public final BooleanValue worldgenAllowHillyTerrain;
   public final IntValue worldgenMaximumTerrainSlope;
   public final IntValue worldgenMaximumFoundationDepth;
   public final IntValue worldgenMaximumTerrainModification;
   public final BooleanValue worldgenUseNativeStructures;
   public final BooleanValue worldgenGenerateInitialRoads;
   public final BooleanValue worldgenGenerateRegionalRoads;
   public final BooleanValue worldgenGenerateBridges;
   public final BooleanValue worldgenSandHighways;
   public final IntValue highwayWidth;
   public final IntValue highwayMaxLinkDistance;
   public final IntValue highwayMaxNeighbors;
   public final BooleanValue highwayTunnels;
   public final BooleanValue highwayTravelers;
   public final IntValue highwayMaxJourneys;
   public final BooleanValue highwayDiscoverVanilla;
   public final BooleanValue highwayDiscoverMod;
   public final BooleanValue highwayDiscoverMillenaire;
   public final BooleanValue highwayIncludeLoneBuildings;
   public final BooleanValue worldgenGenerateStartingWalls;
   public final BooleanValue worldgenPreserveVanillaStructures;
   public final BooleanValue worldgenPreserveModdedStructures;
   public final IntValue worldgenStructureSafetyMargin;
   public final BooleanValue placeVillageWaystones;
   public final BooleanValue worldgenUntaggedBiomeFallback;
   public final BooleanValue worldgenEnableSettlementVariation;
   public final BooleanValue worldgenAllowPartialInitialCompletion;
   public final IntValue worldgenPlanningCacheSize;
   public final IntValue worldgenMaterializationBudget;
   public final IntValue worldgenMaterializationScanBudget;
   public final IntValue worldgenResumeQueueBudget;
   public final IntValue worldgenActivationBudget;
   public final IntValue worldgenPlanningBudget;
   public final IntValue worldgenTickTimeBudgetMs;
   public final IntValue worldgenTerrainColumnsPerTick;
   public final IntValue worldgenPlacementStepCacheSize;
   public final BooleanValue worldgenDetailedDiagnostics;
   public final BooleanValue forceLoadVillages;
   public final IntValue keepActiveRadius;
   public final IntValue villageRadiusOverride;
   public final IntValue minBuildingSpacing;
   public final BooleanValue buildPaths;
   public final IntValue maxChildren;
   public final IntValue backgroundRadius;
   public final EnumValue<NavDriverType> navDriver;
   public final BooleanValue languageLearning;
   public final BooleanValue travelBookLearning;
   public final BooleanValue mapSyncEnabled;
   public final BooleanValue mapSyncShowLoneBuildings;
   public final BooleanValue mapSyncIncludeApproxBounds;
   public final IntValue sentenceDistanceSingleplayer;
   public final IntValue sentenceDistanceMultiplayer;
   public final BooleanValue ignoreResourceCost;
   public final BooleanValue sendStatistics;
   public final BooleanValue sendPlayerName;
   public final ConfigValue<String> apiBaseUrlOverride;
   public final IntValue banditRaidRadius;
   public final IntValue raidingRate;
   public final BooleanValue legacyAutoConvert;
   public final IntValue legacyAutoConvertMaxPngs;
   public final BooleanValue npcChatEnabled;
   public final ConfigValue<String> npcChatProvider;
   public final ConfigValue<String> npcChatBaseUrl;
   public final ConfigValue<String> npcChatModel;
   public final ConfigValue<String> npcChatApiKey;
   public final IntValue npcChatTimeoutSeconds;
   public final IntValue npcChatMaxConcurrent;
   public final IntValue npcChatMinMillisBetweenMessages;
   public final IntValue npcChatChatRange;
   public final IntValue npcChatLeaveRange;
   public final BooleanValue npcChatGossip;
   public final IntValue npcChatGossipExpiryDays;
   public final BooleanValue npcChatMillenaire;
   public final BooleanValue npcChatVanilla;
   public final BooleanValue npcChatModded;
   public final BooleanValue npcChatGenericLiving;
   public final BooleanValue npcChatNonverbal;
   public final BooleanValue npcChatRequireLineOfSight;
   public final IntValue npcChatTransientRetentionDays;
   public final IntValue npcChatMaxStoredMinds;
   public final ConfigValue<String> npcChatEntityAllowList;
   public final ConfigValue<String> npcChatEntityDenyList;
   public final BooleanValue livingVillagesEnabled;
   public final BooleanValue villageNeedsEnabled;
   public final IntValue villageNeedsEvaluationInterval;
   public final IntValue villageNeedsGlobalBudget;
   public final BooleanValue npcSocialEnabled;
   public final IntValue npcSocialInteractionCooldown;
   public final IntValue npcRelationshipRetention;
   public final BooleanValue npcPersonalityEnabled;
   public final IntValue npcMoodDecayInterval;
   public final BooleanValue npcEventMemoryEnabled;
   public final IntValue npcEventRetentionLimit;
   public final IntValue npcKnowledgeRetentionLimit;
   public final BooleanValue villageRequestsEnabled;
   public final IntValue villageRequestMaxPerPlayer;
   public final IntValue villageRequestMaxPerVillage;
   public final IntValue villageRequestExpiryDays;
   public final IntValue villageRequestMaximumDeliveryQuantity;
   public final net.neoforged.neoforge.common.ModConfigSpec.DoubleValue villageRequestReputationMultiplier;
   public final BooleanValue npcWorldReactionsEnabled;
   public final IntValue npcReactionProcessingBudget;
   public final BooleanValue npcDialogueActionSuggestionsEnabled;
   public final BooleanValue livingVillagesDebugLogging;
   public final BooleanValue civilizationEvolutionEnabled;
   public final BooleanValue civilizationAutoTradeEnabled;
   public final BooleanValue civilizationOffscreenEnabled;
   public final BooleanValue civilizationNpcContextEnabled;
   public final BooleanValue civilizationAutonomousFoundingEnabled;
   public final BooleanValue civilizationMetricsLogging;
   public final IntValue civilizationFactionBudget;
   public final IntValue civilizationTradeOpsBudget;
   public final IntValue civilizationEventPublishBudget;
   public final IntValue civilizationMigrationBudget;
   public final BooleanValue civilizationImmigrationEnabled;
   public final IntValue civilizationImmigrationBudget;
   public final BooleanValue civilizationTravelerImmigrationEnabled;
   public final IntValue civilizationTravelerImmigrationDailyCap;
   public final IntValue civilizationCatchupDays;
   public final IntValue civilizationColonizationOpsBudget;
   public final IntValue civilizationColonizationCandidateBudget;
   public final IntValue civilizationColonizationCooldownDays;
   public final BooleanValue civilizationIntraFactionLogistics;
   public final BooleanValue cultureBountyEnabled;
   public final BooleanValue cultureBountyChatEnabled;
   public final BooleanValue cultureBountyOnSightEnabled;
   public final ConfigValue<String> cultureBountyOnSightTier;
   public final BooleanValue treatCreativeAsSurvival;
   public final BooleanValue bountyHudEnabled;
   public final BooleanValue pickpocketEnabled;
   public final DoubleValue pickpocketBaseChance;
   public final BooleanValue arrestEnabled;
   public final IntValue arrestLockSeconds;
   public final IntValue arrestBountyOnArrest;
   public final BooleanValue playerShopEnabled;
   public final BooleanValue roadTravelerShopVisits;
   public final IntValue cultureBountyRaidThreshold;
   public final IntValue cultureBountyWarThreshold;
   public final IntValue cultureBountyStockPerWave;
   public final BooleanValue bountyHunterEnabled;
   public final IntValue bountyHunterMaxPerPlayer;
   public final IntValue bountyHunterDispatchDelaySeconds;
   public final IntValue bountyHunterReplacementCooldownTicks;
   public final IntValue bountyHunterBolaCooldownSeconds;
   public final IntValue bountyHunterBolaSlownessSeconds;
   public final IntValue bountyHunterArrestChannelSeconds;
   public final IntValue bountyHunterJailSentenceSeconds;
   public final IntValue bountyHunterSettlementReduction;
   public final ConfigValue<String> bountyHunterMinTier;
   public final BooleanValue bountyHunterHuntInOverworld;
   public final BooleanValue bountyHunterAllowPeaceful;
   public final BooleanValue civilizationDiplomaticAiEnabled;
   public final IntValue civilizationDiplomaticAiBudget;
   public final BooleanValue computeOffloadEnabled;
   public final IntValue computeWorkerThreads;
   public final IntValue computeApplyBudgetPerTick;
   public final BooleanValue computeOffloadVillagePaths;
   public final BooleanValue computeOffloadHighwayPlan;
   public final BooleanValue computeOffloadWorldgenPlan;
   public final BooleanValue computeOffloadWaypointGraph;

   public static int getDefaultClearMargin() {
      try {
         return SERVER.minBuildingSpacing.getAsInt();
      } catch (IllegalStateException e) {
         return 5;
      }
   }

   private MillenaireServerConfig(Builder builder) {
      builder.comment("World generation settings").push("generation");
      this.generateVillages = builder.comment("Generate Millenaire villages in new chunks")
         .translation("millenaire.config.generateVillages")
         .define("generateVillages", true);
      this.generateLoneBuildings = builder.comment("Generate lone buildings (inns, shrines, ruins) in new chunks")
         .translation("millenaire.config.generateLoneBuildings")
         .define("generateLoneBuildings", true);
      this.generateHamlets = builder.comment(
            new String[]{
               "Generate hamlet satellite villages around parent village types (e.g. Gros Bourg).",
               "When disabled, parent types that define hamlets are excluded from natural spawn.",
               "iso-legacy: disabled by default (hamlets take a lot of space)"
            }
         )
         .translation("millenaire.config.generateHamlets")
         .define("generateHamlets", false);
      this.minVillageDistance = builder.comment("Minimum distance between two villages (blocks)")
         .translation("millenaire.config.minVillageDistance")
         .defineInRange("minVillageDistance", 500, 300, 1000);
      this.minLoneBuildingDistance = builder.comment("Minimum distance between two lone buildings (blocks)")
         .translation("millenaire.config.minLoneBuildingDistance")
         .defineInRange("minLoneBuildingDistance", 500, 300, 1000);
      this.minVillageLoneBuildingDistance = builder.comment("Minimum distance between a village and a lone building (blocks)")
         .translation("millenaire.config.minVillageLoneBuildingDistance")
         .defineInRange("minVillageLoneBuildingDistance", 250, 100, 800);
      this.spawnProtectionRadius = builder.comment(
            "Protected area around world spawn where nothing generates (blocks). Default 0 so settlements may form near spawn."
         )
         .translation("millenaire.config.spawnProtectionRadius")
         .defineInRange("spawnProtectionRadius", 0, 0, 500);
      builder.comment("Progressive completion for distant villages").push("completion");
      this.completionMaxPercentage = builder.comment("Maximum initial progress % for distant villages")
         .translation("millenaire.config.completionMaxPercentage")
         .defineInRange("maxPercentage", 25, 0, 100);
      this.completionMinDistance = builder.comment("Distance from spawn where initial progress starts (blocks)")
         .translation("millenaire.config.completionMinDistance")
         .defineInRange("minDistance", 2000, 0, 25000);
      this.completionMaxDistance = builder.comment("Distance from spawn where max initial progress is reached (blocks)")
         .translation("millenaire.config.completionMaxDistance")
         .defineInRange("maxDistance", 10000, 0, 100000);
      builder.pop();
      builder.comment(
            new String[]{
               "Phase 4 deterministic worldgen engine. When enabled, natural settlements are planned from",
               "the world seed + region grid and materialized per new chunk.",
               "Legacy VillageSpawnQueue natural spawns run only when worldgenLegacyFallback=true",
               "(or when the engine is disabled). Manual /millenaire spawn commands still use VillageSpawner.",
               "Legacy density keys (generateVillages, minVillageDistance, etc.) remain authoritative",
               "minimums; worldgen* keys refine planner behaviour without renaming old keys."
            }
         )
         .push("worldgen");
      this.worldgenEngineEnabled = builder.comment("Enable Phase 4 deterministic settlement worldgen engine")
         .translation("millenaire.config.worldgenEngineEnabled")
         .define("worldgenEngineEnabled", true);
      this.worldgenLegacyFallback = builder.comment(
            new String[]{
               "Also run the legacy VillageSpawnQueue while the Phase 4 engine is enabled.",
               "Default false: Phase 4 is the sole natural spawn path.",
               "Set true only if natural villages fail to appear; spacing rules still prevent overlaps."
            }
         )
         .translation("millenaire.config.worldgenLegacyFallback")
         .define("worldgenLegacyFallback", false);
      this.worldgenRegionSizeChunks = builder.comment("Region grid size in chunks for candidate distribution (independent of village radius)")
         .translation("millenaire.config.worldgenRegionSizeChunks")
         .defineInRange("worldgenRegionSizeChunks", 32, 8, 128);
      this.worldgenSettlementDensity = builder.comment("Probability a region attempts a full village (0-1). Slightly higher for dense biome packs.")
         .translation("millenaire.config.worldgenSettlementDensity")
         .defineInRange("worldgenSettlementDensity", 0.55D, 0.0D, 1.0D);
      this.worldgenLoneBuildingDensity = builder.comment("Probability a region attempts a lone building when no village is selected (0-1)")
         .translation("millenaire.config.worldgenLoneBuildingDensity")
         .defineInRange("worldgenLoneBuildingDensity", 0.22D, 0.0D, 1.0D);
      this.worldgenHamletDensity = builder.comment("Reserved density weight for hamlet satellite planning (0-1)")
         .translation("millenaire.config.worldgenHamletDensity")
         .defineInRange("worldgenHamletDensity", 0.5D, 0.0D, 1.0D);
      this.worldgenMinimumSettlementSpacing = builder.comment(
            "Minimum spacing between planned settlements (blocks). Effective spacing is max(this, minVillageDistance)."
         )
         .translation("millenaire.config.worldgenMinimumSettlementSpacing")
         .defineInRange("worldgenMinimumSettlementSpacing", 500, 200, 2000);
      this.worldgenProtectedSpawnRadius = builder.comment(
            "Additional protected radius around world spawn (blocks). Effective protection is max(this, spawnProtectionRadius). Default 0."
         )
         .translation("millenaire.config.worldgenProtectedSpawnRadius")
         .defineInRange("worldgenProtectedSpawnRadius", 0, 0, 1000);
      this.starterSettlementEnabled = builder.comment(
            "On new worlds, place one starter settlement (default Gabbers Koninkrijk) and move world spawn to its centre."
         )
         .translation("millenaire.config.starterSettlementEnabled")
         .define("starterSettlementEnabled", true);
      this.starterCulture = builder.comment("Culture path for the starter settlement (namespace millenaire)")
         .translation("millenaire.config.starterCulture")
         .define("starterCulture", "gabbers");
      this.starterVillageType = builder.comment("Village type path under the starter culture (e.g. koninkrijk)")
         .translation("millenaire.config.starterVillageType")
         .define("starterVillageType", "koninkrijk");
      this.starterSearchRadius = builder.comment("Max search radius from the initial shared spawn for the starter settlement (blocks)")
         .translation("millenaire.config.starterSearchRadius")
         .defineInRange("starterSearchRadius", 384, 64, 1024);
      this.nearSpawnBoostRadius = builder.comment(
            "Within this radius of world spawn, Phase 4 prioritizes finishing already-loaded settlement footprints (no force-load)."
         )
         .translation("millenaire.config.nearSpawnBoostRadius")
         .defineInRange("nearSpawnBoostRadius", 512, 0, 2048);
      this.worldgenMaximumSettlementFootprint = builder.comment("Soft cap on planned settlement radius used for reservation (blocks)")
         .translation("millenaire.config.worldgenMaximumSettlementFootprint")
         .defineInRange("worldgenMaximumSettlementFootprint", 120, 32, 256);
      this.worldgenAllowHillyTerrain = builder.comment("Allow settlements on rolling/hilly terrain within slope limits")
         .translation("millenaire.config.worldgenAllowHillyTerrain")
         .define("worldgenAllowHillyTerrain", true);
      this.worldgenMaximumTerrainSlope = builder.comment("Maximum average sample step slope (blocks) accepted for natural settlements")
         .translation("millenaire.config.worldgenMaximumTerrainSlope")
         .defineInRange("worldgenMaximumTerrainSlope", 4, 1, 16);
      this.worldgenMaximumFoundationDepth = builder.comment("Maximum foundation depth adjustment (blocks)")
         .translation("millenaire.config.worldgenMaximumFoundationDepth")
         .defineInRange("worldgenMaximumFoundationDepth", 8, 1, 32);
      this.worldgenMaximumTerrainModification = builder.comment("Maximum height difference terraformed under a building footprint (blocks)")
         .translation("millenaire.config.worldgenMaximumTerrainModification")
         .defineInRange("worldgenMaximumTerrainModification", 6, 1, 24);
      this.worldgenUseNativeStructures = builder.comment(
            "Reserved: register settlements as native Structure features when fully supported. Currently plans use chunk-load materialization."
         )
         .translation("millenaire.config.worldgenUseNativeStructures")
         .define("worldgenUseNativeStructures", false);
      this.worldgenGenerateInitialRoads = builder.comment("Plan and materialize initial internal roads during worldgen")
         .translation("millenaire.config.worldgenGenerateInitialRoads")
         .define("worldgenGenerateInitialRoads", true);
      this.worldgenGenerateRegionalRoads = builder.comment("Plan bounded regional connectors between nearby settlements (foundation)")
         .translation("millenaire.config.worldgenGenerateRegionalRoads")
         .define("worldgenGenerateRegionalRoads", false);
      this.worldgenGenerateBridges = builder.comment("Allow short bridge/raised-path segments where road policy supports them")
         .translation("millenaire.config.worldgenGenerateBridges")
         .define("worldgenGenerateBridges", true);
      this.worldgenSandHighways = builder.comment(
            "Discovery-driven 4-wide sand highways between Millenaire, vanilla, and tagged mod settlements (bridges/tunnels/travelers)"
         )
         .translation("millenaire.config.worldgenSandHighways")
         .define("worldgenSandHighways", false);
      this.highwayWidth = builder.comment("Highway surface width in blocks")
         .translation("millenaire.config.highwayWidth")
         .defineInRange("highwayWidth", 4, 1, 8);
      this.highwayMaxLinkDistance = builder.comment("Max distance (blocks) when linking discovered highway nodes")
         .translation("millenaire.config.highwayMaxLinkDistance")
         .defineInRange("highwayMaxLinkDistance", 1600, 200, 4096);
      this.highwayMaxNeighbors = builder.comment("Max highway links per node (faction hubs get +1)")
         .translation("millenaire.config.highwayMaxNeighbors")
         .defineInRange("highwayMaxNeighbors", 3, 1, 8);
      this.highwayTunnels = builder.comment("Carve mountain tunnels when steep surface routing fails")
         .translation("millenaire.config.highwayTunnels")
         .define("highwayTunnels", true);
      this.highwayTravelers = builder.comment("Spawn logical road travelers (traders/warriors/bandits) on connected highways")
         .translation("millenaire.config.highwayTravelers")
         .define("highwayTravelers", true);
      this.highwayMaxJourneys = builder.comment("Soft cap on concurrent road journeys")
         .translation("millenaire.config.highwayMaxJourneys")
         .defineInRange("highwayMaxJourneys", 24, 1, 128);
      this.highwayDiscoverVanilla = builder.comment("Discover vanilla villages (#minecraft:village) as highway endpoints")
         .translation("millenaire.config.highwayDiscoverVanilla")
         .define("highwayDiscoverVanilla", true);
      this.highwayDiscoverMod = builder.comment("Discover #millenaire:highway_endpoint mod structures as highway endpoints")
         .translation("millenaire.config.highwayDiscoverMod")
         .define("highwayDiscoverMod", true);
      this.highwayDiscoverMillenaire = builder.comment("Discover Millenaire villages/plans as highway endpoints")
         .translation("millenaire.config.highwayDiscoverMillenaire")
         .define("highwayDiscoverMillenaire", true);
      this.highwayIncludeLoneBuildings = builder.comment("Include Millenaire lone buildings as highway endpoints")
         .translation("millenaire.config.highwayIncludeLoneBuildings")
         .define("highwayIncludeLoneBuildings", false);
      this.worldgenGenerateStartingWalls = builder.comment("Plan and materialize starting walls/gates when village type defines them")
         .translation("millenaire.config.worldgenGenerateStartingWalls")
         .define("worldgenGenerateStartingWalls", true);
      this.worldgenPreserveVanillaStructures = builder.comment("Reject settlement candidates that conflict with protected vanilla structures")
         .translation("millenaire.config.worldgenPreserveVanillaStructures")
         .define("worldgenPreserveVanillaStructures", true);
      this.worldgenPreserveModdedStructures = builder.comment(
            "Reject settlement candidates that conflict with #millenaire:avoid_settlement structures (Waystones, Dungeons Arise, etc.)"
         )
         .translation("millenaire.config.worldgenPreserveModdedStructures")
         .define("worldgenPreserveModdedStructures", true);
      this.worldgenStructureSafetyMargin = builder.comment("Extra margin around protected structures (blocks)")
         .translation("millenaire.config.worldgenStructureSafetyMargin")
         .defineInRange("worldgenStructureSafetyMargin", 3, 0, 32);
      this.worldgenEnableSettlementVariation = builder.comment("Enable deterministic orientation/variant/decoration variation")
         .translation("millenaire.config.worldgenEnableSettlementVariation")
         .define("worldgenEnableSettlementVariation", true);
      this.worldgenAllowPartialInitialCompletion = builder.comment("Allow distant settlements a deterministic non-zero initial completion percent")
         .translation("millenaire.config.worldgenAllowPartialInitialCompletion")
         .define("worldgenAllowPartialInitialCompletion", true);
      this.worldgenPlanningCacheSize = builder.comment("Soft cap on in-memory plan index hints (plans remain persisted in SavedData)")
         .translation("millenaire.config.worldgenPlanningCacheSize")
         .defineInRange("worldgenPlanningCacheSize", 2048, 64, 16384);
      this.worldgenMaterializationBudget = builder.comment(
            "Max block/terrain operations per server tick for settlement materialization (shared across chunk loads)"
         )
         .translation("millenaire.config.worldgenMaterializationBudget")
         .defineInRange("worldgenMaterializationBudget", 3000, 500, 50000);
      this.worldgenMaterializationScanBudget = builder.comment(
            "Max candidate scans (buildings/walls/roads inspected) per tick while searching for unfinished work"
         )
         .translation("millenaire.config.worldgenMaterializationScanBudget")
         .defineInRange("worldgenMaterializationScanBudget", 256, 16, 4096);
      this.worldgenResumeQueueBudget = builder.comment("Max unfinished chunk jobs drained from the resume queue per server tick")
         .translation("millenaire.config.worldgenResumeQueueBudget")
         .defineInRange("worldgenResumeQueueBudget", 4, 1, 32);
      this.worldgenActivationBudget = builder.comment("Max settlement activation attempts per server tick (counts failures too)")
         .translation("millenaire.config.worldgenActivationBudget")
         .defineInRange("worldgenActivationBudget", 1, 1, 8);
      this.worldgenPlanningBudget = builder.comment("Max region-plan / layout work units drained per server tick")
         .translation("millenaire.config.worldgenPlanningBudget")
         .defineInRange("worldgenPlanningBudget", 6, 1, 32);
      this.worldgenTickTimeBudgetMs = builder.comment(
            "Soft wall-clock budget (ms) for optional worldgen work per server tick; combines with op/scan budgets"
         )
         .translation("millenaire.config.worldgenTickTimeBudgetMs")
         .defineInRange("worldgenTickTimeBudgetMs", 10, 1, 50);
      this.worldgenTerrainColumnsPerTick = builder.comment("Max terrain-map columns scanned per tick while building a resumable layout map")
         .translation("millenaire.config.worldgenTerrainColumnsPerTick")
         .defineInRange("worldgenTerrainColumnsPerTick", 256, 8, 4096);
      this.worldgenPlacementStepCacheSize = builder.comment("Bounded cache of immutable compiled placement steps (per plan/rotation)")
         .translation("millenaire.config.worldgenPlacementStepCacheSize")
         .defineInRange("worldgenPlacementStepCacheSize", 64, 8, 512);
      this.worldgenDetailedDiagnostics = builder.comment("Verbose worldgen planning/materialization logs (opt-in)")
         .translation("millenaire.config.worldgenDetailedDiagnostics")
         .define("worldgenDetailedDiagnostics", false);
      this.placeVillageWaystones = builder.comment(
            "When Waystones is loaded, place a waystone in every Millenaire village and every vanilla village"
         )
         .translation("millenaire.config.placeVillageWaystones")
         .define("placeVillageWaystones", true);
      this.worldgenUntaggedBiomeFallback = builder.comment(
            "When a biome matches no village biome_tags, assign cultures by climate so BOP/OTBWG/Nature's Spirit biomes still get villages"
         )
         .translation("millenaire.config.worldgenUntaggedBiomeFallback")
         .define("worldgenUntaggedBiomeFallback", true);
      builder.pop();
      builder.pop();
      builder.comment("Debug and logging settings").push("debug");
      this.logSpawnAttempts = builder.comment("Log every village/lone building spawn attempt with rejection reasons")
         .translation("millenaire.config.logSpawnAttempts")
         .define("logSpawnAttempts", false);
      builder.pop();
      builder.comment("Village behaviour settings").push("village");
      this.forceLoadVillages = builder.comment(
            new String[]{
               "When false, Millénaire does not force-load village chunks; villages only tick",
               "while a player keeps their chunks loaded. Reduces server load at the cost of",
               "villages pausing when no player is nearby. Applies live via /reload: turning it",
               "off releases currently force-loaded village chunks; turning it back on re-forces",
               "them on the next activation tick.",
               "iso-legacy: default true preserves the always-force-load behavior; false is a new perf opt-out."
            }
         )
         .translation("millenaire.config.forceLoadVillages")
         .define("forceLoadVillages", false);
      this.keepActiveRadius = builder.comment("Radius for keeping village chunks loaded (blocks). 0 = disabled.")
         .translation("millenaire.config.keepActiveRadius")
         .defineInRange("keepActiveRadius", 200, 0, 2000);
      this.villageRadiusOverride = builder.comment(
            new String[]{"Override all village type radii with this value (blocks).", "-1 = use per-type JSON value. Requires world restart to take effect."}
         )
         .translation("millenaire.config.villageRadiusOverride")
         .worldRestart()
         .defineInRange("villageRadiusOverride", -1, -1, 120);
      this.minBuildingSpacing = builder.comment("Minimum spacing between buildings in a village (blocks)")
         .translation("millenaire.config.minBuildingSpacing")
         .worldRestart()
         .defineInRange("minBuildingSpacing", 5, 0, 10);
      this.buildPaths = builder.comment("Generate and upgrade paths between village buildings (lateral paths Pass 3)")
         .translation("millenaire.config.buildPaths")
         .define("buildPaths", true);
      this.maxChildren = builder.comment("Maximum number of children per village")
         .translation("millenaire.config.maxChildren")
         .defineInRange("maxChildren", 10, 2, 20);
      this.backgroundRadius = builder.comment("Radius for inter-village relations: diplomacy, trade, raids (blocks). 0 = disabled.")
         .translation("millenaire.config.backgroundRadius")
         .defineInRange("backgroundRadius", 2000, 0, 3000);
      this.navDriver = builder.comment(
            new String[]{
               "Villager navigation driver (EXPERIMENTAL toggle for A/B testing).",
               "WAYPOINT (default): the current driver — vanilla micro A* + a macro",
               "  waypoint graph with a DIRECT->REPATH->MACRO->TELEPORT escalation.",
               "LOCAL_RECOVERY: experimental port of the Millenaire 1.15 movement",
               "  controller — vanilla A* with soft re-path, a perpendicular door/gate",
               "  nudge to clear chokepoints, and teleport only as a last resort.",
               "Applies to villagers spawned/loaded after the change. Default: WAYPOINT."
            }
         )
         .translation("millenaire.config.navDriver")
         .defineEnum("navDriver", NavDriverType.WAYPOINT);
      builder.pop();
      builder.comment("Gameplay settings").push("gameplay");
      this.languageLearning = builder.comment("Whether NPC languages need to be learned through interaction")
         .translation("millenaire.config.languageLearning")
         .define("languageLearning", true);
      this.travelBookLearning = builder.comment("Whether Travel Book content needs to be discovered through interaction (nearby villagers, buildings, trade)")
         .translation("millenaire.config.travelBookLearning")
         .define("travelBookLearning", true);
      this.mapSyncEnabled = builder.comment("Push discovered/owned settlement locations to clients for map mods (Xaero, etc.)")
         .translation("millenaire.config.mapSyncEnabled")
         .define("mapSyncEnabled", true);
      this.mapSyncShowLoneBuildings = builder.comment("Include lone buildings in settlement map sync when discovered/owned")
         .translation("millenaire.config.mapSyncShowLoneBuildings")
         .define("mapSyncShowLoneBuildings", false);
      this.mapSyncIncludeApproxBounds = builder.comment(
            "Include approximate building AABB corners in map sync (visualization only; not territorial claims)"
         )
         .translation("millenaire.config.mapSyncIncludeApproxBounds")
         .define("mapSyncIncludeApproxBounds", false);
      this.sentenceDistanceSingleplayer = builder.comment("Distance for villager sentences in chat — singleplayer (blocks). 0 = disabled.")
         .translation("millenaire.config.sentenceDistanceSP")
         .defineInRange("sentenceDistanceSingleplayer", 6, 0, 10);
      this.sentenceDistanceMultiplayer = builder.comment("Distance for villager sentences in chat — multiplayer (blocks). 0 = disabled.")
         .translation("millenaire.config.sentenceDistanceMP")
         .defineInRange("sentenceDistanceMultiplayer", 0, 0, 10);
      this.ignoreResourceCost = builder.comment(
            new String[]{
               "Villages build and upgrade for free, ignoring resource requirements (creative-style).",
               "iso-legacy: MillConfigValues.ignoreResourceCost. Default: false."
            }
         )
         .translation("millenaire.config.ignoreResourceCost")
         .define("ignoreResourceCost", false);
      builder.pop();
      builder.comment("Anonymous usage statistics sent to millenaire.org").push("statistics");
      this.sendStatistics = builder.comment(
            new String[]{
               "Send Millénaire statistics to millenaire.org. Master switch: when off, nothing is sent.",
               "The session ping carries no personally identifiable information (anonymous world UID,",
               "version, OS, locale, player count). Advancement progress is reported per player using a",
               "pseudonymous (non re-identifiable) identifier, and only attaches the username (login)",
               "when sendPlayerName is enabled. Note: the donor check sends the username out of",
               "functional necessity, independently of these two flags."
            }
         )
         .translation("millenaire.config.sendStatistics")
         .define("sendStatistics", true);
      this.sendPlayerName = builder.comment(
            new String[]{
               "Attach the player's Minecraft username to the per-player advancement report.",
               "When off, that report uses only the pseudonymous identifier. Off by default."
            }
         )
         .translation("millenaire.config.sendPlayerName")
         .define("sendPlayerName", false);
      this.apiBaseUrlOverride = builder.comment(
            new String[]{
               "Override the millenaire.org API base URL the mod contacts (stats, version, donor).",
               "Empty = use the URLs baked in at build time (primary, then fallback). A non-empty",
               "value forces a single URL with no fallback, e.g. http://localhost:4000 for local testing."
            }
         )
         .translation("millenaire.config.apiBaseUrlOverride")
         .define("apiBaseUrlOverride", "");
      builder.pop();
      builder.comment("Raid settings (combat AI not yet implemented — values reserved)").push("raids");
      this.banditRaidRadius = builder.comment("TODO: Radius for bandit raids (blocks). 0 = disabled.")
         .translation("millenaire.config.banditRaidRadius")
         .defineInRange("banditRaidRadius", 1500, 0, 2000);
      this.raidingRate = builder.comment("TODO: % chance per night of a raid attempt. 0 = disabled.")
         .translation("millenaire.config.raidingRate")
         .defineInRange("raidingRate", 20, 0, 100);
      builder.pop();
      builder.comment("Automatic conversion of legacy 1.12 content packs dropped into millenaire-custom/").push("legacy");
      this.legacyAutoConvert = builder.comment(
            new String[]{
               "Enable automatic conversion of legacy 1.12 Millenaire content packs",
               "dropped into millenaire-custom/. When true, TXT/PNG files are detected",
               "at server start, converted to JSON/NBT in place, and originals renamed",
               "to .legacy. Set to false if you prefer running /millenaire dev convert-addon",
               "manually (required for read-only filesystems, large packs, or custom",
               "staging workflows)."
            }
         )
         .translation("millenaire.config.legacyAutoConvert")
         .define("legacyAutoConvert", true);
      this.legacyAutoConvertMaxPngs = builder.comment(
            new String[]{
               "Per-boot cap on PNG file count before auto-conversion refuses to run.",
               "Large packs should use /millenaire dev convert-addon during a maintenance",
               "window to avoid boot freezes. Measured cold-JVM cost is ~2-4s per 800 PNGs;",
               "the default absorbs every legacy culture at once (~2100 PNGs in total,",
               "the Norman culture alone being 454)."
            }
         )
         .translation("millenaire.config.legacyAutoConvertMaxPngs")
         .defineInRange("legacyAutoConvertMaxPngs", 2500, 0, 5000);
      builder.pop();
      builder.comment("Free-form NPC chat backed by an OpenAI-compatible chat completions API").push("npcChat");
      this.npcChatEnabled = builder.comment("Master switch for NPC free chat. When false, no HTTP calls are made.")
         .translation("millenaire.config.npcChat.enabled")
         .define("enabled", true);
      this.npcChatProvider = builder.comment(
            new String[]{
               "NPC chat provider mode: auto | lmstudio | openai.",
               "auto detects LM Studio from the base URL (localhost OR LAN IP on port 1234, e.g. http://192.168.1.18:1234/v1).",
               "lmstudio forces GET /api/v1/models (+ /v1/models fallback) and loaded LLM selection.",
               "openai keeps the configured model name for remote OpenAI-compatible APIs (requires apiKey + model)."
            }
         )
         .translation("millenaire.config.npcChat.provider")
         .define("provider", "lmstudio");
      this.npcChatBaseUrl = builder.comment(
            new String[]{
               "API base URL without a trailing slash.",
               "LM Studio: http://localhost:1234/v1 (preferred on the same PC) or http://<lan-ip>:1234/v1.",
               "Model name optional for LM Studio; loaded LLMs are auto-detected. Edit this in the world's serverconfig.",
               "Remote example: https://api.x.ai/v1"
            }
         )
         .translation("millenaire.config.npcChat.baseUrl")
         .define("baseUrl", "http://localhost:1234/v1");
      this.npcChatModel = builder.comment(
            new String[]{
               "Model id for remote OpenAI-compatible providers.",
               "For LM Studio: leave empty for automatic selection when exactly one LLM is loaded;",
               "when multiple LLMs are loaded, set this (or use /millenaire npcchat model select) to choose one."
            }
         )
         .translation("millenaire.config.npcChat.model")
         .define("model", "");
      this.npcChatApiKey = builder.comment(
            new String[]{
               "API key for NPC chat. Leave empty to read MILLENAIRE_NPC_API_KEY from the environment.",
               "For local LM Studio, an empty key is allowed (optional LM_API_TOKEN).",
               "Never log this value. For remote providers an empty key disables the model while still storing player quotes."
            }
         )
         .translation("millenaire.config.npcChat.apiKey")
         .define("apiKey", "");
      this.npcChatTimeoutSeconds = builder.comment("HTTP request timeout in seconds")
         .translation("millenaire.config.npcChat.timeoutSeconds")
         .defineInRange("timeoutSeconds", 12, 4, 30);
      this.npcChatMaxConcurrent = builder.comment("Maximum concurrent model requests on this server")
         .translation("millenaire.config.npcChat.maxConcurrent")
         .defineInRange("maxConcurrent", 4, 1, 8);
      this.npcChatMinMillisBetweenMessages = builder.comment("Minimum milliseconds between chat send packets per player")
         .translation("millenaire.config.npcChat.minMillisBetweenMessages")
         .defineInRange("minMillisBetweenMessages", 1500, 0, 60000);
      this.npcChatChatRange = builder.comment("Maximum distance to open or keep talking to a villager (blocks)")
         .translation("millenaire.config.npcChat.chatRange")
         .defineInRange("chatRange", 4, 2, 6);
      this.npcChatLeaveRange = builder.comment("Distance at which an open chat session closes (blocks). Always at least chatRange.")
         .translation("millenaire.config.npcChat.leaveRange")
         .defineInRange("leaveRange", 6, 4, 10);
      this.npcChatGossip = builder.comment("Allow same-culture rumor spreading from free chat")
         .translation("millenaire.config.npcChat.gossip")
         .define("gossip", true);
      this.npcChatGossipExpiryDays = builder.comment("Days before an inactive rumor expires")
         .translation("millenaire.config.npcChat.gossipExpiryDays")
         .defineInRange("gossipExpiryDays", 30, 7, 120);
      this.npcChatMillenaire = builder.comment("Allow dialogue with Millénaire villagers")
         .translation("millenaire.config.npcChat.millenaire")
         .define("millenaire", true);
      this.npcChatVanilla = builder.comment("Allow dialogue with vanilla villagers, traders, illagers, piglins, golems, allays, hostiles and animals")
         .translation("millenaire.config.npcChat.vanilla")
         .define("vanilla", true);
      this.npcChatModded = builder.comment("Allow dialogue with living entities from other mods via the generic adapter")
         .translation("millenaire.config.npcChat.modded")
         .define("modded", true);
      this.npcChatGenericLiving = builder.comment("Generic LivingEntity fallback for types without a dedicated adapter")
         .translation("millenaire.config.npcChat.genericLiving")
         .define("genericLiving", true);
      this.npcChatNonverbal = builder.comment("Allow nonverbal / atmospheric dialogue for animals and monsters")
         .translation("millenaire.config.npcChat.nonverbal")
         .define("nonverbal", true);
      this.npcChatRequireLineOfSight = builder.comment("Require clear line of sight to open or continue dialogue")
         .translation("millenaire.config.npcChat.requireLineOfSight")
         .define("requireLineOfSight", true);
      this.npcChatTransientRetentionDays = builder.comment("Days to retain minds for transient NPCs after last talk")
         .translation("millenaire.config.npcChat.transientRetentionDays")
         .defineInRange("transientRetentionDays", 7, 1, 60);
      this.npcChatMaxStoredMinds = builder.comment("Soft cap on stored NPC minds; excess transient minds are pruned first")
         .translation("millenaire.config.npcChat.maxStoredMinds")
         .defineInRange("maxStoredMinds", 2000, 100, 20000);
      this.npcChatEntityAllowList = builder.comment(
            "Optional comma-separated entity type ids (namespace:path or path) for extra documentation; empty = no allow-list restriction"
         )
         .translation("millenaire.config.npcChat.entityAllowList")
         .define("entityAllowList", "");
      this.npcChatEntityDenyList = builder.comment("Comma-separated entity type ids denied from the generic adapter")
         .translation("millenaire.config.npcChat.entityDenyList")
         .define("entityDenyList", "");
      builder.pop();
      builder.comment("Living Villages 2.0 — needs, social simulation, assistance requests (does not disable core Millénaire when off)")
         .push("livingVillages");
      this.livingVillagesEnabled = builder.comment("Master switch for Living Villages systems")
         .translation("millenaire.config.livingVillages.enabled")
         .define("livingVillagesEnabled", true);
      this.villageNeedsEnabled = builder.comment("Derive village needs from authoritative inventories and projects")
         .translation("millenaire.config.livingVillages.villageNeedsEnabled")
         .define("villageNeedsEnabled", true);
      this.villageNeedsEvaluationInterval = builder.comment("Ticks between need evaluations per village")
         .translation("millenaire.config.livingVillages.villageNeedsEvaluationInterval")
         .defineInRange("villageNeedsEvaluationInterval", 200, 40, 4000);
      this.villageNeedsGlobalBudget = builder.comment("Max village need evaluations per server tick")
         .translation("millenaire.config.livingVillages.villageNeedsGlobalBudget")
         .defineInRange("villageNeedsGlobalBudget", 4, 1, 32);
      this.npcSocialEnabled = builder.comment("NPC-to-NPC social relationship updates from ChatGoal")
         .translation("millenaire.config.livingVillages.npcSocialEnabled")
         .define("npcSocialEnabled", true);
      this.npcSocialInteractionCooldown = builder.comment("Minimum ticks between social updates for the same NPC pair side")
         .translation("millenaire.config.livingVillages.npcSocialInteractionCooldown")
         .defineInRange("npcSocialInteractionCooldown", 1200, 200, 24000);
      this.npcRelationshipRetention = builder.comment("Soft max relationships retained per NPC")
         .translation("millenaire.config.livingVillages.npcRelationshipRetention")
         .defineInRange("npcRelationshipRetention", 24, 8, 128);
      this.npcPersonalityEnabled = builder.comment("Persistent NPC personality traits and mood reactions")
         .translation("millenaire.config.livingVillages.npcPersonalityEnabled")
         .define("npcPersonalityEnabled", true);
      this.npcMoodDecayInterval = builder.comment("Ticks between mood decay checks")
         .translation("millenaire.config.livingVillages.npcMoodDecayInterval")
         .defineInRange("npcMoodDecayInterval", 6000, 200, 24000);
      this.npcEventMemoryEnabled = builder.comment("Persist Living Villages event journal entries")
         .translation("millenaire.config.livingVillages.npcEventMemoryEnabled")
         .define("npcEventMemoryEnabled", true);
      this.npcEventRetentionLimit = builder.comment("Max detailed living events retained per village")
         .translation("millenaire.config.livingVillages.npcEventRetentionLimit")
         .defineInRange("npcEventRetentionLimit", 128, 32, 512);
      this.npcKnowledgeRetentionLimit = builder.comment("Max knowledge items retained per NPC")
         .translation("millenaire.config.livingVillages.npcKnowledgeRetentionLimit")
         .defineInRange("npcKnowledgeRetentionLimit", 24, 8, 128);
      this.villageRequestsEnabled = builder.comment("Dynamic village assistance requests and deliveries")
         .translation("millenaire.config.livingVillages.villageRequestsEnabled")
         .define("villageRequestsEnabled", true);
      this.villageRequestMaxPerPlayer = builder.comment("Max active assistance requests per player")
         .translation("millenaire.config.livingVillages.villageRequestMaxPerPlayer")
         .defineInRange("villageRequestMaxPerPlayer", 3, 1, 16);
      this.villageRequestMaxPerVillage = builder.comment("Max active assistance requests per village")
         .translation("millenaire.config.livingVillages.villageRequestMaxPerVillage")
         .defineInRange("villageRequestMaxPerVillage", 6, 1, 32);
      this.villageRequestExpiryDays = builder.comment("Minecraft days before an assistance request expires")
         .translation("millenaire.config.livingVillages.villageRequestExpiryDays")
         .defineInRange("villageRequestExpiryDays", 7, 1, 60);
      this.villageRequestMaximumDeliveryQuantity = builder.comment("Max quantity of one item requested per assistance offer")
         .translation("millenaire.config.livingVillages.villageRequestMaximumDeliveryQuantity")
         .defineInRange("villageRequestMaximumDeliveryQuantity", 64, 8, 256);
      this.villageRequestReputationMultiplier = builder.comment("Multiplier applied to reputation rewards for completed assistance")
         .translation("millenaire.config.livingVillages.villageRequestReputationMultiplier")
         .defineInRange("villageRequestReputationMultiplier", 1.0D, 0.0D, 5.0D);
      this.npcWorldReactionsEnabled = builder.comment("Seed NPC knowledge from living events")
         .translation("millenaire.config.livingVillages.npcWorldReactionsEnabled")
         .define("npcWorldReactionsEnabled", true);
      this.npcReactionProcessingBudget = builder.comment("Max reaction/event knowledge operations per reaction pass")
         .translation("millenaire.config.livingVillages.npcReactionProcessingBudget")
         .defineInRange("npcReactionProcessingBudget", 16, 1, 64);
      this.npcDialogueActionSuggestionsEnabled = builder.comment("Show contextual Living Villages action buttons in NPC chat")
         .translation("millenaire.config.livingVillages.npcDialogueActionSuggestionsEnabled")
         .define("npcDialogueActionSuggestionsEnabled", true);
      this.livingVillagesDebugLogging = builder.comment("Verbose Living Villages debug logging")
         .translation("millenaire.config.livingVillages.debugLogging")
         .define("livingVillagesDebugLogging", false);
      builder.pop();
      builder.comment(
            new String[]{
               "Phase 5 civilization evolution — orchestration across factions, economy, politics,",
               "diplomacy, military and demographics. When disabled, FactionLifecycle ticks managers",
               "directly as before. Disabling civilization does NOT disable villages, Phase 3 Living",
               "Villages, Phase 4 worldgen, trading, quests or NPC chat."
            }
         )
         .push("civilization");
      this.civilizationEvolutionEnabled = builder.comment("Master switch for CivilizationOrchestrator staged updates")
         .translation("millenaire.config.civilization.evolutionEnabled")
         .define("civilizationEvolutionEnabled", true);
      this.civilizationAutoTradeEnabled = builder.comment("Allow conserved settlement-to-settlement trade when needs/deficits exist")
         .translation("millenaire.config.civilization.autoTradeEnabled")
         .define("civilizationAutoTradeEnabled", true);
      this.civilizationOffscreenEnabled = builder.comment("Bounded coarse catch-up for inactive settlements (never force-loads chunks)")
         .translation("millenaire.config.civilization.offscreenEnabled")
         .define("civilizationOffscreenEnabled", true);
      this.civilizationNpcContextEnabled = builder.comment("Inject verified faction/civilization facts into NPC dialogue context")
         .translation("millenaire.config.civilization.npcContextEnabled")
         .define("civilizationNpcContextEnabled", true);
      this.civilizationAutonomousFoundingEnabled = builder.comment(
            new String[]{
               "Enable bounded autonomous colonization using existing worldgen planning + migration.",
               "Default false for save safety. When enabled, overcrowded faction settlements may found nearby colonies",
               "only in already-loaded chunks, without force-loading or free resident creation."
            }
         )
         .translation("millenaire.config.civilization.autonomousFoundingEnabled")
         .define("civilizationAutonomousFoundingEnabled", false);
      this.civilizationMetricsLogging = builder.comment("Occasional civilization orchestration metrics in logs (opt-in)")
         .translation("millenaire.config.civilization.metricsLogging")
         .define("civilizationMetricsLogging", false);
      this.civilizationFactionBudget = builder.comment("Max factions processed per orchestration stage pass")
         .translation("millenaire.config.civilization.factionBudget")
         .defineInRange("civilizationFactionBudget", 8, 1, 64);
      this.civilizationTradeOpsBudget = builder.comment("Max conserved trade operations per economy stage")
         .translation("millenaire.config.civilization.tradeOpsBudget")
         .defineInRange("civilizationTradeOpsBudget", 4, 0, 32);
      this.civilizationEventPublishBudget = builder.comment("Max civilization chronicle events published per tick")
         .translation("millenaire.config.civilization.eventPublishBudget")
         .defineInRange("civilizationEventPublishBudget", 12, 1, 64);
      this.civilizationMigrationBudget = builder.comment("Max migration transactions per demographics stage")
         .translation("millenaire.config.civilization.migrationBudget")
         .defineInRange("civilizationMigrationBudget", 2, 0, 16);
      this.civilizationImmigrationEnabled = builder.comment("Allow civilians to immigrate into player-founded sovereign settlements")
         .translation("millenaire.config.civilization.immigrationEnabled")
         .define("civilizationImmigrationEnabled", true);
      this.civilizationImmigrationBudget = builder.comment("Max immigration ops (pulls + travelers) per demographics stage")
         .translation("millenaire.config.civilization.immigrationBudget")
         .defineInRange("civilizationImmigrationBudget", 2, 0, 16);
      this.civilizationTravelerImmigrationEnabled = builder.comment("When sources are scarce, allow capped traveler spawns into sovereign housing")
         .translation("millenaire.config.civilization.travelerImmigrationEnabled")
         .define("civilizationTravelerImmigrationEnabled", true);
      this.civilizationTravelerImmigrationDailyCap = builder.comment("Max traveler immigrants per sovereign settlement per Minecraft day")
         .translation("millenaire.config.civilization.travelerImmigrationDailyCap")
         .defineInRange("civilizationTravelerImmigrationDailyCap", 1, 0, 8);
      this.civilizationCatchupDays = builder.comment("Max Minecraft days of offscreen profile-refresh catch-up after long inactivity")
         .translation("millenaire.config.civilization.catchupDays")
         .defineInRange("civilizationCatchupDays", 7, 1, 30);
      this.civilizationColonizationOpsBudget = builder.comment("Max new autonomous colonization operations started per development stage")
         .translation("millenaire.config.civilization.colonizationOpsBudget")
         .defineInRange("civilizationColonizationOpsBudget", 1, 0, 4);
      this.civilizationColonizationCandidateBudget = builder.comment("Max parent settlements scanned for colonization per stage")
         .translation("millenaire.config.civilization.colonizationCandidateBudget")
         .defineInRange("civilizationColonizationCandidateBudget", 4, 1, 32);
      this.civilizationColonizationCooldownDays = builder.comment("Cooldown days before the same parent may start another colonization")
         .translation("millenaire.config.civilization.colonizationCooldownDays")
         .defineInRange("civilizationColonizationCooldownDays", 7, 1, 60);
      this.civilizationIntraFactionLogistics = builder.comment(
            "Allow conserved same-faction goods movement driven by local needs (logistics, not diplomatic trade)"
         )
         .translation("millenaire.config.civilization.intraFactionLogistics")
         .define("civilizationIntraFactionLogistics", true);
      this.cultureBountyEnabled = builder.comment("Enable culture bounty: player crimes raise wanted status, raid waves, and war")
         .translation("millenaire.config.civilization.cultureBountyEnabled")
         .define("cultureBountyEnabled", true);
      this.cultureBountyChatEnabled = builder.comment("Send chat when bounty score increases")
         .translation("millenaire.config.civilization.cultureBountyChatEnabled")
         .define("cultureBountyChatEnabled", true);
      this.cultureBountyOnSightEnabled = builder.comment("Guards pursue wanted players on sight in villages of that culture")
         .translation("millenaire.config.civilization.cultureBountyOnSightEnabled")
         .define("cultureBountyOnSightEnabled", true);
      this.cultureBountyOnSightTier = builder.comment("Minimum wanted tier for on-sight guard pursuit (NONE/SUSPECT/WANTED/PUBLIC_ENEMY/SCOURGE). HUD shows any positive bounty.")
         .translation("millenaire.config.civilization.cultureBountyOnSightTier")
         .define("cultureBountyOnSightTier", "WANTED");
      this.treatCreativeAsSurvival = builder.comment(
            new String[]{
               "When true, creative players still earn culture bounty, get pursued by guards,",
               "and trigger assault/murder reactions as if in survival. Spectator is always ignored.",
               "Vanilla creative invulnerability still blocks most damage (and thus arrests)."
            }
         )
         .translation("millenaire.config.civilization.treatCreativeAsSurvival")
         .define("treatCreativeAsSurvival", true);
      this.bountyHudEnabled = builder.comment("Show persistent bounty HUD for wanted cultures")
         .translation("millenaire.config.civilization.bountyHudEnabled")
         .define("bountyHudEnabled", true);
      this.pickpocketEnabled = builder.comment("Allow sneak+empty-hand pickpocketing of villagers")
         .translation("millenaire.config.civilization.pickpocketEnabled")
         .define("pickpocketEnabled", true);
      this.pickpocketBaseChance = builder.comment("Base success chance for pickpocketing (0.0-1.0)")
         .translation("millenaire.config.civilization.pickpocketBaseChance")
         .defineInRange("pickpocketBaseChance", 0.55, 0.0, 1.0);
      this.arrestEnabled = builder.comment("When guards would kill a wanted player, arrest (soft-death) instead")
         .translation("millenaire.config.civilization.arrestEnabled")
         .define("arrestEnabled", true);
      this.arrestLockSeconds = builder.comment("Seconds before an arrested player may freely reclaim jail-chest items")
         .translation("millenaire.config.civilization.arrestLockSeconds")
         .defineInRange("arrestLockSeconds", 60, 5, 3600);
      this.arrestBountyOnArrest = builder.comment("Bounty score change on arrest (0 = keep score, negative reduces)")
         .translation("millenaire.config.civilization.arrestBountyOnArrest")
         .defineInRange("arrestBountyOnArrest", 0, -500, 0);
      this.playerShopEnabled = builder.comment("Allow designated SHOP buildings and visitor purchases")
         .translation("millenaire.config.civilization.playerShopEnabled")
         .define("playerShopEnabled", true);
      this.roadTravelerShopVisits = builder.comment("Spawn road travelers that visit player shops")
         .translation("millenaire.config.civilization.roadTravelerShopVisits")
         .define("roadTravelerShopVisits", true);
      this.cultureBountyRaidThreshold = builder.comment("Bounty score at which cultures begin planning raid waves on the player")
         .translation("millenaire.config.civilization.cultureBountyRaidThreshold")
         .defineInRange("cultureBountyRaidThreshold", 120, 40, 2000);
      this.cultureBountyWarThreshold = builder.comment("Bounty score at which the culture declares war on the player's faction")
         .translation("millenaire.config.civilization.cultureBountyWarThreshold")
         .defineInRange("cultureBountyWarThreshold", 250, 120, 2000);
      this.cultureBountyStockPerWave = builder.comment("Player settlement stock items per additional bounty raid wave")
         .translation("millenaire.config.civilization.cultureBountyStockPerWave")
         .defineInRange("cultureBountyStockPerWave", 256, 32, 10000);
      this.bountyHunterEnabled = builder.comment("Dispatch professional bounty hunters against players with enforceable culture bounties")
         .translation("millenaire.config.civilization.bountyHunterEnabled")
         .define("bountyHunterEnabled", true);
      this.bountyHunterMaxPerPlayer = builder.comment("Maximum active bounty hunters assigned to one player")
         .translation("millenaire.config.civilization.bountyHunterMaxPerPlayer")
         .defineInRange("bountyHunterMaxPerPlayer", 1, 1, 3);
      this.bountyHunterDispatchDelaySeconds = builder.comment("Seconds after a bounty becomes enforceable before a hunter may spawn")
         .translation("millenaire.config.civilization.bountyHunterDispatchDelaySeconds")
         .defineInRange("bountyHunterDispatchDelaySeconds", 45, 10, 600);
      this.bountyHunterReplacementCooldownTicks = builder.comment("Game ticks before a defeated hunter may be redispatched (24000 = 1 Minecraft day)")
         .translation("millenaire.config.civilization.bountyHunterReplacementCooldownTicks")
         .defineInRange("bountyHunterReplacementCooldownTicks", 24000, 200, 240000);
      this.bountyHunterBolaCooldownSeconds = builder.comment("Bola throw cooldown in seconds")
         .translation("millenaire.config.civilization.bountyHunterBolaCooldownSeconds")
         .defineInRange("bountyHunterBolaCooldownSeconds", 12, 4, 60);
      this.bountyHunterBolaSlownessSeconds = builder.comment("Slowness II duration from a bola hit in seconds")
         .translation("millenaire.config.civilization.bountyHunterBolaSlownessSeconds")
         .defineInRange("bountyHunterBolaSlownessSeconds", 3, 1, 15);
      this.bountyHunterArrestChannelSeconds = builder.comment("Visible restraint channel time before handcuff arrest completes")
         .translation("millenaire.config.civilization.bountyHunterArrestChannelSeconds")
         .defineInRange("bountyHunterArrestChannelSeconds", 2, 1, 10);
      this.bountyHunterJailSentenceSeconds = builder.comment("Server-authoritative jail custody duration after bounty-hunter arrest")
         .translation("millenaire.config.civilization.bountyHunterJailSentenceSeconds")
         .defineInRange("bountyHunterJailSentenceSeconds", 90, 15, 3600);
      this.bountyHunterSettlementReduction = builder.comment("Bounty score removed on successful hunter capture (0 = clear entire culture bounty)")
         .translation("millenaire.config.civilization.bountyHunterSettlementReduction")
         .defineInRange("bountyHunterSettlementReduction", 0, 0, 2000);
      this.bountyHunterMinTier = builder.comment("Minimum wanted tier that allows bounty-hunter dispatch (NONE/SUSPECT/WANTED/PUBLIC_ENEMY/SCOURGE)")
         .translation("millenaire.config.civilization.bountyHunterMinTier")
         .define("bountyHunterMinTier", "WANTED");
      this.bountyHunterHuntInOverworld = builder.comment("Allow bounty hunters to pursue targets in the Overworld")
         .translation("millenaire.config.civilization.bountyHunterHuntInOverworld")
         .define("bountyHunterHuntInOverworld", true);
      this.bountyHunterAllowPeaceful = builder.comment("Allow bounty-hunter pursuit on Peaceful difficulty")
         .translation("millenaire.config.civilization.bountyHunterAllowPeaceful")
         .define("bountyHunterAllowPeaceful", false);
      this.civilizationDiplomaticAiEnabled = builder.comment("Allow NPC factions to form/break treaties and escalate wars autonomously (budget-capped)")
         .translation("millenaire.config.civilization.diplomaticAiEnabled")
         .define("civilizationDiplomaticAiEnabled", true);
      this.civilizationDiplomaticAiBudget = builder.comment("Max autonomous diplomatic actions per diplomacy stage")
         .translation("millenaire.config.civilization.diplomaticAiBudget")
         .defineInRange("civilizationDiplomaticAiBudget", 2, 0, 16);
      builder.pop();

      builder.comment(
            new String[]{
               "Multi-core Compute/Apply — pure planning/path search on worker threads.",
               "World mutations (blocks, entities, SavedData writes) always stay on the server thread.",
               "Each phase has a kill-switch that falls back to the previous synchronous path."
            }
         )
         .push("compute");
      this.computeOffloadEnabled = builder.comment("Master switch for Millenaire compute offload pool")
         .translation("millenaire.config.compute.offloadEnabled")
         .define("computeOffloadEnabled", true);
      this.computeWorkerThreads = builder.comment(
            "Worker threads for pure compute (0 = auto: clamp(processors/4, 2, 8))"
         )
         .translation("millenaire.config.compute.workerThreads")
         .defineInRange("computeWorkerThreads", 0, 0, 32);
      this.computeApplyBudgetPerTick = builder.comment("Max completed compute results applied per server tick")
         .translation("millenaire.config.compute.applyBudgetPerTick")
         .defineInRange("computeApplyBudgetPerTick", 8, 1, 64);
      this.computeOffloadVillagePaths = builder.comment("Offload village path A* searches to worker threads")
         .translation("millenaire.config.compute.offloadVillagePaths")
         .define("computeOffloadVillagePaths", true);
      this.computeOffloadHighwayPlan = builder.comment("Offload highway graph link planning to worker threads")
         .translation("millenaire.config.compute.offloadHighwayPlan")
         .define("computeOffloadHighwayPlan", true);
      this.computeOffloadWorldgenPlan = builder.comment("Offload settlement region plan decisions to worker threads")
         .translation("millenaire.config.compute.offloadWorldgenPlan")
         .define("computeOffloadWorldgenPlan", true);
      this.computeOffloadWaypointGraph = builder.comment("Offload waypoint-graph edge batch prep to worker threads")
         .translation("millenaire.config.compute.offloadWaypointGraph")
         .define("computeOffloadWaypointGraph", true);
      builder.pop();
   }

   public int effectiveNpcChatLeaveRange() {
      int chat = this.npcChatChatRange.getAsInt();
      int leave = this.npcChatLeaveRange.getAsInt();
      return Math.max(chat, leave);
   }

   static {
      Pair<MillenaireServerConfig, ModConfigSpec> pair = new Builder().configure(MillenaireServerConfig::new);
      SERVER = (MillenaireServerConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
