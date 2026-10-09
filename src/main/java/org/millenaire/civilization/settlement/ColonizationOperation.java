package org.millenaire.civilization.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lifecycle for autonomous founding:
 * planned → layout → reserved → materializing → activated → migrating → migrated / failed / cancelled.
 */
public final class ColonizationOperation {
   public enum Status {
      PLANNED,
      AWAITING_CHUNKS,
      LAYOUT,
      RESERVED,
      MATERIALIZING,
      ACTIVATED,
      MIGRATING,
      MIGRATED,
      FAILED,
      CANCELLED
   }

   private final UUID operationId;
   private final UUID parentVillageId;
   private final String factionId;
   private final UUID planId;
   private final BlockPos targetCenter;
   private final long createdAtGameTime;
   private Status status;
   private String reason = "";
   private boolean resourcesReserved;
   /** Exact reserved stacks still refundable (not yet consumed by physical founding). */
   private final List<ItemStack> reservedStacks = new ArrayList<>();
   /** Remainder that could not be refunded on a prior attempt. */
   private final List<ItemStack> refundOutstanding = new ArrayList<>();
   private final List<UUID> intendedFounders = new ArrayList<>();
   private final List<UUID> migratedFounders = new ArrayList<>();
   private int founderTarget = 2;
   private boolean foundingEventPublished;
   private boolean costsConsumed;

   public ColonizationOperation(
      UUID operationId,
      UUID parentVillageId,
      String factionId,
      UUID planId,
      BlockPos targetCenter,
      long createdAtGameTime
   ) {
      this.operationId = operationId;
      this.parentVillageId = parentVillageId;
      this.factionId = factionId;
      this.planId = planId;
      this.targetCenter = targetCenter;
      this.createdAtGameTime = createdAtGameTime;
      this.status = Status.PLANNED;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public UUID parentVillageId() {
      return this.parentVillageId;
   }

   public String factionId() {
      return this.factionId;
   }

   public UUID planId() {
      return this.planId;
   }

   public BlockPos targetCenter() {
      return this.targetCenter;
   }

   public long createdAtGameTime() {
      return this.createdAtGameTime;
   }

   public Status status() {
      return this.status;
   }

   public void setStatus(Status status) {
      this.status = status == null ? Status.FAILED : status;
   }

   public String reason() {
      return this.reason;
   }

   public void setReason(String reason) {
      this.reason = reason == null ? "" : reason;
   }

   public boolean resourcesReserved() {
      return this.resourcesReserved || !this.refundOutstanding.isEmpty();
   }

   public void setResourcesReserved(boolean v) {
      this.resourcesReserved = v;
   }

   public List<ItemStack> reservedStacks() {
      return this.reservedStacks;
   }

   public List<ItemStack> refundOutstanding() {
      return this.refundOutstanding;
   }

   public void setReservedStacks(List<ItemStack> stacks) {
      this.reservedStacks.clear();
      if (stacks != null) {
         for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
               this.reservedStacks.add(stack.copy());
            }
         }
      }
   }

   public List<UUID> intendedFounders() {
      return this.intendedFounders;
   }

   public List<UUID> migratedFounders() {
      return this.migratedFounders;
   }

   public int founderTarget() {
      return this.founderTarget;
   }

   public void setFounderTarget(int target) {
      this.founderTarget = Math.max(1, target);
   }

   public boolean foundingEventPublished() {
      return this.foundingEventPublished;
   }

   public void setFoundingEventPublished(boolean v) {
      this.foundingEventPublished = v;
   }

   public boolean costsConsumed() {
      return this.costsConsumed;
   }

   public void setCostsConsumed(boolean v) {
      this.costsConsumed = v;
   }

   public boolean isTerminal() {
      return this.status == Status.MIGRATED || this.status == Status.FAILED || this.status == Status.CANCELLED;
   }

   public boolean isActive() {
      return !isTerminal();
   }

   public Map<ResourceLocation, Integer> reservedCounts() {
      Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
      for (ItemStack stack : this.reservedStacks) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         map.merge(id, stack.getCount(), Integer::sum);
      }
      return map;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putUUID("parent", this.parentVillageId);
      tag.putString("faction", this.factionId);
      tag.putUUID("plan", this.planId);
      tag.putInt("x", this.targetCenter.getX());
      tag.putInt("y", this.targetCenter.getY());
      tag.putInt("z", this.targetCenter.getZ());
      tag.putLong("at", this.createdAtGameTime);
      tag.putString("status", this.status.name());
      tag.putString("reason", this.reason);
      tag.putBoolean("reserved", this.resourcesReserved);
      tag.putBoolean("costsConsumed", this.costsConsumed);
      tag.putBoolean("foundingEvent", this.foundingEventPublished);
      tag.putInt("founderTarget", this.founderTarget);
      tag.put("reservedStacks", stackList(this.reservedStacks));
      tag.put("refundOutstanding", stackList(this.refundOutstanding));
      tag.put("intendedFounders", uuidList(this.intendedFounders));
      tag.put("migratedFounders", uuidList(this.migratedFounders));
      return tag;
   }

   @Nullable
   public static ColonizationOperation load(CompoundTag tag) {
      try {
         ColonizationOperation op = new ColonizationOperation(
            tag.getUUID("id"),
            tag.getUUID("parent"),
            tag.getString("faction"),
            tag.getUUID("plan"),
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            tag.getLong("at")
         );
         try {
            op.status = Status.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         op.reason = tag.getString("reason");
         op.resourcesReserved = tag.getBoolean("reserved");
         op.costsConsumed = tag.getBoolean("costsConsumed");
         op.foundingEventPublished = tag.getBoolean("foundingEvent");
         if (tag.contains("founderTarget")) {
            op.founderTarget = Math.max(1, tag.getInt("founderTarget"));
         }
         readStacks(tag.getList("reservedStacks", Tag.TAG_COMPOUND), op.reservedStacks);
         readStacks(tag.getList("refundOutstanding", Tag.TAG_COMPOUND), op.refundOutstanding);
         readUuids(tag.getList("intendedFounders", Tag.TAG_STRING), op.intendedFounders);
         readUuids(tag.getList("migratedFounders", Tag.TAG_STRING), op.migratedFounders);
         // Schema 1 compatibility: reserved boolean without stacks — refund path uses counts if present.
         if (op.reservedStacks.isEmpty() && tag.contains("reservedWood")) {
            int wood = tag.getInt("reservedWood");
            int stone = tag.getInt("reservedStone");
            if (wood > 0) {
               op.reservedStacks.add(new ItemStack(net.minecraft.world.item.Items.OAK_LOG, wood));
            }
            if (stone > 0) {
               op.reservedStacks.add(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, stone));
            }
         }
         return op;
      } catch (Exception e) {
         return null;
      }
   }

   private static ListTag stackList(List<ItemStack> stacks) {
      ListTag list = new ListTag();
      for (ItemStack stack : stacks) {
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         CompoundTag st = new CompoundTag();
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         st.putString("id", id.toString());
         st.putInt("Count", stack.getCount());
         list.add(st);
      }
      return list;
   }

   private static void readStacks(ListTag list, List<ItemStack> dest) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag st = list.getCompound(i);
         if (!st.contains("id")) {
            continue;
         }
         ResourceLocation id = ResourceLocation.tryParse(st.getString("id"));
         Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
         if (item != null) {
            dest.add(new ItemStack(item, Math.max(1, st.getInt("Count"))));
         }
      }
   }

   private static ListTag uuidList(List<UUID> ids) {
      ListTag list = new ListTag();
      for (UUID id : ids) {
         list.add(StringTag.valueOf(id.toString()));
      }
      return list;
   }

   private static void readUuids(ListTag list, List<UUID> dest) {
      for (int i = 0; i < list.size(); i++) {
         try {
            dest.add(UUID.fromString(list.getString(i)));
         } catch (Exception ignored) {
         }
      }
   }
}
