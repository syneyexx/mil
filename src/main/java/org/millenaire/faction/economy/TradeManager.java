package org.millenaire.faction.economy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Authoritative faction trade and economic statistics API.
 * Settlement stock remains in Millénaire chests; this layer records trade/policy state and aggregates views.
 */
public final class TradeManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<TradeRouteKey, TradeRoute> routes = new LinkedHashMap<>();
   private final Map<FactionId, Set<TradeRouteKey>> bySource = new LinkedHashMap<>();
   private final Map<FactionId, Set<TradeRouteKey>> byTarget = new LinkedHashMap<>();
   private final Map<FactionId, FactionEconomicProfile> profiles = new LinkedHashMap<>();
   private final Map<String, ResourceDependency> dependencies = new LinkedHashMap<>();
   private final Map<String, Integer> relationshipDeltaBudget = new LinkedHashMap<>();
   private final Runnable markDirty;
   private long lastEconomyUpdateGameTime;
   private long lastActivityDecayCheckGameTime;

   TradeManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   public FactionEconomicProfile getOrCreateProfile(FactionId factionId) {
      Objects.requireNonNull(factionId, "factionId");
      return this.profiles.computeIfAbsent(factionId, FactionEconomicProfile::new);
   }

   @Nullable
   public FactionEconomicProfile getProfile(FactionId factionId) {
      return factionId == null ? null : this.profiles.get(factionId);
   }

   public Collection<FactionEconomicProfile> getAllProfiles() {
      return Collections.unmodifiableCollection(this.profiles.values());
   }

   @Nullable
   public TradeRoute getRoute(FactionId source, FactionId target) {
      if (source == null || target == null || source.equals(target)) {
         return null;
      }

      return this.routes.get(TradeRouteKey.of(source, target));
   }

   public boolean hasRoute(FactionId source, FactionId target) {
      return this.getRoute(source, target) != null;
   }

   public Collection<TradeRoute> getAllRoutes() {
      return Collections.unmodifiableCollection(this.routes.values());
   }

   public Collection<TradeRoute> getRoutesFrom(FactionId source) {
      return this.collectRoutes(this.bySource.get(source));
   }

   public Collection<TradeRoute> getRoutesTo(FactionId target) {
      return this.collectRoutes(this.byTarget.get(target));
   }

   public Collection<TradeRoute> getRoutesInvolving(FactionId factionId) {
      if (factionId == null) {
         return List.of();
      }

      Set<TradeRouteKey> keys = new LinkedHashSet<>();
      Set<TradeRouteKey> sources = this.bySource.get(factionId);
      if (sources != null) {
         keys.addAll(sources);
      }

      Set<TradeRouteKey> targets = this.byTarget.get(factionId);
      if (targets != null) {
         keys.addAll(targets);
      }

      return this.collectRoutes(keys);
   }

   public Collection<ResourceDependency> getDependenciesForImporter(FactionId importer) {
      List<ResourceDependency> result = new ArrayList<>();

      for (ResourceDependency dependency : this.dependencies.values()) {
         if (dependency.getImporterFactionId().equals(importer)) {
            result.add(dependency);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<ResourceDependency> getAllDependencies() {
      return Collections.unmodifiableCollection(this.dependencies.values());
   }

   /**
    * Economic influence of {@code exporter} on {@code importer} (0–100).
    * Based on trade activity and resource dependency — not military or political power.
    */
   public int getEconomicInfluence(FactionId importer, FactionId exporter) {
      if (importer == null || exporter == null || importer.equals(exporter)) {
         return 0;
      }

      TradeRoute route = this.getRoute(exporter, importer);
      int activityScore = route == null ? 0 : Math.min(50, route.getActivity() / 20);
      int dependencyScore = 0;

      for (ResourceDependency dependency : this.dependencies.values()) {
         if (dependency.getImporterFactionId().equals(importer) && dependency.getExporterFactionId().equals(exporter)) {
            dependencyScore = Math.max(dependencyScore, Math.min(50, dependency.getDependencyPercent() / 2));
         }
      }

      return Math.min(100, activityScore + dependencyScore);
   }

   public boolean setRouteDisrupted(FactionId source, FactionId target, TradeDisruptionReason reason, long gameTime) {
      TradeRoute route = this.getOrCreateRoute(source, target, gameTime);
      if (route == null) {
         return false;
      }

      route.setDisrupted(reason);
      this.getOrCreateProfile(source)
         .recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.TRADE_ROUTE_DISRUPTED, target, null, 0, 0));
      this.markDirty.run();
      LOGGER.debug("[Millenaire] Trade route {} → {} disrupted ({})", source, target, reason);
      return true;
   }

   public boolean clearRouteDisruption(FactionId source, FactionId target, long gameTime) {
      TradeRoute route = this.getRoute(source, target);
      if (route == null) {
         return false;
      }

      boolean wasDisrupted = route.getStatus() == TradeRouteStatus.DISRUPTED;
      route.clearDisruption();
      if (wasDisrupted) {
         this.getOrCreateProfile(source)
            .recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.TRADE_ROUTE_RESTORED, target, null, 0, 0));
      }

      this.markDirty.run();
      return true;
   }

   /**
    * Authoritative API for recording a completed or failed trade between settlements/factions.
    */
   public void recordTrade(
      ServerLevel level,
      FactionId sourceFactionId,
      FactionId targetFactionId,
      EconomicResourceId resourceId,
      int quantity,
      int value,
      boolean successful,
      TradeFailureReason failureReason,
      @Nullable VillageId sourceSettlementId,
      @Nullable VillageId targetSettlementId
   ) {
      Objects.requireNonNull(level, "level");
      if (sourceFactionId == null || targetFactionId == null || resourceId == null) {
         LOGGER.warn("[Millenaire] Ignoring trade record with null faction/resource id");
         return;
      }

      if (sourceFactionId.equals(targetFactionId)) {
         LOGGER.debug("[Millenaire] Ignoring intra-faction goods movement {} (not faction trade)", sourceFactionId);
         return;
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(sourceFactionId) || !factions.exists(targetFactionId)) {
         LOGGER.warn("[Millenaire] Ignoring trade involving missing faction(s) {} / {}", sourceFactionId, targetFactionId);
         return;
      }

      long gameTime = level.getGameTime();
      TradeRoute route = this.getOrCreateRoute(sourceFactionId, targetFactionId, gameTime);
      if (route == null) {
         return;
      }

      if (!successful) {
         if (route.getStatus() == TradeRouteStatus.DISRUPTED) {
            failureReason = TradeFailureReason.ROUTE_DISRUPTED;
         }

         route.recordFailure(resourceId, quantity, value, gameTime, failureReason, sourceSettlementId, targetSettlementId);
         this.markDirty.run();
         LOGGER.debug("[Millenaire] Failed trade {} → {} {} x{} ({})", sourceFactionId, targetFactionId, resourceId, quantity, failureReason);
         return;
      }

      if (!route.isTradeable()) {
         route.recordFailure(resourceId, quantity, value, gameTime, TradeFailureReason.ROUTE_DISRUPTED, sourceSettlementId, targetSettlementId);
         this.markDirty.run();
         return;
      }

      if (!DiplomacySavedData.get(level).getDiplomaticManager().canTrade(sourceFactionId, targetFactionId)) {
         route.recordFailure(resourceId, quantity, value, gameTime, TradeFailureReason.EMBARGO, sourceSettlementId, targetSettlementId);
         this.markDirty.run();
         LOGGER.debug("[Millenaire] Trade {} → {} blocked by diplomatic embargo/restriction", sourceFactionId, targetFactionId);
         return;
      }

      boolean newlyEstablished = route.getSuccessfulTrades() == 0;
      route.recordSuccess(resourceId, quantity, value, gameTime, sourceSettlementId, targetSettlementId);
      FactionEconomicProfile exporter = this.getOrCreateProfile(sourceFactionId);
      FactionEconomicProfile importer = this.getOrCreateProfile(targetFactionId);
      exporter.recordExport(resourceId, quantity, value);
      importer.recordImport(resourceId, quantity, value);
      this.updateDependency(importer, sourceFactionId, resourceId, quantity, gameTime);
      if (newlyEstablished) {
         exporter.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.TRADE_ROUTE_ESTABLISHED, targetFactionId, resourceId, quantity, value));
      }

      if (TradeValueCalculator.isMajorTrade(value)) {
         exporter.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.MAJOR_EXPORT, targetFactionId, resourceId, quantity, value));
         importer.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.MAJOR_IMPORT, sourceFactionId, resourceId, quantity, value));
         exporter.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.MAJOR_TRADE, targetFactionId, resourceId, quantity, value));
      }

      this.applyTradeRelationshipInfluence(level, sourceFactionId, targetFactionId, value, gameTime);
      this.markDirty.run();
      LOGGER.debug(
         "[Millenaire] Recorded trade {} → {} {} x{} value={} activity={}",
         sourceFactionId,
         targetFactionId,
         resourceId,
         quantity,
         value,
         route.getActivity()
      );
   }

   /**
    * Records physical Millénaire settlement goods transfer as faction trade when factions differ.
    */
   public void recordSettlementGoodsTransfer(ServerLevel level, Village sourceVillage, Village targetVillage, Map<Item, Integer> transferred) {
      if (level == null || sourceVillage == null || targetVillage == null || transferred == null || transferred.isEmpty()) {
         return;
      }

      FactionId sourceFaction = sourceVillage.getFactionId();
      FactionId targetFaction = targetVillage.getFactionId();
      if (sourceFaction == null || targetFaction == null || sourceFaction.equals(targetFaction)) {
         return;
      }

      for (Map.Entry<Item, Integer> entry : transferred.entrySet()) {
         Item item = entry.getKey();
         int quantity = entry.getValue() == null ? 0 : entry.getValue();
         if (item == null || quantity <= 0) {
            continue;
         }

         EconomicResourceId resourceId = EconomicResourceId.of(item);
         int value = TradeValueCalculator.estimateValue(sourceVillage.getCultureId(), item, quantity);
         this.recordTrade(
            level,
            sourceFaction,
            targetFaction,
            resourceId,
            quantity,
            value,
            true,
            TradeFailureReason.NONE,
            sourceVillage.getId(),
            targetVillage.getId()
         );
      }
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastActivityDecayCheckGameTime <= 0L || gameTime - this.lastActivityDecayCheckGameTime >= EconomicConfig.TRADE_ACTIVITY_DECAY_CHECK_TICKS) {
         this.lastActivityDecayCheckGameTime = gameTime;
         boolean dirty = false;

         for (TradeRoute route : this.routes.values()) {
            if (route.decayActivity(gameTime)) {
               dirty = true;
            }
         }

         long currentDay = dayIndex(gameTime);
         boolean prunedBudgets = this.relationshipDeltaBudget.entrySet().removeIf(entry -> {
            int at = entry.getKey().lastIndexOf('@');
            if (at < 0) {
               return true;
            }

            try {
               long day = Long.parseLong(entry.getKey().substring(at + 1));
               return day < currentDay - 2L;
            } catch (NumberFormatException e) {
               return true;
            }
         });
         if (prunedBudgets) {
            dirty = true;
         }

         if (dirty) {
            this.markDirty.run();
         }
      }

      if (this.lastEconomyUpdateGameTime > 0L && gameTime - this.lastEconomyUpdateGameTime < EconomicConfig.ECONOMY_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastEconomyUpdateGameTime = gameTime;
      this.refreshAllProfiles(level);
   }

   public void refreshAllProfiles(ServerLevel level) {
      FactionManager factionManager = FactionSavedData.get(level).getFactionManager();
      long gameTime = level.getGameTime();

      for (Faction faction : factionManager.getAllFactions()) {
         this.refreshProfile(level, faction, gameTime);
      }

      this.markDirty.run();
   }

   public void refreshProfile(ServerLevel level, Faction faction, long gameTime) {
      FactionEconomicProfile profile = this.getOrCreateProfile(faction.getId());
      Map<EconomicResourceId, ResourceBalance> balances = FactionEconomyAggregator.aggregateBalances(level, faction);
      int stockValue = FactionEconomyAggregator.estimateStockValue(level, faction, balances);
      int demandPressure = FactionEconomyAggregator.estimateDemandPressure(balances);
      int surplus = FactionEconomyAggregator.countSurplusResources(balances);
      int deficit = FactionEconomyAggregator.countDeficitResources(balances);
      int significantDependencies = 0;

      for (ResourceDependency dependency : this.getDependenciesForImporter(faction.getId())) {
         if (dependency.isSignificant()) {
            significantDependencies++;
         }
      }

      EconomicBalanceLevel previous = profile.getBalanceLevel();
      profile.applySnapshot(balances, stockValue, demandPressure, surplus, deficit, significantDependencies, gameTime);
      if (deficit > 0 && (previous != EconomicBalanceLevel.UNDER_PRESSURE && previous != EconomicBalanceLevel.IMPORT_DEPENDENT)) {
         profile.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.RESOURCE_SHORTAGE, null, null, deficit, demandPressure));
      } else if (surplus > 0 && deficit == 0 && previous != EconomicBalanceLevel.HEALTHY) {
         profile.recordHistory(new EconomicHistoryEntry(gameTime, EconomicEventType.RESOURCE_SURPLUS, null, null, surplus, stockValue));
      }
   }

   public int removeAllInvolving(FactionId factionId) {
      if (factionId == null) {
         return 0;
      }

      Set<TradeRouteKey> keys = new LinkedHashSet<>();
      Set<TradeRouteKey> sources = this.bySource.get(factionId);
      if (sources != null) {
         keys.addAll(sources);
      }

      Set<TradeRouteKey> targets = this.byTarget.get(factionId);
      if (targets != null) {
         keys.addAll(targets);
      }

      int removed = 0;

      for (TradeRouteKey key : keys) {
         if (this.routes.remove(key) != null) {
            this.unindex(key);
            removed++;
         }
      }

      this.bySource.remove(factionId);
      this.byTarget.remove(factionId);
      boolean hadProfile = this.profiles.remove(factionId) != null;
      boolean removedDependencies = this.dependencies.entrySet().removeIf(entry -> {
         ResourceDependency dependency = entry.getValue();
         return dependency.getImporterFactionId().equals(factionId) || dependency.getExporterFactionId().equals(factionId);
      });
      boolean removedBudgets = this.relationshipDeltaBudget.entrySet().removeIf(entry -> entry.getKey().contains(factionId.asString()));
      if (removed > 0 || hadProfile || removedDependencies || removedBudgets) {
         this.markDirty.run();
      }

      if (removed > 0) {
         LOGGER.info("[Millenaire] Removed {} trade route(s) involving faction {}", removed, factionId);
      }

      return removed;
   }

   public int purgeOrphans(FactionManager factionManager) {
      List<TradeRouteKey> orphanRoutes = new ArrayList<>();

      for (TradeRouteKey key : this.routes.keySet()) {
         if (!factionManager.exists(key.source()) || !factionManager.exists(key.target())) {
            orphanRoutes.add(key);
         }
      }

      for (TradeRouteKey key : orphanRoutes) {
         this.routes.remove(key);
         this.unindex(key);
      }

      this.profiles.entrySet().removeIf(entry -> !factionManager.exists(entry.getKey()));
      this.dependencies.entrySet().removeIf(entry -> {
         ResourceDependency dependency = entry.getValue();
         return !factionManager.exists(dependency.getImporterFactionId()) || !factionManager.exists(dependency.getExporterFactionId());
      });
      if (!orphanRoutes.isEmpty()) {
         this.markDirty.run();
         LOGGER.warn("[Millenaire] Purged {} orphan trade route(s)", orphanRoutes.size());
      }

      return orphanRoutes.size();
   }

   void loadRoute(TradeRoute route) {
      Objects.requireNonNull(route, "route");
      TradeRouteKey key = route.getKey();
      if (key.isSelf()) {
         LOGGER.error("[Millenaire] Ignoring self trade route {}", key.source());
         return;
      }

      if (this.routes.containsKey(key)) {
         LOGGER.error("[Millenaire] Ignoring duplicate trade route {} → {}", key.source(), key.target());
         return;
      }

      this.indexRoute(route);
   }

   void loadProfile(FactionEconomicProfile profile) {
      Objects.requireNonNull(profile, "profile");
      this.profiles.put(profile.getFactionId(), profile);
   }

   void loadDependency(ResourceDependency dependency) {
      Objects.requireNonNull(dependency, "dependency");
      this.dependencies.put(dependencyKey(dependency.getImporterFactionId(), dependency.getExporterFactionId(), dependency.getResourceId()), dependency);
   }

   long getLastEconomyUpdateGameTime() {
      return this.lastEconomyUpdateGameTime;
   }

   void setLastEconomyUpdateGameTime(long gameTime) {
      this.lastEconomyUpdateGameTime = gameTime;
   }

   long getLastActivityDecayCheckGameTime() {
      return this.lastActivityDecayCheckGameTime;
   }

   void setLastActivityDecayCheckGameTime(long gameTime) {
      this.lastActivityDecayCheckGameTime = gameTime;
   }

   void clear() {
      this.routes.clear();
      this.bySource.clear();
      this.byTarget.clear();
      this.profiles.clear();
      this.dependencies.clear();
      this.relationshipDeltaBudget.clear();
      this.lastEconomyUpdateGameTime = 0L;
      this.lastActivityDecayCheckGameTime = 0L;
   }

   @Nullable
   private TradeRoute getOrCreateRoute(FactionId source, FactionId target, long gameTime) {
      if (source == null || target == null || source.equals(target)) {
         return null;
      }

      TradeRouteKey key = TradeRouteKey.of(source, target);
      TradeRoute existing = this.routes.get(key);
      if (existing != null) {
         return existing;
      }

      TradeRoute created = new TradeRoute(source, target, gameTime);
      this.indexRoute(created);
      this.getOrCreateProfile(source);
      this.getOrCreateProfile(target);
      LOGGER.debug("[Millenaire] Established trade route {} → {}", source, target);
      return created;
   }

   private void updateDependency(FactionEconomicProfile importer, FactionId exporter, EconomicResourceId resourceId, int quantity, long gameTime) {
      String key = dependencyKey(importer.getFactionId(), exporter, resourceId);
      ResourceDependency dependency = this.dependencies.computeIfAbsent(
         key, ignored -> new ResourceDependency(importer.getFactionId(), exporter, resourceId)
      );
      dependency.recordImport(quantity, gameTime);
      long totalImports = Math.max(1L, importer.totalImportsOf(resourceId));
      int percent = (int)Math.min(100L, dependency.getCumulativeVolume() * 100L / totalImports);
      dependency.setDependencyPercent(percent);
   }

   private void applyTradeRelationshipInfluence(ServerLevel level, FactionId exporter, FactionId importer, int value, long gameTime) {
      int delta = EconomicConfig.relationshipDeltaForValue(value);
      if (delta <= 0) {
         return;
      }

      String budgetKey = exporter.asString() + ">" + importer.asString() + "@" + dayIndex(gameTime);
      int used = this.relationshipDeltaBudget.getOrDefault(budgetKey, 0);
      int remaining = EconomicConfig.MAX_RELATIONSHIP_DELTA_PER_DAY_PER_PAIR - used;
      if (remaining <= 0) {
         return;
      }

      int applied = Math.min(delta, remaining);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .modifyRelationship(level, importer, exporter, applied, RelationshipChangeReason.TRADE, gameTime);
      // Mild reciprocal goodwill from sustained commerce, still capped.
      int reciprocal = Math.min(1, applied);
      if (reciprocal > 0) {
         String reverseBudgetKey = importer.asString() + ">" + exporter.asString() + "@" + dayIndex(gameTime);
         int reverseUsed = this.relationshipDeltaBudget.getOrDefault(reverseBudgetKey, 0);
         int reverseRemaining = EconomicConfig.MAX_RELATIONSHIP_DELTA_PER_DAY_PER_PAIR - reverseUsed;
         if (reverseRemaining > 0) {
            int reverseApplied = Math.min(reciprocal, reverseRemaining);
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .modifyRelationship(level, exporter, importer, reverseApplied, RelationshipChangeReason.TRADE, gameTime);
            this.relationshipDeltaBudget.put(reverseBudgetKey, reverseUsed + reverseApplied);
         }
      }

      this.relationshipDeltaBudget.put(budgetKey, used + applied);
   }

   private static long dayIndex(long gameTime) {
      return gameTime / org.millenaire.TickConstants.DAY_LENGTH;
   }

   private Collection<TradeRoute> collectRoutes(@Nullable Set<TradeRouteKey> keys) {
      if (keys == null || keys.isEmpty()) {
         return List.of();
      }

      List<TradeRoute> result = new ArrayList<>(keys.size());

      for (TradeRouteKey key : keys) {
         TradeRoute route = this.routes.get(key);
         if (route != null) {
            result.add(route);
         }
      }

      return Collections.unmodifiableList(result);
   }

   private void indexRoute(TradeRoute route) {
      TradeRouteKey key = route.getKey();
      this.routes.put(key, route);
      this.bySource.computeIfAbsent(key.source(), ignored -> new LinkedHashSet<>()).add(key);
      this.byTarget.computeIfAbsent(key.target(), ignored -> new LinkedHashSet<>()).add(key);
   }

   private void unindex(TradeRouteKey key) {
      Set<TradeRouteKey> sources = this.bySource.get(key.source());
      if (sources != null) {
         sources.remove(key);
         if (sources.isEmpty()) {
            this.bySource.remove(key.source());
         }
      }

      Set<TradeRouteKey> targets = this.byTarget.get(key.target());
      if (targets != null) {
         targets.remove(key);
         if (targets.isEmpty()) {
            this.byTarget.remove(key.target());
         }
      }
   }

   private static String dependencyKey(FactionId importer, FactionId exporter, EconomicResourceId resourceId) {
      return importer.asString() + "|" + exporter.asString() + "|" + resourceId.asString();
   }
}
