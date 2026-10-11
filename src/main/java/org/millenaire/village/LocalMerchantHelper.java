package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public final class LocalMerchantHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   static final double MAX_MERCHANT_DISTANCE = 2000.0;
   static final int MIN_NIGHTS_BEFORE_MOVE = 2;
   static final int NIGHTS_BEFORE_BACKUP = 3;
   private static final long IMPORTS_CACHE_TTL_MS = 60000L;

   private LocalMerchantHelper() {
   }

   public static void attemptMerchantMoves(ServerLevel level, Village village) {
      List<BuildingInstance> inns = village.getBuildingsWithTag("inn");
      if (!inns.isEmpty()) {
         VillageManager vm = VillageSavedData.get(level).getVillageManager();
         LOGGER.debug("[Millenaire] [LocalMerchant] {} — checking {} inn(s) for merchant moves", village.getVillageName(), inns.size());

         for (BuildingInstance inn : inns) {
            VillagerRecord merchant = getMerchantRecord(village, inn);
            if (merchant == null) {
               LOGGER.debug("[Millenaire] [LocalMerchant] {} — no merchant record in inn {}", village.getVillageName(), inn.getId());
            } else {
               int nbNights = inn.incrementAndGetMerchantNights();
               if (nbNights >= 2) {
                  attemptMerchantMoveFromInn(level, village, inn, merchant, vm, false);
               }
            }
         }
      }
   }

   public static void forceAttemptMerchantMoves(ServerLevel level, Village village) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();

      for (BuildingInstance building : village.getBuildingsWithTag("inn")) {
         VillagerRecord merchant = getMerchantRecord(village, building);
         if (merchant != null) {
            attemptMerchantMoveFromInn(level, village, building, merchant, vm, true);
         }
      }
   }

   static void attemptMerchantMoveFromInn(ServerLevel level, Village village, BuildingInstance inn, VillagerRecord merchant, VillageManager vm, boolean forced) {
      List<LocalMerchantHelper.WeightedTarget> targets = new ArrayList<>();
      List<LocalMerchantHelper.WeightedTarget> backupTargets = new ArrayList<>();
      Map<Item, Integer> innContents = getInnContents(level, inn);

      for (Entry<VillageId, Integer> rel : village.getRelations().entrySet()) {
         if (rel.getValue() >= 0) {
            Village other = vm.getVillage(rel.getKey());
            if (other != null && other != village && other.isActive() && !other.getVillageTypeId().equals(village.getVillageTypeId())) {
               double distance = linearDistance(village.getCenter(), other.getCenter());
               if (!(distance >= 2000.0)) {
                  for (BuildingInstance destInn : other.getBuildingsWithTag("inn")) {
                     boolean moveNeeded = false;

                     for (Entry<Item, Integer> content : innContents.entrySet()) {
                        if (content.getValue() > 0 && nbGoodNeeded(level, other, content.getKey()) > 0) {
                           moveNeeded = true;
                           break;
                        }
                     }

                     if (moveNeeded) {
                        VillagerRecord destMerchant = getMerchantRecord(other, destInn);
                        if (destMerchant == null) {
                           targets.add(new LocalMerchantHelper.WeightedTarget(destInn, other));
                           targets.add(new LocalMerchantHelper.WeightedTarget(destInn, other));
                           targets.add(new LocalMerchantHelper.WeightedTarget(destInn, other));
                        } else if (destInn.getNbNightsMerchant() > 1 || forced) {
                           targets.add(new LocalMerchantHelper.WeightedTarget(destInn, other));
                        }
                     } else if (inn.getNbNightsMerchant() > 3) {
                        backupTargets.add(new LocalMerchantHelper.WeightedTarget(destInn, other));
                     }
                  }
               }
            }
         }
      }

      if (targets.isEmpty() && backupTargets.isEmpty()) {
         LOGGER.debug(
            "[Millenaire] [LocalMerchant] No destination found for merchant {} (inn={}, nights={}, relations={}, innContents={})",
            new Object[]{merchant.getFirstName(), inn.getId(), inn.getNbNightsMerchant(), village.getRelations().size(), innContents.size()}
         );
      } else {
         LocalMerchantHelper.WeightedTarget target;
         if (!targets.isEmpty()) {
            target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
         } else {
            target = backupTargets.get(ThreadLocalRandom.current().nextInt(backupTargets.size()));
         }

         VillagerRecord destMerchant = getMerchantRecord(target.village(), target.inn());
         if (destMerchant == null) {
            moveMerchant(level, village, inn, merchant, target.village(), target.inn());
         } else if (target.inn().getNbNightsMerchant() > 1 || forced) {
            swapMerchants(level, village, inn, merchant, target.village(), target.inn(), destMerchant);
         }
      }
   }

   private static void moveMerchant(
      ServerLevel level, Village srcVillage, BuildingInstance srcInn, VillagerRecord merchant, Village destVillage, BuildingInstance destInn
   ) {
      transferGoods(level, srcVillage, srcInn, destVillage, destInn);
      if (org.millenaire.worldgen.highway.travel.RoadJourneyScheduler.tryStartMerchantJourney(
         level, srcVillage, destVillage, merchant.getVillagerTypeId(), merchant.getUuid()
      )) {
         // Discard so transferVillagerPermanently rehomes the record without teleporting.
         if (level.getEntity(merchant.getUuid()) instanceof org.millenaire.entity.MillVillager live) {
            live.discard();
         }
         srcVillage.transferVillagerPermanently(level, merchant.getUuid(), destVillage, destInn.getId());
         String merchantName = merchant.getFirstName() + " " + merchant.getFamilyName();
         srcInn.addVisitorLog(
            "panels.merchantmovedout;" + merchantName + ";" + merchant.getRoleName() + ";" + destVillage.getVillageName() + ";" + srcInn.getNbNightsMerchant()
         );
         destInn.addVisitorLog("panels.merchantarrived;" + merchantName + ";" + merchant.getRoleName() + ";" + srcVillage.getVillageName());
         merchant.setInnTradingAnnounced(true);
         srcInn.resetMerchantNights();
         return;
      }
      srcVillage.transferVillagerPermanently(level, merchant.getUuid(), destVillage, destInn.getId());
      String merchantName = merchant.getFirstName() + " " + merchant.getFamilyName();
      srcInn.addVisitorLog(
         "panels.merchantmovedout;" + merchantName + ";" + merchant.getRoleName() + ";" + destVillage.getVillageName() + ";" + srcInn.getNbNightsMerchant()
      );
      destInn.addVisitorLog("panels.merchantarrived;" + merchantName + ";" + merchant.getRoleName() + ";" + srcVillage.getVillageName());
      merchant.setInnTradingAnnounced(true);
      LOGGER.info(
         "[Millenaire] [LocalMerchant] Moved merchant {} from {} to {}", new Object[]{merchantName, srcVillage.getVillageName(), destVillage.getVillageName()}
      );
      srcInn.resetMerchantNights();
   }

   private static void swapMerchants(
      ServerLevel level,
      Village srcVillage,
      BuildingInstance srcInn,
      VillagerRecord srcMerchant,
      Village destVillage,
      BuildingInstance destInn,
      VillagerRecord destMerchant
   ) {
      Map<Item, Integer> srcContents = getInnContents(level, srcInn);
      Map<Item, Integer> destContents = getInnContents(level, destInn);
      transferGoodsFromSnapshot(level, srcVillage, srcInn, destVillage, destInn, srcContents);
      transferGoodsFromSnapshot(level, destVillage, destInn, srcVillage, srcInn, destContents);
      srcVillage.transferVillagerPermanently(level, srcMerchant.getUuid(), destVillage, destInn.getId());
      destVillage.transferVillagerPermanently(level, destMerchant.getUuid(), srcVillage, srcInn.getId());
      String srcName = srcMerchant.getFirstName() + " " + srcMerchant.getFamilyName();
      String destName = destMerchant.getFirstName() + " " + destMerchant.getFamilyName();
      srcInn.addVisitorLog(
         "panels.merchantmovedout;" + srcName + ";" + srcMerchant.getRoleName() + ";" + destVillage.getVillageName() + ";" + srcInn.getNbNightsMerchant()
      );
      destInn.addVisitorLog(
         "panels.merchantmovedout;" + destName + ";" + destMerchant.getRoleName() + ";" + srcVillage.getVillageName() + ";" + destInn.getNbNightsMerchant()
      );
      srcInn.addVisitorLog("panels.merchantarrived;" + destName + ";" + destMerchant.getRoleName() + ";" + destVillage.getVillageName());
      destInn.addVisitorLog("panels.merchantarrived;" + srcName + ";" + srcMerchant.getRoleName() + ";" + srcVillage.getVillageName());
      destMerchant.setInnTradingAnnounced(true);
      srcMerchant.setInnTradingAnnounced(true);
      LOGGER.info(
         "[Millenaire] [LocalMerchant] Swapped merchants {} ({}) and {} ({})",
         new Object[]{srcName, srcVillage.getVillageName(), destName, destVillage.getVillageName()}
      );
      srcInn.resetMerchantNights();
      destInn.resetMerchantNights();
   }

   private static void transferGoods(ServerLevel level, Village srcVillage, BuildingInstance srcInn, Village destVillage, BuildingInstance destInn) {
      Map<Item, Integer> contents = getInnContents(level, srcInn);
      transferGoodsFromSnapshot(level, srcVillage, srcInn, destVillage, destInn, contents);
   }

   private static void transferGoodsFromSnapshot(
      ServerLevel level, Village srcVillage, BuildingInstance srcInn, Village destVillage, BuildingInstance destInn, Map<Item, Integer> contents
   ) {
      BuildingInventory srcInv = srcInn.getInventory();
      BuildingInventory destInv = destInn.getInventory();
      Map<Item, Integer> transferred = new LinkedHashMap<>();
      if (srcInv != null && destInv != null) {
         for (Entry<Item, Integer> entry : contents.entrySet()) {
            Item item = entry.getKey();
            int count = entry.getValue();
            if (count > 0) {
               int taken = srcInv.remove(level, item, count);
               if (taken > 0) {
                  int stored = destInv.add(level, item, taken);
                  if (stored < taken) {
                     int restored = srcInv.add(level, item, taken - stored);
                     if (restored < taken - stored) {
                        LOGGER.warn(
                           "[Millenaire] [LocalMerchant] Lost {} x{} during transfer from {} to {} (destination and source chests unavailable)",
                           new Object[]{taken - stored - restored, item, srcInn.getId(), destInn.getId()}
                        );
                     }
                  }

                  if (stored > 0) {
                     destInn.addImported(item, stored);
                     srcInn.addExported(item, stored);
                     transferred.merge(item, stored, Integer::sum);
                  }
               }
            }
         }
      }

      if (!transferred.isEmpty()) {
         EconomySavedData.get(level).getTradeManager().recordSettlementGoodsTransfer(level, srcVillage, destVillage, transferred);
      }
   }

   @Nullable
   public static VillagerRecord getMerchantRecord(Village village, BuildingInstance inn) {
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (!record.isKilled() && inn.getId().equals(record.getHomeBuilding())) {
            VillagerType vType = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vType != null && vType.hasTag("localmerchant") && !vType.isChild()) {
               return record;
            }
         }
      }

      return null;
   }

   private static Map<Item, Integer> getInnContents(ServerLevel level, BuildingInstance inn) {
      BuildingInventory inv = inn.getInventory();
      return inv == null ? Map.of() : inv.scanChests(level);
   }

   private static double linearDistance(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dy = a.getY() - b.getY();
      double dz = a.getZ() - b.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   static int nbGoodNeeded(ServerLevel level, Village village, Item item) {
      return GoodAvailabilityHelper.nbGoodNeeded(level, village, item);
   }

   public static Map<Item, Integer> getImportsNeededByOtherVillages(ServerLevel level, Village village) {
      long now = System.currentTimeMillis();
      Map<Item, Integer> cached = village.getImportsNeededCache();
      if (cached != null && now < village.getImportsNeededCacheExpiry()) {
         return cached;
      }

      Map<Item, Integer> result = new HashMap<>();
      VillageManager vm = VillageSavedData.get(level).getVillageManager();

      for (Village other : vm.getAllVillages()) {
         if (other != village
            && other.getCultureId().equals(village.getCultureId())
            && !other.getVillageTypeId().equals(village.getVillageTypeId())
            && other.isActive()
            && !other.getBuildingsWithTag("inn").isEmpty()) {
            for (TradeGood good : TradeGoodsLoader.getGoods(other.getCultureId())) {
               if (good.targetQuantity() > 0) {
                  Item item = ItemHelper.resolve(good.item());
                  if (item != null) {
                     int needed = nbGoodNeeded(level, other, item);
                     if (needed > 0) {
                        result.merge(item, needed, Integer::sum);
                     }
                  }
               }
            }
         }
      }

      village.setImportsNeededCache(result, now + 60000L);
      return result;
   }

   private record WeightedTarget(BuildingInstance inn, Village village) {
   }
}
