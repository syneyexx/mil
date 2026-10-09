package org.millenaire.worldgen.plan;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlannedHamlet {
   private final UUID hamletPlanId;
   private final ResourceLocation villageTypeId;
   private final BlockPos center;
   private final UUID parentPlanId;
   private SettlementPlanStatus status;

   public PlannedHamlet(UUID hamletPlanId, ResourceLocation villageTypeId, BlockPos center, UUID parentPlanId) {
      this.hamletPlanId = hamletPlanId;
      this.villageTypeId = villageTypeId;
      this.center = center;
      this.parentPlanId = parentPlanId;
      this.status = SettlementPlanStatus.CANDIDATE;
   }

   public UUID hamletPlanId() {
      return this.hamletPlanId;
   }

   public ResourceLocation villageTypeId() {
      return this.villageTypeId;
   }

   public BlockPos center() {
      return this.center;
   }

   public UUID parentPlanId() {
      return this.parentPlanId;
   }

   public SettlementPlanStatus status() {
      return this.status;
   }

   public void setStatus(SettlementPlanStatus status) {
      this.status = status == null ? SettlementPlanStatus.CANDIDATE : status;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.hamletPlanId);
      if (this.villageTypeId != null) {
         tag.putString("type", this.villageTypeId.toString());
      }
      tag.putInt("x", this.center.getX());
      tag.putInt("y", this.center.getY());
      tag.putInt("z", this.center.getZ());
      tag.putUUID("parent", this.parentPlanId);
      tag.putString("status", this.status.name());
      return tag;
   }

   @Nullable
   public static PlannedHamlet load(CompoundTag tag) {
      try {
         PlannedHamlet h = new PlannedHamlet(
            tag.getUUID("id"),
            tag.contains("type") ? ResourceLocation.tryParse(tag.getString("type")) : null,
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            tag.getUUID("parent")
         );
         try {
            h.status = SettlementPlanStatus.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         return h;
      } catch (Exception e) {
         return null;
      }
   }
}
