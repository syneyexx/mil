package org.millenaire.world.road;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

public final class RoadEdge {
   private final long id;
   private final String nodeA;
   private final String nodeB;
   private EdgeState state;
   private int failCount;
   private long nextRetryGameTime;
   /** Packed block polyline: x0,y0,z0,x1,y1,z1,... after planning. */
   @Nullable
   private int[] polyline;
   /** Segment kinds aligned with polyline points (except last). See RouteSegment. */
   @Nullable
   private byte[] segmentKinds;
   /** Chunk keys (ChunkPos.asLong) that have been built for this edge. */
   @Nullable
   private long[] builtChunks;
   private int builtChunkCount;

   public RoadEdge(long id, String nodeA, String nodeB) {
      this.id = id;
      this.nodeA = nodeA;
      this.nodeB = nodeB;
      this.state = EdgeState.PENDING;
   }

   public long id() {
      return this.id;
   }

   public String nodeA() {
      return this.nodeA;
   }

   public String nodeB() {
      return this.nodeB;
   }

   public EdgeState state() {
      return this.state;
   }

   public void setState(EdgeState state) {
      this.state = state;
   }

   public int failCount() {
      return this.failCount;
   }

   public void recordFailure(long nextRetryGameTime) {
      this.failCount++;
      this.nextRetryGameTime = nextRetryGameTime;
      this.state = EdgeState.FAILED;
   }

   public long nextRetryGameTime() {
      return this.nextRetryGameTime;
   }

   public void resetForRetry() {
      this.state = EdgeState.PENDING;
      this.polyline = null;
      this.segmentKinds = null;
   }

   public void resetFully() {
      this.state = EdgeState.PENDING;
      this.failCount = 0;
      this.nextRetryGameTime = 0L;
      this.polyline = null;
      this.segmentKinds = null;
      this.builtChunks = null;
      this.builtChunkCount = 0;
   }

   @Nullable
   public int[] polyline() {
      return this.polyline;
   }

   public void setPolyline(int[] polyline, byte[] segmentKinds) {
      this.polyline = polyline;
      this.segmentKinds = segmentKinds;
      this.state = EdgeState.PLANNED;
   }

   @Nullable
   public byte[] segmentKinds() {
      return this.segmentKinds;
   }

   public boolean isChunkBuilt(long chunkKey) {
      if (this.builtChunks == null) {
         return false;
      }
      for (int i = 0; i < this.builtChunkCount; i++) {
         if (this.builtChunks[i] == chunkKey) {
            return true;
         }
      }
      return false;
   }

   public void markChunkBuilt(long chunkKey) {
      if (this.isChunkBuilt(chunkKey)) {
         return;
      }
      if (this.builtChunks == null) {
         this.builtChunks = new long[4];
      } else if (this.builtChunkCount >= this.builtChunks.length) {
         this.builtChunks = Arrays.copyOf(this.builtChunks, this.builtChunks.length * 2);
      }
      this.builtChunks[this.builtChunkCount++] = chunkKey;
   }

   public int builtChunkCount() {
      return this.builtChunkCount;
   }

   public void clearBuiltMarks() {
      this.builtChunks = null;
      this.builtChunkCount = 0;
   }

   public static long stableId(String a, String b) {
      String first = a.compareTo(b) <= 0 ? a : b;
      String second = a.compareTo(b) <= 0 ? b : a;
      long h = 1125899906842597L;
      for (int i = 0; i < first.length(); i++) {
         h = 31L * h + first.charAt(i);
      }
      h = 31L * h + '|';
      for (int i = 0; i < second.length(); i++) {
         h = 31L * h + second.charAt(i);
      }
      return h;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putLong("id", this.id);
      tag.putString("a", this.nodeA);
      tag.putString("b", this.nodeB);
      tag.putString("state", this.state.name());
      tag.putInt("fail", this.failCount);
      tag.putLong("retryAt", this.nextRetryGameTime);
      if (this.polyline != null) {
         tag.putIntArray("poly", this.polyline);
      }
      if (this.segmentKinds != null) {
         tag.putByteArray("seg", this.segmentKinds);
      }
      if (this.builtChunks != null && this.builtChunkCount > 0) {
         tag.putLongArray("built", Arrays.copyOf(this.builtChunks, this.builtChunkCount));
      }
      return tag;
   }

   public static RoadEdge load(CompoundTag tag) {
      RoadEdge edge = new RoadEdge(tag.getLong("id"), tag.getString("a"), tag.getString("b"));
      edge.state = EdgeState.valueOf(tag.getString("state"));
      edge.failCount = tag.getInt("fail");
      edge.nextRetryGameTime = tag.getLong("retryAt");
      if (tag.contains("poly")) {
         edge.polyline = tag.getIntArray("poly");
      }
      if (tag.contains("seg")) {
         edge.segmentKinds = tag.getByteArray("seg");
      }
      if (tag.contains("built")) {
         long[] built = tag.getLongArray("built");
         edge.builtChunks = built;
         edge.builtChunkCount = built.length;
      }
      return edge;
   }
}
