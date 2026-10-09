package org.millenaire.civilization.economy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;
import org.millenaire.faction.economy.EconomicResourceId;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Conserved physical settlement-to-settlement transfer using concrete ItemStacks.
 * Incomplete deliveries keep unique persistent operations — never overwrite another txn's debt.
 * Intra-faction movement is logistics (profile refresh), not diplomatic trade booking.
 *
 * <p>Lifecycle: persist op identity → withdraw → deliver → compensate → finalize (book + event).
 * Finalization-only ops remain recoverable even when pending lists are empty.
 * Crash boundary: setDirty does not immediately flush; recovery is best-effort idempotent.
 */
public final class SettlementTradeExecutor {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_TRADE_RECOVERY_OPS = 4;
   private static final int MAX_TRADE_RECOVERY_STACKS = 16;
   private static final int MAX_TRADE_RECOVERY_INSERTS = 24;
   private static final int MAX_RECOVERY_ATTEMPTS_SOFT = 64;
   private static final String LEGACY_MARKER_PREFIX = "trade-recovery:";

   private SettlementTradeExecutor() {
   }

   public static boolean tryTransferDeficitItem(
      ServerLevel level,
      Village exporter,
      Village importer,
      EconomicResourceId resourceId,
      int requested
   ) {
      if (level == null || exporter == null || importer == null || resourceId == null || requested <= 0) {
         return false;
      }
      Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.getHolder(resourceId.id());
      if (holder.isEmpty()) {
         return false;
      }
      Item item = holder.get().value();

      FactionId exportFaction = exporter.getFactionId();
      FactionId importFaction = importer.getFactionId();
      if (exportFaction == null || importFaction == null) {
         return false;
      }
      boolean sameFaction = exportFaction.equals(importFaction);
      if (!sameFaction) {
         DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
         if (!diplomacy.canTrade(exportFaction, importFaction)) {
            return false;
         }
         if (WarSavedData.get(level).getWarManager().areAtWar(exportFaction, importFaction)) {
            return false;
         }
      }

      int available = countExportable(level, exporter, item);
      int amount = Math.min(requested, available);
      if (amount <= 0) {
         return false;
      }
      if (importer.getTownhall() == null || importer.getTownhall().getInventory() == null) {
         return false;
      }

      CivilizationSavedData civ = CivilizationSavedData.get(level);
      String daySuccessKey = "trade-ok:"
         + exporter.getId().uuid()
         + ":"
         + importer.getId().uuid()
         + ":"
         + resourceId.id()
         + ":"
         + (level.getGameTime() / 24000L);
      if (civ.isPublished(daySuccessKey, level.getGameTime(), 24000L)) {
         return false;
      }
      if (hasOpenTradeLane(civ, exporter.getId().uuid(), importer.getId().uuid(), resourceId.id().toString())) {
         // Unfinished work on this lane must resume via processOutstanding — never start a second withdrawal.
         return false;
      }

      // Persist operation identity before the first physical mutation so recovery evidence exists.
      SettlementTradeOperation op = new SettlementTradeOperation(
         UUID.randomUUID(),
         exporter.getId().uuid(),
         importer.getId().uuid(),
         resourceId.id().toString(),
         level.getGameTime(),
         sameFaction
      );
      civ.putTradeOperation(op);

      List<ItemStack> removed = removeExactFromVillage(level, exporter, item, amount);
      int removedCount = removed.stream().mapToInt(ItemStack::getCount).sum();
      if (removedCount <= 0) {
         op.setStatus(SettlementTradeOperation.Status.FAILED);
         op.setBlockReason(SettlementTradeOperation.BlockReason.SOURCE_MISSING);
         op.touch(level.getGameTime());
         civ.putTradeOperation(op);
         return false;
      }

      op.acceptWithdrawn(removed);
      civ.putTradeOperation(op);

      DeliveryOutcome delivery = deliverExact(level, importer, op.pendingDelivery());
      op.addDeliveredCount(delivery.placed());
      op.touch(level.getGameTime());

      if (!op.pendingDelivery().isEmpty()) {
         // Keep delivered goods; compensate undelivered remainder with the same concrete stacks.
         op.pendingCompensation().addAll(copyStacks(op.pendingDelivery()));
         op.pendingDelivery().clear();
         CompensationOutcome compensation = compensateExact(level, exporter, op.pendingCompensation());
         op.addCompensatedCount(compensation.placed());
         if (!op.pendingCompensation().isEmpty()) {
            op.setStatus(SettlementTradeOperation.Status.COMPENSATING);
            op.setBlockReason(SettlementTradeOperation.BlockReason.DESTINATION_FULL);
            civ.putTradeOperation(op);
            LOGGER.warn(
               "[Millenaire] Trade compensation incomplete for op {} ({} remaining stacks) from {} to {}",
               op.operationId(),
               op.pendingCompensation().size(),
               exporter.getId().uuid(),
               importer.getId().uuid()
            );
            return false;
         }
         if (op.deliveredCount() <= 0) {
            op.setStatus(SettlementTradeOperation.Status.FAILED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.DESTINATION_FULL);
            civ.putTradeOperation(op);
            return false;
         }
         // Partial delivery with full compensation of the rest — book only delivered amount.
      }

      op.setStatus(SettlementTradeOperation.Status.DELIVERED);
      civ.putTradeOperation(op);
      return finalizeSuccessfulTransfer(level, civ, op, exporter, importer, item);
   }

   /**
    * Bounded recovery of outstanding trade compensation / partial deliveries / finalization-only ops.
    * Failed insert attempts consume budget. Does not invent stacks from legacy markers.
    */
   public static void processOutstanding(ServerLevel level, CivilizationSavedData civ) {
      if (level == null || civ == null) {
         return;
      }
      migrateLegacyMarkers(level, civ);

      List<SettlementTradeOperation> work = new ArrayList<>();
      for (SettlementTradeOperation op : civ.tradeOperationsView()) {
         if (op.needsWork()) {
            work.add(op);
         }
      }
      if (work.isEmpty()) {
         return;
      }
      work.sort((a, b) -> a.operationId().compareTo(b.operationId()));
      int n = work.size();
      int start = Math.floorMod(civ.tradeRecoveryCursor(), n);
      int examined = 0;
      int stacksWorked = 0;
      int inserts = 0;

      for (int i = 0; i < n && examined < MAX_TRADE_RECOVERY_OPS && stacksWorked < MAX_TRADE_RECOVERY_STACKS && inserts < MAX_TRADE_RECOVERY_INSERTS; i++) {
         SettlementTradeOperation op = work.get(Math.floorMod(start + i, n));
         examined++;
         op.incrementRecoveryAttempts();
         if (op.recoveryAttempts() > MAX_RECOVERY_ATTEMPTS_SOFT
            && op.pendingDelivery().isEmpty()
            && op.pendingCompensation().isEmpty()
            && !op.needsFinalization()) {
            op.setStatus(SettlementTradeOperation.Status.RECOVERY_REQUIRED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.AMBIGUOUS);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            inserts++;
            continue;
         }
         if (op.decodeUnresolved()
            && op.pendingDelivery().isEmpty()
            && op.pendingCompensation().isEmpty()
            && !op.needsFinalization()) {
            // Keep unresolved evidence; do not invent goods or mark COMPLETE.
            op.setStatus(SettlementTradeOperation.Status.RECOVERY_REQUIRED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.DECODE_UNRESOLVED);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            inserts++;
            continue;
         }

         Village exporter = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.exporterVillageId()));
         Village importer = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.importerVillageId()));
         if (exporter == null) {
            op.setStatus(SettlementTradeOperation.Status.RECOVERY_REQUIRED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.SOURCE_MISSING);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            inserts++;
            continue;
         }
         if (importer == null && !op.pendingDelivery().isEmpty()) {
            op.setStatus(SettlementTradeOperation.Status.RECOVERY_REQUIRED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.DESTINATION_MISSING);
            op.pendingCompensation().addAll(copyStacks(op.pendingDelivery()));
            op.pendingDelivery().clear();
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            inserts++;
            continue;
         }

         // Delivery phase — never re-withdraw.
         if (!op.pendingDelivery().isEmpty() && importer != null) {
            int before = countStacks(op.pendingDelivery());
            int cursor = Math.min(op.refundCursor(), Math.max(0, op.pendingDelivery().size() - 1));
            DeliveryOutcome outcome = deliverExactFromCursor(level, importer, op.pendingDelivery(), cursor, MAX_TRADE_RECOVERY_INSERTS - inserts);
            inserts += Math.max(1, outcome.attempts());
            stacksWorked += Math.max(1, before - countStacks(op.pendingDelivery()) + (outcome.placed() > 0 ? 1 : 0));
            op.addDeliveredCount(outcome.placed());
            op.setRefundCursor(outcome.nextCursor());
            if (!op.pendingDelivery().isEmpty()) {
               op.setStatus(SettlementTradeOperation.Status.IN_TRANSIT);
               op.setBlockReason(SettlementTradeOperation.BlockReason.DESTINATION_FULL);
               op.touch(level.getGameTime());
               civ.putTradeOperation(op);
               continue;
            }
         }

         // Compensation phase — only the real remainder.
         if (!op.pendingCompensation().isEmpty()) {
            int before = countStacks(op.pendingCompensation());
            int cursor = Math.min(op.refundCursor(), Math.max(0, op.pendingCompensation().size() - 1));
            CompensationOutcome outcome = compensateExactFromCursor(level, exporter, op.pendingCompensation(), cursor, MAX_TRADE_RECOVERY_INSERTS - inserts);
            inserts += Math.max(1, outcome.attempts());
            stacksWorked += Math.max(1, before - countStacks(op.pendingCompensation()) + (outcome.placed() > 0 ? 1 : 0));
            op.addCompensatedCount(outcome.placed());
            op.setRefundCursor(outcome.nextCursor());
            if (!op.pendingCompensation().isEmpty()) {
               op.setStatus(SettlementTradeOperation.Status.COMPENSATING);
               op.setBlockReason(SettlementTradeOperation.BlockReason.SOURCE_FULL);
               op.touch(level.getGameTime());
               civ.putTradeOperation(op);
               continue;
            }
         }

         // Finalization-only: pending empty, physical transfer done, booking/event may be missing.
         if (op.deliveredCount() > 0 && op.pendingDelivery().isEmpty() && op.pendingCompensation().isEmpty()) {
            op.setStatus(SettlementTradeOperation.Status.DELIVERED);
            Item item = resolveItem(op.resourceId());
            boolean ok = finalizeSuccessfulTransfer(level, civ, op, exporter, importer, item);
            if (!ok && op.status() != SettlementTradeOperation.Status.COMPLETE) {
               // Keep DELIVERED / RECOVERY so a later pass can retry booking — never re-deliver.
               if (op.status() != SettlementTradeOperation.Status.FAILED) {
                  op.setStatus(SettlementTradeOperation.Status.DELIVERED);
               }
               civ.putTradeOperation(op);
            }
         } else if (op.deliveredCount() <= 0 && op.pendingDelivery().isEmpty() && op.pendingCompensation().isEmpty()) {
            op.setStatus(SettlementTradeOperation.Status.FAILED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.NONE);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
         }
      }
      civ.setTradeRecoveryCursor(start + Math.max(1, examined));
   }

   private static boolean hasOpenTradeLane(CivilizationSavedData civ, UUID exporter, UUID importer, String resourceId) {
      for (SettlementTradeOperation op : civ.tradeOperationsView()) {
         if (!op.holdsEscrowOrEvidence()) {
            continue;
         }
         if (op.status() == SettlementTradeOperation.Status.COMPLETE
            || op.status() == SettlementTradeOperation.Status.FAILED
            || op.status() == SettlementTradeOperation.Status.LEGACY_UNRECOVERABLE) {
            continue;
         }
         if (op.exporterVillageId().equals(exporter)
            && op.importerVillageId().equals(importer)
            && op.resourceId().equals(resourceId)) {
            return true;
         }
      }
      return false;
   }

   private static void migrateLegacyMarkers(ServerLevel level, CivilizationSavedData civ) {
      List<String> keys = new ArrayList<>();
      for (Map.Entry<String, String> e : civ.stateMarkersView().entrySet()) {
         if (e.getKey() != null && e.getKey().startsWith(LEGACY_MARKER_PREFIX)) {
            keys.add(e.getKey());
         }
      }
      for (String key : keys) {
         String value = civ.getStateMarker(key);
         String[] parts = key.substring(LEGACY_MARKER_PREFIX.length()).split(":", 2);
         if (parts.length < 2) {
            continue;
         }
         UUID exporterId;
         try {
            exporterId = UUID.fromString(parts[0]);
         } catch (IllegalArgumentException ex) {
            continue;
         }
         int lost = 0;
         try {
            lost = Integer.parseInt(value == null ? "0" : value);
         } catch (NumberFormatException ignored) {
         }
         SettlementTradeOperation legacy = SettlementTradeOperation.legacyFromMarker(exporterId, parts[1], lost, level.getGameTime());
         civ.putTradeOperation(legacy);
         civ.removeStateMarker(key);
         LOGGER.warn(
            "[Millenaire] Legacy trade-recovery marker retained as unrecoverable op {} (resource={}, claimedLost={})",
            legacy.operationId(),
            parts[1],
            lost
         );
      }
   }

   private static boolean finalizeSuccessfulTransfer(
      ServerLevel level,
      CivilizationSavedData civ,
      SettlementTradeOperation op,
      Village exporter,
      Village importer,
      Item item
   ) {
      int booked = op.deliveredCount();
      if (booked <= 0) {
         op.setStatus(SettlementTradeOperation.Status.FAILED);
         civ.putTradeOperation(op);
         return false;
      }
      if (!op.pendingDelivery().isEmpty() || !op.pendingCompensation().isEmpty()) {
         return false;
      }

      boolean newlyBooked = false;
      if (!op.economyBooked()) {
         if (exporter == null) {
            op.setStatus(SettlementTradeOperation.Status.RECOVERY_REQUIRED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.SOURCE_MISSING);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            return false;
         }
         Map<Item, Integer> transferred = new LinkedHashMap<>();
         if (item != null) {
            transferred.put(item, booked);
         }
         TradeManager trade = EconomySavedData.get(level).getTradeManager();
         if (op.sameFactionLogistics()) {
            FactionId fid = exporter.getFactionId();
            Faction faction = fid == null ? null : FactionSavedData.get(level).getFactionManager().getFaction(fid);
            if (faction != null) {
               trade.refreshProfile(level, faction, level.getGameTime());
            }
         } else if (!transferred.isEmpty()) {
            trade.recordSettlementGoodsTransfer(level, exporter, importer, transferred);
         }
         op.setEconomyBooked(true);
         newlyBooked = true;
      }

      String effectsKey = op.effectsKey();
      String dayKey = op.daySuccessKey();
      // Effects dedupe is per operation; day key only blocks a fresh withdrawal lane.
      if (!civ.isPublished(effectsKey, level.getGameTime(), 24000L * 8L)) {
         civ.tryMarkPublished(effectsKey, level.getGameTime(), 24000L * 8L);
         if (newlyBooked) {
            CivilizationMetrics.TRADE_OPS.incrementAndGet();
         }
      }
      civ.tryMarkPublished(dayKey, level.getGameTime(), 24000L);

      if (!op.completionEventPublished()) {
         if (importer == null) {
            op.setStatus(SettlementTradeOperation.Status.DELIVERED);
            op.setBlockReason(SettlementTradeOperation.BlockReason.DESTINATION_MISSING);
            op.touch(level.getGameTime());
            civ.putTradeOperation(op);
            return false;
         }
         var emitted = LivingEventService.emit(
            level,
            LivingEventType.TRADE_COMPLETED,
            LivingEventSource.SYSTEM,
            importer,
            null,
            null,
            (op.sameFactionLogistics() ? "logistics=" : "trade=") + op.resourceId() + " qty=" + booked + " from=" + op.exporterVillageId(),
            "public",
            effectsKey
         );
         // null: living disabled, journal dedupe (already emitted), or tick budget.
         // Dedupe / disabled must not block COMPLETE; budget can retry next tick.
         if (emitted == null) {
            boolean livingOn = true;
            try {
               livingOn = org.millenaire.config.MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
                  && org.millenaire.config.MillenaireServerConfig.SERVER.npcEventMemoryEnabled.get();
            } catch (IllegalStateException ignored) {
            }
            boolean alreadyChronicled = false;
            if (livingOn) {
               try {
                  for (var recent : LivingEventService.recentPublic(level, importer.getId(), 32)) {
                     if (effectsKey.equals(recent.dedupeKey())) {
                        alreadyChronicled = true;
                        break;
                     }
                  }
               } catch (Exception ignored) {
               }
            }
            if (livingOn && !alreadyChronicled) {
               op.setStatus(SettlementTradeOperation.Status.DELIVERED);
               op.touch(level.getGameTime());
               civ.putTradeOperation(op);
               return false;
            }
         }
         op.setCompletionEventPublished(true);
      }

      op.setStatus(SettlementTradeOperation.Status.COMPLETE);
      op.setBlockReason(SettlementTradeOperation.BlockReason.NONE);
      op.touch(level.getGameTime());
      civ.putTradeOperation(op);
      return true;
   }

   public static int countExportable(ServerLevel level, Village village, Item item) {
      int total = village.getVillageItemCount(level, item);
      int reserve = Math.min(8, Math.max(0, total / 4));
      return Math.max(0, total - reserve);
   }

   private static List<ItemStack> removeExactFromVillage(ServerLevel level, Village village, Item item, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (BuildingInstance building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         inv.invalidateCacheOncePerTick(level.getGameTime());
         BuildingInventory.RemoveStacksResult part = inv.removeByItemExact(level, item, left);
         taken.addAll(part.stacks());
         left = part.unsatisfied();
      }
      return taken;
   }

   private static DeliveryOutcome deliverExact(ServerLevel level, Village village, List<ItemStack> stacks) {
      return deliverExactFromCursor(level, village, stacks, 0, Integer.MAX_VALUE);
   }

   private static DeliveryOutcome deliverExactFromCursor(
      ServerLevel level,
      Village village,
      List<ItemStack> stacks,
      int startCursor,
      int maxAttempts
   ) {
      int placed = 0;
      int attempts = 0;
      if (stacks.isEmpty() || maxAttempts <= 0) {
         return new DeliveryOutcome(0, 0, 0);
      }
      int n = stacks.size();
      int cursor = Math.floorMod(startCursor, n);
      int visited = 0;
      while (visited < n && attempts < maxAttempts && !stacks.isEmpty()) {
         if (cursor >= stacks.size()) {
            cursor = 0;
         }
         ItemStack stack = stacks.get(cursor);
         attempts++;
         visited++;
         if (stack == null || stack.isEmpty()) {
            stacks.remove(cursor);
            if (stacks.isEmpty()) {
               break;
            }
            cursor = cursor % stacks.size();
            continue;
         }
         int before = stack.getCount();
         ItemStack remaining = addExactToVillage(level, village, stack);
         int moved = before - remaining.getCount();
         placed += Math.max(0, moved);
         if (remaining.isEmpty()) {
            stacks.remove(cursor);
            if (stacks.isEmpty()) {
               break;
            }
            cursor = cursor % stacks.size();
         } else {
            stacks.set(cursor, remaining);
            cursor = (cursor + 1) % stacks.size();
            if (moved <= 0) {
               break;
            }
         }
      }
      return new DeliveryOutcome(placed, attempts, stacks.isEmpty() ? 0 : cursor);
   }

   private static CompensationOutcome compensateExact(ServerLevel level, Village village, List<ItemStack> stacks) {
      return compensateExactFromCursor(level, village, stacks, 0, Integer.MAX_VALUE);
   }

   private static CompensationOutcome compensateExactFromCursor(
      ServerLevel level,
      Village village,
      List<ItemStack> stacks,
      int startCursor,
      int maxAttempts
   ) {
      DeliveryOutcome d = deliverExactFromCursor(level, village, stacks, startCursor, maxAttempts);
      return new CompensationOutcome(d.placed(), d.attempts(), d.nextCursor());
   }

   private static ItemStack addExactToVillage(ServerLevel level, Village village, ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return ItemStack.EMPTY;
      }
      ItemStack remaining = stack.copy();
      for (BuildingInstance building : village.getBuildings()) {
         if (remaining.isEmpty()) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         inv.invalidateCacheOncePerTick(level.getGameTime());
         BuildingInventory.AddStackResult result = inv.addExactStack(level, remaining);
         remaining = result.remainder();
         if (result.placed() > 0) {
            inv.invalidateCache();
         }
      }
      return remaining;
   }

   private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
      List<ItemStack> out = new ArrayList<>(stacks.size());
      for (ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty()) {
            out.add(stack.copy());
         }
      }
      return out;
   }

   private static int countStacks(List<ItemStack> stacks) {
      int n = 0;
      for (ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty()) {
            n++;
         }
      }
      return n;
   }

   @javax.annotation.Nullable
   private static Item resolveItem(String resourceId) {
      if (resourceId == null || resourceId.isBlank()) {
         return null;
      }
      try {
         net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(resourceId);
         return id == null ? null : BuiltInRegistries.ITEM.get(id);
      } catch (Exception e) {
         return null;
      }
   }

   private record DeliveryOutcome(int placed, int attempts, int nextCursor) {
   }

   private record CompensationOutcome(int placed, int attempts, int nextCursor) {
   }
}
