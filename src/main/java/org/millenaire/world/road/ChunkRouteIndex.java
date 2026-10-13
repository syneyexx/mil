package org.millenaire.world.road;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Maps chunk keys to edge ids that have planned geometry through that chunk.
 */
public final class ChunkRouteIndex {
   private final Long2ObjectOpenHashMap<LongList> chunkToEdges = new Long2ObjectOpenHashMap<>();

   public void clear() {
      this.chunkToEdges.clear();
   }

   public void indexEdge(long edgeId, @Nullable Long2ObjectOpenHashMap<int[]> chunkSegments) {
      if (chunkSegments == null) {
         return;
      }
      chunkSegments.forEach((chunkKey, ignored) -> {
         LongList list = this.chunkToEdges.computeIfAbsent(chunkKey.longValue(), k -> new LongArrayList());
         if (!list.contains(edgeId)) {
            list.add(edgeId);
         }
      });
   }

   public void indexEdgeFromPolyline(long edgeId, int[] polyline) {
      if (polyline == null) {
         return;
      }
      int points = polyline.length / 3;
      for (int i = 0; i < points; i++) {
         int x = polyline[i * 3];
         int z = polyline[i * 3 + 2];
         long key = net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4);
         LongList list = this.chunkToEdges.computeIfAbsent(key, k -> new LongArrayList());
         if (!list.contains(edgeId)) {
            list.add(edgeId);
         }
      }
   }

   public List<Long> edgesForChunk(long chunkKey) {
      LongList list = this.chunkToEdges.get(chunkKey);
      if (list == null || list.isEmpty()) {
         return List.of();
      }
      List<Long> out = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
         out.add(list.getLong(i));
      }
      return out;
   }

   public int chunkCount() {
      return this.chunkToEdges.size();
   }
}
