package org.millenaire.worldgen.plan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.millenaire.building.BuildingInstance;
import org.millenaire.world.TerrainReachability;
import org.millenaire.world.VillageTerrainMap;
import org.millenaire.worldgen.content.VillageTypeWorldgenEntry;

/**
 * In-memory resumable layout work. Cleared on server stop; mid-layout save/load restarts
 * deterministically from the plan seed.
 */
public final class WorldgenLayoutSessions {
   public enum Phase {
      TERRAIN_SCAN,
      PLACE_CENTRE,
      PLACE_STARTERS,
      PLACE_WALLS,
      PLACE_ROADS_HAMLETS,
      FINALIZE,
      DONE
   }

   public static final class Session {
      public final UUID planId;
      public Phase phase = Phase.TERRAIN_SCAN;
      @Nullable
      public VillageTerrainMap.ScanSession terrainScan;
      @Nullable
      public VillageTerrainMap terrainMap;
      @Nullable
      public TerrainReachability reachability;
      @Nullable
      public List<VillageTypeWorldgenEntry.LayoutSlotRef> starters;
      public int starterIndex;
      @Nullable
      public List<BuildingInstance> existing;
      @Nullable
      public VillageTypeWorldgenEntry.LayoutSlotRef centreSlot;

      Session(UUID planId) {
         this.planId = planId;
         this.starters = new ArrayList<>();
         this.existing = new ArrayList<>();
      }
   }

   private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

   private WorldgenLayoutSessions() {
   }

   public static Session getOrCreate(UUID planId) {
      return SESSIONS.computeIfAbsent(planId, Session::new);
   }

   @Nullable
   public static Session get(UUID planId) {
      return SESSIONS.get(planId);
   }

   public static void remove(UUID planId) {
      SESSIONS.remove(planId);
   }

   public static void clear() {
      SESSIONS.clear();
   }

   public static int size() {
      return SESSIONS.size();
   }

   /** Drop stale sessions for plans that are no longer layout-eligible. */
   public static void retainOnly(java.util.Set<UUID> keep) {
      Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
      while (it.hasNext()) {
         if (!keep.contains(it.next().getKey())) {
            it.remove();
         }
      }
   }
}
