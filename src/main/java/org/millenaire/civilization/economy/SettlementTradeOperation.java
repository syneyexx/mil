package org.millenaire.civilization.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Persistent settlement-to-settlement physical transfer.
 * Holds concrete stacks so retries never recreate generic type+count replacements.
 */
public final class SettlementTradeOperation {
   public enum Status {
      /** Goods still in source inventories — not yet reserved. */
      PENDING_WITHDRAW,
      /** Goods removed from source; delivery / compensation in progress. */
      IN_TRANSIT,
      /** Destination accepted all remaining stacks. */
      DELIVERED,
      /** Undelivered remainder must return to source. */
      COMPENSATING,
      /** Successful conserved commit (delivery + booking done). */
      COMPLETE,
      /** Ambiguous or unrecoverable; hold concrete stacks, do not invent goods. */
      RECOVERY_REQUIRED,
      /** Legacy marker without reconstructible stacks. */
      LEGACY_UNRECOVERABLE,
      FAILED
   }

   public enum BlockReason {
      NONE,
      DESTINATION_FULL,
      SOURCE_FULL,
      SOURCE_MISSING,
      DESTINATION_MISSING,
      CHUNK_UNAVAILABLE,
      BUDGET,
      LEGACY_INSUFFICIENT_EVIDENCE,
      AMBIGUOUS
   }

   private final UUID operationId;
   private final UUID exporterVillageId;
   private final UUID importerVillageId;
   private final String resourceId;
   private final long createdAtGameTime;
   private Status status;
   private BlockReason blockReason = BlockReason.NONE;
   private final List<ItemStack> withdrawnStacks = new ArrayList<>();
   private final List<ItemStack> pendingDelivery = new ArrayList<>();
   private final List<ItemStack> pendingCompensation = new ArrayList<>();
   private int deliveredCount;
   private int compensatedCount;
   private boolean economyBooked;
   private boolean completionEventPublished;
   private boolean sameFactionLogistics;
   private long lastChangeGameTime;
   private int refundCursor;

   public SettlementTradeOperation(
      UUID operationId,
      UUID exporterVillageId,
      UUID importerVillageId,
      String resourceId,
      long createdAtGameTime,
      boolean sameFactionLogistics
   ) {
      this.operationId = operationId;
      this.exporterVillageId = exporterVillageId;
      this.importerVillageId = importerVillageId;
      this.resourceId = resourceId == null ? "" : resourceId;
      this.createdAtGameTime = createdAtGameTime;
      this.sameFactionLogistics = sameFactionLogistics;
      this.status = Status.PENDING_WITHDRAW;
      this.lastChangeGameTime = createdAtGameTime;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public UUID exporterVillageId() {
      return this.exporterVillageId;
   }

   public UUID importerVillageId() {
      return this.importerVillageId;
   }

   public String resourceId() {
      return this.resourceId;
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

   public BlockReason blockReason() {
      return this.blockReason;
   }

   public void setBlockReason(BlockReason reason) {
      this.blockReason = reason == null ? BlockReason.NONE : reason;
   }

   public List<ItemStack> withdrawnStacks() {
      return this.withdrawnStacks;
   }

   public List<ItemStack> pendingDelivery() {
      return this.pendingDelivery;
   }

   public List<ItemStack> pendingCompensation() {
      return this.pendingCompensation;
   }

   public int deliveredCount() {
      return this.deliveredCount;
   }

   public void addDeliveredCount(int amount) {
      this.deliveredCount += Math.max(0, amount);
   }

   public int compensatedCount() {
      return this.compensatedCount;
   }

   public void addCompensatedCount(int amount) {
      this.compensatedCount += Math.max(0, amount);
   }

   public boolean economyBooked() {
      return this.economyBooked;
   }

   public void setEconomyBooked(boolean v) {
      this.economyBooked = v;
   }

   public boolean completionEventPublished() {
      return this.completionEventPublished;
   }

   public void setCompletionEventPublished(boolean v) {
      this.completionEventPublished = v;
   }

   public boolean sameFactionLogistics() {
      return this.sameFactionLogistics;
   }

   public long lastChangeGameTime() {
      return this.lastChangeGameTime;
   }

   public void touch(long gameTime) {
      this.lastChangeGameTime = Math.max(0L, gameTime);
   }

   public int refundCursor() {
      return this.refundCursor;
   }

   public void setRefundCursor(int cursor) {
      this.refundCursor = Math.max(0, cursor);
   }

   public boolean needsWork() {
      return switch (this.status) {
         case IN_TRANSIT, COMPENSATING, RECOVERY_REQUIRED ->
            !this.pendingDelivery.isEmpty() || !this.pendingCompensation.isEmpty();
         case LEGACY_UNRECOVERABLE -> false;
         default -> false;
      };
   }

   public boolean isActive() {
      return this.status != Status.COMPLETE
         && this.status != Status.FAILED
         && this.status != Status.LEGACY_UNRECOVERABLE;
   }

   public String statusCode() {
      return this.status.name().toLowerCase(Locale.ROOT);
   }

   public String reasonCode() {
      return this.blockReason.name().toLowerCase(Locale.ROOT);
   }

   public void acceptWithdrawn(List<ItemStack> stacks) {
      this.withdrawnStacks.clear();
      this.pendingDelivery.clear();
      if (stacks != null) {
         for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
               ItemStack copy = stack.copy();
               this.withdrawnStacks.add(copy.copy());
               this.pendingDelivery.add(copy);
            }
         }
      }
      this.status = Status.IN_TRANSIT;
   }

   public CompoundTag save(@Nullable Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putUUID("exporter", this.exporterVillageId);
      tag.putUUID("importer", this.importerVillageId);
      tag.putString("resource", this.resourceId);
      tag.putLong("at", this.createdAtGameTime);
      tag.putString("status", this.status.name());
      tag.putString("block", this.blockReason.name());
      tag.putInt("delivered", this.deliveredCount);
      tag.putInt("compensated", this.compensatedCount);
      tag.putBoolean("economyBooked", this.economyBooked);
      tag.putBoolean("eventPublished", this.completionEventPublished);
      tag.putBoolean("logistics", this.sameFactionLogistics);
      tag.putLong("lastChange", this.lastChangeGameTime);
      tag.putInt("refundCursor", this.refundCursor);
      tag.put("withdrawn", stackList(this.withdrawnStacks, registries));
      tag.put("pendingDelivery", stackList(this.pendingDelivery, registries));
      tag.put("pendingCompensation", stackList(this.pendingCompensation, registries));
      return tag;
   }

   @Nullable
   public static SettlementTradeOperation load(CompoundTag tag, @Nullable Provider registries) {
      if (tag == null || !tag.hasUUID("id") || !tag.hasUUID("exporter") || !tag.hasUUID("importer")) {
         return null;
      }
      try {
         SettlementTradeOperation op = new SettlementTradeOperation(
            tag.getUUID("id"),
            tag.getUUID("exporter"),
            tag.getUUID("importer"),
            tag.getString("resource"),
            tag.getLong("at"),
            tag.getBoolean("logistics")
         );
         try {
            op.status = Status.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         try {
            op.blockReason = BlockReason.valueOf(tag.getString("block"));
         } catch (Exception ignored) {
         }
         op.deliveredCount = tag.getInt("delivered");
         op.compensatedCount = tag.getInt("compensated");
         op.economyBooked = tag.getBoolean("economyBooked");
         op.completionEventPublished = tag.getBoolean("eventPublished");
         op.lastChangeGameTime = tag.contains("lastChange") ? tag.getLong("lastChange") : op.createdAtGameTime;
         op.refundCursor = tag.getInt("refundCursor");
         readStacks(tag.getList("withdrawn", Tag.TAG_COMPOUND), op.withdrawnStacks, registries);
         readStacks(tag.getList("pendingDelivery", Tag.TAG_COMPOUND), op.pendingDelivery, registries);
         readStacks(tag.getList("pendingCompensation", Tag.TAG_COMPOUND), op.pendingCompensation, registries);
         return op;
      } catch (Exception e) {
         return null;
      }
   }

   /** Convert a legacy count-only marker into an explicit unrecoverable recovery state. */
   public static SettlementTradeOperation legacyFromMarker(
      UUID exporterVillageId,
      String resourceId,
      int lostCount,
      long gameTime
   ) {
      SettlementTradeOperation op = new SettlementTradeOperation(
         UUID.nameUUIDFromBytes(("legacy-trade:" + exporterVillageId + ":" + resourceId + ":" + gameTime).getBytes()),
         exporterVillageId,
         exporterVillageId,
         resourceId,
         gameTime,
         false
      );
      op.status = Status.LEGACY_UNRECOVERABLE;
      op.blockReason = BlockReason.LEGACY_INSUFFICIENT_EVIDENCE;
      op.deliveredCount = 0;
      op.compensatedCount = 0;
      // Do not invent stacks from a count-only marker.
      op.touch(gameTime);
      return op;
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
               Optional<ItemStack> parsed = ItemStack.parse(registries, st.get("Item"));
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
}
