package org.millenaire.world.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lightweight discovery record for a settlement that can participate in the road network.
 * Not a Millenaire {@code Village} — no tick cost.
 */
public final class RoadNode {
   private final String id;
   private final RoadNodeSource source;
   private BlockPos center;
   private BoundingBox boundingBox;
   private final List<BlockPos> entranceCandidates;
   @Nullable
   private ResourceLocation biomeId;
   @Nullable
   private UUID millenaireVillageId;

   public RoadNode(
      String id,
      RoadNodeSource source,
      BlockPos center,
      BoundingBox boundingBox,
      List<BlockPos> entranceCandidates,
      @Nullable ResourceLocation biomeId,
      @Nullable UUID millenaireVillageId
   ) {
      this.id = id;
      this.source = source;
      this.center = center.immutable();
      this.boundingBox = boundingBox;
      this.entranceCandidates = new ArrayList<>(entranceCandidates);
      this.biomeId = biomeId;
      this.millenaireVillageId = millenaireVillageId;
   }

   public String id() {
      return this.id;
   }

   public RoadNodeSource source() {
      return this.source;
   }

   public BlockPos center() {
      return this.center;
   }

   public BoundingBox boundingBox() {
      return this.boundingBox;
   }

   public List<BlockPos> entranceCandidates() {
      return Collections.unmodifiableList(this.entranceCandidates);
   }

   @Nullable
   public ResourceLocation biomeId() {
      return this.biomeId;
   }

   @Nullable
   public UUID millenaireVillageId() {
      return this.millenaireVillageId;
   }

   public void updateGeometry(BlockPos center, BoundingBox boundingBox, List<BlockPos> entrances) {
      this.center = center.immutable();
      this.boundingBox = boundingBox;
      this.entranceCandidates.clear();
      this.entranceCandidates.addAll(entrances);
   }

   public void setBiomeId(@Nullable ResourceLocation biomeId) {
      this.biomeId = biomeId;
   }

   public static List<BlockPos> computeEntrances(BoundingBox box, int margin) {
      int minX = box.minX() - margin;
      int maxX = box.maxX() + margin;
      int minZ = box.minZ() - margin;
      int maxZ = box.maxZ() + margin;
      int y = box.minY();
      int midX = (box.minX() + box.maxX()) / 2;
      int midZ = (box.minZ() + box.maxZ()) / 2;
      List<BlockPos> out = new ArrayList<>(8);
      out.add(new BlockPos(midX, y, minZ));
      out.add(new BlockPos(midX, y, maxZ));
      out.add(new BlockPos(minX, y, midZ));
      out.add(new BlockPos(maxX, y, midZ));
      out.add(new BlockPos(minX, y, minZ));
      out.add(new BlockPos(maxX, y, minZ));
      out.add(new BlockPos(minX, y, maxZ));
      out.add(new BlockPos(maxX, y, maxZ));
      return out;
   }

   public static BlockPos pickEntranceToward(RoadNode from, RoadNode toward) {
      BlockPos target = toward.center();
      BlockPos best = from.center();
      double bestDist = Double.MAX_VALUE;
      for (BlockPos candidate : from.entranceCandidates()) {
         double d = candidate.distSqr(target);
         if (d < bestDist) {
            bestDist = d;
            best = candidate;
         }
      }
      return best;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.id);
      tag.putString("source", this.source.name());
      tag.putInt("cx", this.center.getX());
      tag.putInt("cy", this.center.getY());
      tag.putInt("cz", this.center.getZ());
      tag.putInt("minX", this.boundingBox.minX());
      tag.putInt("minY", this.boundingBox.minY());
      tag.putInt("minZ", this.boundingBox.minZ());
      tag.putInt("maxX", this.boundingBox.maxX());
      tag.putInt("maxY", this.boundingBox.maxY());
      tag.putInt("maxZ", this.boundingBox.maxZ());
      if (this.biomeId != null) {
         tag.putString("biome", this.biomeId.toString());
      }
      if (this.millenaireVillageId != null) {
         tag.putUUID("villageUuid", this.millenaireVillageId);
      }
      ListTag entrances = new ListTag();
      for (BlockPos p : this.entranceCandidates) {
         CompoundTag e = new CompoundTag();
         e.putInt("x", p.getX());
         e.putInt("y", p.getY());
         e.putInt("z", p.getZ());
         entrances.add(e);
      }
      tag.put("entrances", entrances);
      return tag;
   }

   public static RoadNode load(CompoundTag tag) {
      String id = tag.getString("id");
      RoadNodeSource source = RoadNodeSource.valueOf(tag.getString("source"));
      BlockPos center = new BlockPos(tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz"));
      BoundingBox box = new BoundingBox(
         tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"), tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ")
      );
      ResourceLocation biome = tag.contains("biome") ? ResourceLocation.tryParse(tag.getString("biome")) : null;
      UUID villageUuid = tag.hasUUID("villageUuid") ? tag.getUUID("villageUuid") : null;
      List<BlockPos> entrances = new ArrayList<>();
      ListTag entrancesTag = tag.getList("entrances", Tag.TAG_COMPOUND);
      for (int i = 0; i < entrancesTag.size(); i++) {
         CompoundTag e = entrancesTag.getCompound(i);
         entrances.add(new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
      }
      if (entrances.isEmpty()) {
         entrances = computeEntrances(box, 8);
      }
      return new RoadNode(id, source, center, box, entrances, biome, villageUuid);
   }

   public static String millenaireId(UUID villageUuid) {
      return "millenaire:" + villageUuid;
   }

   public static String vanillaId(int x, int z, String structureKey) {
      return "vanilla:" + structureKey + ":" + x + ":" + z;
   }
}
