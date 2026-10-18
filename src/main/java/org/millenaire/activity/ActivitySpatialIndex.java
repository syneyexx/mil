package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

/**
 * Per-dimension spatial index in 256x256 cells. Incremental updates only; no per-tick full scans.
 */
public final class ActivitySpatialIndex {
   public static final int CELL_SIZE = 256;

   public record CellKey(int cx, int cz) {
      public static CellKey of(BlockPos pos) {
         return new CellKey(Math.floorDiv(pos.getX(), CELL_SIZE), Math.floorDiv(pos.getZ(), CELL_SIZE));
      }

      public static CellKey of(int x, int z) {
         return new CellKey(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE));
      }
   }

   public record IndexedSettlement(UUID id, BlockPos center, boolean camp, boolean loneBuilding, String culturePath) {}

   private final ResourceLocation dimension;
   private final Map<CellKey, Set<UUID>> settlementsByCell = new HashMap<>();
   private final Map<UUID, IndexedSettlement> settlements = new HashMap<>();
   private final Map<CellKey, Set<UUID>> groupsByCell = new HashMap<>();
   private final Map<UUID, BlockPos> groupPositions = new HashMap<>();
   private final Map<CellKey, List<BlockPos>> routePointsByCell = new HashMap<>();
   private boolean bootstrapped;
   private int bootstrapCursor;

   public ActivitySpatialIndex(ResourceLocation dimension) {
      this.dimension = dimension;
   }

   public ResourceLocation dimension() {
      return this.dimension;
   }

   public boolean isBootstrapped() {
      return this.bootstrapped;
   }

   /** Bounded incremental fill from village manager. */
   public int bootstrapStep(ServerLevel level, int budget) {
      if (this.bootstrapped || budget <= 0) {
         return 0;
      }
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<Village> all = new ArrayList<>(vm.getAllVillages());
      int done = 0;
      while (this.bootstrapCursor < all.size() && done < budget) {
         Village v = all.get(this.bootstrapCursor++);
         upsertSettlement(v);
         done++;
      }
      if (this.bootstrapCursor >= all.size()) {
         this.bootstrapped = true;
      }
      return done;
   }

   public void upsertSettlement(Village village) {
      if (village == null || village.getCenter() == null) {
         return;
      }
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      boolean camp = vt != null && vt.loneBuilding() && (vt.carriesRaid() || "illager_camp".equals(vt.specialType()));
      boolean lone = vt != null && vt.loneBuilding();
      String culture = village.getCultureId() != null ? village.getCultureId().getPath() : "";
      IndexedSettlement indexed = new IndexedSettlement(village.getId().uuid(), village.getCenter(), camp, lone, culture);
      IndexedSettlement prev = this.settlements.put(indexed.id(), indexed);
      if (prev != null) {
         removeFromCell(this.settlementsByCell, CellKey.of(prev.center()), prev.id());
      }
      this.settlementsByCell.computeIfAbsent(CellKey.of(indexed.center()), k -> new HashSet<>()).add(indexed.id());
   }

   public void removeSettlement(UUID id) {
      IndexedSettlement prev = this.settlements.remove(id);
      if (prev != null) {
         removeFromCell(this.settlementsByCell, CellKey.of(prev.center()), id);
      }
   }

   public void upsertGroup(UUID groupId, BlockPos pos) {
      if (groupId == null || pos == null) {
         return;
      }
      BlockPos prev = this.groupPositions.put(groupId, pos);
      if (prev != null) {
         removeFromCell(this.groupsByCell, CellKey.of(prev), groupId);
      }
      this.groupsByCell.computeIfAbsent(CellKey.of(pos), k -> new HashSet<>()).add(groupId);
   }

   public void removeGroup(UUID groupId) {
      BlockPos prev = this.groupPositions.remove(groupId);
      if (prev != null) {
         removeFromCell(this.groupsByCell, CellKey.of(prev), groupId);
      }
   }

   public void addRoutePoint(BlockPos pos) {
      if (pos == null) {
         return;
      }
      this.routePointsByCell.computeIfAbsent(CellKey.of(pos), k -> new ArrayList<>()).add(pos);
   }

   public Collection<IndexedSettlement> allSettlements() {
      return this.settlements.values();
   }

   @Nullable
   public IndexedSettlement getSettlement(UUID id) {
      return this.settlements.get(id);
   }

   public List<IndexedSettlement> settlementsNear(BlockPos center, int cellRadius) {
      List<IndexedSettlement> out = new ArrayList<>();
      CellKey origin = CellKey.of(center);
      for (int dx = -cellRadius; dx <= cellRadius; dx++) {
         for (int dz = -cellRadius; dz <= cellRadius; dz++) {
            Set<UUID> ids = this.settlementsByCell.get(new CellKey(origin.cx() + dx, origin.cz() + dz));
            if (ids == null) {
               continue;
            }
            for (UUID id : ids) {
               IndexedSettlement s = this.settlements.get(id);
               if (s != null) {
                  out.add(s);
               }
            }
         }
      }
      return out;
   }

   public List<UUID> groupsNear(BlockPos center, int cellRadius) {
      List<UUID> out = new ArrayList<>();
      CellKey origin = CellKey.of(center);
      for (int dx = -cellRadius; dx <= cellRadius; dx++) {
         for (int dz = -cellRadius; dz <= cellRadius; dz++) {
            Set<UUID> ids = this.groupsByCell.get(new CellKey(origin.cx() + dx, origin.cz() + dz));
            if (ids != null) {
               out.addAll(ids);
            }
         }
      }
      return out;
   }

   public int countGroupsInCellAndNeighbors(BlockPos pos) {
      CellKey origin = CellKey.of(pos);
      int n = 0;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            Set<UUID> ids = this.groupsByCell.get(new CellKey(origin.cx() + dx, origin.cz() + dz));
            if (ids != null) {
               n += ids.size();
            }
         }
      }
      return n;
   }

   public List<BlockPos> routePointsNear(BlockPos center, int cellRadius, int max) {
      List<BlockPos> out = new ArrayList<>();
      CellKey origin = CellKey.of(center);
      for (int dx = -cellRadius; dx <= cellRadius && out.size() < max; dx++) {
         for (int dz = -cellRadius; dz <= cellRadius && out.size() < max; dz++) {
            List<BlockPos> pts = this.routePointsByCell.get(new CellKey(origin.cx() + dx, origin.cz() + dz));
            if (pts != null) {
               for (BlockPos p : pts) {
                  out.add(p);
                  if (out.size() >= max) {
                     return out;
                  }
               }
            }
         }
      }
      return out;
   }

   private static void removeFromCell(Map<CellKey, Set<UUID>> map, CellKey key, UUID id) {
      Set<UUID> set = map.get(key);
      if (set != null) {
         set.remove(id);
         if (set.isEmpty()) {
            map.remove(key);
         }
      }
   }
}
