package org.millenaire.founding;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class FoundingOperation {
   private final UUID id;
   private final UUID parentVillageId;
   @Nullable
   private final UUID ownerPlayerId;
   private FoundingState state;
   private BlockPos targetCenter;
   private ResourceLocation villageTypeId;
   private ResourceLocation cultureId;
   private int reservedFood;
   private int reservedTools;
   private int reservedDeniers;
   private int founderSlots;
   private String reason = "";
   private final long createdGameTime;

   public FoundingOperation(
      UUID id,
      UUID parentVillageId,
      @Nullable UUID ownerPlayerId,
      BlockPos targetCenter,
      ResourceLocation cultureId,
      ResourceLocation villageTypeId,
      long createdGameTime
   ) {
      this.id = id;
      this.parentVillageId = parentVillageId;
      this.ownerPlayerId = ownerPlayerId;
      this.targetCenter = targetCenter;
      this.cultureId = cultureId;
      this.villageTypeId = villageTypeId;
      this.createdGameTime = createdGameTime;
      this.state = FoundingState.PROPOSED;
      this.founderSlots = 4;
   }

   public UUID id() {
      return this.id;
   }

   public UUID parentVillageId() {
      return this.parentVillageId;
   }

   @Nullable
   public UUID ownerPlayerId() {
      return this.ownerPlayerId;
   }

   public FoundingState state() {
      return this.state;
   }

   public void setState(FoundingState state) {
      this.state = state;
   }

   public BlockPos targetCenter() {
      return this.targetCenter;
   }

   public ResourceLocation villageTypeId() {
      return this.villageTypeId;
   }

   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public int reservedFood() {
      return this.reservedFood;
   }

   public int reservedTools() {
      return this.reservedTools;
   }

   public int reservedDeniers() {
      return this.reservedDeniers;
   }

   public int founderSlots() {
      return this.founderSlots;
   }

   public void setReservations(int food, int tools, int deniers) {
      this.reservedFood = food;
      this.reservedTools = tools;
      this.reservedDeniers = deniers;
   }

   public String reason() {
      return this.reason;
   }

   public void setReason(String reason) {
      this.reason = reason == null ? "" : reason;
   }

   public boolean isOpen() {
      return this.state != FoundingState.DONE
         && this.state != FoundingState.CANCELLED
         && this.state != FoundingState.REFUND;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("parent", this.parentVillageId);
      if (this.ownerPlayerId != null) {
         tag.putUUID("owner", this.ownerPlayerId);
      }
      tag.putString("state", this.state.name());
      tag.putIntArray("center", new int[]{this.targetCenter.getX(), this.targetCenter.getY(), this.targetCenter.getZ()});
      tag.putString("culture", this.cultureId.toString());
      tag.putString("type", this.villageTypeId.toString());
      tag.putInt("food", this.reservedFood);
      tag.putInt("tools", this.reservedTools);
      tag.putInt("deniers", this.reservedDeniers);
      tag.putInt("founders", this.founderSlots);
      tag.putString("reason", this.reason);
      tag.putLong("created", this.createdGameTime);
      return tag;
   }

   public static FoundingOperation load(CompoundTag tag, HolderLookup.Provider registries) {
      int[] c = tag.getIntArray("center");
      BlockPos center = c.length == 3 ? new BlockPos(c[0], c[1], c[2]) : BlockPos.ZERO;
      FoundingOperation op = new FoundingOperation(
         tag.getUUID("id"),
         tag.getUUID("parent"),
         tag.hasUUID("owner") ? tag.getUUID("owner") : null,
         center,
         ResourceLocation.parse(tag.getString("culture")),
         ResourceLocation.parse(tag.getString("type")),
         tag.getLong("created")
      );
      op.state = FoundingState.valueOf(tag.getString("state"));
      op.reservedFood = tag.getInt("food");
      op.reservedTools = tag.getInt("tools");
      op.reservedDeniers = tag.getInt("deniers");
      op.founderSlots = tag.getInt("founders");
      op.reason = tag.contains("reason") ? tag.getString("reason") : "";
      return op;
   }
}
