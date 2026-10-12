package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.BuildingId;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.FactionLifecycle;
import org.millenaire.living.LivingVillagesLifecycle;
import org.millenaire.perf.tick.LevelTickCache;
import org.millenaire.perf.tick.ServerTickGovernor;
import org.millenaire.perf.tick.TickPhase;
import org.millenaire.perf.tick.VillageSimulationLod;
import org.slf4j.Logger;

public class VillageManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<VillageId, Village> villages = new LinkedHashMap<>();
   private static final int FIREPLACE_SYNC_INTERVAL = 200;
   /** Mid-LOD villages deferred when the soft tick budget was spent; drained next ticks. */
   private final java.util.ArrayList<VillageId> midLodDeferred = new java.util.ArrayList<>();
   /** Near villages deferred when per-tick Near quota / soft budget spent. */
   private final java.util.ArrayList<VillageId> nearLodDeferred = new java.util.ArrayList<>();
   /** Coalesce VillageSavedData.setDirty to reduce full-NBT autosave hitch frequency. */
   private static final int DIRTY_COALESCE_TICKS = 20;
   private boolean pendingSavedDataDirty;
   /** Full activation/LOD/dirty reconcile of every village once per this many ticks (hash-sliced). */
   private static final int RECONCILE_INTERVAL_TICKS = 20;
   /**
    * Villages needing per-tick work: active (Near/Mid), raid-urgent, or force-loading/force-active
    * awaiting chunks. Everything else is Far data-only and visited via {@link #farBackgroundSlots}.
    */
   private final Set<Village> hotVillages = Collections.newSetFromMap(new IdentityHashMap<>());
   private final VillageTickSlots<Village> reconcileSlots = new VillageTickSlots<>();
   private final VillageTickSlots<Village> farBackgroundSlots = new VillageTickSlots<>();
   private final ArrayList<Village> hotScratch = new ArrayList<>();
   private boolean slotsDirty = true;

   public void addVillage(Village village) {
      this.villages.put(village.getId(), village);
      this.slotsDirty = true;
      org.millenaire.map.sync.SettlementMapSyncService.markDirty(village.getId());
   }

   /**
    * Removes a village from the manager and detaches it from its faction.
    */
   public void removeVillage(ServerLevel level, VillageId id) {
      Village village = this.villages.get(id);
      if (village != null) {
         FactionLifecycle.onVillageRemoved(level, village);
         try {
            org.millenaire.living.LivingVillagesLifecycle.onVillageRemoved(level, village);
         } catch (Exception e) {
            // Living Villages cleanup must not block faction detach / removal.
         }
      }

      this.villages.remove(id);
      if (village != null) {
         this.hotVillages.remove(village);
      }
      this.slotsDirty = true;
      org.millenaire.map.sync.SettlementMapSyncService.markRemoved(id);
   }

   @Nullable
   public Village getVillage(VillageId id) {
      return this.villages.get(id);
   }

   public Collection<Village> getAllVillages() {
      return Collections.unmodifiableCollection(this.villages.values());
   }

   public void clear() {
      this.villages.clear();
      this.hotVillages.clear();
      this.slotsDirty = true;
   }

   @Nullable
   public Village findNearestVillage(BlockPos pos, double maxDistance) {
      Village nearest = null;
      double nearestDistSq = maxDistance * maxDistance;

      for (Village v : this.villages.values()) {
         double distSq = v.getCenter().distSqr(pos);
         if (distSq <= nearestDistSq) {
            nearestDistSq = distSq;
            nearest = v;
         }
      }

      return nearest;
   }

   @Nullable
   public Village findVillageContaining(BuildingId buildingId) {
      for (Village v : this.villages.values()) {
         if (v.findBuildingById(buildingId) != null) {
            return v;
         }
      }

      return null;
   }

   public boolean isWithinMinDistance(BlockPos pos, double minDistance) {
      double minDistSq = minDistance * minDistance;

      for (Village v : this.villages.values()) {
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if ((vt == null || !vt.loneBuilding()) && v.getCenter().distSqr(pos) < minDistSq) {
            return true;
         }
      }

      return false;
   }

   public boolean overlapsExistingVillage(BlockPos pos, int radius) {
      for (Village v : this.villages.values()) {
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if (vt == null || !vt.loneBuilding()) {
            int otherRadius = vt != null ? vt.radius() : 0;
            double minDist = (double)radius + otherRadius;
            int dx = v.getCenter().getX() - pos.getX();
            int dz = v.getCenter().getZ() - pos.getZ();
            if ((double)dx * dx + (double)dz * dz < minDist * minDist) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isWithinMinDistanceLB(
      BlockPos pos, double minDistLBtoLB, double minDistLBtoVillage, List<VillageSavedData.LoneBuildingEntry> loneBuildingPositions
   ) {
      double minDistVillageSq = minDistLBtoVillage * minDistLBtoVillage;

      for (Village v : this.villages.values()) {
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if ((vt == null || !vt.loneBuilding()) && v.getCenter().distSqr(pos) < minDistVillageSq) {
            return true;
         }
      }

      double minDistLBSq = minDistLBtoLB * minDistLBtoLB;

      for (VillageSavedData.LoneBuildingEntry entry : loneBuildingPositions) {
         if (entry.pos().distSqr(pos) < minDistLBSq) {
            return true;
         }
      }

      return false;
   }

   public VillageManager.LBPlacement classifyLBPlacement(
      BlockPos pos, double minDistLBtoLB, double minDistLBtoVillage, List<VillageSavedData.LoneBuildingEntry> loneBuildingPositions
   ) {
      boolean keyOnly = false;
      double villageBlockSq = minDistLBtoVillage / 2.0 * (minDistLBtoVillage / 2.0);
      double villageNearSq = minDistLBtoVillage * minDistLBtoVillage;

      for (Village v : this.villages.values()) {
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if (vt == null || !vt.loneBuilding()) {
            double d = v.getCenter().distSqr(pos);
            if (d < villageBlockSq) {
               return VillageManager.LBPlacement.BLOCKED;
            }

            if (d < villageNearSq) {
               keyOnly = true;
            }
         }
      }

      double lbBlockSq = minDistLBtoLB / 4.0 * (minDistLBtoLB / 4.0);
      double lbNearSq = minDistLBtoLB * minDistLBtoLB;

      for (VillageSavedData.LoneBuildingEntry entry : loneBuildingPositions) {
         double d = entry.pos().distSqr(pos);
         if (d < lbBlockSq) {
            return VillageManager.LBPlacement.BLOCKED;
         }

         if (d < lbNearSq) {
            keyOnly = true;
         }
      }

      return keyOnly ? VillageManager.LBPlacement.KEY_ONLY : VillageManager.LBPlacement.FAR;
   }

   public boolean isWithinMinDistanceOfLoneBuildings(BlockPos pos, double minDist, List<VillageSavedData.LoneBuildingEntry> loneBuildingPositions) {
      double minDistSq = minDist * minDist;

      for (VillageSavedData.LoneBuildingEntry entry : loneBuildingPositions) {
         if (entry.pos().distSqr(pos) < minDistSq) {
            return true;
         }
      }

      return false;
   }

   public void tick(ServerLevel level) {
      boolean anyDirty = false;
      long gameTime = level.getGameTime();
      LevelTickCache.ensure(level);
      ArrayList<Village> nearDue = new ArrayList<>();
      ArrayList<Village> midDue = new ArrayList<>();
      Set<Village> nearDueSet = Collections.newSetFromMap(new IdentityHashMap<>());
      Set<Village> midDueSet = Collections.newSetFromMap(new IdentityHashMap<>());

      // Drain previously deferred Near villages first (round-robin fairness).
      if (!this.nearLodDeferred.isEmpty()) {
         for (VillageId id : this.nearLodDeferred) {
            Village deferred = this.villages.get(id);
            if (deferred != null && deferred.isActive() && nearDueSet.add(deferred)) {
               nearDue.add(deferred);
            }
         }
         this.nearLodDeferred.clear();
      }

      // Drain previously deferred Mid villages.
      if (!this.midLodDeferred.isEmpty()) {
         for (VillageId id : this.midLodDeferred) {
            Village deferred = this.villages.get(id);
            if (deferred != null && deferred.isActive() && midDueSet.add(deferred)) {
               midDue.add(deferred);
            }
         }
         this.midLodDeferred.clear();
      }

      ServerTickGovernor.enter(TickPhase.VILLAGE_NEAR);
      int farBgInterval = 20;
      try {
         farBgInterval = Math.max(1, MillenaireServerConfig.SERVER.farBackgroundTickInterval.getAsInt());
      } catch (IllegalStateException ignored) {
      }

      boolean fullReconcile = this.slotsDirty || this.farBackgroundSlots.interval() != farBgInterval;
      if (fullReconcile) {
         this.rebuildSlots(farBgInterval);
      }
      // Activation/LOD discovery for non-hot villages: hash-sliced (or a one-off full pass after add/remove).
      anyDirty |= this.reconcileInactive(level, fullReconcile ? this.villages.values() : this.reconcileSlots.due(gameTime));

      int hotAtStart = this.hotVillages.size();
      this.hotScratch.clear();
      this.hotScratch.addAll(this.hotVillages);
      for (int hi = 0; hi < this.hotScratch.size(); hi++) {
         Village v = this.hotScratch.get(hi);
         this.tickVillageActivation(level, v, false);
         VillageSimulationLod lod = VillageSimulationLod.resolve(level, v);
         ServerTickGovernor.noteLod(lod);

         int villageHash = VillageSimulationLod.villageHash(v);

         if (lod == VillageSimulationLod.NEAR && v.isActive() && nearDueSet.add(v)) {
            nearDue.add(v);
         } else if (
            lod == VillageSimulationLod.MID
               && v.isActive()
               && lod.shouldRunFullTick(gameTime, villageHash)
               && midDueSet.add(v)
         ) {
            midDue.add(v);
         }

         if (v.isActive()) {
            ServerTickGovernor.noteActiveVillage();
         } else {
            ServerTickGovernor.noteDataOnlyVillage();
         }

         // Far/inactive: data-only — stagger backgroundTick (raid-urgent always runs).
         // Under budget pressure, skip non-urgent Near/Mid background so full Near ticks keep the pie.
         boolean raidUrgent = v.isRaidActiveOrSuffering();
         boolean runBackground;
         if (raidUrgent) {
            runBackground = true;
         } else if (lod == VillageSimulationLod.NEAR || (lod == VillageSimulationLod.MID && v.isActive())) {
            runBackground = ServerTickGovernor.hasTime();
            if (!runBackground) {
               ServerTickGovernor.noteBackgroundTickSkipped();
               ServerTickGovernor.noteDefer(TickPhase.VILLAGE_NEAR);
            }
         } else {
            int stagger = Math.floorMod(villageHash, farBgInterval);
            runBackground = Math.floorMod(gameTime, farBgInterval) == stagger;
            if (!runBackground) {
               ServerTickGovernor.noteBackgroundTickSkipped();
            }
         }
         if (runBackground) {
            v.backgroundTick(level);
         }
         if (v.consumeDirty()) {
            anyDirty = true;
         }
      }

      // Far, non-hot villages: due-slot background only (no per-tick touch of every Far village).
      List<Village> farDue = this.farBackgroundSlots.due(gameTime);
      int farRan = 0;
      for (int fi = 0; fi < farDue.size(); fi++) {
         Village v = farDue.get(fi);
         if (this.hotVillages.contains(v) || this.isHotCandidate(v)) {
            continue;
         }
         v.backgroundTick(level);
         farRan++;
         if (v.consumeDirty()) {
            anyDirty = true;
         }
         if (v.isRaidActiveOrSuffering()) {
            this.hotVillages.add(v);
         }
      }
      int farIdle = Math.max(0, this.villages.size() - hotAtStart);
      ServerTickGovernor.noteFarDataOnlyVillages(farIdle, Math.max(0, farIdle - farRan));

      for (int hi = 0; hi < this.hotScratch.size(); hi++) {
         Village v = this.hotScratch.get(hi);
         if (!this.isHotCandidate(v)) {
            this.hotVillages.remove(v);
         }
      }

      int nearMax = 2;
      try {
         nearMax = Math.max(1, MillenaireServerConfig.SERVER.nearVillageMaxFullTicksPerTick.getAsInt());
      } catch (IllegalStateException ignored) {
      }
      int nearRan = 0;
      for (int i = 0; i < nearDue.size(); i++) {
         Village v = nearDue.get(i);
         if (nearRan >= nearMax || (!ServerTickGovernor.hasTime() && nearRan > 0)) {
            for (int j = i; j < nearDue.size(); j++) {
               this.nearLodDeferred.add(nearDue.get(j).getId());
               ServerTickGovernor.noteDefer(TickPhase.VILLAGE_NEAR);
            }
            break;
         }
         v.tick(level);
         nearRan++;
         long uuidMix = v.getId().uuid().getMostSignificantBits() ^ v.getId().uuid().getLeastSignificantBits();
         int offset = (int)Math.floorMod(uuidMix, 200L);
         if (Math.floorMod(gameTime - offset, 200L) == 0L) {
            v.sendFireplacePositions(level);
         }
         if (v.consumeDirty()) {
            anyDirty = true;
         }
      }

      ServerTickGovernor.enter(TickPhase.VILLAGE_MID);
      for (int i = 0; i < midDue.size(); i++) {
         Village v = midDue.get(i);
         if (!ServerTickGovernor.hasTime() && i > 0) {
            for (int j = i; j < midDue.size(); j++) {
               this.midLodDeferred.add(midDue.get(j).getId());
               ServerTickGovernor.noteDefer(TickPhase.VILLAGE_MID);
               ServerTickGovernor.noteMidDeferred();
            }
            break;
         }
         v.tick(level);
         long uuidMix = v.getId().uuid().getMostSignificantBits() ^ v.getId().uuid().getLeastSignificantBits();
         int offset = (int)Math.floorMod(uuidMix, 200L);
         if (Math.floorMod(gameTime - offset, 200L) == 0L) {
            v.sendFireplacePositions(level);
         }
         if (v.consumeDirty()) {
            anyDirty = true;
         }
      }

      if (anyDirty) {
         this.pendingSavedDataDirty = true;
      }
      if (this.pendingSavedDataDirty && Math.floorMod(gameTime, DIRTY_COALESCE_TICKS) == 0L) {
         VillageSavedData.get(level).setDirty();
         this.pendingSavedDataDirty = false;
      }

      ServerTickGovernor.enter(TickPhase.AI_SUPPORT);
      if (ServerTickGovernor.hasTime()) {
         org.millenaire.chat.NpcGossipScheduler.tick(level);
         org.millenaire.living.LivingVillagesLifecycle.onVillageManagerTick(level);
         org.millenaire.religion.ReligionLifecycle.onVillageManagerTick(level);
      } else {
         ServerTickGovernor.noteDefer(TickPhase.AI_SUPPORT);
      }
   }

   private boolean isHotCandidate(Village v) {
      return v.isActive() || v.isRaidActiveOrSuffering() || v.isChunksForceLoaded() || v.isForceActive();
   }

   private void rebuildSlots(int farBgInterval) {
      this.slotsDirty = false;
      this.reconcileSlots.rebuild(this.villages.values(), VillageSimulationLod::villageHash, RECONCILE_INTERVAL_TICKS);
      this.farBackgroundSlots.rebuild(this.villages.values(), VillageSimulationLod::villageHash, farBgInterval);
      this.hotVillages.removeIf(v -> this.villages.get(v.getId()) != v);
   }

   /** Re-evaluate activation for villages not on the hot path; promote to hot when work appears. */
   private boolean reconcileInactive(ServerLevel level, Collection<Village> candidates) {
      boolean anyDirty = false;
      for (Village v : candidates) {
         if (this.hotVillages.contains(v)) {
            continue;
         }
         this.tickVillageActivation(level, v, true);
         if (v.consumeDirty()) {
            anyDirty = true;
         }
         if (this.isHotCandidate(v)) {
            this.hotVillages.add(v);
         }
      }
      return anyDirty;
   }

   private void tickVillageActivation(ServerLevel level, Village v, boolean ignoreStagger) {
      boolean forceLoadEnabled = (Boolean)MillenaireServerConfig.SERVER.forceLoadVillages.get();
      if (!forceLoadEnabled && !v.isForceActive()) {
         if (v.isChunksForceLoaded()) {
            v.syncRecords(level);
            this.unloadChunks(level, v);
         }

         v.setActive(level.isLoaded(v.getCenter()));
      } else {
         if (v.isForceActive()) {
            if (!v.isChunksForceLoaded()) {
               this.loadChunks(level, v);
            }
         } else {
            // Amortize player-distance scans: each village at most once every 5 ticks.
            int stagger = Math.floorMod(VillageSimulationLod.villageHash(v), 5);
            if (ignoreStagger || Math.floorMod(level.getGameTime(), 5L) == stagger) {
               BlockPos center = v.getCenter();
               double nearestDistSq = LevelTickCache.nearestPlayerDistSqToBlock(level, center.getX(), center.getY(), center.getZ());

               double keepRadiusSq = (double)Village.getKeepActiveRadius() * Village.getKeepActiveRadius();
               double unloadRadiusSq = (double)Village.getUnloadRadius() * Village.getUnloadRadius();
               if (nearestDistSq < keepRadiusSq) {
                  if (!v.isChunksForceLoaded()) {
                     this.loadChunks(level, v);
                  }
               } else if (nearestDistSq > unloadRadiusSq && v.isChunksForceLoaded()) {
                  v.syncRecords(level);
                  this.unloadChunks(level, v);
               }
            }
         }

         if (v.isChunksForceLoaded() && v.isChunksNeedRefresh()) {
            Set<ChunkPos> newChunks = v.computeVillageChunks();
            VillageChunkLoader.updateVillageChunks(level, v, newChunks);
            v.setChunksNeedRefresh(false);
         }

         boolean active;
         if (v.isChunksForceLoaded()) {
            active = this.isAreaLoaded(level, v.getLoadedChunks());
         } else if (v.isForceActive()) {
            active = true;
         } else {
            active = level.isLoaded(v.getCenter());
         }

         v.setActive(active);
      }
   }

   private void loadChunks(ServerLevel level, Village v) {
      Set<ChunkPos> chunks = v.computeVillageChunks();
      VillageChunkLoader.forceVillageChunks(level, v.getCenter(), chunks);
      v.setLoadedChunks(chunks);
      v.setChunksForceLoaded(true);
      LOGGER.debug("[Millénaire] Village {} activated ({} chunks)", v.getVillageName(), chunks.size());
   }

   private void unloadChunks(ServerLevel level, Village v) {
      if (!v.getLoadedChunks().isEmpty()) {
         VillageChunkLoader.releaseVillageChunks(level, v.getCenter(), v.getLoadedChunks());
      }

      v.setLoadedChunks(Set.of());
      v.setChunksForceLoaded(false);
      v.setActive(false);
      LOGGER.debug("[Millénaire] Village {} deactivated", v.getVillageName());
   }

   private boolean isAreaLoaded(ServerLevel level, Set<ChunkPos> chunks) {
      if (chunks.isEmpty()) {
         return false;
      }

      for (ChunkPos cp : chunks) {
         if (!level.hasChunk(cp.x, cp.z)) {
            return false;
         }
      }

      return true;
   }

   public enum LBPlacement {
      FAR,
      KEY_ONLY,
      BLOCKED;
   }
}
