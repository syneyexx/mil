package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class HighwayNodeSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_highway_nodes";
   public static final int SCHEMA = 1;
   private static final int MAX_NODES = 1024;
   private static final int TRIM_TARGET = 768;

   private final Map<String, HighwayNode> nodes = new HashMap<>();
   private final Set<Long> pendingChunks = new LinkedHashSet<>();
   private int millenaireScanCursor;

   public static Factory<HighwayNodeSavedData> factory() {
      return new Factory<>(HighwayNodeSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static HighwayNodeSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Collection<HighwayNode> allNodes() {
      return List.copyOf(this.nodes.values());
   }

   public int size() {
      return this.nodes.size();
   }

   @Nullable
   public HighwayNode get(String key) {
      return this.nodes.get(key);
   }

   public boolean upsert(HighwayNode node) {
      if (node == null || node.key() == null || node.key().isEmpty()) {
         return false;
      }
      HighwayNode existing = this.nodes.get(node.key());
      if (existing != null) {
         existing.setCenter(node.center());
         existing.setRadiusHint(node.radiusHint());
         if (node.factionHub()) {
            existing.setFactionHub(true);
         }
         if (node.millenaireVillageId() != null) {
            existing.setMillenaireVillageId(node.millenaireVillageId());
         }
         if (node.planId() != null) {
            existing.setPlanId(node.planId());
         }
         if (node.structureId() != null) {
            existing.setStructureId(node.structureId());
         }
         this.setDirty();
         return false;
      }
      this.nodes.put(node.key(), node);
      this.trimIfNeeded();
      this.setDirty();
      return true;
   }

   public void enqueueChunk(ChunkPos chunk) {
      if (chunk == null) {
         return;
      }
      if (this.pendingChunks.add(chunk.toLong())) {
         this.setDirty();
      }
   }

   public List<ChunkPos> drainPendingChunks(int max) {
      List<ChunkPos> out = new ArrayList<>();
      var it = this.pendingChunks.iterator();
      while (it.hasNext() && out.size() < max) {
         out.add(new ChunkPos(it.next()));
         it.remove();
      }
      if (!out.isEmpty()) {
         this.setDirty();
      }
      return out;
   }

   public int pendingChunkCount() {
      return this.pendingChunks.size();
   }

   public int millenaireScanCursor() {
      return this.millenaireScanCursor;
   }

   public void setMillenaireScanCursor(int cursor) {
      this.millenaireScanCursor = Math.max(0, cursor);
      this.setDirty();
   }

   /** Prefer Millenaire over vanilla/mod when centers overlap. */
   public boolean hasPreferredNear(BlockPosApprox center, int radius) {
      long r2 = (long)radius * radius;
      for (HighwayNode node : this.nodes.values()) {
         if (node.kind() != HighwayNodeKind.MILLENAIRE && node.kind() != HighwayNodeKind.MILLENAIRE_PLAN) {
            continue;
         }
         long dx = (long)node.center().getX() - center.x();
         long dz = (long)node.center().getZ() - center.z();
         if (dx * dx + dz * dz <= r2) {
            return true;
         }
      }
      return false;
   }

   public record BlockPosApprox(int x, int z) {
   }

   private void trimIfNeeded() {
      if (this.nodes.size() <= MAX_NODES) {
         return;
      }
      List<HighwayNode> unlinked = new ArrayList<>();
      for (HighwayNode n : this.nodes.values()) {
         if (n.linkDegree() == 0
            && n.kind() != HighwayNodeKind.MILLENAIRE
            && n.kind() != HighwayNodeKind.MILLENAIRE_PLAN) {
            unlinked.add(n);
         }
      }
      unlinked.sort(Comparator.comparingLong(HighwayNode::discoveredAtGameTime));
      int remove = this.nodes.size() - TRIM_TARGET;
      for (int i = 0; i < unlinked.size() && i < remove; i++) {
         this.nodes.remove(unlinked.get(i).key());
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", SCHEMA);
      tag.putInt("millScan", this.millenaireScanCursor);
      ListTag nodeList = new ListTag();
      for (HighwayNode node : this.nodes.values()) {
         nodeList.add(node.save());
      }
      tag.put("nodes", nodeList);
      ListTag pending = new ListTag();
      for (long packed : this.pendingChunks) {
         pending.add(LongTag.valueOf(packed));
      }
      tag.put("pending", pending);
      return tag;
   }

   private static HighwayNodeSavedData load(CompoundTag tag) {
      HighwayNodeSavedData data = new HighwayNodeSavedData();
      data.millenaireScanCursor = tag.getInt("millScan");
      ListTag nodeList = tag.getList("nodes", Tag.TAG_COMPOUND);
      for (int i = 0; i < nodeList.size(); i++) {
         try {
            HighwayNode node = HighwayNode.load(nodeList.getCompound(i));
            data.nodes.put(node.key(), node);
         } catch (Exception ignored) {
         }
      }
      ListTag pending = tag.getList("pending", Tag.TAG_LONG);
      for (int i = 0; i < pending.size(); i++) {
         data.pendingChunks.add(((net.minecraft.nbt.NumericTag)pending.get(i)).getAsLong());
      }
      return data;
   }
}
