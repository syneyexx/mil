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
   public final IntValue playerFoundTerritoryWarDistance;
   public final IntValue playerCityDesignationRadius;
   public final IntValue minLoneBuildingDistance;
   public final IntValue minVillageLoneBuildingDistance;
   public final IntValue spawnProtectionRadius;
   public final IntValue completionMaxPercentage;
   public final IntValue completionMinDistance;
   public final IntValue completionMaxDistance;
   public final BooleanValue logSpawnAttempts;
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
   public final IntValue sentenceDistanceSingleplayer;
   public final IntValue sentenceDistanceMultiplayer;
   public final BooleanValue ignoreResourceCost;
   public final BooleanValue sendStatistics;
   public final BooleanValue sendPlayerName;
   public final ConfigValue<String> apiBaseUrlOverride;
   public final IntValue banditRaidRadius;
   public final IntValue raidingRate;
   public final IntValue maxConcurrentRaids;
   public final IntValue illagerRaidPackSize;
   public final IntValue illagerCampGarrisonCap;
   public final IntValue illagerLoneBuildingQuota;
   public final IntValue cultureStandingDriftChance;
   public final IntValue cultureWarAutoThreshold;
   public final IntValue cultureWarRaidAttemptsPerNight;
   public final BooleanValue placeWaystonesInMillenaire;
   public final BooleanValue placeWaystonesInVanilla;
   public final BooleanValue vanillaVillagersUseDeniers;
   public final IntValue emeraldToDenierRate;
   public final IntValue immigrantAutoPeriodTicks;
   public final IntValue immigrantAutoPerDay;
   public final IntValue immigrantManualPerDay;
   public final IntValue immigrantMaxQueue;
   public final IntValue immigrantDenierCost;
   public final IntValue cityStatsFailsafeTicks;
   public final BooleanValue legacyAutoConvert;
   public final IntValue legacyAutoConvertMaxPngs;
   public final BooleanValue enableCrime;
   public final BooleanValue resetBountyOnDeath;
   public final IntValue crimeTradeBanThreshold;
   public final IntValue crimeHostileThreshold;
   public final IntValue crimeRaidCityThreshold;
   public final IntValue faithFestivalIntervalDays;
   public final IntValue faithFestivalDurationTicks;
   public final BooleanValue religionEnabled;
   public final BooleanValue ordersEnabled;
   public final BooleanValue npcOrderConversionEnabled;
   public final BooleanValue religionVisualEffectsEnabled;
   public final BooleanValue religionSupernaturalEnabled;
   public final IntValue religionCatchupLimit;
   public final IntValue religionPlannerThreads;
   public final IntValue religionMinPopForOrder;
   public final DoubleValue religionOrderSpawnChance;
   public final BooleanValue roadsEnabled;
   public final IntValue roadsWidth;
   public final IntValue roadsMaxEdgeLength;
   public final IntValue roadsHardMaxEdgeLength;
   public final IntValue roadsKNearest;
   public final ConfigValue<Double> roadsLoopRatio;
   public final ConfigValue<Double> roadsMaxMillisPerTick;
   public final IntValue roadsMaxChunksPerTick;
   public final IntValue roadsMaxQueueSize;
   public final ConfigValue<Double> roadsMsptPauseThreshold;
   public final IntValue roadsTunnelThreshold;
   public final IntValue roadsMaxBridgeLength;
   public final BooleanValue roadsBridgesEnabled;
   public final BooleanValue roadsTunnelsEnabled;
   public final IntValue roadsPlannerThreads;
   public final BooleanValue settlementsEnabled;
   public final ConfigValue<String> settlementsFallbackCulture;
   public final BooleanValue settlementsTakeoverEnabled;
   public final IntValue settlementsMaxActivePromotions;
   public final IntValue settlementsTakeoverScanInterval;
   public final BooleanValue bankRequiredForCityPurchases;
   public final IntValue bankShopSurplusShare;
   public final IntValue bankSimulatedNpcTax;
   public final IntValue bankMaxLedgerEntries;
   public final BooleanValue warCampaignsEnabled;
   public final IntValue warWavesPerCampaign;
   public final IntValue warWaveIntervalTicks;
   public final IntValue warMaxLevy;
   public final BooleanValue warPlayerDeathIsDefeat;
   public final BooleanValue warBuildingDamageEnabled;
   public final IntValue warDamageBlocksPerTick;
   public final IntValue warDamageBlocksPerWave;
   public final IntValue warThDamageCap;

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
      this.playerFoundTerritoryWarDistance = builder.comment(
            "Distance under which founding a player city with the VillageCreate wand triggers unavoidable war. Default 100; minVillageDistance stays separate."
         )
         .translation("millenaire.config.playerFoundTerritoryWarDistance")
         .defineInRange("playerFoundTerritoryWarDistance", 100, 50, 2000);
      this.playerCityDesignationRadius = builder.comment(
            "How far from a player city center the player can designate building functions (blocks). Separate from village radius (overlap/growth)."
         )
         .translation("millenaire.config.playerCityDesignationRadius")
         .defineInRange("playerCityDesignationRadius", 750, 50, 2000);
      this.minLoneBuildingDistance = builder.comment("Minimum distance between two lone buildings (blocks)")
         .translation("millenaire.config.minLoneBuildingDistance")
         .defineInRange("minLoneBuildingDistance", 500, 300, 1000);
      this.minVillageLoneBuildingDistance = builder.comment("Minimum distance between a village and a lone building (blocks)")
         .translation("millenaire.config.minVillageLoneBuildingDistance")
         .defineInRange("minVillageLoneBuildingDistance", 250, 100, 800);
      this.spawnProtectionRadius = builder.comment("Protected area around world spawn where nothing generates (blocks)")
         .translation("millenaire.config.spawnProtectionRadius")
         .defineInRange("spawnProtectionRadius", 250, 0, 500);
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
         .define("forceLoadVillages", true);
      this.keepActiveRadius = builder.comment("Radius for keeping village chunks loaded (blocks). 0 = disabled.")
         .translation("millenaire.config.keepActiveRadius")
         .defineInRange("keepActiveRadius", 200, 0, 2000);
      this.villageRadiusOverride = builder.comment(
            new String[]{
               "Override all village type radii with this value (blocks).",
               "-1 = use per-type JSON value (player-controlled cities use radius 100).",
               "Does not affect playerCityDesignationRadius.",
               "Requires world restart to take effect."
            }
         )
         .translation("millenaire.config.villageRadiusOverride")
         .worldRestart()
         .defineInRange("villageRadiusOverride", -1, -1, 2000);
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
      builder.comment("Raid and bandit presence settings (Phase 0 tick contract caps)").push("raids");
      this.banditRaidRadius = builder.comment("Radius for bandit raids (blocks). 0 = disabled.")
         .translation("millenaire.config.banditRaidRadius")
         .defineInRange("banditRaidRadius", 1500, 0, 2000);
      this.raidingRate = builder.comment("% chance per night of a raid attempt. 0 = disabled.")
         .translation("millenaire.config.raidingRate")
         .defineInRange("raidingRate", 20, 0, 100);
      this.maxConcurrentRaids = builder.comment("Max simultaneous active raids world-wide. Extra plans are skipped.")
         .translation("millenaire.config.maxConcurrentRaids")
         .defineInRange("maxConcurrentRaids", 3, 1, 16);
      this.illagerRaidPackSize = builder.comment("Max vanilla Illagers spawned per hybrid raid wave.")
         .translation("millenaire.config.illagerRaidPackSize")
         .defineInRange("illagerRaidPackSize", 12, 0, 32);
      this.illagerCampGarrisonCap = builder.comment("Soft cap of vanilla Illagers maintained at an active Illager camp.")
         .translation("millenaire.config.illagerCampGarrisonCap")
         .defineInRange("illagerCampGarrisonCap", 6, 0, 16);
      this.illagerLoneBuildingQuota = builder.comment("Max Illager camps counted toward the dedicated lone-building spawn quota.")
         .translation("millenaire.config.illagerLoneBuildingQuota")
         .defineInRange("illagerLoneBuildingQuota", 24, 0, 200);
      builder.pop();
      builder.comment("Culture-level diplomacy and war").push("war");
      this.cultureStandingDriftChance = builder.comment("% chance per night for each culture pair to drift in standing. 0 = disabled.")
         .translation("millenaire.config.cultureStandingDriftChance")
         .defineInRange("cultureStandingDriftChance", 25, 0, 100);
      this.cultureWarAutoThreshold = builder.comment("Standing at or below this value auto-declares war between cultures.")
         .translation("millenaire.config.cultureWarAutoThreshold")
         .defineInRange("cultureWarAutoThreshold", -75, -100, 0);
      this.cultureWarRaidAttemptsPerNight = builder.comment("Max culture-war raid plans scheduled per Minecraft night.")
         .translation("millenaire.config.cultureWarRaidAttemptsPerNight")
         .defineInRange("cultureWarRaidAttemptsPerNight", 1, 0, 16);
      builder.pop();
      builder.comment("Optional Waystones integration").push("waystones");
      this.placeWaystonesInMillenaire = builder.comment("Place a Waystone near Millenaire town halls when the Waystones mod is loaded.")
         .translation("millenaire.config.placeWaystonesInMillenaire")
         .define("placeWaystonesInMillenaire", true);
      this.placeWaystonesInVanilla = builder.comment("Place a Waystone at discovered vanilla village centers when the Waystones mod is loaded.")
         .translation("millenaire.config.placeWaystonesInVanilla")
         .define("placeWaystonesInVanilla", true);
      builder.pop();
      builder.comment("Vanilla villager economy").push("vanilla_economy");
      this.vanillaVillagersUseDeniers = builder.comment("Replace emerald costs/results in vanilla villager and wandering trader offers with Millenaire deniers.")
         .translation("millenaire.config.vanillaVillagersUseDeniers")
         .define("vanillaVillagersUseDeniers", true);
      this.emeraldToDenierRate = builder.comment("How many bronze deniers equal one emerald when converting vanilla trades (64 equals 1 argent).")
         .translation("millenaire.config.emeraldToDenierRate")
         .defineInRange("emeraldToDenierRate", 64, 1, 4096);
      builder.pop();
      builder.comment("Controlled city management (immigrants / jobs) — Phase 0 budgets").push("city");
      this.immigrantAutoPeriodTicks = builder.comment("Staggered period (ticks) between auto immigrant checks per village.")
         .translation("millenaire.config.immigrantAutoPeriodTicks")
         .defineInRange("immigrantAutoPeriodTicks", 300, 40, 2400);
      this.immigrantAutoPerDay = builder.comment("Max automatic immigrant arrivals per controlled village per Minecraft day.")
         .translation("millenaire.config.immigrantAutoPerDay")
         .defineInRange("immigrantAutoPerDay", 2, 0, 20);
      this.immigrantManualPerDay = builder.comment("Max manual 'call immigrants' successes per controlled village per Minecraft day.")
         .translation("millenaire.config.immigrantManualPerDay")
         .defineInRange("immigrantManualPerDay", 1, 0, 10);
      this.immigrantMaxQueue = builder.comment("Max open immigrant / simultaneous spawn budget per village.")
         .translation("millenaire.config.immigrantMaxQueue")
         .defineInRange("immigrantMaxQueue", 3, 1, 20);
      this.immigrantDenierCost = builder.comment("Deniers taken from town hall stock per immigrant (0 = free).")
         .translation("millenaire.config.immigrantDenierCost")
         .defineInRange("immigrantDenierCost", 64, 0, 10000);
      this.cityStatsFailsafeTicks = builder.comment("Failsafe CityStats recount period when dirty flag was missed.")
         .translation("millenaire.config.cityStatsFailsafeTicks")
         .defineInRange("cityStatsFailsafeTicks", 600, 100, 6000);
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
      builder.comment("Player crime: pickpocket, lockpicking, per-culture bounty").push("crime");
      this.enableCrime = builder.comment("Enable pickpocketing, lockpicking, and culture bounty consequences")
         .translation("millenaire.config.enableCrime")
         .define("enableCrime", true);
      this.resetBountyOnDeath = builder.comment("Clear all culture bounties when the player dies")
         .translation("millenaire.config.resetBountyOnDeath")
         .define("resetBountyOnDeath", true);
      this.crimeTradeBanThreshold = builder.comment("Bounty at or above this blocks trade with the player and their city")
         .translation("millenaire.config.crimeTradeBanThreshold")
         .defineInRange("tradeBanThreshold", 50, 1, 1000);
      this.crimeHostileThreshold = builder.comment("Bounty at or above this makes helpInAttacks villagers attack the player on sight")
         .translation("millenaire.config.crimeHostileThreshold")
         .defineInRange("hostileThreshold", 100, 1, 1000);
      this.crimeRaidCityThreshold = builder.comment("Bounty at or above this can schedule raids against the player's controlled city")
         .translation("millenaire.config.crimeRaidCityThreshold")
         .defineInRange("raidCityThreshold", 200, 1, 1000);
      builder.pop();
      builder.comment("Faith / festivals (day-batched, not per-tick)").push("faith");
      this.faithFestivalIntervalDays = builder.comment("Minecraft days between village festivals")
         .translation("millenaire.config.faithFestivalIntervalDays")
         .defineInRange("festivalIntervalDays", 5, 1, 40);
      this.faithFestivalDurationTicks = builder.comment("How long a festival lasts in ticks")
         .translation("millenaire.config.faithFestivalDurationTicks")
         .defineInRange("festivalDurationTicks", 4000, 200, 24000);
      builder.pop();
      builder.comment("Religion and secret orders (singleplayer-oriented; bounded tick work)").push("religion");
      this.religionEnabled = builder.comment("Master switch for religion simulation; SavedData retained when disabled")
         .translation("millenaire.config.religionEnabled")
         .define("religionEnabled", true);
      this.ordersEnabled = builder.comment("Enable secret order chapters and rituals")
         .translation("millenaire.config.ordersEnabled")
         .define("ordersEnabled", true);
      this.npcOrderConversionEnabled = builder.comment("Allow NPCs to join order chapters")
         .translation("millenaire.config.npcOrderConversionEnabled")
         .define("npcOrderConversionEnabled", true);
      this.religionVisualEffectsEnabled = builder.comment("Budgeted particles/sounds for festivals and rituals")
         .translation("millenaire.config.religionVisualEffectsEnabled")
         .define("visualEffectsEnabled", true);
      this.religionSupernaturalEnabled = builder.comment("Reserved for later supernatural events (default off)")
         .translation("millenaire.config.religionSupernaturalEnabled")
         .define("supernaturalEnabled", false);
      this.religionCatchupLimit = builder.comment("Max ritual deadlines processed after sleep/time-skip")
         .translation("millenaire.config.religionCatchupLimit")
         .defineInRange("catchupLimit", 16, 0, 64);
      this.religionPlannerThreads = builder.comment("Background religion planner threads")
         .translation("millenaire.config.religionPlannerThreads")
         .defineInRange("plannerThreads", 2, 1, 4);
      this.religionMinPopForOrder = builder.comment("Minimum villagers before a secret order may seed")
         .translation("millenaire.config.religionMinPopForOrder")
         .defineInRange("minPopForOrder", 8, 1, 64);
      this.religionOrderSpawnChance = builder.comment("Chance a eligible village seeds an order on plan")
         .translation("millenaire.config.religionOrderSpawnChance")
         .defineInRange("orderSpawnChance", 0.25, 0.0, 1.0);
      builder.pop();
      builder.comment("Inter-village road network (async planning, budgetted building)").push("roads");
      this.roadsEnabled = builder.comment("Enable automatic roads between known villages")
         .translation("millenaire.config.roadsEnabled")
         .define("enabled", true);
      this.roadsWidth = builder.comment("Road width in blocks (2-5)")
         .translation("millenaire.config.roadsWidth")
         .defineInRange("width", 3, 2, 5);
      this.roadsMaxEdgeLength = builder.comment("Max candidate edge length for k-nearest graph (blocks)")
         .translation("millenaire.config.roadsMaxEdgeLength")
         .defineInRange("maxEdgeLength", 1500, 200, 8000);
      this.roadsHardMaxEdgeLength = builder.comment("Hard max when linking disconnected components")
         .translation("millenaire.config.roadsHardMaxEdgeLength")
         .defineInRange("hardMaxEdgeLength", 4000, 500, 16000);
      this.roadsKNearest = builder.comment("k-nearest neighbors per node before MST")
         .translation("millenaire.config.roadsKNearest")
         .defineInRange("kNearest", 3, 2, 6);
      this.roadsLoopRatio = builder.comment("Extra loop edges as fraction of MST size (0.10-0.25)")
         .translation("millenaire.config.roadsLoopRatio")
         .defineInRange("loopRatio", 0.15, 0.0, 0.5);
      this.roadsMaxMillisPerTick = builder.comment("RoadBuilder time budget per tick (ms)")
         .translation("millenaire.config.roadsMaxMillisPerTick")
         .defineInRange("maxMillisPerTick", 1.0, 0.1, 10.0);
      this.roadsMaxChunksPerTick = builder.comment("Max chunks built per tick")
         .translation("millenaire.config.roadsMaxChunksPerTick")
         .defineInRange("maxChunksPerTick", 1, 1, 8);
      this.roadsMaxQueueSize = builder.comment("Max pending build chunk jobs")
         .translation("millenaire.config.roadsMaxQueueSize")
         .defineInRange("maxQueueSize", 2048, 64, 16384);
      this.roadsMsptPauseThreshold = builder.comment("Pause building when smoothed MSPT exceeds this")
         .translation("millenaire.config.roadsMsptPauseThreshold")
         .defineInRange("msptPauseThreshold", 45.0, 20.0, 100.0);
      this.roadsTunnelThreshold = builder.comment("Height above road that triggers tunnel mode")
         .translation("millenaire.config.roadsTunnelThreshold")
         .defineInRange("tunnelThreshold", 12, 4, 48);
      this.roadsMaxBridgeLength = builder.comment("Max bridge length in blocks")
         .translation("millenaire.config.roadsMaxBridgeLength")
         .defineInRange("maxBridgeLength", 48, 8, 128);
      this.roadsBridgesEnabled = builder.comment("Allow bridge segments")
         .translation("millenaire.config.roadsBridgesEnabled")
         .define("bridgesEnabled", true);
      this.roadsTunnelsEnabled = builder.comment("Allow tunnel segments")
         .translation("millenaire.config.roadsTunnelsEnabled")
         .define("tunnelsEnabled", true);
      this.roadsPlannerThreads = builder.comment("Async route planner thread count (1-2)")
         .translation("millenaire.config.roadsPlannerThreads")
         .defineInRange("plannerThreads", 1, 1, 2);
      builder.pop();
      builder.comment("Thin settlement registry + capped vanilla takeover").push("settlements");
      this.settlementsEnabled = builder.comment("Track vanilla/Millenaire settlements with name and culture")
         .translation("millenaire.config.settlementsEnabled")
         .define("enabled", true);
      this.settlementsFallbackCulture = builder.comment("Fallback culture id when biome matches none")
         .translation("millenaire.config.settlementsFallbackCulture")
         .define("fallbackCulture", "millenaire:norman");
      builder.push("takeover");
      this.settlementsTakeoverEnabled = builder.comment("Promote nearby vanilla settlements to Millenaire villages (capped)")
         .translation("millenaire.config.settlementsTakeoverEnabled")
         .define("enabled", true);
      this.settlementsMaxActivePromotions = builder.comment("Hard cap on active promoted vanilla settlements")
         .translation("millenaire.config.settlementsMaxActivePromotions")
         .defineInRange("maxActivePromotions", 4, 0, 32);
      this.settlementsTakeoverScanInterval = builder.comment("Ticks between promote/demote scans")
         .translation("millenaire.config.settlementsTakeoverScanInterval")
         .defineInRange("scanIntervalTicks", 20, 5, 200);
      builder.pop();
      builder.pop();
      builder.comment("Controlled city bank / treasury").push("bank");
      this.bankRequiredForCityPurchases = builder.comment("City purchases (immigrants etc.) require treasury balance; town hall is fallback if no Bank")
         .translation("millenaire.config.bankRequiredForCityPurchases")
         .define("requiredForCityPurchases", true);
      this.bankShopSurplusShare = builder.comment("Percent of player shop-buy deniers deposited into the village treasury (0-100)")
         .translation("millenaire.config.bankShopSurplusShare")
         .defineInRange("shopSurplusShare", 50, 0, 100);
      this.bankSimulatedNpcTax = builder.comment("Simulated daily deniers deposited into controlled-city treasury (0 = off)")
         .translation("millenaire.config.bankSimulatedNpcTax")
         .defineInRange("simulatedNpcTax", 8, 0, 1000);
      this.bankMaxLedgerEntries = builder.comment("Max treasury ledger lines kept per village")
         .translation("millenaire.config.bankMaxLedgerEntries")
         .defineInRange("maxLedgerEntries", 32, 8, 128);
      builder.pop();
      builder.comment("Wave war campaigns (raids with kill/loot/sabotage)").push("warCampaign");
      this.warCampaignsEnabled = builder.comment("Enable wave war campaigns; when false, legacy RaidManager single raids remain")
         .translation("millenaire.config.warCampaignsEnabled")
         .define("enabled", true);
      this.warWavesPerCampaign = builder.comment("Waves per campaign")
         .translation("millenaire.config.warWavesPerCampaign")
         .defineInRange("wavesPerCampaign", 3, 1, 8);
      this.warWaveIntervalTicks = builder.comment("Ticks between waves")
         .translation("millenaire.config.warWaveIntervalTicks")
         .defineInRange("waveIntervalTicks", 6000, 200, 48000);
      this.warMaxLevy = builder.comment("Max player muster levy size")
         .translation("millenaire.config.warMaxLevy")
         .defineInRange("maxLevy", 8, 1, 32);
      this.warPlayerDeathIsDefeat = builder.comment("Player death during WAVE_FIGHT on the front counts as defeat")
         .translation("millenaire.config.warPlayerDeathIsDefeat")
         .define("playerDeathIsDefeat", true);
      this.warBuildingDamageEnabled = builder.comment("Raiders sabotage buildings with budgeted block damage")
         .translation("millenaire.config.warBuildingDamageEnabled")
         .define("buildingDamageEnabled", true);
      this.warDamageBlocksPerTick = builder.comment("Max war sabotage setBlock ops per tick world-wide")
         .translation("millenaire.config.warDamageBlocksPerTick")
         .defineInRange("damageBlocksPerTick", 12, 1, 64);
      this.warDamageBlocksPerWave = builder.comment("Max blocks broken per wave per front")
         .translation("millenaire.config.warDamageBlocksPerWave")
         .defineInRange("damageBlocksPerWave", 48, 4, 512);
      this.warThDamageCap = builder.comment("Soft warDamageLevel cap for town hall (never demolished)")
         .translation("millenaire.config.warThDamageCap")
         .defineInRange("thDamageCap", 40, 0, 100);
      builder.pop();
   }

   static {
      Pair<MillenaireServerConfig, ModConfigSpec> pair = new Builder().configure(MillenaireServerConfig::new);
      SERVER = (MillenaireServerConfig)pair.getLeft();
      SPEC = (ModConfigSpec)pair.getRight();
   }
}
