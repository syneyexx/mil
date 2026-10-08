package org.millenaire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
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
      this.npcChatBaseUrl = builder.comment("API base URL without a trailing slash (e.g. https://api.x.ai/v1)")
         .translation("millenaire.config.npcChat.baseUrl")
         .define("baseUrl", "https://api.x.ai/v1");
      this.npcChatModel = builder.comment("Model id sent in the chat completions body")
         .translation("millenaire.config.npcChat.model")
         .define("model", "grok-4-latest");
      this.npcChatApiKey = builder.comment(
            new String[]{
               "API key for NPC chat. Leave empty to read MILLENAIRE_NPC_API_KEY from the environment.",
               "Never log this value. An empty key disables the model while still storing player quotes."
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
