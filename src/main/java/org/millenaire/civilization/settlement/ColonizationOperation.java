package org.millenaire.civilization.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
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

   public enum FounderState {
      /** Selected but not yet exclusively reserved. */
      INTENDED,
      /** Exclusively reserved for this operation. */
      RESERVED,
      /** Temporarily unavailable; may become eligible again after backoff. */
      TEMP_BLOCKED,
      /** Permanently unsuitable for this founding — replace. */
      UNSUITABLE,
      /** Authoritatively present at the destination. */
      MIGRATED;

      public boolean holdsReservation() {
         return this == RESERVED || this == TEMP_BLOCKED;
      }

      public boolean needsReplacement() {
         return this == UNSUITABLE;
      }

      public boolean countsTowardTarget() {
         return this == MIGRATED;
      }
   }

   public static final class FounderSlot {
      private final UUID villagerId;
      private FounderState state;
      private long blockedUntil;
      private String reason = "";

      public FounderSlot(UUID villagerId, FounderState state) {
         this.villagerId = villagerId;
         this.state = state == null ? FounderState.INTENDED : state;
      }

      public UUID villagerId() {
         return this.villagerId;
      }

      public FounderState state() {
         return this.state;
      }

      public void setState(FounderState state) {
         this.state = state == null ? FounderState.INTENDED : state;
      }

      public long blockedUntil() {
         return this.blockedUntil;
      }

      public void setBlockedUntil(long until) {
         this.blockedUntil = Math.max(0L, until);
      }

      public String reason() {
         return this.reason;
      }

      public void setReason(String reason) {
         this.reason = reason == null ? "" : reason;
      }

      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putUUID("id", this.villagerId);
         tag.putString("state", this.state.name());
         tag.putLong("blockedUntil", this.blockedUntil);
         tag.putString("reason", this.reason);
         return tag;
      }

      @Nullable
      public static FounderSlot load(CompoundTag tag) {
         if (tag == null || !tag.hasUUID("id")) {
            return null;
         }
         FounderState state = FounderState.INTENDED;
         try {
            state = FounderState.valueOf(tag.getString("state"));
         } catch (Exception ignored) {
         }
         FounderSlot slot = new FounderSlot(tag.getUUID("id"), state);
         slot.blockedUntil = tag.getLong("blockedUntil");
         slot.reason = tag.getString("reason");
         return slot;
      }
   }

   private final UUID operationId;
   private final UUID parentVillageId;
   private final String factionId;
   private final UUID planId;
   private final BlockPos targetCenter;
   private final long createdAtGameTime;
   private Status status;
   private String reason = "";
   private String lastPublishedReason = "";
   private boolean resourcesReserved;
   /** Exact reserved stacks still refundable (not yet consumed by physical founding). */
   private final List<ItemStack> reservedStacks = new ArrayList<>();
   /** Remainder that could not be refunded on a prior attempt. */
   private final List<ItemStack> refundOutstanding = new ArrayList<>();
   private final List<FounderSlot> founderSlots = new ArrayList<>();
   private final List<UUID> migratedFounders = new ArrayList<>();
   private int founderTarget = 2;
   private boolean foundingEventPublished;
   private boolean costsConsumed;
   private boolean proposalEventPublished;
   private boolean reservedEventPublished;

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

   public String lastPublishedReason() {
      return this.lastPublishedReason;
   }

   public void setLastPublishedReason(String reason) {
      this.lastPublishedReason = reason == null ? "" : reason;
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

   /** Legacy view of founder ids that are still intended/reserved/blocked. */
   public List<UUID> intendedFounders() {
      List<UUID> ids = new ArrayList<>();
      for (FounderSlot slot : this.founderSlots) {
         if (slot.state() != FounderState.UNSUITABLE && slot.state() != FounderState.MIGRATED) {
            ids.add(slot.villagerId());
         }
      }
      return ids;
   }

   public List<FounderSlot> founderSlots() {
      return this.founderSlots;
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

   public boolean proposalEventPublished() {
      return this.proposalEventPublished;
   }

   public void setProposalEventPublished(boolean v) {
      this.proposalEventPublished = v;
   }

   public boolean reservedEventPublished() {
      return this.reservedEventPublished;
   }

   public void setReservedEventPublished(boolean v) {
      this.reservedEventPublished = v;
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

   public boolean hasPendingFounderWork() {
      if (isTerminal() && this.status == Status.MIGRATED) {
         return false;
      }
      for (FounderSlot slot : this.founderSlots) {
         if (slot.state().holdsReservation()) {
            return true;
         }
      }
      return false;
   }

   public boolean needsRefundWork() {
      return !this.refundOutstanding.isEmpty() || (this.isTerminal() && this.resourcesReserved && !this.costsConsumed && !this.reservedStacks.isEmpty());
   }

   public int migratedCount() {
      return this.migratedFounders.size();
   }

   @Nullable
   public FounderSlot slotFor(UUID villagerId) {
      if (villagerId == null) {
         return null;
      }
      for (FounderSlot slot : this.founderSlots) {
         if (villagerId.equals(slot.villagerId())) {
            return slot;
         }
      }
      return null;
   }

   public Map<ResourceLocation, Integer> reservedCounts() {
      Map<ResourceLocation, Integer> map = new LinkedHashMap<>();
      for (ItemStack stack : this.reservedStacks) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         map.merge(id, stack.getCount(), Integer::sum);
      }
      return map;
   }

   public String statusCode() {
      return this.status.name().toLowerCase(Locale.ROOT);
   }

   public CompoundTag save(Provider registries) {
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
      tag.putString("lastPublishedReason", this.lastPublishedReason);
      tag.putBoolean("reserved", this.resourcesReserved);
      tag.putBoolean("costsConsumed", this.costsConsumed);
      tag.putBoolean("foundingEvent", this.foundingEventPublished);
      tag.putBoolean("proposalEvent", this.proposalEventPublished);
      tag.putBoolean("reservedEvent", this.reservedEventPublished);
      tag.putInt("founderTarget", this.founderTarget);
      tag.put("reservedStacks", stackList(this.reservedStacks, registries));
      tag.put("refundOutstanding", stackList(this.refundOutstanding, registries));
      ListTag slots = new ListTag();
      for (FounderSlot slot : this.founderSlots) {
         slots.add(slot.save());
      }
      tag.put("founderSlots", slots);
      // Legacy mirror for older readers.
      tag.put("intendedFounders", uuidList(intendedFounders()));
      tag.put("migratedFounders", uuidList(this.migratedFounders));
      return tag;
   }

   /** @deprecated use {@link #save(Provider)} */
   @Deprecated
   public CompoundTag save() {
      return save(null);
   }

   @Nullable
   public static ColonizationOperation load(CompoundTag tag, @Nullable Provider registries) {
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
         op.lastPublishedReason = tag.getString("lastPublishedReason");
         op.resourcesReserved = tag.getBoolean("reserved");
         op.costsConsumed = tag.getBoolean("costsConsumed");
         op.foundingEventPublished = tag.getBoolean("foundingEvent");
         op.proposalEventPublished = tag.getBoolean("proposalEvent");
         op.reservedEventPublished = tag.getBoolean("reservedEvent");
         if (tag.contains("founderTarget")) {
            op.founderTarget = Math.max(1, tag.getInt("founderTarget"));
         }
         readStacks(tag.getList("reservedStacks", Tag.TAG_COMPOUND), op.reservedStacks, registries);
         readStacks(tag.getList("refundOutstanding", Tag.TAG_COMPOUND), op.refundOutstanding, registries);
         ListTag slots = tag.getList("founderSlots", Tag.TAG_COMPOUND);
         if (!slots.isEmpty()) {
            for (int i = 0; i < slots.size(); i++) {
               FounderSlot slot = FounderSlot.load(slots.getCompound(i));
               if (slot != null) {
                  op.founderSlots.add(slot);
               }
            }
         } else {
            // Migrate schema-2 intendedFounders into RESERVED slots.
            ListTag intended = tag.getList("intendedFounders", Tag.TAG_STRING);
            for (int i = 0; i < intended.size(); i++) {
               try {
                  UUID id = UUID.fromString(intended.getString(i));
                  op.founderSlots.add(new FounderSlot(id, FounderState.RESERVED));
               } catch (Exception ignored) {
               }
            }
         }
         readUuids(tag.getList("migratedFounders", Tag.TAG_STRING), op.migratedFounders);
         for (UUID migrated : op.migratedFounders) {
            FounderSlot slot = op.slotFor(migrated);
            if (slot == null) {
               FounderSlot migratedSlot = new FounderSlot(migrated, FounderState.MIGRATED);
               op.founderSlots.add(migratedSlot);
            } else {
               slot.setState(FounderState.MIGRATED);
            }
         }
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

   @Nullable
   public static ColonizationOperation load(CompoundTag tag) {
      return load(tag, null);
   }

   private static ListTag stackList(List<ItemStack> stacks, @Nullable Provider registries) {
      ListTag list = new ListTag();
      for (ItemStack stack : stacks) {
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         CompoundTag st = new CompoundTag();
         if (registries != null) {
            try {
               Tag encoded = stack.save(registries);
               if (encoded instanceof CompoundTag compound) {
                  st.put("Item", compound);
               } else {
                  st.put("Item", encoded);
               }
            } catch (Exception ignored) {
               // Fall through to id/count.
            }
         }
         if (!st.contains("Item")) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            st.putString("id", id.toString());
            st.putInt("Count", stack.getCount());
         }
         list.add(st);
      }
      return list;
   }

   private static void readStacks(ListTag list, List<ItemStack> dest, @Nullable Provider registries) {
      for (int i = 0; i < list.size(); i++) {
         CompoundTag st = list.getCompound(i);
         ItemStack stack = ItemStack.EMPTY;
         if (st.contains("Item") && registries != null) {
            try {
               Tag itemTag = st.get("Item");
               Optional<ItemStack> parsed = ItemStack.parse(registries, itemTag);
               if (parsed.isPresent()) {
                  stack = parsed.get();
               }
            } catch (Exception ignored) {
            }
         }
         if (stack.isEmpty() && st.contains("id")) {
            ResourceLocation id = ResourceLocation.tryParse(st.getString("id"));
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            if (item != null) {
               stack = new ItemStack(item, Math.max(1, st.getInt("Count")));
            }
         }
         if (!stack.isEmpty()) {
            dest.add(stack);
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
