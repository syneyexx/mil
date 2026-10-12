package org.millenaire.world;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;

/**
 * Budgeted terrain-map cache for village growth. Incomplete scans resume across ticks;
 * missing chunks wait instead of generating.
 */
public final class VillageTerrainMapCache {
   private static final class Entry {
      final BlockPos center;
      final int radius;
      final long gameTimeCreated;
      VillageTerrainMap.ScanSession scan;
      @Nullable
      VillageTerrainMap map;

      Entry(BlockPos center, int radius, long gameTime) {
         this.center = center;
         this.radius = radius;
         this.gameTimeCreated = gameTime;
      }
   }

   private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
   private static final long TTL_TICKS = 200L;

   private VillageTerrainMapCache() {
   }

   /**
    * Returns a completed map, or null when still scanning / waiting for chunks.
    */
   @Nullable
   public static VillageTerrainMap obtain(ServerLevel level, UUID villageId, BlockPos center, int radius) {
      Entry entry = ENTRIES.get(villageId);
      if (entry != null
         && (entry.center.getX() != center.getX()
            || entry.center.getZ() != center.getZ()
            || entry.radius != radius
            || level.getGameTime() - entry.gameTimeCreated > TTL_TICKS)) {
         ENTRIES.remove(villageId);
         entry = null;
      }
      if (entry == null) {
         entry = new Entry(center.immutable(), radius, level.getGameTime());
         entry.scan = VillageTerrainMap.beginScan(center, radius, level.getMinBuildHeight(), level.getMaxBuildHeight());
         ENTRIES.put(villageId, entry);
      }
      if (entry.map != null) {
         return entry.map;
      }
      MaterializationTickBudget.ensureTick(level.getGameTime());
      int columns = MillenaireServerConfig.SERVER.worldgenTerrainColumnsPerTick.getAsInt();
      if (org.millenaire.perf.tick.ServerTickGovernor.pressure()
         == org.millenaire.perf.tick.TickPressure.HEALTHY) {
         columns = Math.min(4096, columns * 2);
      }
      long deadline = System.nanoTime() + MillenaireServerConfig.SERVER.worldgenTickTimeBudgetMs.getAsInt() * 1_000_000L;
      // Growth shares the server thread — never generate distant columns.
      VillageTerrainMap.ScanSession.Status status = entry.scan.advance(level, columns, deadline, false);
      if (status == VillageTerrainMap.ScanSession.Status.COMPLETE) {
         entry.map = entry.scan.result();
         return entry.map;
      }
      return null;
   }

   public static void invalidate(UUID villageId) {
      ENTRIES.remove(villageId);
   }

   public static void clear() {
      ENTRIES.clear();
   }

   public static void purgeStale(long gameTime) {
      Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator();
      while (it.hasNext()) {
         Entry e = it.next().getValue();
         if (gameTime - e.gameTimeCreated > TTL_TICKS * 4) {
            it.remove();
         }
      }
   }
}
