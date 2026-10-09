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
   public final IntValue worldgenRegionSizeChunks;
   public final DoubleValue worldgenSettlementDensity;
   public final DoubleValue worldgenLoneBuildingDensity;
   public final DoubleValue worldgenHamletDensity;
   public final IntValue worldgenMinimumSettlementSpacing;
   public final IntValue worldgenProtectedSpawnRadius;
   public final IntValue worldgenMaximumSettlementFootprint;
   public final BooleanValue worldgenAllowHillyTerrain;
   public final IntValue worldgenMaximumTerrainSlope;
   public final IntValue worldgenMaximumFoundationDepth;
   public final IntValue worldgenMaximumTerrainModification;
   public final BooleanValue worldgenUseNativeStructures;
   public final BooleanValue worldgenGenerateInitialRoads;
   public final BooleanValue worldgenGenerateRegionalRoads;
   public final BooleanValue worldgenGenerateBridges;
   public final BooleanValue worldgenGenerateStartingWalls;
   public final BooleanValue worldgenPreserveVanillaStructures;
   public final BooleanValue worldgenPreserveModdedStructures;
   public final IntValue worldgenStructureSafetyMargin;
   public final BooleanValue worldgenEnableSettlementVariation;
   public final BooleanValue worldgenAllowPartialInitialCompletion;
   public final IntValue worldgenPlanningCacheSize;
   public final IntValue worldgenMaterializationBudget;
   public final IntValue worldgenActivationBudget;
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
      builder.comment(
            new String[]{
               "Phase 4 deterministic worldgen engine. When enabled, natural settlements are planned from",
               "the world seed + region grid and materialized per new chunk. Legacy VillageSpawnQueue",
               "enqueue for natural spawns is suppressed. Manual /millenaire spawn commands still use",
               "VillageSpawner. Legacy density keys (generateVillages, minVillageDistance, etc.) remain",
               "authoritative minimums; worldgen* keys refine planner behaviour without renaming old keys."
            }
         )
         .push("worldgen");
      this.worldgenEngineEnabled = builder.comment("Enable Phase 4 deterministic settlement worldgen engine")
         .translation("millenaire.config.worldgenEngineEnabled")
         .define("worldgenEngineEnabled", true);
      this.worldgenRegionSizeChunks = builder.comment("Region grid size in chunks for candidate distribution (independent of village radius)")
         .translation("millenaire.config.worldgenRegionSizeChunks")
         .defineInRange("worldgenRegionSizeChunks", 32, 8, 128);
      this.worldgenSettlementDensity = builder.comment("Probability a region attempts a full village (0-1)")
         .translation("millenaire.config.worldgenSettlementDensity")
         .defineInRange("worldgenSettlementDensity", 0.35D, 0.0D, 1.0D);
      this.worldgenLoneBuildingDensity = builder.comment("Probability a region attempts a lone building when no village is selected (0-1)")
         .translation("millenaire.config.worldgenLoneBuildingDensity")
         .defineInRange("worldgenLoneBuildingDensity", 0.15D, 0.0D, 1.0D);
      this.worldgenHamletDensity = builder.comment("Reserved density weight for hamlet satellite planning (0-1)")
         .translation("millenaire.config.worldgenHamletDensity")
         .defineInRange("worldgenHamletDensity", 0.5D, 0.0D, 1.0D);
      this.worldgenMinimumSettlementSpacing = builder.comment(
            "Minimum spacing between planned settlements (blocks). Effective spacing is max(this, minVillageDistance)."
         )
         .translation("millenaire.config.worldgenMinimumSettlementSpacing")
         .defineInRange("worldgenMinimumSettlementSpacing", 500, 200, 2000);
      this.worldgenProtectedSpawnRadius = builder.comment(
            "Additional protected radius around world spawn (blocks). Effective protection is max(this, spawnProtectionRadius)."
         )
         .translation("millenaire.config.worldgenProtectedSpawnRadius")
         .defineInRange("worldgenProtectedSpawnRadius", 250, 0, 1000);
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
      this.worldgenGenerateStartingWalls = builder.comment("Plan and materialize starting walls/gates when village type defines them")
         .translation("millenaire.config.worldgenGenerateStartingWalls")
         .define("worldgenGenerateStartingWalls", true);
      this.worldgenPreserveVanillaStructures = builder.comment("Reject settlement candidates that conflict with protected vanilla structures")
         .translation("millenaire.config.worldgenPreserveVanillaStructures")
         .define("worldgenPreserveVanillaStructures", true);
      this.worldgenPreserveModdedStructures = builder.comment("Reserved: respect other mods' registered structures when metadata is available")
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
      this.worldgenMaterializationBudget = builder.comment("Max block placement operations per chunk materialization pass")
         .translation("millenaire.config.worldgenMaterializationBudget")
         .defineInRange("worldgenMaterializationBudget", 8000, 500, 50000);
      this.worldgenActivationBudget = builder.comment("Max settlement activations per server tick")
         .translation("millenaire.config.worldgenActivationBudget")
         .defineInRange("worldgenActivationBudget", 1, 1, 8);
      this.worldgenDetailedDiagnostics = builder.comment("Verbose worldgen planning/materialization logs (opt-in)")
         .translation("millenaire.config.worldgenDetailedDiagnostics")
         .define("worldgenDetailedDiagnostics", false);
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
               "auto detects LM Studio from the base URL (e.g. http://localhost:1234/v1).",
               "lmstudio forces GET /api/v1/models discovery and loaded_instances selection.",
               "openai keeps the configured model name for remote OpenAI-compatible APIs."
            }
         )
         .translation("millenaire.config.npcChat.provider")
         .define("provider", "auto");
      this.npcChatBaseUrl = builder.comment(
            new String[]{
               "API base URL without a trailing slash.",
               "Remote example: https://api.x.ai/v1",
               "LM Studio local example: http://localhost:1234/v1 (model name optional; loaded LLMs are auto-detected)."
            }
         )
         .translation("millenaire.config.npcChat.baseUrl")
         .define("baseUrl", "https://api.x.ai/v1");
      this.npcChatModel = builder.comment(
            new String[]{
               "Model id for remote OpenAI-compatible providers.",
               "For LM Studio: leave empty for automatic selection when exactly one LLM is loaded;",
               "when multiple LLMs are loaded, set this (or use /millenaire npcchat model select) to choose one."
            }
         )
         .translation("millenaire.config.npcChat.model")
         .define("model", "grok-4-latest");
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
