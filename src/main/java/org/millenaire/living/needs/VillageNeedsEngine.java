package org.millenaire.living.needs;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Deterministic village-needs authority. Derives needs from existing Millénaire state only.
 */
public final class VillageNeedsEngine {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<UUID, VillageNeedsSnapshot> snapshots = new HashMap<>();
   private final Map<UUID, Map<String, VillageNeedPriority>> previousSeverity = new HashMap<>();
   private final Map<UUID, Long> lastEvalTick = new HashMap<>();
   private final Map<UUID, Boolean> knownShortage = new HashMap<>();
   private final Map<UUID, Boolean> knownAttack = new HashMap<>();
   private int evaluationsThisTick;
   private long currentTick = Long.MIN_VALUE;

   public void invalidate(UUID villageId) {
      this.snapshots.remove(villageId);
   }

   public void invalidateAll() {
      this.snapshots.clear();
   }

   public void onVillageRemoved(UUID villageId) {
      this.snapshots.remove(villageId);
      this.previousSeverity.remove(villageId);
      this.lastEvalTick.remove(villageId);
      this.knownShortage.remove(villageId);
      this.knownAttack.remove(villageId);
   }

   @Nullable
   public VillageNeedsSnapshot getCached(UUID villageId) {
      return this.snapshots.get(villageId);
   }

   public VillageNeedsSnapshot evaluate(ServerLevel level, Village village, boolean force) {
      UUID id = village.getId().uuid();
      long now = level.getGameTime();
      if (now != this.currentTick) {
         this.currentTick = now;
         this.evaluationsThisTick = 0;
      }

      VillageNeedsSnapshot cached = this.snapshots.get(id);
      int interval = MillenaireServerConfig.SERVER.villageNeedsEvaluationInterval.getAsInt();
      Long last = this.lastEvalTick.get(id);
      if (!force && cached != null && last != null && now - last < interval) {
         return cached;
      }

      int budget = MillenaireServerConfig.SERVER.villageNeedsGlobalBudget.getAsInt();
      if (!force && this.evaluationsThisTick >= budget) {
         return cached != null ? cached : emptySnapshot(id, now);
      }

      // Stagger by village hash so villages do not all evaluate on the same tick.
      if (!force && last == null) {
         int stagger = Math.floorMod(id.hashCode(), Math.max(1, interval));
         if (now % interval != stagger) {
            return cached != null ? cached : emptySnapshot(id, now);
         }
      }

      this.evaluationsThisTick++;
      VillageNeedsSnapshot snapshot = compute(level, village, now);
      this.snapshots.put(id, snapshot);
      this.lastEvalTick.put(id, now);
      this.emitTransitions(level, village, snapshot);
      return snapshot;
   }

   public void tickActiveVillages(ServerLevel level) {
      if (!enabled()) {
         return;
      }
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      if (manager == null) {
         return;
      }
      for (Village village : manager.getAllVillages()) {
         if (village.isActive()) {
            this.evaluate(level, village, false);
         }
      }
   }

   private VillageNeedsSnapshot compute(ServerLevel level, Village village, long now) {
      List<VillageNeed> needs = new ArrayList<>();
      Map<String, VillageNeedPriority> prev = this.previousSeverity.computeIfAbsent(village.getId().uuid(), k -> new HashMap<>());
      int version = (int)(now & 0x7fffffff);

      VillageNeedResolver.ConstructionShortage construction = VillageNeedResolver.resolveConstructionShortage(level, village);
      if (construction != null && construction.hasShortage()) {
         double requiredTotal = construction.required().values().stream().mapToInt(Integer::intValue).sum();
         double missingTotal = construction.shortage().values().stream().mapToInt(Integer::intValue).sum();
         double ratio = requiredTotal <= 0 ? 0 : missingTotal / requiredTotal;
         VillageNeedPriority prevSev = prev.getOrDefault("construction", VillageNeedPriority.NONE);
         VillageNeedPriority severity = VillageNeedPriority.fromShortageRatio(ratio, prevSev);
         prev.put("construction", severity);
         needs.add(
            new VillageNeed(
               "construction:" + construction.projectRef(),
               village.getId().uuid(),
               VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE,
               severity,
               construction.projectRef(),
               construction.required(),
               construction.available(),
               construction.shortage(),
               "living.millenaire.need.construction_material_shortage",
               construction.projectName(),
               now,
               version
            )
         );
      } else {
         prev.put("construction", VillageNeedPriority.NONE);
      }

      if (construction != null && construction.stallReason() != null && !construction.hasShortage()) {
         VillageNeedPriority severity = VillageNeedPriority.MODERATE;
         needs.add(
            new VillageNeed(
               "stalled:" + construction.projectRef(),
               village.getId().uuid(),
               VillageNeedType.CONSTRUCTION_STALLED,
               severity,
               construction.projectRef(),
               Map.of(),
               Map.of(),
               Map.of(),
               "living.millenaire.need.construction_stalled",
               construction.stallReason(),
               now,
               version
            )
         );
      }

      if (village.isUnderAttack()) {
         needs.add(
            new VillageNeed(
               "security:raid",
               village.getId().uuid(),
               VillageNeedType.VILLAGE_SECURITY_THREAT,
               VillageNeedPriority.CRITICAL,
               null,
               Map.of(),
               Map.of(),
               Map.of(),
               "living.millenaire.need.village_security_threat",
               "raid",
               now,
               version
            )
         );
      }

      int recentDeaths = countRecentDeaths(village);
      if (recentDeaths > 0) {
         needs.add(
            new VillageNeed(
               "resident_loss",
               village.getId().uuid(),
               VillageNeedType.RESIDENT_LOSS,
               recentDeaths >= 3 ? VillageNeedPriority.HIGH : VillageNeedPriority.MODERATE,
               null,
               Map.of(),
               Map.of(),
               Map.of(),
               "living.millenaire.need.resident_loss",
               String.valueOf(recentDeaths),
               now,
               version
            )
         );
      }

      // Merchant / supply: only when MarketManager exposes no market inventory and a market building exists.
      if (hasMarketWithoutStock(level, village)) {
         needs.add(
            new VillageNeed(
               "merchant_unavailable",
               village.getId().uuid(),
               VillageNeedType.MERCHANT_OR_SUPPLY_UNAVAILABLE,
               VillageNeedPriority.LOW,
               null,
               Map.of(),
               Map.of(),
               Map.of(),
               "living.millenaire.need.merchant_or_supply_unavailable",
               "market",
               now,
               version
            )
         );
      }

      this.previousSeverity.put(village.getId().uuid(), prev);
      return new VillageNeedsSnapshot(village.getId().uuid(), now, version, needs);
   }

   private void emitTransitions(ServerLevel level, Village village, VillageNeedsSnapshot snapshot) {
      UUID id = village.getId().uuid();
      boolean shortage = snapshot.firstOfType(VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE).isPresent();
      Boolean wasShortage = this.knownShortage.put(id, shortage);
      if (shortage && (wasShortage == null || !wasShortage)) {
         VillageNeed need = snapshot.firstOfType(VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE).orElse(null);
         LivingEventService.emit(
            level,
            LivingEventType.MATERIAL_SHORTAGE_DETECTED,
            LivingEventSource.NEEDS_ENGINE,
            village,
            null,
            null,
            need != null ? need.summaryDetail() + " shortage=" + need.totalShortage() : "shortage",
            "public",
            "shortage_detected:" + id + ":" + (need != null ? need.projectRef() : "")
         );
      } else if (!shortage && Boolean.TRUE.equals(wasShortage)) {
         LivingEventService.emit(
            level,
            LivingEventType.MATERIAL_SHORTAGE_RESOLVED,
            LivingEventSource.NEEDS_ENGINE,
            village,
            null,
            null,
            "resolved",
            "public",
            "shortage_resolved:" + id
         );
      }

      boolean attack = village.isUnderAttack();
      Boolean wasAttack = this.knownAttack.put(id, attack);
      if (attack && (wasAttack == null || !wasAttack)) {
         LivingEventService.emit(
            level,
            LivingEventType.ATTACK_STARTED,
            LivingEventSource.RAID,
            village,
            null,
            null,
            "under_attack",
            "public",
            "attack_start:" + id + ":" + level.getGameTime() / 200
         );
      } else if (!attack && Boolean.TRUE.equals(wasAttack)) {
         LivingEventService.emit(
            level,
            LivingEventType.ATTACK_ENDED,
            LivingEventSource.RAID,
            village,
            null,
            null,
            "attack_ended",
            "public",
            "attack_end:" + id + ":" + level.getGameTime() / 200
         );
      }
   }

   private static int countRecentDeaths(Village village) {
      int count = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            count++;
         }
      }
      return count;
   }

   private static boolean hasMarketWithoutStock(ServerLevel level, Village village) {
      List<BuildingInstance> markets = village.getBuildingsWithTag("market");
      if (markets.isEmpty()) {
         markets = village.getBuildingsWithTag("shop");
      }
      if (markets.isEmpty()) {
         return false;
      }
      boolean anyScanned = false;
      for (BuildingInstance market : markets) {
         BuildingInventory inv = market.getInventory();
         if (inv == null) {
            continue;
         }
         anyScanned = true;
         inv.invalidateCacheOncePerTick(level.getGameTime());
         var contents = inv.getCachedContents();
         if (contents != null && !contents.isEmpty()) {
            return false;
         }
      }
      return anyScanned;
   }

   private static VillageNeedsSnapshot emptySnapshot(UUID id, long now) {
      return new VillageNeedsSnapshot(id, now, 0, List.of());
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.villageNeedsEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public VillageNeedsSnapshot requireFresh(ServerLevel level, VillageId villageId) {
      Village village = Village.resolve(level, villageId);
      if (village == null) {
         return emptySnapshot(villageId.uuid(), level.getGameTime());
      }
      this.invalidate(villageId.uuid());
      return this.evaluate(level, village, true);
   }
}
