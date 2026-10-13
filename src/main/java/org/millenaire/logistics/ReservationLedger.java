package org.millenaire.logistics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.village.Village;

/**
 * Single reservation authority. Physical goods stay in chests until pull/push commits.
 */
public final class ReservationLedger {
   private final Map<UUID, StockReservation> reservations = new LinkedHashMap<>();

   public List<StockReservation> all() {
      return List.copyOf(this.reservations.values());
   }

   @Nullable
   public StockReservation get(UUID id) {
      return this.reservations.get(id);
   }

   public int reservedAmount(UUID villageId, Item item) {
      int sum = 0;
      for (StockReservation r : this.reservations.values()) {
         if (r.villageId().equals(villageId)
            && r.item() == item
            && (r.state() == ReservationState.HELD || r.state() == ReservationState.IN_TRANSIT)) {
            sum += r.amount();
         }
      }
      return sum;
   }

   /**
    * Try to reserve {@code amount} of {@code item} at village TH if physical stock covers
    * existing holds + new amount.
    */
   @Nullable
   public StockReservation tryReserve(
      ServerLevel level,
      Village village,
      Item item,
      int amount,
      String ownerRef
   ) {
      if (village == null || item == null || amount <= 0) {
         return null;
      }
      BuildingInstance th = village.getTownhall();
      BuildingInventory inv = th != null ? th.getInventory() : null;
      if (inv == null) {
         return null;
      }
      int physical = inv.getCount(level, item);
      int already = reservedAmount(village.getId().uuid(), item);
      if (physical < already + amount) {
         return null;
      }
      // Cap concurrent reservations
      int open = 0;
      for (StockReservation r : this.reservations.values()) {
         if (r.villageId().equals(village.getId().uuid()) && r.state() == ReservationState.HELD) {
            open++;
         }
      }
      if (open >= 16) {
         return null;
      }
      StockReservation reservation = new StockReservation(
         UUID.randomUUID(),
         village.getId().uuid(),
         item,
         amount,
         ownerRef,
         ReservationState.HELD
      );
      this.reservations.put(reservation.id(), reservation);
      return reservation;
   }

   public boolean markInTransit(UUID reservationId) {
      StockReservation r = this.reservations.get(reservationId);
      if (r == null || r.state() != ReservationState.HELD) {
         return false;
      }
      r.setState(ReservationState.IN_TRANSIT);
      return true;
   }

   public boolean release(UUID reservationId) {
      StockReservation r = this.reservations.get(reservationId);
      if (r == null) {
         return false;
      }
      r.setState(ReservationState.RELEASED);
      this.reservations.remove(reservationId);
      return true;
   }

   public boolean consume(UUID reservationId) {
      StockReservation r = this.reservations.get(reservationId);
      if (r == null) {
         return false;
      }
      r.setState(ReservationState.CONSUMED);
      this.reservations.remove(reservationId);
      return true;
   }

   public void cancelByOwnerPrefix(String ownerPrefix) {
      if (ownerPrefix == null) {
         return;
      }
      Iterator<Map.Entry<UUID, StockReservation>> it = this.reservations.entrySet().iterator();
      while (it.hasNext()) {
         StockReservation r = it.next().getValue();
         if (r.ownerRef().startsWith(ownerPrefix)) {
            r.setState(ReservationState.RELEASED);
            it.remove();
         }
      }
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (StockReservation r : this.reservations.values()) {
         list.add(r.save(registries));
      }
      tag.put("reservations", list);
      return tag;
   }

   public void load(CompoundTag tag, HolderLookup.Provider registries) {
      this.reservations.clear();
      if (!tag.contains("reservations", Tag.TAG_LIST)) {
         return;
      }
      ListTag list = tag.getList("reservations", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         StockReservation r = StockReservation.load(list.getCompound(i), registries);
         this.reservations.put(r.id(), r);
      }
   }

   public void clear() {
      this.reservations.clear();
   }
}
