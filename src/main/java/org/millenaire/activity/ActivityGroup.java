package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Lightweight group identity attached to an operation. */
public final class ActivityGroup {
   private final UUID groupId;
   private final UUID operationId;
   private final List<UUID> memberIds = new ArrayList<>();
   @Nullable
   private UUID leaderId;
   @Nullable
   private BlockPos rallyPos;
   private long lastDecisionGameTime;
   private long lastScanGameTime;

   public ActivityGroup(UUID groupId, UUID operationId) {
      this.groupId = groupId;
      this.operationId = operationId;
   }

   public UUID groupId() {
      return this.groupId;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public List<UUID> memberIds() {
      return Collections.unmodifiableList(this.memberIds);
   }

   public void setMembers(List<UUID> ids) {
      this.memberIds.clear();
      this.memberIds.addAll(ids);
      if (this.leaderId == null && !this.memberIds.isEmpty()) {
         this.leaderId = this.memberIds.getFirst();
      }
   }

   @Nullable
   public UUID leaderId() {
      return this.leaderId;
   }

   public void setLeaderId(@Nullable UUID leaderId) {
      this.leaderId = leaderId;
   }

   @Nullable
   public BlockPos rallyPos() {
      return this.rallyPos;
   }

   public void setRallyPos(@Nullable BlockPos rallyPos) {
      this.rallyPos = rallyPos;
   }

   public long lastDecisionGameTime() {
      return this.lastDecisionGameTime;
   }

   public void setLastDecisionGameTime(long t) {
      this.lastDecisionGameTime = t;
   }

   public long lastScanGameTime() {
      return this.lastScanGameTime;
   }

   public void setLastScanGameTime(long t) {
      this.lastScanGameTime = t;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.groupId);
      tag.putUUID("op", this.operationId);
      ListTag members = new ListTag();
      for (UUID u : this.memberIds) {
         CompoundTag m = new CompoundTag();
         m.putUUID("u", u);
         members.add(m);
      }
      tag.put("members", members);
      if (this.leaderId != null) {
         tag.putUUID("leader", this.leaderId);
      }
      if (this.rallyPos != null) {
         tag.putIntArray("rally", new int[]{this.rallyPos.getX(), this.rallyPos.getY(), this.rallyPos.getZ()});
      }
      tag.putLong("lastDecision", this.lastDecisionGameTime);
      tag.putLong("lastScan", this.lastScanGameTime);
      return tag;
   }

   public static ActivityGroup load(CompoundTag tag) {
      ActivityGroup g = new ActivityGroup(tag.getUUID("id"), tag.getUUID("op"));
      ListTag members = tag.getList("members", Tag.TAG_COMPOUND);
      for (int i = 0; i < members.size(); i++) {
         g.memberIds.add(members.getCompound(i).getUUID("u"));
      }
      if (tag.hasUUID("leader")) {
         g.leaderId = tag.getUUID("leader");
      }
      if (tag.contains("rally")) {
         int[] p = tag.getIntArray("rally");
         if (p.length == 3) {
            g.rallyPos = new BlockPos(p[0], p[1], p[2]);
         }
      }
      g.lastDecisionGameTime = tag.getLong("lastDecision");
      g.lastScanGameTime = tag.getLong("lastScan");
      return g;
   }
}
