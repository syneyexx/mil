package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/**
 * Persistent colonization / autonomous founding operations.
 * Schema 3: advance/refund cursors and exclusive founder reservations.
 */
public class ColonizationSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_colonization";
   public static final int SCHEMA = 3;
   private static final int RETENTION = 128;

   private final Map<UUID, ColonizationOperation> operations = new HashMap<>();
   /** villagerId → operationId — exclusive founder reservation index. */
   private final Map<UUID, UUID> founderReservations = new HashMap<>();
   private int schema = SCHEMA;
   private int advanceCursor;
   private int refundCursor;

   public static Factory<ColonizationSavedData> factory() {
      return new Factory<>(ColonizationSavedData::new, ColonizationSavedData::load, null);
   }

   public static ColonizationSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Map<UUID, ColonizationOperation> operations() {
      return this.operations;
   }

   public int advanceCursor() {
      return this.advanceCursor;
   }

   public void setAdvanceCursor(int cursor) {
      if (this.advanceCursor != cursor) {
         this.advanceCursor = cursor;
         this.setDirty();
      }
   }

   public int refundCursor() {
      return this.refundCursor;
   }

   public void setRefundCursor(int cursor) {
      if (this.refundCursor != cursor) {
         this.refundCursor = cursor;
         this.setDirty();
      }
   }

   @Nullable
   public ColonizationOperation get(UUID id) {
      return id == null ? null : this.operations.get(id);
   }

   public void put(ColonizationOperation op) {
      if (op == null) {
         return;
      }
      this.operations.put(op.operationId(), op);
      pruneTerminalIdle();
      this.setDirty();
   }

   /** True when another active operation already holds this villager. */
   public boolean isFounderReservedByOther(UUID villagerId, UUID operationId) {
      if (villagerId == null) {
         return false;
      }
      UUID holder = this.founderReservations.get(villagerId);
      return holder != null && !holder.equals(operationId);
   }

   @Nullable
   public UUID reservationHolder(UUID villagerId) {
      return villagerId == null ? null : this.founderReservations.get(villagerId);
   }

   public boolean reserveFounder(UUID villagerId, UUID operationId) {
      if (villagerId == null || operationId == null) {
         return false;
      }
      UUID existing = this.founderReservations.get(villagerId);
      if (existing != null && !existing.equals(operationId)) {
         return false;
      }
      this.founderReservations.put(villagerId, operationId);
      this.setDirty();
      return true;
   }

   public void releaseFounder(UUID villagerId, UUID operationId) {
      if (villagerId == null) {
         return;
      }
      UUID existing = this.founderReservations.get(villagerId);
      if (existing != null && (operationId == null || existing.equals(operationId))) {
         this.founderReservations.remove(villagerId);
         this.setDirty();
      }
   }

   public void releaseAllForOperation(UUID operationId) {
      if (operationId == null) {
         return;
      }
      boolean changed = false;
      Iterator<Entry<UUID, UUID>> it = this.founderReservations.entrySet().iterator();
      while (it.hasNext()) {
         Entry<UUID, UUID> e = it.next();
         if (operationId.equals(e.getValue())) {
            it.remove();
            changed = true;
         }
      }
      if (changed) {
         this.setDirty();
      }
   }

   /** Drop reservations whose operation is missing or terminal without pending founders. */
   public void reconcileOrphanReservations() {
      boolean changed = false;
      Iterator<Entry<UUID, UUID>> it = this.founderReservations.entrySet().iterator();
      while (it.hasNext()) {
         Entry<UUID, UUID> e = it.next();
         ColonizationOperation op = this.operations.get(e.getValue());
         if (op == null || (op.isTerminal() && !op.hasPendingFounderWork())) {
            it.remove();
            changed = true;
         }
      }
      if (changed) {
         this.setDirty();
      }
   }

   private void pruneTerminalIdle() {
      if (this.operations.size() <= RETENTION) {
         return;
      }
      this.operations.entrySet().removeIf(e -> {
         ColonizationOperation op = e.getValue();
         if (!op.isTerminal()) {
            return false;
         }
         // Never prune recoverable refunds, unresolved stack evidence, or pending founder cleanup.
         if (!op.refundOutstanding().isEmpty()
            || op.decodeUnresolved()
            || !op.unresolvedReservedStacks().isEmpty()
            || !op.unresolvedRefundOutstanding().isEmpty()
            || op.hasPendingFounderWork()) {
            return false;
         }
         return this.operations.size() > RETENTION / 2;
      });
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", this.schema);
      tag.putInt("advanceCursor", this.advanceCursor);
      tag.putInt("refundCursor", this.refundCursor);
      ListTag list = new ListTag();
      for (ColonizationOperation op : this.operations.values()) {
         list.add(op.save(registries));
      }
      tag.put("ops", list);
      ListTag reservations = new ListTag();
      for (Entry<UUID, UUID> e : this.founderReservations.entrySet()) {
         CompoundTag row = new CompoundTag();
         row.putUUID("villager", e.getKey());
         row.putUUID("op", e.getValue());
         reservations.add(row);
      }
      tag.put("founderReservations", reservations);
      return tag;
   }

   private static ColonizationSavedData load(CompoundTag tag, Provider registries) {
      ColonizationSavedData data = new ColonizationSavedData();
      data.schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
      data.advanceCursor = tag.contains("advanceCursor") ? tag.getInt("advanceCursor") : 0;
      data.refundCursor = tag.contains("refundCursor") ? tag.getInt("refundCursor") : 0;
      ListTag list = tag.getList("ops", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         ColonizationOperation op = ColonizationOperation.load(list.getCompound(i), registries);
         if (op != null) {
            data.operations.put(op.operationId(), op);
         } else {
            LOGGER.warn("[Millenaire] Skipping corrupt colonization op #{}", i);
         }
      }
      ListTag reservations = tag.getList("founderReservations", Tag.TAG_COMPOUND);
      for (int i = 0; i < reservations.size(); i++) {
         CompoundTag row = reservations.getCompound(i);
         if (row.hasUUID("villager") && row.hasUUID("op")) {
            UUID villager = row.getUUID("villager");
            UUID opId = row.getUUID("op");
            UUID prior = data.founderReservations.putIfAbsent(villager, opId);
            if (prior != null && !prior.equals(opId)) {
               LOGGER.warn(
                  "[Millenaire] Duplicate founder reservation for {} — keeping {} rejecting {}",
                  villager,
                  prior,
                  opId
               );
            }
         }
      }
      // Rebuild reservation index from active founder slots when loading pre-schema-3 worlds.
      if (data.schema < 3 || data.founderReservations.isEmpty()) {
         for (ColonizationOperation op : data.operations.values()) {
            if (!op.isActive()) {
               continue;
            }
            for (ColonizationOperation.FounderSlot slot : op.founderSlots()) {
               if (slot.state().holdsReservation()) {
                  data.founderReservations.putIfAbsent(slot.villagerId(), op.operationId());
               }
            }
         }
      }
      data.schema = SCHEMA;
      data.reconcileOrphanReservations();
      return data;
   }
}
