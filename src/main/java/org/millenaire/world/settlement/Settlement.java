package org.millenaire.world.settlement;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Thin settlement record — name/culture/geometry only. Never ticked like a Village.
 */
public final class Settlement {
   private final SettlementId id;
   private final SettlementSource source;
   private BlockPos center;
   private BoundingBox boundingBox;
   @Nullable
   private ResourceLocation cultureId;
   @Nullable
   private String displayName;
   private boolean discovered;
   @Nullable
   private UUID linkedVillageId;
   private boolean promoted;
   @Nullable
   private ResourceLocation biomeId;

   public Settlement(
      SettlementId id,
      SettlementSource source,
      BlockPos center,
      BoundingBox boundingBox,
      @Nullable ResourceLocation cultureId,
      @Nullable String displayName,
      @Nullable ResourceLocation biomeId,
      @Nullable UUID linkedVillageId
   ) {
      this.id = id;
      this.source = source;
      this.center = center.immutable();
      this.boundingBox = boundingBox;
      this.cultureId = cultureId;
      this.displayName = displayName;
      this.biomeId = biomeId;
      this.linkedVillageId = linkedVillageId;
      this.promoted = linkedVillageId != null && source == SettlementSource.MILLENAIRE;
   }

   public SettlementId id() {
      return this.id;
   }

   public SettlementSource source() {
      return this.source;
   }

   public BlockPos center() {
      return this.center;
   }

   public BoundingBox boundingBox() {
      return this.boundingBox;
   }

   @Nullable
   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public void setCultureId(@Nullable ResourceLocation cultureId) {
      this.cultureId = cultureId;
   }

   @Nullable
   public String displayName() {
      return this.displayName;
   }

   public void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   public boolean discovered() {
      return this.discovered;
   }

   public void setDiscovered(boolean discovered) {
      this.discovered = discovered;
   }

   @Nullable
   public UUID linkedVillageId() {
      return this.linkedVillageId;
   }

   public void setLinkedVillageId(@Nullable UUID linkedVillageId) {
      this.linkedVillageId = linkedVillageId;
   }

   public boolean promoted() {
      return this.promoted;
   }

   public void setPromoted(boolean promoted) {
      this.promoted = promoted;
   }

   @Nullable
   public ResourceLocation biomeId() {
      return this.biomeId;
   }

   public void updateGeometry(BlockPos center, BoundingBox box) {
      this.center = center.immutable();
      this.boundingBox = box;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.id.value());
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
      if (this.cultureId != null) {
         tag.putString("culture", this.cultureId.toString());
      }
      if (this.displayName != null) {
         tag.putString("name", this.displayName);
      }
      if (this.biomeId != null) {
         tag.putString("biome", this.biomeId.toString());
      }
      if (this.linkedVillageId != null) {
         tag.putUUID("village", this.linkedVillageId);
      }
      tag.putBoolean("discovered", this.discovered);
      tag.putBoolean("promoted", this.promoted);
      return tag;
   }

   public static Settlement load(CompoundTag tag) {
      SettlementId id = SettlementId.of(tag.getString("id"));
      SettlementSource source = SettlementSource.valueOf(tag.getString("source"));
      BlockPos center = new BlockPos(tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz"));
      BoundingBox box = new BoundingBox(
         tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"), tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ")
      );
      ResourceLocation culture = tag.contains("culture") ? ResourceLocation.tryParse(tag.getString("culture")) : null;
      String name = tag.contains("name") ? tag.getString("name") : null;
      ResourceLocation biome = tag.contains("biome") ? ResourceLocation.tryParse(tag.getString("biome")) : null;
      UUID village = tag.hasUUID("village") ? tag.getUUID("village") : null;
      Settlement s = new Settlement(id, source, center, box, culture, name, biome, village);
      s.discovered = tag.getBoolean("discovered");
      s.promoted = tag.getBoolean("promoted");
      return s;
   }
}
