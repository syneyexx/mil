package org.millenaire.logistics;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.economy.Need;
import org.millenaire.economy.NeedService;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Delivery contracts + reservation commits on the server thread.
 * Matching is requested from economy plans; advance is time-based (abstract) without chunk loads.
 */
public final class LogisticsService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int ADVANCE_BUDGET = 4;
   private static final long DEFAULT_TRAVEL_TICKS = 6000L;

   private LogisticsService() {
   }

   public static void onServerStarted(ServerLevel overworld) {
      LogisticsSavedData.get(overworld);
      LOGGER.debug("[Logistics] SavedData ready");
   }

   public static void onServerStopping() {
      // SavedData flush is handled by Minecraft; nothing to await.
   }

   public static void tick(ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.logisticsSimulationEnabled.getAsBoolean()) {
         return;
      }
      LogisticsSavedData data = LogisticsSavedData.get(level);
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      CommitBudget budget = CommitBudget.get();
      long gameTime = level.getGameTime();

      int advanced = 0;
      List<DeliveryContract> snapshot = new ArrayList<>(data.contracts().values());
      for (DeliveryContract contract : snapshot) {
         if (advanced >= ADVANCE_BUDGET || !budget.trySpendCommit(1)) {
            break;
         }
         if (!contract.isOpen()) {
            continue;
         }
         if (advanceOne(level, manager, data, contract, gameTime)) {
            advanced++;
            TickPerfCounters.LOGISTICS_COMMITS.incrementAndGet();
            data.setDirty();
         }
      }

      // Bound completed contract map growth
      if (data.contracts().size() > 256) {
         pruneClosed(data);
      }
   }

   /**
    * Create a trade contract: partner supplies {@code item} to {@code buyer}.
    * Reserves at partner TH when stock allows; otherwise marks BLOCKED with reason.
    */
   public static void requestTradeContract(
      ServerLevel level,
      Village buyer,
      UUID sellerVillageId,
      Item item,
      int amount,
      long gameTime
   ) {
      if (!MillenaireServerConfig.SERVER.logisticsSimulationEnabled.getAsBoolean()) {
         return;
      }
      if (buyer == null || sellerVillageId == null || item == null || amount <= 0) {
         return;
      }
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      Village seller = findVillage(manager, sellerVillageId);
      if (seller == null) {
         return;
      }

      LogisticsSavedData data = LogisticsSavedData.get(level);
      int max = MillenaireServerConfig.SERVER.logisticsMaxContractsPerVillage.getAsInt();
      if (countOpenFor(data, buyer.getId().uuid()) >= max || countOpenFor(data, sellerVillageId) >= max) {
         return;
      }
      // Dedupe open contracts for same pair+item
      for (DeliveryContract existing : data.contracts().values()) {
         if (existing.isOpen()
            && existing.sourceVillageId().equals(sellerVillageId)
            && existing.destVillageId().equals(buyer.getId().uuid())
            && existing.item() == item) {
            return;
         }
      }

      long travel = estimateTravelTicks(buyer, seller);
      DeliveryContract contract = new DeliveryContract(
         UUID.randomUUID(),
         sellerVillageId,
         buyer.getId().uuid(),
         item,
         amount,
         ContractState.PROPOSED,
         gameTime,
         gameTime + travel
      );

      StockReservation reservation = data.ledger().tryReserve(level, seller, item, amount, "contract:" + contract.id());
      if (reservation == null) {
         contract.setState(ContractState.BLOCKED);
         contract.setReason("insufficient_source_stock");
         // Still store briefly so UI can show the block; economy will retry later.
      } else {
         contract.setReservationId(reservation.id());
         contract.setState(ContractState.RESERVED);
         contract.setReason("");
      }

      // Link top matching need if any
      for (Need need : buyer.getNeedService().activeNeeds()) {
         if (need.item() == item) {
            contract.setLinkedNeedId(need.id());
            break;
         }
      }

      data.putContract(contract);
      buyer.markDirty();
      seller.markDirty();
   }

   private static boolean advanceOne(
      ServerLevel level,
      VillageManager manager,
      LogisticsSavedData data,
      DeliveryContract contract,
      long gameTime
   ) {
      Village source = findVillage(manager, contract.sourceVillageId());
      Village dest = findVillage(manager, contract.destVillageId());
      if (source == null || dest == null) {
         cancel(data, contract, "missing_village");
         return true;
      }

      switch (contract.state()) {
         case PROPOSED, BLOCKED -> {
            // Retry reserve once stock available
            if (contract.reservationId() == null) {
               StockReservation reservation = data.ledger()
                  .tryReserve(level, source, contract.item(), contract.amount(), "contract:" + contract.id());
               if (reservation != null) {
                  contract.setReservationId(reservation.id());
                  contract.setState(ContractState.RESERVED);
                  contract.setReason("");
                  return true;
               }
            }
            return false;
         }
         case RESERVED -> {
            if (!pullFromSource(level, source, data, contract)) {
               contract.setState(ContractState.BLOCKED);
               contract.setReason("pull_failed");
               return true;
            }
            contract.setState(ContractState.ACTIVE);
            contract.setMode("ABSTRACT");
            contract.setEtaGameTime(gameTime + estimateTravelTicks(source, dest));
            return true;
         }
         case ACTIVE -> {
            if (gameTime < contract.etaGameTime()) {
               return false;
            }
            contract.setState(ContractState.COMPLETING);
            return true;
         }
         case COMPLETING -> {
            if (!deliverToDest(level, dest, data, contract)) {
               contract.setState(ContractState.RETURN_REQUIRED);
               contract.setReason("dest_full_or_missing");
               return true;
            }
            contract.setState(ContractState.DONE);
            contract.setDelivered(contract.amount());
            if (contract.reservationId() != null) {
               data.ledger().consume(contract.reservationId());
            }
            if (contract.linkedNeedId() != null) {
               NeedService needs = dest.getNeedService();
               needs.applyDelivery(contract.linkedNeedId(), contract.amount());
            }
            return true;
         }
         case RETURN_REQUIRED -> {
            // Best-effort return to source
            if (contract.reservationId() != null) {
               data.ledger().release(contract.reservationId());
               contract.setReservationId(null);
            }
            // Attempt to put goods back if we had pulled them (simplified: recreate stack at source)
            BuildingInstance th = source.getTownhall();
            if (th != null && th.getInventory() != null) {
               th.getInventory().add(level, contract.item(), contract.amount());
            }
            contract.setState(ContractState.CANCELLED);
            contract.setReason("returned");
            return true;
         }
         default -> {
            return false;
         }
      }
   }

   private static boolean pullFromSource(ServerLevel level, Village source, LogisticsSavedData data, DeliveryContract contract) {
      BuildingInstance th = source.getTownhall();
      BuildingInventory inv = th != null ? th.getInventory() : null;
      if (inv == null) {
         return false;
      }
      int have = inv.getCount(level, contract.item());
      if (have < contract.amount()) {
         return false;
      }
      int removed = inv.remove(level, contract.item(), contract.amount());
      if (removed < contract.amount()) {
         if (removed > 0) {
            inv.add(level, contract.item(), removed);
         }
         return false;
      }
      if (contract.reservationId() != null) {
         data.ledger().markInTransit(contract.reservationId());
      }
      return true;
   }

   private static boolean deliverToDest(ServerLevel level, Village dest, LogisticsSavedData data, DeliveryContract contract) {
      BuildingInstance th = dest.getTownhall();
      BuildingInventory inv = th != null ? th.getInventory() : null;
      if (inv == null) {
         return false;
      }
      inv.add(level, contract.item(), contract.amount());
      return true;
   }

   private static void cancel(LogisticsSavedData data, DeliveryContract contract, String reason) {
      if (contract.reservationId() != null) {
         data.ledger().release(contract.reservationId());
         contract.setReservationId(null);
      }
      contract.setState(ContractState.CANCELLED);
      contract.setReason(reason);
   }

   private static long estimateTravelTicks(Village a, Village b) {
      BlockPos pa = a.getTownhall() != null ? a.getTownhall().getOrigin() : a.getCenter();
      BlockPos pb = b.getTownhall() != null ? b.getTownhall().getOrigin() : b.getCenter();
      if (pa == null || pb == null) {
         return DEFAULT_TRAVEL_TICKS;
      }
      double dist = Math.sqrt(pa.distSqr(pb));
      // ~1 block / 4 ticks abstract; clamp 1 min .. 20 min at 20 TPS
      long ticks = (long)Math.max(1200L, Math.min(24000L, dist * 4.0));
      return ticks;
   }

   private static int countOpenFor(LogisticsSavedData data, UUID villageId) {
      int n = 0;
      for (DeliveryContract c : data.contracts().values()) {
         if (c.isOpen() && (c.sourceVillageId().equals(villageId) || c.destVillageId().equals(villageId))) {
            n++;
         }
      }
      return n;
   }

   private static void pruneClosed(LogisticsSavedData data) {
      Iterator<Map.Entry<UUID, DeliveryContract>> it = data.contracts().entrySet().iterator();
      int removed = 0;
      while (it.hasNext() && removed < 64) {
         DeliveryContract c = it.next().getValue();
         if (!c.isOpen()) {
            it.remove();
            removed++;
         }
      }
      if (removed > 0) {
         data.setDirty();
      }
   }

   @Nullable
   private static Village findVillage(VillageManager manager, UUID villageId) {
      for (Village v : manager.getAllVillages()) {
         if (v.getId().uuid().equals(villageId)) {
            return v;
         }
      }
      return null;
   }

   public static List<DeliveryContract> openContractsFor(ServerLevel level, UUID villageId) {
      LogisticsSavedData data = LogisticsSavedData.get(level);
      List<DeliveryContract> out = new ArrayList<>();
      for (DeliveryContract c : data.contracts().values()) {
         if (c.isOpen() && (c.sourceVillageId().equals(villageId) || c.destVillageId().equals(villageId))) {
            out.add(c);
         }
      }
      return out;
   }
}
