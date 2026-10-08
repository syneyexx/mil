package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.BuildingId;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.FactionLifecycle;
import org.slf4j.Logger;

public class VillageManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<VillageId, Village> villages = new LinkedHashMap<>();
   private static final int FIREPLACE_SYNC_INTERVAL = 200;

   public void addVillage(Village village) {
      this.villages.put(village.getId(), village);
   }

   /**
    * Removes a village from the manager and detaches it from its faction.
    */
   public void removeVillage(ServerLevel level, VillageId id) {
      Village village = this.villages.get(id);
      if (village != null) {
         FactionLifecycle.onVillageRemoved(level, village);
      }

      this.villages.remove(id);
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
      boolean fireplaceSync = level.getGameTime() % 200L == 0L;

      for (Village v : this.villages.values()) {
         this.tickVillageActivation(level, v);
         if (v.isActive()) {
            v.tick(level);
            if (fireplaceSync) {
               v.sendFireplacePositions(level);
            }
         }

         v.backgroundTick(level);
         if (v.consumeDirty()) {
            anyDirty = true;
         }
      }

      if (anyDirty) {
         VillageSavedData.get(level).setDirty();
      }

      org.millenaire.chat.NpcGossipScheduler.tick(level);
   }

   private void tickVillageActivation(ServerLevel level, Village v) {
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
            double nearestDistSq = Double.MAX_VALUE;

            for (ServerPlayer player : level.players()) {
               double distSq = player.blockPosition().distSqr(v.getCenter());
               if (distSq < nearestDistSq) {
                  nearestDistSq = distSq;
               }
            }

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
