package org.millenaire.worldgen.highway;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A discovered settlement endpoint for the sand highway graph.
 */
public final class HighwayNode {
   private final String key;
   private final HighwayNodeKind kind;
   private BlockPos center;
   private int radiusHint;
   private boolean factionHub;
   @Nullable
   private UUID millenaireVillageId;
   @Nullable
   private UUID planId;
   @Nullable
   private ResourceLocation structureId;
   private long discoveredAtGameTime;
   private int linkDegree;

   public HighwayNode(String key, HighwayNodeKind kind, BlockPos center, int radiusHint) {
      this.key = key;
      this.kind = kind;
      this.center = center;
      this.radiusHint = Math.max(8, radiusHint);
   }

   public String key() {
      return this.key;
   }

   public HighwayNodeKind kind() {
      return this.kind;
   }

   public BlockPos center() {
      return this.center;
   }

   public void setCenter(BlockPos center) {
      if (center != null) {
         this.center = center;
      }
   }

   public int radiusHint() {
      return this.radiusHint;
   }

   public void setRadiusHint(int radiusHint) {
      this.radiusHint = Math.max(8, radiusHint);
   }

   public boolean factionHub() {
      return this.factionHub;
   }

   public void setFactionHub(boolean factionHub) {
      this.factionHub = factionHub;
   }

   @Nullable
   public UUID millenaireVillageId() {
      return this.millenaireVillageId;
   }

   public void setMillenaireVillageId(@Nullable UUID id) {
      this.millenaireVillageId = id;
   }

   @Nullable
   public UUID planId() {
      return this.planId;
   }

   public void setPlanId(@Nullable UUID planId) {
      this.planId = planId;
   }

   @Nullable
   public ResourceLocation structureId() {
      return this.structureId;
   }

   public void setStructureId(@Nullable ResourceLocation structureId) {
      this.structureId = structureId;
   }

   public long discoveredAtGameTime() {
      return this.discoveredAtGameTime;
   }

   public void setDiscoveredAtGameTime(long gameTime) {
      this.discoveredAtGameTime = gameTime;
   }

   public int linkDegree() {
      return this.linkDegree;
   }

   public void setLinkDegree(int linkDegree) {
      this.linkDegree = Math.max(0, linkDegree);
   }

   public void incrementLinkDegree() {
      this.linkDegree++;
   }

   public int maxNeighbors(int baseMax) {
      return this.factionHub ? baseMax + 1 : baseMax;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("key", this.key);
      tag.putString("kind", this.kind.name());
      tag.putInt("x", this.center.getX());
      tag.putInt("y", this.center.getY());
      tag.putInt("z", this.center.getZ());
      tag.putInt("radius", this.radiusHint);
      tag.putBoolean("hub", this.factionHub);
      tag.putLong("discovered", this.discoveredAtGameTime);
      tag.putInt("degree", this.linkDegree);
      if (this.millenaireVillageId != null) {
         tag.putUUID("village", this.millenaireVillageId);
      }
      if (this.planId != null) {
         tag.putUUID("plan", this.planId);
      }
      if (this.structureId != null) {
         tag.putString("structure", this.structureId.toString());
      }
      return tag;
   }

   public static HighwayNode load(CompoundTag tag) {
      String key = tag.getString("key");
      HighwayNodeKind kind = HighwayNodeKind.fromId(tag.getString("kind"));
      BlockPos center = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
      HighwayNode node = new HighwayNode(key, kind, center, tag.getInt("radius"));
      node.factionHub = tag.getBoolean("hub");
      node.discoveredAtGameTime = tag.getLong("discovered");
      node.linkDegree = tag.getInt("degree");
      if (tag.hasUUID("village")) {
         node.millenaireVillageId = tag.getUUID("village");
      }
      if (tag.hasUUID("plan")) {
         node.planId = tag.getUUID("plan");
      }
      if (tag.contains("structure")) {
         node.structureId = ResourceLocation.tryParse(tag.getString("structure"));
      }
      return node;
   }
}
