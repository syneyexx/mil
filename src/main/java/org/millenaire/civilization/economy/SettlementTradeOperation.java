package org.millenaire.civilization.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.millenaire.util.ExactStackCodec;

/**
 * Persistent settlement-to-settlement physical transfer.
 * Holds concrete stacks so retries never recreate generic type+count replacements.
 * Schema 4: unresolved stack evidence + finalization-aware recovery.
 */
public final class SettlementTradeOperation {
   public static final int SCHEMA = 4;

   public enum Status {
      /** Goods still in source inventories — not yet reserved. */
      PENDING_WITHDRAW,
      /** Goods removed from source; delivery / compensation in progress. */
      IN_TRANSIT,
      /** Destination accepted all remaining stacks; booking/events may still be pending. */
      DELIVERED,
      /** Undelivered remainder must return to source. */
      COMPENSATING,
      /** Successful conserved commit (delivery + booking done). */
      COMPLETE,
      /** Ambiguous or unrecoverable; hold concrete stacks / unresolved evidence. */
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
      DECODE_UNRESOLVED,
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
   private final List<CompoundTag> unresolvedWithdrawn = new ArrayList<>();
   private final List<CompoundTag> unresolvedPendingDelivery = new ArrayList<>();
   private final List<CompoundTag> unresolvedPendingCompensation = new ArrayList<>();
   private int deliveredCount;
   private int compensatedCount;
   private boolean economyBooked;
   private boolean completionEventPublished;
   private boolean sameFactionLogistics;
   private long lastChangeGameTime;
   private int refundCursor;
   private int recoveryAttempts;
   private int schemaVersion = SCHEMA;
   private boolean decodeUnresolved;

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

   /** Stable day key from creation time — never rewrite during recovery on a later day. */
   public long createdDayKey() {
      return this.createdAtGameTime / 24000L;
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

   public List<CompoundTag> unresolvedWithdrawn() {
      return this.unresolvedWithdrawn;
   }

   public List<CompoundTag> unresolvedPendingDelivery() {
      return this.unresolvedPendingDelivery;
   }

   public List<CompoundTag> unresolvedPendingCompensation() {
      return this.unresolvedPendingCompensation;
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

   public int recoveryAttempts() {
      return this.recoveryAttempts;
   }

   public void incrementRecoveryAttempts() {
      this.recoveryAttempts++;
   }

   public boolean decodeUnresolved() {
      return this.decodeUnresolved;
   }

   public int schemaVersion() {
      return this.schemaVersion;
   }

   /** Stable effects / booking / event key for this operation (not UI keymap). */
   public String effectsKey() {
      return "trade-fx:" + this.operationId;
   }

   /** Logical day success key for preventing a second withdrawal of the same lane. */
   public String daySuccessKey() {
      return "trade-ok:"
         + this.exporterVillageId
         + ":"
         + this.importerVillageId
         + ":"
         + this.resourceId
         + ":"
         + createdDayKey();
   }

   public boolean needsFinalization() {
      if (this.status == Status.COMPLETE || this.status == Status.FAILED || this.status == Status.LEGACY_UNRECOVERABLE) {
         return false;
      }
      if (this.decodeUnresolved) {
         return false;
      }
      return this.deliveredCount > 0
         && this.pendingDelivery.isEmpty()
         && this.pendingCompensation.isEmpty()
         && (!this.economyBooked || !this.completionEventPublished);
   }

   public boolean needsWork() {
      if (this.decodeUnresolved || this.status == Status.RECOVERY_REQUIRED) {
         return !this.pendingDelivery.isEmpty()
            || !this.pendingCompensation.isEmpty()
            || needsFinalization()
            || (!this.unresolvedPendingDelivery.isEmpty() || !this.unresolvedPendingCompensation.isEmpty());
      }
      return switch (this.status) {
         case IN_TRANSIT, COMPENSATING, DELIVERED ->
            !this.pendingDelivery.isEmpty() || !this.pendingCompensation.isEmpty() || needsFinalization();
         case LEGACY_UNRECOVERABLE, COMPLETE, FAILED, PENDING_WITHDRAW -> false;
         default -> false;
      };
   }

   public boolean isActive() {
      if (this.decodeUnresolved) {
         return true;
      }
      return this.status != Status.COMPLETE
         && this.status != Status.FAILED
         && this.status != Status.LEGACY_UNRECOVERABLE;
   }

   /** Unfinished escrow / evidence that must survive retention pruning. */
   public boolean holdsEscrowOrEvidence() {
      return isActive()
         || !this.pendingDelivery.isEmpty()
         || !this.pendingCompensation.isEmpty()
         || !this.unresolvedWithdrawn.isEmpty()
         || !this.unresolvedPendingDelivery.isEmpty()
         || !this.unresolvedPendingCompensation.isEmpty()
         || this.decodeUnresolved;
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
      tag.putInt("schema", this.schemaVersion);
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
      tag.putInt("recoveryAttempts", this.recoveryAttempts);
      tag.putBoolean("decodeUnresolved", this.decodeUnresolved);
      tag.put("withdrawn", ExactStackCodec.encode(this.withdrawnStacks, this.unresolvedWithdrawn, registries));
      tag.put("pendingDelivery", ExactStackCodec.encode(this.pendingDelivery, this.unresolvedPendingDelivery, registries));
      tag.put("pendingCompensation", ExactStackCodec.encode(this.pendingCompensation, this.unresolvedPendingCompensation, registries));
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
         op.schemaVersion = tag.contains("schema") ? tag.getInt("schema") : 3;
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
         op.recoveryAttempts = tag.getInt("recoveryAttempts");
         op.decodeUnresolved = tag.getBoolean("decodeUnresolved");

         applyStackList(tag.getList("withdrawn", Tag.TAG_COMPOUND), op.withdrawnStacks, op.unresolvedWithdrawn, registries, op);
         applyStackList(tag.getList("pendingDelivery", Tag.TAG_COMPOUND), op.pendingDelivery, op.unresolvedPendingDelivery, registries, op);
         applyStackList(tag.getList("pendingCompensation", Tag.TAG_COMPOUND), op.pendingCompensation, op.unresolvedPendingCompensation, registries, op);

         // Schema-3 → 4: keep status, but mark decode-unresolved ops for recovery diagnosis.
         if (op.schemaVersion < SCHEMA) {
            op.schemaVersion = SCHEMA;
         }
         if (op.decodeUnresolved && op.status != Status.COMPLETE && op.status != Status.FAILED && op.status != Status.LEGACY_UNRECOVERABLE) {
            op.status = Status.RECOVERY_REQUIRED;
            op.blockReason = BlockReason.DECODE_UNRESOLVED;
         }
         return op;
      } catch (Exception e) {
         return null;
      }
   }

   private static void applyStackList(
      ListTag list,
      List<ItemStack> dest,
      List<CompoundTag> unresolvedDest,
      @Nullable Provider registries,
      SettlementTradeOperation op
   ) {
      ExactStackCodec.DecodeResult decoded = ExactStackCodec.decode(list, registries);
      dest.clear();
      dest.addAll(decoded.stacks());
      unresolvedDest.clear();
      unresolvedDest.addAll(decoded.unresolvedRaw());
      if (decoded.hasUnresolved()) {
         op.decodeUnresolved = true;
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
      op.touch(gameTime);
      return op;
   }
}
