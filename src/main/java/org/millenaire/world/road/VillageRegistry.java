package org.millenaire.world.road;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * In-memory registry of known road nodes (discovered villages). Marks dirty when the set changes
 * so the road graph can be rebuilt.
 */
public final class VillageRegistry {
   private final Map<String, RoadNode> nodes = new LinkedHashMap<>();
   private boolean dirty;

   public boolean register(RoadNode node) {
      RoadNode existing = this.nodes.get(node.id());
      if (existing != null) {
         existing.updateGeometry(node.center(), node.boundingBox(), node.entranceCandidates());
         if (node.biomeId() != null) {
            existing.setBiomeId(node.biomeId());
         }
         return false;
      }
      this.nodes.put(node.id(), node);
      this.dirty = true;
      return true;
   }

   public boolean remove(String id) {
      if (this.nodes.remove(id) != null) {
         this.dirty = true;
         return true;
      }
      return false;
   }

   @Nullable
   public RoadNode get(String id) {
      return this.nodes.get(id);
   }

   public Collection<RoadNode> getAll() {
      return Collections.unmodifiableCollection(this.nodes.values());
   }

   public int size() {
      return this.nodes.size();
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void clear() {
      this.nodes.clear();
      this.dirty = true;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (RoadNode node : this.nodes.values()) {
         list.add(node.save());
      }
      tag.put("nodes", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.nodes.clear();
      ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         RoadNode node = RoadNode.load(list.getCompound(i));
         this.nodes.put(node.id(), node);
      }
      this.dirty = false;
   }
}
