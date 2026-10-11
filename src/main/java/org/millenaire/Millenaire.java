package org.millenaire;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.advancement.AdvancementReporter;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.advancement.UsageReporter;
import org.millenaire.block.BlockSilkWorm;
import org.millenaire.block.BlockSnailSoil;
import org.millenaire.block.BlockWetBrick;
import org.millenaire.block.ModBlockEntities;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.ExportTransaction;
import org.millenaire.building.MillenaireStructureLoader;
import org.millenaire.command.ConvertAddonCommand;
import org.millenaire.command.DevCommand;
import org.millenaire.command.QueryCommand;
import org.millenaire.command.ReputationCommand;
import org.millenaire.command.SpawnLoneBuildingCommand;
import org.millenaire.command.SpawnVillageCommand;
import org.millenaire.command.TestCommand;
import org.millenaire.command.TpCommand;
import org.millenaire.command.VillagesCommand;
import org.millenaire.commerce.ModMenuTypes;
import org.millenaire.compat.LegacyRegistryAliases;
import org.millenaire.config.MillenaireClientConfig;
import org.millenaire.config.MillenaireCommonConfig;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.config.VillagerConfig;
import org.millenaire.content.BuiltInCultures;
import org.millenaire.content.ContentDirectoryManager;
import org.millenaire.content.ContentIssueLog;
import org.millenaire.content.ContentStatsReporter;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.NativeContentDeployer;
import org.millenaire.content.ReferenceCatalogGenerator;
import org.millenaire.content.ValidationReport;
import org.millenaire.content.legacy.LegacyAutoConverter;
import org.millenaire.culture.CultureLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.diagnostics.WaypointVisualizationManager;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.faction.FactionLifecycle;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.gathering.GatheringHandlerRegistry;
import org.millenaire.goal.gathering.GatheringTypeLoader;
import org.millenaire.goal.impl.BringBackHomeGoal;
import org.millenaire.goal.impl.BuildGoal;
import org.millenaire.goal.impl.BuildPathGoal;
import org.millenaire.goal.impl.ChatGoal;
import org.millenaire.goal.impl.ChildBecomeAdultGoal;
import org.millenaire.goal.impl.ClearOldPathGoal;
import org.millenaire.goal.impl.DefendVillageGoal;
import org.millenaire.goal.impl.DeliverResourcesToShopGoal;
import org.millenaire.goal.impl.EngageTargetGoal;
import org.millenaire.goal.impl.ForeignMerchantKeepStallGoal;
import org.millenaire.goal.impl.GatherGoodsGoal;
import org.millenaire.goal.impl.GetGoodsForHouseholdGoal;
import org.millenaire.goal.impl.GetResourcesForShopsGoal;
import org.millenaire.goal.impl.GetToolGoal;
import org.millenaire.goal.impl.HideGoal;
import org.millenaire.goal.impl.HiredEscortGoal;
import org.millenaire.goal.impl.HostileVillagerGoal;
import org.millenaire.goal.impl.HuntMonsterGoal;
import org.millenaire.goal.impl.IdleGoal;
import org.millenaire.goal.impl.LightHearthGoal;
import org.millenaire.goal.impl.MerchantVisitBuildingGoal;
import org.millenaire.goal.impl.MerchantVisitInnGoal;
import org.millenaire.goal.impl.RaidVillageGoal;
import org.millenaire.goal.impl.RestGoal;
import org.millenaire.goal.impl.SellerGoal;
import org.millenaire.goal.impl.SocialiseGoal;
import org.millenaire.goal.impl.SpotGatheringGoal;
import org.millenaire.goal.visit.VisitGoalLoader;
import org.millenaire.goal.visit.VisitGoalValidator;
import org.millenaire.item.InuitHuntingDropHandler;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.ModCreativeTabs;
import org.millenaire.item.ModItems;
import org.millenaire.language.I18nKeyAuditor;
import org.millenaire.language.ServerTranslationCache;
import org.millenaire.map.VillageMapDecorationTypes;
import org.millenaire.map.VillageMapMarkerService;
import org.millenaire.net.DonorChecker;
import org.millenaire.net.VersionChecker;
import org.millenaire.network.ModPayloads;
import org.millenaire.network.PlanPreviewRequestPayload;
import org.millenaire.network.QuestNetworkHelper;
import org.millenaire.quest.QuestInstance;
import org.millenaire.quest.QuestLoader;
import org.millenaire.quest.QuestManager;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.tag.ModTags;
import org.millenaire.test.TestPlayerManager;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.TravelBookNavigationState;
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.village.VillageRehydrator;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BiomeTagDisjointnessValidator;
import org.millenaire.world.ModProcessors;
import org.millenaire.world.VillageSpawnQueue;
import org.millenaire.worldgen.WorldgenEngine;
import org.millenaire.worldgen.command.WorldgenDiagCommand;
import org.slf4j.Logger;

@Mod("millenaire")
public class Millenaire {
   public static final String MODID = "millenaire";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static GoalRegistry goalRegistryInstance;
   private static ModContainer modContainerInstance;
   private static VillageSpawnQueue spawnQueueInstance;
   private static volatile boolean naturalSpawnSuppressed = false;

   public static void setNaturalSpawnSuppressed(boolean suppressed) {
      naturalSpawnSuppressed = suppressed;
   }

   public static GoalRegistry getGoalRegistry() {
      return goalRegistryInstance;
   }

   public static ModContainer getModContainer() {
      return modContainerInstance;
   }

   public static String getModVersion() {
      return modContainerInstance.getModInfo().getVersion().toString();
   }

   @Nullable
   public static VillageSpawnQueue getSpawnQueue() {
      return spawnQueueInstance;
   }

   public Millenaire(IEventBus modEventBus, ModContainer modContainer) {
      modContainerInstance = modContainer;
      LOGGER.info("Millénaire {} — Initialisation", modContainer.getModInfo().getVersion());
      modContainer.registerConfig(Type.SERVER, MillenaireServerConfig.SPEC);
      modContainer.registerConfig(Type.COMMON, MillenaireCommonConfig.SPEC);
      modContainer.registerConfig(Type.CLIENT, MillenaireClientConfig.SPEC);
      modEventBus.addListener(ModConfigEvent.class, event -> {
         if (event.getConfig().getType() == Type.COMMON) {
            MillenaireCommonConfig.COMMON.bindLogCategories();
         }
      });
      modEventBus.addListener(ModConfigEvent.class, event -> {
         if (event.getConfig().getType() == Type.COMMON) {
            MillenaireCommonConfig.COMMON.bindLogCategories();
         }
      });
      ModBlocks.register(modEventBus);
      ModBlockEntities.register(modEventBus);
      ModEntities.register(modEventBus);
      ModItems.register(modEventBus);
      ModCreativeTabs.register(modEventBus);
      ModMenuTypes.MENU_TYPES.register(modEventBus);
      ModProcessors.register(modEventBus);
      MillAdvancements.register(modEventBus);
      VillageMapDecorationTypes.register(modEventBus);
      LegacyRegistryAliases.register();
      modEventBus.addListener(ModPayloads::register);
      modEventBus.addListener(
         RegisterCapabilitiesEvent.class,
         event -> event.registerBlockEntity(ItemHandler.BLOCK, (BlockEntityType)ModBlockEntities.LOCKED_CHEST.get(), (blockEntity, side) -> null)
      );
      modEventBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(VillageChunkLoader.getController()));
      GoalRegistry goalRegistry = new GoalRegistry();
      goalRegistry.register(new IdleGoal());
      goalRegistry.register(new RestGoal());
      goalRegistry.register(new BuildGoal());
      goalRegistry.register(new SocialiseGoal());
      goalRegistry.register(new ChatGoal());
      goalRegistry.register(new SellerGoal());
      goalRegistry.register(new ChildBecomeAdultGoal());
      goalRegistry.register(new BringBackHomeGoal());
      goalRegistry.register(new DeliverResourcesToShopGoal());
      goalRegistry.register(new GetResourcesForShopsGoal());
      goalRegistry.register(new GetGoodsForHouseholdGoal());
      goalRegistry.register(new GetToolGoal());
      goalRegistry.register(new GatherGoodsGoal());
      goalRegistry.register(new LightHearthGoal());
      goalRegistry.register(new HuntMonsterGoal());
      goalRegistry.register(new RaidVillageGoal());
      goalRegistry.register(new DefendVillageGoal());
      goalRegistry.register(new org.millenaire.goal.impl.PursueWantedPlayerGoal());
      goalRegistry.register(new org.millenaire.goal.impl.VisitShopGoal());
      goalRegistry.register(new HideGoal());
      goalRegistry.register(new HostileVillagerGoal());
      goalRegistry.register(new EngageTargetGoal());
      goalRegistry.register(new HiredEscortGoal());
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_silk"),
            "silkwormfarm",
            2,
            100,
            2,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> (Item)ModItems.SILK.get(),
            128,
            BuildingInstance::getSilkwormBlockPositions,
            (level, pos) -> {
               BlockState st = level.getBlockState(pos);
               return st.is((Block)ModBlocks.SILK_WORM.get()) && (Integer)st.getValue(BlockSilkWorm.AGE) == 3;
            },
            (ctx, pos) -> {
               BlockState st = ctx.level().getBlockState(pos);
               if (st.is((Block)ModBlocks.SILK_WORM.get()) && (Integer)st.getValue(BlockSilkWorm.AGE) == 3) {
                  ctx.level().setBlock(pos, (BlockState)st.setValue(BlockSilkWorm.AGE, 0), 2);
                  ctx.villager().getInventory().add((Item)ModItems.SILK.get(), 1);
               }
            },
            false
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_snails"),
            "snailsfarm",
            2,
            100,
            2,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> Items.PURPLE_DYE,
            128,
            BuildingInstance::getSnailSoilBlockPositions,
            (level, pos) -> {
               BlockState st = level.getBlockState(pos);
               return st.is((Block)ModBlocks.SNAIL_SOIL.get()) && (Integer)st.getValue(BlockSnailSoil.AGE) == 3;
            },
            (ctx, pos) -> {
               BlockState st = ctx.level().getBlockState(pos);
               if (st.is((Block)ModBlocks.SNAIL_SOIL.get()) && (Integer)st.getValue(BlockSnailSoil.AGE) == 3) {
                  ctx.level().setBlock(pos, (BlockState)st.setValue(BlockSnailSoil.AGE, 0), 2);
                  ctx.villager().getInventory().add(Items.PURPLE_DYE, 1);
               }
            },
            false
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "gather_brick"),
            "brickkiln",
            1,
            100,
            2,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> ((Block)ModBlocks.MUD_BRICK.get()).asItem(),
            4096,
            BuildingInstance::getBrickSpotPositions,
            (level, pos) -> level.getBlockState(pos).is((Block)ModBlocks.MUD_BRICK.get()),
            (ctx, pos) -> {
               if (ctx.level().getBlockState(pos).is((Block)ModBlocks.MUD_BRICK.get())) {
                  ctx.villager().getInventory().add(((Block)ModBlocks.MUD_BRICK.get()).asItem(), 1);
                  ctx.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "dry_brick"),
            "brickkiln",
            1,
            120,
            0,
            SpotGatheringGoal.StockSource.NONE,
            null,
            0,
            BuildingInstance::getBrickSpotPositions,
            (level, pos) -> level.getBlockState(pos).isAir(),
            (ctx, pos) -> {
               if (ctx.level().getBlockState(pos).isAir()) {
                  ctx.level().setBlock(pos, ((BlockWetBrick)ModBlocks.WET_BRICK.get()).defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "harvest_sugar_cane"),
            "sugarplantation",
            3,
            200,
            4,
            SpotGatheringGoal.StockSource.TOWNHALL,
            () -> Items.SUGAR_CANE,
            0,
            b -> b.getSoilPositions("sugarcane"),
            (level, pos) -> level.getBlockState(pos.above(2)).is(Blocks.SUGAR_CANE),
            (ctx, pos) -> {
               int irrigation = ctx.village().getVillageIrrigation();
               BlockPos top = pos.above(3);
               if (ctx.level().getBlockState(top).is(Blocks.SUGAR_CANE)) {
                  ctx.level().setBlock(top, Blocks.AIR.defaultBlockState(), 3);
                  int nbCrop = 1;
                  if (Math.random() < irrigation / 100.0) {
                     nbCrop++;
                  }

                  ctx.villager().getInventory().add(Items.SUGAR_CANE, nbCrop);
               }

               BlockPos mid = pos.above(2);
               if (ctx.level().getBlockState(mid).is(Blocks.SUGAR_CANE)) {
                  ctx.level().setBlock(mid, Blocks.AIR.defaultBlockState(), 3);
                  int nbCrop = 1;
                  if (Math.random() < irrigation / 100.0) {
                     nbCrop++;
                  }

                  ctx.villager().getInventory().add(Items.SUGAR_CANE, nbCrop);
               }
            },
            true
         )
      );
      goalRegistry.register(
         new SpotGatheringGoal(
            ResourceLocation.fromNamespaceAndPath("millenaire", "plant_sugar_cane"),
            "sugarplantation",
            3,
            120,
            0,
            SpotGatheringGoal.StockSource.NONE,
            null,
            0,
            b -> b.getSoilPositions("sugarcane"),
            (level, pos) -> level.getBlockState(pos.above()).isAir(),
            (ctx, pos) -> {
               BlockPos cropPos = pos.above();
               BlockState state = ctx.level().getBlockState(cropPos);
               if (state.isAir() || state.is(BlockTags.LEAVES)) {
                  ctx.level().setBlock(cropPos, Blocks.SUGAR_CANE.defaultBlockState(), 3);
               }
            },
            true
         )
      );
      goalRegistry.register(new ClearOldPathGoal());
      goalRegistry.register(new BuildPathGoal());
      VisitGoalLoader.loadAll(goalRegistry);
      goalRegistry.register(new org.millenaire.religion.goal.AttendCeremonyGoal());
      goalRegistry.register(new ForeignMerchantKeepStallGoal());
      goalRegistry.register(new MerchantVisitInnGoal());
      goalRegistry.register(new MerchantVisitBuildingGoal());
      GatheringHandlerRegistry.registerDefaults();
      GatheringTypeLoader.loadAll(goalRegistry);
      goalRegistry.freezeBuiltins();
      goalRegistryInstance = goalRegistry;
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, Pre.class, org.millenaire.faction.crime.ArrestService::onLivingDamagePre);
      NeoForge.EVENT_BUS.addListener(Pre.class, event -> {
         if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getEntity() instanceof ServerPlayer) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.is(ModTags.Items.CULTURE_WEAPONS)) {
               MillAdvancements.grant(attacker, MillAdvancements.MP_WEAPON);
            }
         }
      });
      NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, InuitHuntingDropHandler::onLivingDrops);
      NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class, event -> {
         SpawnVillageCommand.register(event.getDispatcher());
         SpawnLoneBuildingCommand.register(event.getDispatcher());
         ReputationCommand.register(event.getDispatcher());
         VillagesCommand.register(event.getDispatcher());
         QueryCommand.register(event.getDispatcher());
         TestCommand.register(event.getDispatcher());
         DevCommand.register(event.getDispatcher());
         TpCommand.register(event.getDispatcher());
         WorldgenDiagCommand.register(event.getDispatcher());
         org.millenaire.worldgen.highway.HighwayCommand.register(event.getDispatcher());
         org.millenaire.command.NpcChatModelCommand.register(event);
      });
      org.millenaire.living.LivingVillagesLifecycle.bootstrap();
      org.millenaire.religion.ReligionLifecycle.bootstrap();
      NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
         org.millenaire.chat.NpcChatService.onServerStopping();
         org.millenaire.living.LivingVillagesLifecycle.onServerStopping();
         TestPlayerManager.onServerStopping();
         TravelBookNavigationState.clearAll();
         ConvertAddonCommand.onServerStopping(event);
         MillenaireStructureLoader.clearCache();
         ItemHelper.clearCache();
         WorldgenEngine.onServerStopping();
         org.millenaire.worldgen.highway.HighwayMaterializer.onServerStopping();
         org.millenaire.perf.compute.MillenaireComputePool.shutdown();
         org.millenaire.map.sync.SettlementMapSyncService.onServerStopping();
         if (spawnQueueInstance != null) {
            spawnQueueInstance.reset();
         }

         VersionChecker.reset();
         UsageReporter.reset();
      });
      NeoForge.EVENT_BUS.addListener(PlayerLoggedOutEvent.class, event -> {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            TravelBookNavigationState.clear(serverPlayer.getUUID());
            PlanPreviewRequestPayload.clearPrePicked(serverPlayer.getUUID());
            org.millenaire.chat.NpcChatService.onPlayerLogout(serverPlayer);
            org.millenaire.living.LivingVillagesLifecycle.onPlayerLogout(serverPlayer);
         }
      });
      NeoForge.EVENT_BUS.addListener(PlayerLoggedInEvent.class, event -> {
         if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ContentStatsReporter.notifyOpIfRelevant(serverPlayer);
            VersionChecker.onPlayerLogin(serverPlayer);
            DonorChecker.onPlayerLogin(serverPlayer);
            AdvancementReporter.onPlayerLogin(serverPlayer);
            ServerLevel overworld = serverPlayer.server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
               PlayerQuestData data = PlayerQuestData.get(overworld, QuestRegistry::get);

               for (QuestInstance qi : data.getActiveQuests(serverPlayer.getUUID())) {
                  PacketDistributor.sendToPlayer(serverPlayer, QuestNetworkHelper.buildSyncPayload(qi, serverPlayer), new CustomPacketPayload[0]);
               }
               org.millenaire.faction.crime.BountyHudSync.sync(overworld, serverPlayer);
               org.millenaire.faction.crime.hunter.BountyHunterCustodyTicker.onPlayerLogin(serverPlayer);
               org.millenaire.map.sync.SettlementMapSyncService.sendSnapshot(serverPlayer);
            }
         }
      });
      VillageSpawnQueue spawnQueue = new VillageSpawnQueue();
      spawnQueueInstance = spawnQueue;
      NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
         UsageReporter.tryReport(event.getServer());
         TestPlayerManager.acknowledgeChunkBatch();
         ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
         if (overworld != null) {
            // Apply completed worker results before village/worldgen consume shared budgets.
            if (org.millenaire.perf.compute.MillenaireComputePool.isRunning()) {
               org.millenaire.perf.compute.ComputeMailbox.get()
                  .drainAndApply(overworld, org.millenaire.perf.compute.ComputeOffload.applyBudgetPerTick());
            }
            // Shared soft deadline for village + worldgen work on this tick.
            org.millenaire.worldgen.materialize.MaterializationTickBudget.ensureTick(overworld.getGameTime());
            VillageSavedData.get(overworld).getVillageManager().tick(overworld);
            org.millenaire.village.RoadTravelerShopScheduler.tick(overworld);
            if (org.millenaire.civilization.CivilizationOrchestrator.isEnabled()) {
               org.millenaire.civilization.CivilizationOrchestrator.onServerTick(overworld);
            } else {
               FactionLifecycle.onServerTick(overworld);
            }
            org.millenaire.faction.crime.hunter.BountyHunterDispatchManager.tick(overworld);
            org.millenaire.faction.crime.hunter.BountyHunterCustodyTicker.tick(overworld);
            if (WorldgenEngine.isEnabled()) {
               WorldgenEngine.onServerTick(overworld);
            }
            org.millenaire.worldgen.starter.StarterSettlementService.tick(overworld);
            if (org.millenaire.worldgen.highway.HighwayEngine.isEnabled()) {
               org.millenaire.worldgen.highway.HighwayEngine.tick(overworld);
            }
            // Legacy fallback keeps natural villages appearing if the Phase 4 engine stalls.
            boolean legacyFallback = false;
            try {
               legacyFallback = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenLegacyFallback.get());
            } catch (IllegalStateException ignored) {
            }
            if (!naturalSpawnSuppressed && (!WorldgenEngine.isEnabled() || legacyFallback)) {
               spawnQueue.trySpawnNext(overworld);
            }
         }

         org.millenaire.chat.NpcChatService.tickSessions(event.getServer());

         for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel playerLevel = player.serverLevel();
            QuestManager.tickQuests(player, playerLevel);
            QuestManager.tickSpecialActions(player, playerLevel);
         }

         WaypointVisualizationManager.tick(event.getServer());
         if (overworld != null && overworld.getGameTime() % 20L == 0L) {
            VillageMapMarkerService.processAllPlayers(event.getServer());
         }
         org.millenaire.map.sync.SettlementMapSyncService.tick(event.getServer());
         // Backfill Millenaire village waystones for existing worlds (every ~30s).
         if (overworld != null && overworld.getGameTime() % 600L == 0L) {
            org.millenaire.compat.WaystoneVillagePlacer.tickBackfill(overworld);
         }
      });
      NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
         if (naturalSpawnSuppressed) {
            return;
         }
         if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD) {
            ChunkPos chunkPos = event.getChunk().getPos();
            boolean isNewChunk = event.isNewChunk();
            // Enqueue only — server.execute still runs on the server thread and is not a worker.
            // Heavy planning/materialization is drained from WorldgenEngine.onServerTick under budgets.
            serverLevel.getServer().execute(() -> {
               spawnQueue.markResolved(chunkPos);
               if (WorldgenEngine.isEnabled()) {
                  if (isNewChunk) {
                     WorldgenEngine.onNewChunk(serverLevel, chunkPos);
                  } else {
                     WorldgenEngine.onChunkAvailable(serverLevel, chunkPos);
                  }
               }
               boolean legacyFallback = false;
               try {
                  legacyFallback = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenLegacyFallback.get());
               } catch (IllegalStateException ignored) {
               }
               if ((!WorldgenEngine.isEnabled() || legacyFallback) && isNewChunk) {
                  BlockPos biomeSamplePos = chunkPos.getMiddleBlockPosition(serverLevel.getMaxBuildHeight() - 1);
                  Holder<Biome> centerBiome = serverLevel.getBiome(biomeSamplePos);
                  if (VillageSpawnQueue.couldHostAnyVillage(ModCultures.getAllVillageTypes().values(), centerBiome)) {
                     spawnQueue.enqueue(chunkPos.getMiddleBlockPosition(64));
                  }
               }
               org.millenaire.compat.WaystoneVillagePlacer.onChunkLoad(serverLevel, chunkPos);
               org.millenaire.worldgen.highway.HighwayEngine.enqueueChunk(serverLevel, chunkPos);
            });
         }
      });
      NeoForge.EVENT_BUS
         .addListener(
            EventPriority.NORMAL,
            false,
            ServerStartedEvent.class,
            event -> {
               try {
                  int workers = 0;
                  try {
                     workers = MillenaireServerConfig.SERVER.computeWorkerThreads.getAsInt();
                  } catch (IllegalStateException ignored) {
                  }
                  org.millenaire.perf.compute.MillenaireComputePool.start(workers);
                  org.millenaire.chat.NpcChatService.bootstrap();
                  org.millenaire.map.sync.SettlementMapSyncService.onServerStarting(event.getServer());
                  ContentDirectoryManager.init(event.getServer());
                  ExportTransaction.reclaimStaging(BuildingExporter.getExportedRoot(event.getServer().getServerDirectory()));
                  ContentIssueLog.startCapture();
                  NativeContentDeployer.deployIfNeeded();
                  LegacyAutoConverter.convertIfNeeded();
                  CustomContentIndex.rebuild(BuiltInCultures.IDS, true);
                  ToolCategoryRegistry.load();
                  ServerTranslationCache.load();
                  VillagerConfig.load();
                  goalRegistryInstance.resetToBuiltins();
                  CultureLoader.loadAll();
                  WorldgenEngine.onContentLoaded();
                  if (spawnQueueInstance != null) {
                     spawnQueueInstance.invalidateBiomeIndex();
                  }

                  GatheringTypeLoader.loadExternal(goalRegistryInstance);
                  VisitGoalLoader.loadExternal(goalRegistryInstance);
                  VisitGoalLoader.loadExternalPerCultureGoals(goalRegistryInstance, CultureLoader.discoverCultures());
                  CultureLoader.validateAll(goalRegistryInstance);
                  GatheringTypeLoader.validateAll(goalRegistryInstance);
                  VisitGoalValidator.validateAll(goalRegistryInstance);
                  QuestRegistry.clear();
                  QuestLoader.loadAll();
                  ContentIssueLog.stopCapture();
                  ValidationReport.generate(CultureLoader.discoverCultures(), new HashSet<>(CultureLoader.BUILTIN_CULTURES));
                  ReferenceCatalogGenerator.generate(goalRegistryInstance);
                  I18nKeyAuditor.audit();
               } catch (Exception e) {
                  LOGGER.error(
                     "[Millenaire] Content loading failed — the server keeps running, but Millénaire content is incomplete or absent for this session. Check the custom content under millenaire-custom/: {}",
                     e.getClass().getSimpleName() + ": " + e.getMessage(),
                     e
                  );
               } finally {
                  ContentIssueLog.stopCapture();
               }

               try {
                  ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
                  if (overworld != null) {
                     BiomeTagDisjointnessValidator.validate(overworld.registryAccess());
                     WorldgenEngine.recoverPrematureRejects(overworld);
                     VillageRehydrator.rehydrateAll(overworld);
                     FactionLifecycle.onWorldReady(overworld);
                     org.millenaire.worldgen.starter.StarterSettlementService.onWorldReady(overworld);
                     this.initVillagerGoals(overworld);
                  }
               } catch (Exception e) {
                  LOGGER.error(
                     "[Millenaire] World-side startup (rehydration / villager goals) failed — the server keeps running, but loaded villages may lack special points, inventories or goals until the next restart: {}",
                     e.getClass().getSimpleName() + ": " + e.getMessage(),
                     e
                  );
               }
            }
         );
      NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
         if (event.getEntity() instanceof MillVillager villager && !event.getLevel().isClientSide() && villager.getVillagerTypeId() != null) {
            VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
            if (vType != null) {
               villager.syncTypeDerivedFlags(vType);
               if (villager.getGoalScheduler() == null && goalRegistryInstance != null) {
                  villager.initGoals(goalRegistryInstance, vType);
                  LOGGER.debug("Goals reinitialized for villager {} (loaded from chunk)", villager.getVillagerTypeId());
               }
            }
         }
      });
   }

   private void initVillagerGoals(ServerLevel level) {
      int count = 0;

      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof MillVillager villager && villager.getVillagerTypeId() != null) {
            VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
            if (vType != null) {
               villager.initGoals(goalRegistryInstance, vType);
               villager.syncTypeDerivedFlags(vType);
               if (villager.getTexture() == null) {
                  VillagerAppearanceFactory.randomizeAppearance(villager, vType);
               }

               count++;
            }
         }
      }

      if (count > 0) {
         LOGGER.info("Goals initialized for {} existing villagers", count);
      }
   }
}
