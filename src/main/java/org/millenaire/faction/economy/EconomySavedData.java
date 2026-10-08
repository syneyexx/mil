package org.millenaire.faction.economy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for faction trade routes, economic profiles, and dependencies.
 */
public final class EconomySavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_economy";

   private final TradeManager tradeManager = new TradeManager(this::setDirty);

   private EconomySavedData() {
   }

   public static Factory<EconomySavedData> factory() {
      return new Factory<>(EconomySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static EconomySavedData get(ServerLevel level) {
      return (EconomySavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public TradeManager getTradeManager() {
      return this.tradeManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_economy_update", this.tradeManager.getLastEconomyUpdateGameTime());
      root.putLong("last_activity_decay_check", this.tradeManager.getLastActivityDecayCheckGameTime());
      ListTag routes = new ListTag();

      for (TradeRoute route : this.tradeManager.getAllRoutes()) {
         try {
            routes.add(saveRoute(route));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize trade route {} → {}", route.getSourceFactionId(), route.getTargetFactionId(), e);
         }
      }

      root.put("routes", routes);
      ListTag profiles = new ListTag();

      for (FactionEconomicProfile profile : this.tradeManager.getAllProfiles()) {
         try {
            profiles.add(saveProfile(profile));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize economic profile {}", profile.getFactionId(), e);
         }
      }

      root.put("profiles", profiles);
      ListTag dependencies = new ListTag();

      for (ResourceDependency dependency : this.tradeManager.getAllDependencies()) {
         try {
            dependencies.add(saveDependency(dependency));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize resource dependency", e);
         }
      }

      root.put("dependencies", dependencies);
      LOGGER.debug(
         "[Millenaire] Saved faction economy ({} routes, {} profiles, {} dependencies)",
         routes.size(),
         profiles.size(),
         dependencies.size()
      );
      return root;
   }

   private static CompoundTag saveRoute(TradeRoute route) {
      CompoundTag tag = new CompoundTag();
      tag.putString("source", route.getSourceFactionId().asString());
      tag.putString("target", route.getTargetFactionId().asString());
      tag.putString("status", route.getStatus().name());
      tag.putString("disruption_reason", route.getDisruptionReason().name());
      tag.putInt("activity", route.getActivity());
      tag.putInt("successful_trades", route.getSuccessfulTrades());
      tag.putInt("failed_trades", route.getFailedTrades());
      tag.putLong("cumulative_volume", route.getCumulativeVolume());
      tag.putLong("cumulative_value", route.getCumulativeValue());
      tag.putLong("established", route.getEstablishedGameTime());
      tag.putLong("last_success", route.getLastSuccessfulTradeGameTime());
      tag.putLong("last_attempt", route.getLastAttemptGameTime());
      tag.putLong("last_activity_decay", route.getLastActivityDecayGameTime());
      if (route.getLastSourceSettlementId() != null) {
         tag.putUUID("last_source_settlement", route.getLastSourceSettlementId().uuid());
      }

      if (route.getLastTargetSettlementId() != null) {
         tag.putUUID("last_target_settlement", route.getLastTargetSettlementId().uuid());
      }

      ListTag resources = new ListTag();

      for (TradeResourceStats stats : route.getResourceStats().values()) {
         CompoundTag resourceTag = new CompoundTag();
         resourceTag.putString("resource", stats.getResourceId().asString());
         resourceTag.putLong("volume", stats.getVolume());
         resourceTag.putLong("value", stats.getValue());
         resourceTag.putInt("success_count", stats.getSuccessCount());
         resourceTag.putLong("last_trade", stats.getLastTradeGameTime());
         resources.add(resourceTag);
      }

      tag.put("resources", resources);
      ListTag history = new ListTag();

      for (TradeHistoryEntry entry : route.getHistory()) {
         CompoundTag historyTag = new CompoundTag();
         historyTag.putLong("game_time", entry.getGameTime());
         historyTag.putString("resource", entry.getResourceId().asString());
         historyTag.putInt("quantity", entry.getQuantity());
         historyTag.putInt("value", entry.getValue());
         historyTag.putBoolean("successful", entry.isSuccessful());
         historyTag.putString("failure_reason", entry.getFailureReason().name());
         if (entry.getSourceSettlementId() != null) {
            historyTag.putUUID("source_settlement", entry.getSourceSettlementId().uuid());
         }

         if (entry.getTargetSettlementId() != null) {
            historyTag.putUUID("target_settlement", entry.getTargetSettlementId().uuid());
         }

         history.add(historyTag);
      }

      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveProfile(FactionEconomicProfile profile) {
      CompoundTag tag = new CompoundTag();
      tag.putString("faction", profile.getFactionId().asString());
      tag.putLong("export_volume", profile.getCumulativeExportVolume());
      tag.putLong("import_volume", profile.getCumulativeImportVolume());
      tag.putLong("export_value", profile.getCumulativeExportValue());
      tag.putLong("import_value", profile.getCumulativeImportValue());
      tag.putLong("last_simulated", profile.getLastSimulatedGameTime());
      tag.putInt("stock_value", profile.getLastStockValue());
      tag.putInt("demand_pressure", profile.getLastDemandPressure());
      tag.putInt("surplus_resources", profile.getLastSurplusResources());
      tag.putInt("deficit_resources", profile.getLastDeficitResources());
      tag.putInt("significant_dependencies", profile.getLastSignificantDependencies());
      tag.putString("balance", profile.getBalanceLevel().name());
      tag.put("export_volumes", saveVolumeMap(profile.getExportVolumes()));
      tag.put("import_volumes", saveVolumeMap(profile.getImportVolumes()));
      ListTag history = new ListTag();

      for (EconomicHistoryEntry entry : profile.getHistory()) {
         CompoundTag historyTag = new CompoundTag();
         historyTag.putLong("game_time", entry.getGameTime());
         historyTag.putString("type", entry.getType().name());
         if (entry.getOtherFactionId() != null) {
            historyTag.putString("other_faction", entry.getOtherFactionId().asString());
         }

         if (entry.getResourceId() != null) {
            historyTag.putString("resource", entry.getResourceId().asString());
         }

         historyTag.putInt("quantity", entry.getQuantity());
         historyTag.putInt("value", entry.getValue());
         history.add(historyTag);
      }

      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveDependency(ResourceDependency dependency) {
      CompoundTag tag = new CompoundTag();
      tag.putString("importer", dependency.getImporterFactionId().asString());
      tag.putString("exporter", dependency.getExporterFactionId().asString());
      tag.putString("resource", dependency.getResourceId().asString());
      tag.putLong("volume", dependency.getCumulativeVolume());
      tag.putInt("percent", dependency.getDependencyPercent());
      tag.putLong("last_update", dependency.getLastUpdateGameTime());
      return tag;
   }

   private static ListTag saveVolumeMap(Map<EconomicResourceId, Long> volumes) {
      ListTag list = new ListTag();

      for (Map.Entry<EconomicResourceId, Long> entry : volumes.entrySet()) {
         CompoundTag tag = new CompoundTag();
         tag.putString("resource", entry.getKey().asString());
         tag.putLong("volume", entry.getValue());
         list.add(tag);
      }

      return list;
   }

   private static EconomySavedData load(CompoundTag root) {
      EconomySavedData data = new EconomySavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] EconomySavedData version {} newer than supported {} — best-effort load", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading EconomySavedData version {}", version);
      }

      if (root.contains("last_economy_update")) {
         data.tradeManager.setLastEconomyUpdateGameTime(root.getLong("last_economy_update"));
      }

      if (root.contains("last_activity_decay_check")) {
         data.tradeManager.setLastActivityDecayCheckGameTime(root.getLong("last_activity_decay_check"));
      }

      if (root.contains("routes")) {
         ListTag routes = root.getList("routes", 10);

         for (int i = 0; i < routes.size(); i++) {
            try {
               TradeRoute route = readRoute(routes.getCompound(i));
               if (route != null) {
                  data.tradeManager.loadRoute(route);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed trade route at index {}", i, e);
            }
         }
      }

      if (root.contains("profiles")) {
         ListTag profiles = root.getList("profiles", 10);

         for (int i = 0; i < profiles.size(); i++) {
            try {
               FactionEconomicProfile profile = readProfile(profiles.getCompound(i));
               if (profile != null) {
                  data.tradeManager.loadProfile(profile);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed economic profile at index {}", i, e);
            }
         }
      }

      if (root.contains("dependencies")) {
         ListTag dependencies = root.getList("dependencies", 10);

         for (int i = 0; i < dependencies.size(); i++) {
            try {
               ResourceDependency dependency = readDependency(dependencies.getCompound(i));
               if (dependency != null) {
                  data.tradeManager.loadDependency(dependency);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed dependency at index {}", i, e);
            }
         }
      }

      return data;
   }

   @Nullable
   private static TradeRoute readRoute(CompoundTag tag) {
      FactionId source = FactionId.tryParse(tag.contains("source") ? tag.getString("source") : null);
      FactionId target = FactionId.tryParse(tag.contains("target") ? tag.getString("target") : null);
      if (source == null || target == null || source.equals(target)) {
         LOGGER.error("[Millenaire] Invalid trade route source/target — skipped");
         return null;
      }

      TradeRoute route = new TradeRoute(source, target, tag.contains("established") ? tag.getLong("established") : 0L);
      TradeRouteStatus status = TradeRouteStatus.tryParse(tag.contains("status") ? tag.getString("status") : null);
      TradeDisruptionReason disruption = TradeDisruptionReason.tryParse(tag.contains("disruption_reason") ? tag.getString("disruption_reason") : null);
      route.loadState(
         status,
         disruption,
         tag.contains("activity") ? tag.getInt("activity") : 0,
         tag.contains("successful_trades") ? tag.getInt("successful_trades") : 0,
         tag.contains("failed_trades") ? tag.getInt("failed_trades") : 0,
         tag.contains("cumulative_volume") ? tag.getLong("cumulative_volume") : 0L,
         tag.contains("cumulative_value") ? tag.getLong("cumulative_value") : 0L,
         tag.contains("established") ? tag.getLong("established") : 0L,
         tag.contains("last_success") ? tag.getLong("last_success") : 0L,
         tag.contains("last_attempt") ? tag.getLong("last_attempt") : 0L,
         tag.contains("last_activity_decay") ? tag.getLong("last_activity_decay") : 0L,
         tag.hasUUID("last_source_settlement") ? new VillageId(tag.getUUID("last_source_settlement")) : null,
         tag.hasUUID("last_target_settlement") ? new VillageId(tag.getUUID("last_target_settlement")) : null
      );
      Map<EconomicResourceId, TradeResourceStats> resources = new LinkedHashMap<>();
      if (tag.contains("resources")) {
         ListTag resourceList = tag.getList("resources", 10);

         for (int i = 0; i < resourceList.size(); i++) {
            CompoundTag resourceTag = resourceList.getCompound(i);
            EconomicResourceId resourceId = EconomicResourceId.tryParse(resourceTag.getString("resource"));
            if (resourceId == null) {
               continue;
            }

            TradeResourceStats stats = new TradeResourceStats(resourceId);
            stats.load(
               resourceTag.contains("volume") ? resourceTag.getLong("volume") : 0L,
               resourceTag.contains("value") ? resourceTag.getLong("value") : 0L,
               resourceTag.contains("success_count") ? resourceTag.getInt("success_count") : 0,
               resourceTag.contains("last_trade") ? resourceTag.getLong("last_trade") : 0L
            );
            resources.put(resourceId, stats);
         }
      }

      route.replaceResourceStats(resources);
      List<TradeHistoryEntry> history = new ArrayList<>();
      if (tag.contains("history")) {
         ListTag historyList = tag.getList("history", 10);

         for (int i = 0; i < historyList.size(); i++) {
            CompoundTag historyTag = historyList.getCompound(i);
            EconomicResourceId resourceId = EconomicResourceId.tryParse(historyTag.contains("resource") ? historyTag.getString("resource") : null);
            if (resourceId == null) {
               continue;
            }

            TradeFailureReason failure = TradeFailureReason.tryParse(historyTag.contains("failure_reason") ? historyTag.getString("failure_reason") : null);
            history.add(
               new TradeHistoryEntry(
                  historyTag.contains("game_time") ? historyTag.getLong("game_time") : 0L,
                  resourceId,
                  historyTag.contains("quantity") ? historyTag.getInt("quantity") : 0,
                  historyTag.contains("value") ? historyTag.getInt("value") : 0,
                  !historyTag.contains("successful") || historyTag.getBoolean("successful"),
                  failure == null ? TradeFailureReason.NONE : failure,
                  historyTag.hasUUID("source_settlement") ? new VillageId(historyTag.getUUID("source_settlement")) : null,
                  historyTag.hasUUID("target_settlement") ? new VillageId(historyTag.getUUID("target_settlement")) : null
               )
            );
         }
      }

      route.replaceHistory(history);
      return route;
   }

   @Nullable
   private static FactionEconomicProfile readProfile(CompoundTag tag) {
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         LOGGER.error("[Millenaire] Economic profile missing faction id — skipped");
         return null;
      }

      FactionEconomicProfile profile = new FactionEconomicProfile(factionId);
      EconomicBalanceLevel balance = null;
      if (tag.contains("balance")) {
         try {
            balance = EconomicBalanceLevel.valueOf(tag.getString("balance").trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            balance = EconomicBalanceLevel.BALANCED;
         }
      }

      profile.loadTotals(
         tag.contains("export_volume") ? tag.getLong("export_volume") : 0L,
         tag.contains("import_volume") ? tag.getLong("import_volume") : 0L,
         tag.contains("export_value") ? tag.getLong("export_value") : 0L,
         tag.contains("import_value") ? tag.getLong("import_value") : 0L,
         tag.contains("last_simulated") ? tag.getLong("last_simulated") : 0L,
         tag.contains("stock_value") ? tag.getInt("stock_value") : 0,
         tag.contains("demand_pressure") ? tag.getInt("demand_pressure") : 0,
         tag.contains("surplus_resources") ? tag.getInt("surplus_resources") : 0,
         tag.contains("deficit_resources") ? tag.getInt("deficit_resources") : 0,
         tag.contains("significant_dependencies") ? tag.getInt("significant_dependencies") : 0,
         balance
      );
      profile.replaceExportVolumes(readVolumeMap(tag.contains("export_volumes") ? tag.getList("export_volumes", 10) : new ListTag()));
      profile.replaceImportVolumes(readVolumeMap(tag.contains("import_volumes") ? tag.getList("import_volumes", 10) : new ListTag()));
      List<EconomicHistoryEntry> history = new ArrayList<>();
      if (tag.contains("history")) {
         ListTag historyList = tag.getList("history", 10);

         for (int i = 0; i < historyList.size(); i++) {
            CompoundTag historyTag = historyList.getCompound(i);
            EconomicEventType type = EconomicEventType.tryParse(historyTag.contains("type") ? historyTag.getString("type") : null);
            if (type == null) {
               continue;
            }

            history.add(
               new EconomicHistoryEntry(
                  historyTag.contains("game_time") ? historyTag.getLong("game_time") : 0L,
                  type,
                  FactionId.tryParse(historyTag.contains("other_faction") ? historyTag.getString("other_faction") : null),
                  EconomicResourceId.tryParse(historyTag.contains("resource") ? historyTag.getString("resource") : null),
                  historyTag.contains("quantity") ? historyTag.getInt("quantity") : 0,
                  historyTag.contains("value") ? historyTag.getInt("value") : 0
               )
            );
         }
      }

      profile.replaceHistory(history);
      return profile;
   }

   @Nullable
   private static ResourceDependency readDependency(CompoundTag tag) {
      FactionId importer = FactionId.tryParse(tag.contains("importer") ? tag.getString("importer") : null);
      FactionId exporter = FactionId.tryParse(tag.contains("exporter") ? tag.getString("exporter") : null);
      EconomicResourceId resourceId = EconomicResourceId.tryParse(tag.contains("resource") ? tag.getString("resource") : null);
      if (importer == null || exporter == null || resourceId == null || importer.equals(exporter)) {
         return null;
      }

      ResourceDependency dependency = new ResourceDependency(importer, exporter, resourceId);
      dependency.load(
         tag.contains("volume") ? tag.getLong("volume") : 0L,
         tag.contains("percent") ? tag.getInt("percent") : 0,
         tag.contains("last_update") ? tag.getLong("last_update") : 0L
      );
      return dependency;
   }

   private static Map<EconomicResourceId, Long> readVolumeMap(ListTag list) {
      Map<EconomicResourceId, Long> map = new LinkedHashMap<>();

      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         EconomicResourceId resourceId = EconomicResourceId.tryParse(tag.contains("resource") ? tag.getString("resource") : null);
         if (resourceId != null) {
            map.put(resourceId, tag.contains("volume") ? Math.max(0L, tag.getLong("volume")) : 0L);
         }
      }

      return map;
   }
}
