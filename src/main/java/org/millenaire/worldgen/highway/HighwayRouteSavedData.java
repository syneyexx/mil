package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class HighwayRouteSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_highway_routes";
   public static final int SCHEMA = 1;
   private final Map<UUID, HighwayRoute> routes = new HashMap<>();
   private int roundRobinCursor;
   private long topologyRevision;
   private long statusRevision;
   private List<HighwayRoute> activeCache;
   private long activeCacheRevision = -1L;
   private List<HighwayRoute> connectedCache;
   private long connectedCacheRevision = -1L;

   public static Factory<HighwayRouteSavedData> factory() {
      return new Factory<>(HighwayRouteSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static HighwayRouteSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   /** Bumps when routes are added or removed; not persisted. */
   public long topologyRevision() {
      return this.topologyRevision;
   }

   /** Bumps on topology change or when a route status moves between active and terminal. */
   public long statusRevision() {
      return this.statusRevision;
   }

   public void notifyStatusChanged() {
      this.statusRevision++;
   }

   /** Routes still needing materialization (not CONNECTED/FAILED), sorted by id. */
   public List<HighwayRoute> activeRoutes() {
      if (this.activeCache == null || this.activeCacheRevision != this.statusRevision) {
         List<HighwayRoute> active = new ArrayList<>();
         for (HighwayRoute route : this.routes.values()) {
            if (route.status() != HighwayRoute.Status.CONNECTED && route.status() != HighwayRoute.Status.FAILED) {
               active.add(route);
            }
         }
         active.sort(Comparator.comparing(HighwayRoute::routeId));
         this.activeCache = List.copyOf(active);
         this.activeCacheRevision = this.statusRevision;
      }
      return this.activeCache;
   }

   /** Fully materialized routes, sorted by id. Cached until {@link #statusRevision()} changes. */
   public List<HighwayRoute> connectedRoutes() {
      if (this.connectedCache == null || this.connectedCacheRevision != this.statusRevision) {
         List<HighwayRoute> connected = new ArrayList<>();
         for (HighwayRoute route : this.routes.values()) {
            if (route.status() == HighwayRoute.Status.CONNECTED) {
               connected.add(route);
            }
         }
         connected.sort(Comparator.comparing(HighwayRoute::routeId));
         this.connectedCache = List.copyOf(connected);
         this.connectedCacheRevision = this.statusRevision;
      }
      return this.connectedCache;
   }

   public Collection<HighwayRoute> allRoutes() {
      return List.copyOf(this.routes.values());
   }

   public int size() {
      return this.routes.size();
   }

   @Nullable
   public HighwayRoute get(UUID id) {
      return this.routes.get(id);
   }

   public boolean hasLink(String a, String b) {
      for (HighwayRoute route : this.routes.values()) {
         if (route.connects(a, b)) {
            return true;
         }
      }
      return false;
   }

   public void put(HighwayRoute route) {
      if (route == null) {
         return;
      }
      this.routes.put(route.routeId(), route);
      this.topologyRevision++;
      this.statusRevision++;
      this.trimConnected();
      this.setDirty();
   }

   public int roundRobinCursor() {
      return this.roundRobinCursor;
   }

   public void setRoundRobinCursor(int cursor) {
      this.roundRobinCursor = Math.max(0, cursor);
      this.setDirty();
   }

   public boolean isRegisteredCell(BlockPos pos) {
      if (pos == null) {
         return false;
      }
      long packed = HighwayRoute.packCell(pos.getX(), pos.getZ());
      for (HighwayRoute route : this.routes.values()) {
         if (route.completedCells().contains(packed)) {
            return true;
         }
      }
      return false;
   }

   private void trimConnected() {
      if (this.routes.size() <= 512) {
         return;
      }
      List<UUID> completed = new ArrayList<>();
      for (HighwayRoute r : this.routes.values()) {
         if (r.status() == HighwayRoute.Status.CONNECTED) {
            completed.add(r.routeId());
         }
      }
      completed.sort(Comparator.naturalOrder());
      for (int i = 0; i < completed.size() - 256; i++) {
         this.routes.remove(completed.get(i));
         this.topologyRevision++;
         this.statusRevision++;
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", SCHEMA);
      tag.putInt("cursor", this.roundRobinCursor);
      ListTag list = new ListTag();
      for (HighwayRoute route : this.routes.values()) {
         list.add(route.save());
      }
      tag.put("routes", list);
      return tag;
   }

   private static HighwayRouteSavedData load(CompoundTag tag) {
      HighwayRouteSavedData data = new HighwayRouteSavedData();
      data.roundRobinCursor = tag.getInt("cursor");
      ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         try {
            HighwayRoute route = HighwayRoute.load(list.getCompound(i));
            data.routes.put(route.routeId(), route);
         } catch (Exception ignored) {
         }
      }
      return data;
   }
}
