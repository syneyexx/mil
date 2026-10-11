package org.millenaire.civilization.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.FirePitBlockEntity;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.economy.SettlementTradeOperation;
import org.millenaire.civilization.population.MigrationOperation;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.civilization.settlement.FoundingCostService;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.TradePolicy;
import org.millenaire.faction.diplomacy.AgreementStatus;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.requests.VillageRequestState;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.worldgen.net.RegionalRoadMaterializer;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Builds bounded, server-derived civilization snapshots for UI, chronicle and NPC context.
 * Never force-loads chunks. Unknown inventory is not reported as zero stock.
 */
public final class CivilizationPresentationService {
   private static final int MAX_NEEDS = 5;
   private static final int MAX_HISTORY = 8;
   private static final int MAX_SETTLEMENT_NAMES = 12;
   private static final int MAX_TRADE_LINES = 3;
   private static final int MAX_MIGRATION_LINES = 5;
   private static final long RECENT_TRADE_WINDOW = 24000L;

   private CivilizationPresentationService() {
   }

   public static CivilizationVillageSnapshot villageSnapshot(
      ServerLevel level,
      Village village,
      @Nullable ServerPlayer viewer
   ) {
      long now = level.getGameTime();
      PopulationSnapshot pop = PopulationAggregator.aggregateVillage(village, now);
      boolean housingKnown = true;
      boolean overcrowded = housingKnown
         && ((pop.housingCapacity() == 0 && pop.residents() > 0)
            || (pop.housingCapacity() > 0 && pop.residents() > pop.housingCapacity()));

      FactionId factionId = village.getFactionId();
      String factionDisplay = factionDisplayName(level, factionId);
      String development = CivilizationSavedData.get(level).getDevelopmentLabel(village.getId().uuid());
      if (development == null || development.isBlank()) {
         development = "unknown";
      }
      String developmentKey = "civilization.millenaire.development." + development.toLowerCase(Locale.ROOT);

      InventoryCertainty inventoryCertainty = evaluateInventoryCertainty(level, village);

      List<CivilizationVillageSnapshot.NeedLine> needs = new ArrayList<>();
      VillageNeedsSnapshot needSnap = LivingVillagesService.get().needsEngine().evaluate(level, village, false);
      if (needSnap != null) {
         for (VillageNeed need : needSnap.needs()) {
            if (needs.size() >= MAX_NEEDS) {
               break;
            }
            if (need.isEmpty()) {
               continue;
            }
            int observed = need.totalShortage();
            boolean proven = inventoryCertainty.isFullyKnown();
            needs.add(new CivilizationVillageSnapshot.NeedLine(
               need.needId(),
               need.summaryKey(),
               need.summaryDetail(),
               proven ? observed : 0,
               observed,
               proven,
               need.severity().name().toLowerCase(Locale.ROOT)
            ));
         }
      }
      var shopStats = org.millenaire.village.PlayerShopService.statsForToday(village, level);
      needs.add(new CivilizationVillageSnapshot.NeedLine(
         "shopstats",
         "shopstats",
         shopStats.visits + "|" + shopStats.deniers,
         0,
         0,
         true,
         "info"
      ));

      CivilizationVillageSnapshot.ProjectLine project = resolveProject(village);
      List<CivilizationVillageSnapshot.TradeLine> trades = resolveTrade(level, village, now);
      List<CivilizationVillageSnapshot.MigrationLine> migrations = resolveMigration(level, village);
      CivilizationVillageSnapshot.ColonizationLine colonization = resolveColonization(level, village);
      CivilizationVillageSnapshot.ConnectionLine connection = resolveConnection(level, village);
      List<CivilizationVillageSnapshot.HistoryLine> history = resolveHistory(level, village);
      List<CivilizationVillageSnapshot.AllowedAction> actions =
         resolveActions(level, village, viewer, needSnap, colonization);

      return new CivilizationVillageSnapshot(
         village.getId().uuid(),
         villageName(village),
         factionId == null ? null : factionId.asString(),
         factionDisplay,
         development,
         developmentKey,
         pop.residents(),
         pop.housingCapacity(),
         housingKnown,
         overcrowded,
         inventoryCertainty,
         List.copyOf(needs),
         project,
         List.copyOf(trades),
         List.copyOf(migrations),
         colonization,
         connection,
         List.copyOf(history),
         List.copyOf(actions),
         now
      );
   }

   public static CivilizationFactionSnapshot factionSnapshot(ServerLevel level, FactionId factionId) {
      long now = level.getGameTime();
      Faction faction = factionId == null ? null : FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      String display = faction != null ? faction.getDisplayName() : factionDisplayName(level, factionId);
      PoliticalLeader leader = factionId == null ? null : PoliticsQueries.leader(level, factionId);
      String leaderName = null;
      if (faction != null && faction.getType() == org.millenaire.faction.FactionType.PLAYER && faction.getSovereignOwnerUuid() != null) {
         var online = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(faction.getSovereignOwnerUuid()) : null;
         if (online != null) {
            leaderName = online.getName().getString();
         } else if (faction.getDisplayName() != null && !faction.getDisplayName().isBlank()) {
            leaderName = faction.getDisplayName();
         }
      }
      if (leaderName == null && leader != null && leader.getDisplayName() != null && !leader.getDisplayName().isBlank()) {
         leaderName = leader.getDisplayName();
      }

      int residents = 0;
      int housing = 0;
      boolean housingKnown = faction != null;
      List<String> settlementNames = new ArrayList<>();
      Set<UUID> colonizationIds = new LinkedHashSet<>();
      if (faction != null) {
         for (VillageId vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v == null) {
               continue;
            }
            PopulationSnapshot pop = PopulationAggregator.aggregateVillage(v, now);
            residents += pop.residents();
            housing += pop.housingCapacity();
            if (settlementNames.size() < MAX_SETTLEMENT_NAMES) {
               settlementNames.add(villageName(v));
            }
            for (ColonizationOperation op : ColonizationService.listForVillage(level, vid.uuid(), 8)) {
               if (op != null && op.isActive()) {
                  colonizationIds.add(op.operationId());
               }
            }
         }
      }

      int activeMigrations = 0;
      if (faction != null) {
         Set<UUID> settlementUuids = new HashSet<>();
         for (VillageId vid : faction.getSettlementIds()) {
            settlementUuids.add(vid.uuid());
         }
         for (MigrationOperation op : CivilizationSavedData.get(level).migrationOpsView()) {
            if (op == null || !op.isActive()) {
               continue;
            }
            if (settlementUuids.contains(op.originVillageId()) || settlementUuids.contains(op.destinationVillageId())) {
               activeMigrations++;
            }
         }
      }

      FactionEconomicProfile profile = factionId == null
         ? null
         : EconomySavedData.get(level).getTradeManager().getProfile(factionId);
      String economyKey = profile == null
         ? "civilization.millenaire.economy.unknown"
         : "civilization.millenaire.economy." + profile.getBalanceLevel().name().toLowerCase(Locale.ROOT);

      boolean atWar = factionId != null && WarSavedData.get(level).getWarManager().isAtWar(factionId);
      int treaties = 0;
      if (factionId != null) {
         for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getAllAgreements()) {
            if (agreement.getStatus() != AgreementStatus.ACTIVE) {
               continue;
            }
            if (factionId.equals(agreement.getPrimaryFactionId()) || factionId.equals(agreement.getSecondaryFactionId())) {
               treaties++;
            }
         }
      }

      List<CivilizationVillageSnapshot.HistoryLine> events = new ArrayList<>();
      if (faction != null) {
         Set<String> seen = new HashSet<>();
         for (VillageId vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v == null) {
               continue;
            }
            for (CivilizationVillageSnapshot.HistoryLine line : resolveHistory(level, v)) {
               String key = line.typeKey() + "|" + line.summary() + "|" + line.gameTime();
               if (seen.add(key)) {
                  events.add(line);
               }
            }
         }
         events.sort(Comparator.comparingLong(CivilizationVillageSnapshot.HistoryLine::gameTime).reversed());
         if (events.size() > MAX_HISTORY) {
            events = new ArrayList<>(events.subList(0, MAX_HISTORY));
         }
      }

      boolean overcrowded = housingKnown
         && ((housing == 0 && residents > 0) || (housing > 0 && residents > housing));

      return new CivilizationFactionSnapshot(
         factionId == null ? "" : factionId.asString(),
         display,
         leaderName,
         faction == null ? 0 : faction.getSettlementCount(),
         residents,
         housing,
         housingKnown,
         overcrowded,
         economyKey,
         atWar,
         treaties,
         colonizationIds.size(),
         activeMigrations,
         List.copyOf(settlementNames),
         List.copyOf(events),
         now
      );
   }

   public static String factionDisplayName(ServerLevel level, @Nullable FactionId factionId) {
      if (factionId == null) {
         return "";
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction != null && faction.getDisplayName() != null && !faction.getDisplayName().isBlank()) {
         return faction.getDisplayName();
      }
      return "";
   }

   public static String factionDisplayOrFallback(ServerLevel level, @Nullable FactionId factionId) {
      String name = factionDisplayName(level, factionId);
      return name.isBlank() ? null : name;
   }

   private static String villageName(Village village) {
      String name = village.getVillageName();
      return name == null || name.isBlank() ? "???" : name;
   }

   /**
    * Inspect registered chest/furnace/firepit positions without force-loading.
    * Missing containers in loaded chunks are simply not counted as known stock.
    */
   static InventoryCertainty evaluateInventoryCertainty(ServerLevel level, Village village) {
      int registered = 0;
      int readable = 0;
      for (BuildingInstance building : village.getBuildings()) {
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            // Fall back to registered special points when inventory was not initialized.
            for (BlockPos pos : building.getChestPositions()) {
               registered++;
               if (isChestReadable(level, pos)) {
                  readable++;
               }
            }
            for (BlockPos pos : building.getFurnacePositions()) {
               registered++;
               if (isFurnaceReadable(level, pos)) {
                  readable++;
               }
            }
            for (BlockPos pos : building.getFirePitPositions()) {
               registered++;
               if (isFirepitReadable(level, pos)) {
                  readable++;
               }
            }
            continue;
         }
         for (BlockPos pos : inv.getChestPositions()) {
            registered++;
            if (isChestReadable(level, pos)) {
               readable++;
            }
         }
         for (BlockPos pos : inv.getFurnacePositions()) {
            registered++;
            if (isFurnaceReadable(level, pos)) {
               readable++;
            }
         }
         for (BlockPos pos : inv.getFirepitPositions()) {
            registered++;
            if (isFirepitReadable(level, pos)) {
               readable++;
            }
         }
      }

      if (registered == 0) {
         if (village.getCenter() == null
            || !level.hasChunk(village.getCenter().getX() >> 4, village.getCenter().getZ() >> 4)) {
            return InventoryCertainty.UNKNOWN;
         }
         return InventoryCertainty.KNOWN;
      }
      if (readable == 0) {
         return InventoryCertainty.UNKNOWN;
      }
      if (readable < registered) {
         return InventoryCertainty.PARTIAL;
      }
      return InventoryCertainty.KNOWN;
   }

   private static boolean isChestReadable(ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos)) {
         return false;
      }
      BlockEntity be = level.getBlockEntity(pos);
      if (!(be instanceof ChestBlockEntity)) {
         return false;
      }
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof ChestBlock chestBlock)) {
         return false;
      }
      return ChestBlock.getContainer(chestBlock, state, level, pos, true) != null;
   }

   private static boolean isFurnaceReadable(ServerLevel level, BlockPos pos) {
      return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity;
   }

   private static boolean isFirepitReadable(ServerLevel level, BlockPos pos) {
      return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FirePitBlockEntity;
   }

   @Nullable
   private static CivilizationVillageSnapshot.ProjectLine resolveProject(Village village) {
      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            String name = BuildingNameHelper.getServerFallbackName(b);
            return new CivilizationVillageSnapshot.ProjectLine(
               name,
               b.getStatus() == BuildingInstance.Status.UPGRADING,
               b.getLevel(),
               b.getStatus().name().toLowerCase(Locale.ROOT)
            );
         }
      }
      ControlledQueuedProject head = village.controlledQueueHead();
      if (head != null) {
         return new CivilizationVillageSnapshot.ProjectLine(
            head.planSetId() == null ? "project" : head.planSetId().toString(),
            false,
            head.level(),
            "queued"
         );
      }
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null && pending.planSetId() != null) {
         return new CivilizationVillageSnapshot.ProjectLine(
            pending.planSetId().toString(),
            pending.isUpgrade(),
            pending.level(),
            "pending_resources"
         );
      }
      return null;
   }

   private static List<CivilizationVillageSnapshot.TradeLine> resolveTrade(
      ServerLevel level,
      Village village,
      long now
   ) {
      UUID villageId = village.getId().uuid();
      List<CivilizationVillageSnapshot.TradeLine> out = new ArrayList<>();

      for (SettlementTradeOperation op : CivilizationSavedData.get(level).tradeOperationsView()) {
         if (op == null) {
            continue;
         }
         boolean involved = villageId.equals(op.exporterVillageId()) || villageId.equals(op.importerVillageId());
         if (!involved) {
            continue;
         }
         SettlementTradeOperation.Status status = op.status();
         boolean recovery = status == SettlementTradeOperation.Status.COMPENSATING
            || status == SettlementTradeOperation.Status.RECOVERY_REQUIRED
            || status == SettlementTradeOperation.Status.LEGACY_UNRECOVERABLE;
         boolean active = op.isActive() || recovery;
         boolean recentTerminal = (status == SettlementTradeOperation.Status.COMPLETE
               || status == SettlementTradeOperation.Status.FAILED)
            && now - op.lastChangeGameTime() <= RECENT_TRADE_WINDOW;
         if (!active && !recentTerminal) {
            continue;
         }

         String kind;
         if (recovery) {
            kind = "trade_recovery";
         } else if (op.sameFactionLogistics()) {
            kind = "logistics";
         } else {
            kind = "settlement_trade";
         }

         boolean exporting = villageId.equals(op.exporterVillageId());
         UUID partnerId = exporting ? op.importerVillageId() : op.exporterVillageId();
         String partner = villageNameOrFallback(level, partnerId, partnerId.toString());
         String itemLabel = op.resourceId() == null || op.resourceId().isBlank() ? "unknown" : op.resourceId();
         int outstanding = countStacks(op.pendingDelivery()) + countStacks(op.pendingCompensation());
         // Legacy markers must not invent outstanding goods from a count-only record.
         if (status == SettlementTradeOperation.Status.LEGACY_UNRECOVERABLE) {
            outstanding = 0;
         }
         out.add(new CivilizationVillageSnapshot.TradeLine(
            kind,
            op.statusCode(),
            partner,
            itemLabel,
            exporting ? "outbound" : "inbound",
            op.deliveredCount(),
            outstanding,
            op.reasonCode(),
            op.lastChangeGameTime()
         ));
      }

      for (VillageRequest req : VillageRequestSavedData.get(level).forVillage(villageId)) {
         if (!VillageRequestState.activeStates().contains(req.state())) {
            continue;
         }
         String itemLabel = req.required().isEmpty()
            ? req.type().name().toLowerCase(Locale.ROOT)
            : req.required().entrySet().iterator().next().getKey().toString();
         int delivered = 0;
         for (int v : req.delivered().values()) {
            delivered += Math.max(0, v);
         }
         int outstanding = 0;
         for (int v : req.remaining().values()) {
            outstanding += Math.max(0, v);
         }
         out.add(new CivilizationVillageSnapshot.TradeLine(
            "player_support",
            req.state().name().toLowerCase(Locale.ROOT),
            villageName(village),
            itemLabel,
            "inbound_support",
            delivered,
            outstanding,
            "",
            req.lastUpdateGameTime()
         ));
      }

      // Surface sovereign trade policy when it blocks auto-import (otherwise failures look silent).
      if (out.stream().noneMatch(t -> "settlement_trade".equals(t.kindCode()) || "logistics".equals(t.kindCode()))) {
         Faction faction = village.getFactionId() == null
            ? null
            : FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
         if (faction != null && faction.getType() == FactionType.PLAYER && !faction.getTradePolicy().allowsImport()) {
            out.add(new CivilizationVillageSnapshot.TradeLine(
               "settlement_trade",
               "blocked",
               faction.getDisplayName() == null ? "" : faction.getDisplayName(),
               "policy",
               "inbound",
               0,
               0,
               "trade_policy_" + faction.getTradePolicy().name().toLowerCase(Locale.ROOT),
               now
            ));
         }
      }

      out.sort(Comparator
         .comparingInt((CivilizationVillageSnapshot.TradeLine t) -> tradeKindPriority(t.kindCode()))
         .thenComparingLong(CivilizationVillageSnapshot.TradeLine::lastTransferGameTime)
         .reversed());
      if (out.size() > MAX_TRADE_LINES) {
         return new ArrayList<>(out.subList(0, MAX_TRADE_LINES));
      }
      return out;
   }

   private static int tradeKindPriority(String kind) {
      return switch (kind == null ? "" : kind) {
         case "trade_recovery" -> 3;
         case "settlement_trade", "logistics" -> 2;
         case "player_support" -> 1;
         default -> 0;
      };
   }

   private static int countStacks(List<ItemStack> stacks) {
      int n = 0;
      if (stacks == null) {
         return 0;
      }
      for (ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty()) {
            n += stack.getCount();
         }
      }
      return n;
   }

   private static List<CivilizationVillageSnapshot.MigrationLine> resolveMigration(ServerLevel level, Village village) {
      UUID villageId = village.getId().uuid();
      List<CivilizationVillageSnapshot.MigrationLine> out = new ArrayList<>();
      Set<UUID> colonistVillagerIds = new HashSet<>();

      // Prefer colonization founder lines so underlying MigrationOperations are not double-counted.
      for (ColonizationOperation op : ColonizationService.listForVillage(level, villageId, 8)) {
         if (op == null) {
            continue;
         }
         if (op.status() != ColonizationOperation.Status.MIGRATING
            && op.status() != ColonizationOperation.Status.ACTIVATED) {
            continue;
         }
         for (ColonizationOperation.FounderSlot slot : op.founderSlots()) {
            if (slot != null && slot.villagerId() != null) {
               colonistVillagerIds.add(slot.villagerId());
            }
         }
         SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
         String dest = plan != null && plan.villageUuid() != null
            ? villageNameOrFallback(level, plan.villageUuid(), op.targetCenter().toShortString())
            : op.targetCenter().toShortString();
         String origin = villageNameOrFallback(level, op.parentVillageId(), villageName(village));
         out.add(new CivilizationVillageSnapshot.MigrationLine(
            "colonist",
            op.statusCode(),
            CivilizationStatusCodes.normalizeReason(op.reason()),
            origin,
            dest,
            op.migratedCount(),
            op.founderTarget()
         ));
      }

      for (MigrationOperation op : CivilizationSavedData.get(level).migrationOpsView()) {
         if (op == null || !op.isActive()) {
            continue;
         }
         if (!villageId.equals(op.originVillageId()) && !villageId.equals(op.destinationVillageId())) {
            continue;
         }
         if (colonistVillagerIds.contains(op.villagerId())) {
            continue;
         }
         String kind = op.needsRecovery() ? "recovery" : "ordinary";
         int migrated = switch (op.status()) {
            case ADDED_TO_DESTINATION, COMPLETE -> 1;
            default -> 0;
         };
         String reason = op.failureReason() == null || op.failureReason().isBlank()
            ? (op.needsRecovery() ? "recovery" : "none")
            : CivilizationStatusCodes.normalizeReason(op.failureReason());
         out.add(new CivilizationVillageSnapshot.MigrationLine(
            kind,
            op.status().name().toLowerCase(Locale.ROOT),
            reason,
            villageNameOrFallback(level, op.originVillageId(), op.originVillageId().toString()),
            villageNameOrFallback(level, op.destinationVillageId(), op.destinationVillageId().toString()),
            migrated,
            1
         ));
         if (out.size() >= MAX_MIGRATION_LINES) {
            break;
         }
      }

      if (out.size() > MAX_MIGRATION_LINES) {
         return new ArrayList<>(out.subList(0, MAX_MIGRATION_LINES));
      }
      return out;
   }

   @Nullable
   private static CivilizationVillageSnapshot.ColonizationLine resolveColonization(ServerLevel level, Village village) {
      ColonizationOperation op = ColonizationService.findActiveForParent(level, village.getId().uuid());
      if (op == null) {
         for (ColonizationOperation candidate : ColonizationService.listForVillage(level, village.getId().uuid(), 4)) {
            op = candidate;
            break;
         }
      }
      if (op == null) {
         return null;
      }
      String reasonCode = CivilizationStatusCodes.normalizeReason(op.reason());
      return new CivilizationVillageSnapshot.ColonizationLine(
         op.operationId(),
         op.statusCode(),
         reasonCode,
         CivilizationStatusCodes.statusLabelKey(op.statusCode()),
         CivilizationStatusCodes.reasonLabelKey(reasonCode),
         CivilizationStatusCodes.nextStepKey(reasonCode),
         op.migratedCount(),
         op.founderTarget(),
         !op.refundOutstanding().isEmpty(),
         op.resourcesReserved(),
         op.costsConsumed(),
         op.targetCenter().toShortString()
      );
   }

   @Nullable
   private static CivilizationVillageSnapshot.ConnectionLine resolveConnection(ServerLevel level, Village village) {
      RegionalRoadMaterializer.RouteProgress progress = RegionalRoadMaterializer.findProgressForVillage(level, village);
      if (progress == null) {
         return null;
      }
      UUID selfPlan = null;
      for (SettlementPlan plan : SettlementPlanSavedData.get(level).allPlans()) {
         if (village.getId().uuid().equals(plan.villageUuid())) {
            selfPlan = plan.planId();
            break;
         }
      }
      return new CivilizationVillageSnapshot.ConnectionLine(
         progress.status.name().toLowerCase(Locale.ROOT),
         RegionalRoadMaterializer.endpointLabel(level, progress, selfPlan),
         CivilizationStatusCodes.normalizeReason(progress.blockReason)
      );
   }

   private static List<CivilizationVillageSnapshot.HistoryLine> resolveHistory(ServerLevel level, Village village) {
      List<CivilizationVillageSnapshot.HistoryLine> out = new ArrayList<>();
      List<LivingEvent> events = LivingEventService.recentPublic(level, village.getId(), MAX_HISTORY);
      for (LivingEvent event : events) {
         out.add(new CivilizationVillageSnapshot.HistoryLine(
            event.eventType().i18nKey(),
            event.details() == null ? "" : event.details(),
            event.gameTime()
         ));
      }
      return out;
   }

   private static List<CivilizationVillageSnapshot.AllowedAction> resolveActions(
      ServerLevel level,
      Village village,
      @Nullable ServerPlayer viewer,
      @Nullable VillageNeedsSnapshot needSnap,
      @Nullable CivilizationVillageSnapshot.ColonizationLine colonization
   ) {
      List<CivilizationVillageSnapshot.AllowedAction> actions = new ArrayList<>();
      boolean controlled = viewer != null && village.isControlledBy(viewer.getUUID());
      boolean civilizationOn = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationEvolutionEnabled.get());

      VillageNeed topNeed = needSnap == null ? null : needSnap.mostSevere().orElse(null);
      boolean hasNeed = topNeed != null && !topNeed.isEmpty();
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SUPPORT_NEED,
         hasNeed,
         CivilizationActionId.SUPPORT_NEED.i18nKey(),
         hasNeed ? "" : "civilization.millenaire.action.blocked.no_need",
         hasNeed ? topNeed.needId() : null
      ));

      ControlledQueuedProject housing = findHousingProject(village);
      boolean canPrioritize = controlled && housing != null;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.PRIORITIZE_HOUSING,
         canPrioritize,
         CivilizationActionId.PRIORITIZE_HOUSING.i18nKey(),
         !controlled
            ? "civilization.millenaire.action.blocked.not_controller"
            : housing == null ? "civilization.millenaire.action.blocked.no_housing_project" : "",
         housing == null || housing.planSetId() == null ? null : housing.planSetId().toString()
      ));

      boolean foundingEnabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get());
      boolean hasColonization = colonization != null;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.VIEW_COLONIZATION,
         civilizationOn && (hasColonization || foundingEnabled),
         CivilizationActionId.VIEW_COLONIZATION.i18nKey(),
         !civilizationOn
            ? "civilization.millenaire.action.blocked.civilization_off"
            : !hasColonization && !foundingEnabled
               ? "civilization.millenaire.action.blocked.colonization_off"
               : "",
         hasColonization ? colonization.operationId().toString() : null
      ));

      ColonizationOperation materialOp = null;
      if (hasColonization) {
         for (ColonizationOperation candidate : ColonizationService.listForVillage(level, village.getId().uuid(), 8)) {
            if (candidate.operationId().equals(colonization.operationId())) {
               materialOp = candidate;
               break;
            }
         }
         if (materialOp == null) {
            materialOp = ColonizationService.findActiveForParent(level, village.getId().uuid());
         }
      }
      boolean materialsMissing = materialOp != null && colonizationMaterialsMissing(level, materialOp);
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS,
         materialsMissing,
         CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS.i18nKey(),
         !hasColonization
            ? "civilization.millenaire.action.blocked.no_colonization"
            : !materialsMissing ? "civilization.millenaire.action.blocked.materials_complete" : "",
         hasColonization ? colonization.operationId().toString() : null
      ));

      boolean resumable = materialOp != null && isColonizationResumable(materialOp);
      String authorizeBlocked;
      if (!controlled) {
         authorizeBlocked = "civilization.millenaire.action.blocked.not_controller";
      } else if (!hasColonization) {
         authorizeBlocked = "civilization.millenaire.action.blocked.no_colonization";
      } else if (!resumable) {
         authorizeBlocked = "civilization.millenaire.action.blocked.not_resumable";
      } else {
         authorizeBlocked = "";
      }
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.AUTHORIZE_COLONIZATION,
         controlled && resumable,
         CivilizationActionId.AUTHORIZE_COLONIZATION.i18nKey(),
         authorizeBlocked,
         hasColonization ? colonization.operationId().toString() : null
      ));

      boolean interrupted = colonization != null
         && ("failed".equals(colonization.statusCode())
            || "cancelled".equals(colonization.statusCode())
            || colonization.refundOutstanding()
            || "chunk_not_loaded".equals(colonization.reasonCode())
            || "refund_outstanding".equals(colonization.reasonCode()));
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.VIEW_INTERRUPTED_OPERATION,
         interrupted,
         CivilizationActionId.VIEW_INTERRUPTED_OPERATION.i18nKey(),
         interrupted ? "" : "civilization.millenaire.action.blocked.no_interrupted",
         interrupted ? colonization.operationId().toString() : null
      ));

      Faction sovereign = resolveSovereignFactionForViewer(level, village, viewer);
      boolean isSovereign = sovereign != null;
      ImmigrationPolicy immigration = isSovereign ? sovereign.getImmigrationPolicy() : ImmigrationPolicy.OPEN;
      TradePolicy trade = isSovereign ? sovereign.getTradePolicy() : TradePolicy.INBOUND_ONLY;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SET_IMMIGRATION_POLICY,
         isSovereign,
         CivilizationActionId.SET_IMMIGRATION_POLICY.i18nKey(),
         isSovereign ? "" : "civilization.millenaire.action.blocked.not_sovereign",
         cycleImmigrationPolicy(immigration).name()
      ));
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SET_TRADE_POLICY,
         isSovereign,
         CivilizationActionId.SET_TRADE_POLICY.i18nKey(),
         isSovereign ? "" : "civilization.millenaire.action.blocked.not_sovereign",
         cycleTradePolicy(trade).name()
      ));

      boolean canManageProjects = controlled || isSovereign;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.OPEN_CONTROLLED_PROJECTS,
         canManageProjects,
         CivilizationActionId.OPEN_CONTROLLED_PROJECTS.i18nKey(),
         canManageProjects ? "" : "civilization.millenaire.action.blocked.not_controller",
         null
      ));

      if (!isSovereign) {
         actions.add(disabledDiplomacy(CivilizationActionId.DECLARE_WAR, "civilization.millenaire.action.blocked.not_sovereign"));
         actions.add(disabledDiplomacy(CivilizationActionId.OFFER_PEACE, "civilization.millenaire.action.blocked.not_sovereign"));
         actions.add(disabledDiplomacy(CivilizationActionId.SET_EMBARGO, "civilization.millenaire.action.blocked.not_sovereign"));
         actions.add(disabledDiplomacy(CivilizationActionId.CLEAR_EMBARGO, "civilization.millenaire.action.blocked.not_sovereign"));
      } else {
         appendDiplomacyTargetActions(level, sovereign, actions);
         appendGuardAssignmentActions(village, actions);
      }

      return actions;
   }

   private static void appendGuardAssignmentActions(
      Village village, List<CivilizationVillageSnapshot.AllowedAction> actions
   ) {
      int added = 0;
      for (org.millenaire.village.VillagerRecord record : village.getVillagerRecords().values()) {
         if (record == null || record.isKilled() || added >= 8) {
            continue;
         }
         String name = (record.getFirstName() == null ? "" : record.getFirstName())
            + " "
            + (record.getFamilyName() == null ? "" : record.getFamilyName());
         name = name.trim();
         if (name.isBlank()) {
            name = record.getUuid().toString().substring(0, 8);
         }
         String targetId = record.getUuid().toString();
         if (record.isPlayerAssignedGuard()) {
            actions.add(
               new CivilizationVillageSnapshot.AllowedAction(
                  CivilizationActionId.RELIEVE_GUARD,
                  true,
                  CivilizationActionId.RELIEVE_GUARD.i18nKey() + "|" + name,
                  "",
                  targetId
               )
            );
         } else {
            boolean canAssign = org.millenaire.village.GuardAssignmentService.findGuardType(village.getCultureId()) != null;
            actions.add(
               new CivilizationVillageSnapshot.AllowedAction(
                  CivilizationActionId.ASSIGN_GUARD,
                  canAssign,
                  CivilizationActionId.ASSIGN_GUARD.i18nKey() + "|" + name,
                  canAssign ? "" : "civilization.millenaire.action.blocked.no_guard_type",
                  targetId
               )
            );
         }
         added++;
      }
   }

   private static CivilizationVillageSnapshot.AllowedAction disabledDiplomacy(CivilizationActionId id, String reason) {
      return new CivilizationVillageSnapshot.AllowedAction(id, false, id.i18nKey(), reason, null);
   }

   private static void appendDiplomacyTargetActions(
      ServerLevel level,
      Faction sovereign,
      List<CivilizationVillageSnapshot.AllowedAction> actions
   ) {
      List<Faction> targets = new ArrayList<>();
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getId().equals(sovereign.getId())) {
            continue;
         }
         boolean interesting = WarSavedData.get(level).getWarManager().areAtWar(sovereign.getId(), faction.getId())
            || RelationshipSavedData.get(level).getRelationshipManager().isHostile(sovereign.getId(), faction.getId())
            || RelationshipSavedData.get(level).getRelationshipManager().isHostile(faction.getId(), sovereign.getId())
            || (faction.getType() == FactionType.CULTURE && faction.getCultureId() != null);
         if (!interesting) {
            continue;
         }
         targets.add(faction);
         if (targets.size() >= 24) {
            break;
         }
      }
      if (targets.isEmpty()) {
         actions.add(disabledDiplomacy(CivilizationActionId.DECLARE_WAR, "civilization.millenaire.action.blocked.no_targets"));
         actions.add(disabledDiplomacy(CivilizationActionId.OFFER_PEACE, "civilization.millenaire.action.blocked.no_targets"));
         actions.add(disabledDiplomacy(CivilizationActionId.SET_EMBARGO, "civilization.millenaire.action.blocked.no_targets"));
         actions.add(disabledDiplomacy(CivilizationActionId.CLEAR_EMBARGO, "civilization.millenaire.action.blocked.no_targets"));
         return;
      }
      for (Faction target : targets) {
         boolean atWar = WarSavedData.get(level).getWarManager().areAtWar(sovereign.getId(), target.getId());
         boolean embargo = DiplomacySavedData.get(level).getDiplomaticManager().isEmbargoed(sovereign.getId(), target.getId())
            || DiplomacySavedData.get(level).getDiplomaticManager().isEmbargoed(target.getId(), sovereign.getId());
         String targetId = target.getId().asString();
         String labelSuffix = target.getDisplayName() == null ? targetId : target.getDisplayName();
         actions.add(new CivilizationVillageSnapshot.AllowedAction(
            CivilizationActionId.DECLARE_WAR,
            !atWar,
            CivilizationActionId.DECLARE_WAR.i18nKey() + "|" + labelSuffix,
            atWar ? "civilization.millenaire.action.blocked.already_war" : "",
            targetId
         ));
         actions.add(new CivilizationVillageSnapshot.AllowedAction(
            CivilizationActionId.OFFER_PEACE,
            atWar,
            CivilizationActionId.OFFER_PEACE.i18nKey() + "|" + labelSuffix,
            atWar ? "" : "civilization.millenaire.action.blocked.no_war",
            targetId
         ));
         actions.add(new CivilizationVillageSnapshot.AllowedAction(
            CivilizationActionId.SET_EMBARGO,
            !embargo,
            CivilizationActionId.SET_EMBARGO.i18nKey() + "|" + labelSuffix,
            embargo ? "civilization.millenaire.action.blocked.already_embargo" : "",
            targetId
         ));
         actions.add(new CivilizationVillageSnapshot.AllowedAction(
            CivilizationActionId.CLEAR_EMBARGO,
            embargo,
            CivilizationActionId.CLEAR_EMBARGO.i18nKey() + "|" + labelSuffix,
            embargo ? "" : "civilization.millenaire.action.blocked.no_embargo",
            targetId
         ));
      }
   }

   @Nullable
   private static Faction resolveSovereignFactionForViewer(
      ServerLevel level,
      Village village,
      @Nullable ServerPlayer viewer
   ) {
      if (viewer == null || !PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
         return null;
      }
      boolean owner = village.isControlledBy(viewer.getUUID())
         || (village.getOwnerUUID() != null && village.getOwnerUUID().equals(viewer.getUUID()));
      if (!owner) {
         return null;
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
      if (faction == null || faction.getType() != FactionType.PLAYER) {
         return null;
      }
      return faction.isSovereignOwnedBy(viewer.getUUID()) ? faction : null;
   }

   private static ImmigrationPolicy cycleImmigrationPolicy(ImmigrationPolicy current) {
      return switch (current == null ? ImmigrationPolicy.OPEN : current) {
         case OPEN -> ImmigrationPolicy.RESTRICTED;
         case RESTRICTED -> ImmigrationPolicy.CLOSED;
         case CLOSED -> ImmigrationPolicy.OPEN;
      };
   }

   private static TradePolicy cycleTradePolicy(TradePolicy current) {
      return switch (current == null ? TradePolicy.INBOUND_ONLY : current) {
         case OPEN -> TradePolicy.INBOUND_ONLY;
         case INBOUND_ONLY -> TradePolicy.CLOSED;
         case CLOSED -> TradePolicy.OPEN;
      };
   }

   private static boolean isColonizationResumable(@Nullable ColonizationOperation op) {
      if (op == null || !op.isActive()) {
         return false;
      }
      return switch (op.status()) {
         case PLANNED, AWAITING_CHUNKS, LAYOUT, RESERVED, MATERIALIZING, ACTIVATED, MIGRATING -> true;
         default -> false;
      };
   }

   private static boolean colonizationMaterialsMissing(ServerLevel level, ColonizationOperation op) {
      if (op == null || op.costsConsumed() || op.isTerminal()) {
         return false;
      }
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      if (plan == null) {
         return false;
      }
      FoundingCostService.Coverage coverage = FoundingCostService.assessCoverage(plan, op.reservedStacks());
      return !coverage.fullyCovered();
   }

   @Nullable
   private static ControlledQueuedProject findHousingProject(Village village) {
      List<ControlledQueuedProject> queue = village.getControlledQueue();
      for (int i = 0; i < queue.size(); i++) {
         ControlledQueuedProject p = queue.get(i);
         if (p.planSetId() == null) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(p.planSetId());
         if (set != null && set.isResidential()) {
            return p;
         }
      }
      return null;
   }

   private static String villageNameOrFallback(ServerLevel level, UUID villageUuid, String fallback) {
      Village v = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
      return v == null ? fallback : villageName(v);
   }
}
