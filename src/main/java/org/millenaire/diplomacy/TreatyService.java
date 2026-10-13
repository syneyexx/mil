package org.millenaire.diplomacy;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.logistics.ContractState;
import org.millenaire.logistics.DeliveryContract;
import org.millenaire.logistics.LogisticsService;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Village-level treaties (culture ≠ empire). Evaluation limited to contacts / neighbors / active treaties.
 */
public final class TreatyService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long DEFAULT_DURATION = 24000L * 10L;
   private static final int EVAL_BUDGET = 6;

   private TreatyService() {
   }

   public static void tick(ServerLevel level) {
      TreatySavedData data = TreatySavedData.get(level);
      CommitBudget budget = CommitBudget.get();
      long gameTime = level.getGameTime();
      int evaluated = 0;
      for (Treaty treaty : data.all()) {
         if (evaluated >= EVAL_BUDGET || !budget.trySpendCandidate(1)) {
            break;
         }
         evaluated++;
         if (!treaty.active()) {
            continue;
         }
         if (gameTime >= treaty.expiresGameTime()) {
            treaty.expire();
            data.setDirty();
            continue;
         }
         if (treaty.type() == TreatyType.TRADE_PACT) {
            evaluateTradePact(level, data, treaty);
         }
      }
   }

   public static Treaty proposeTradePact(ServerLevel level, Village a, Village b, Item item, int amount, long gameTime) {
      if (a == null || b == null || a.getId().equals(b.getId())) {
         return null;
      }
      TreatySavedData data = TreatySavedData.get(level);
      Treaty existing = data.findActive(a.getId().uuid(), b.getId().uuid(), TreatyType.TRADE_PACT);
      if (existing != null) {
         return existing;
      }
      Treaty treaty = new Treaty(
         UUID.randomUUID(),
         a.getId().uuid(),
         b.getId().uuid(),
         TreatyType.TRADE_PACT,
         gameTime,
         gameTime + DEFAULT_DURATION
      );
      treaty.setTradeObligation(item, amount);
      data.put(treaty);
      a.recordChronicleEvent(level, VillageEventType.TREATY, "Trade pact with " + b.getVillageName(), item.toString());
      b.recordChronicleEvent(level, VillageEventType.TREATY, "Trade pact with " + a.getVillageName(), item.toString());
      // Kick a logistics contract to carry the obligation
      LogisticsService.requestTradeContract(level, a, b.getId().uuid(), item, amount, gameTime);
      LOGGER.debug("[Treaty] TRADE_PACT {} <-> {}", a.getVillageName(), b.getVillageName());
      return treaty;
   }

   public static Treaty proposeNonAggression(ServerLevel level, Village a, Village b, long gameTime) {
      if (a == null || b == null) {
         return null;
      }
      TreatySavedData data = TreatySavedData.get(level);
      Treaty existing = data.findActive(a.getId().uuid(), b.getId().uuid(), TreatyType.NON_AGGRESSION);
      if (existing != null) {
         return existing;
      }
      Treaty treaty = new Treaty(
         UUID.randomUUID(),
         a.getId().uuid(),
         b.getId().uuid(),
         TreatyType.NON_AGGRESSION,
         gameTime,
         gameTime + DEFAULT_DURATION
      );
      data.put(treaty);
      a.recordChronicleEvent(level, VillageEventType.TREATY, "Non-aggression with " + b.getVillageName(), null);
      return treaty;
   }

   /** Returns true if a NAP/treaty blocks culture-wide auto war between these villages. */
   public static boolean blocksWar(ServerLevel level, UUID villageA, UUID villageB) {
      TreatySavedData data = TreatySavedData.get(level);
      Treaty nap = data.findActive(villageA, villageB, TreatyType.NON_AGGRESSION);
      return nap != null && nap.active();
   }

   public static void onWarDeclared(ServerLevel level, UUID villageA, UUID villageB, String reason) {
      TreatySavedData data = TreatySavedData.get(level);
      for (Treaty t : data.all()) {
         if (t.active() && t.isBetween(villageA, villageB)) {
            if (t.type() == TreatyType.NON_AGGRESSION || t.type() == TreatyType.TRADE_PACT) {
               t.markBreached(reason == null ? "war_declared" : reason);
               data.setDirty();
            }
         }
      }
   }

   private static void evaluateTradePact(ServerLevel level, TreatySavedData data, Treaty treaty) {
      boolean blocked = false;
      boolean delivered = false;
      for (DeliveryContract c : LogisticsService.openContractsFor(level, treaty.villageA())) {
         if (c.destVillageId().equals(treaty.villageB()) || c.sourceVillageId().equals(treaty.villageB())) {
            if (c.state() == ContractState.BLOCKED) {
               blocked = true;
               // Blockade ≠ automatic betrayal
               treaty.adjustTrust(-2);
               treaty.setLastReason("delivery_blocked:" + c.reason());
            } else if (c.state() == ContractState.DONE || c.state() == ContractState.ACTIVE) {
               delivered = true;
            }
         }
      }
      for (DeliveryContract c : LogisticsService.openContractsFor(level, treaty.villageB())) {
         if (c.state() == ContractState.DONE && c.destVillageId().equals(treaty.villageA())) {
            delivered = true;
         }
      }
      if (delivered) {
         treaty.adjustTrust(3);
      }
      if (blocked && treaty.trust() <= 10) {
         treaty.markBreached("repeated_failures");
      }
      data.setDirty();
   }

   /** Auto-suggest trade pacts from successful economy trade choices (neighbors only). */
   public static void maybeOfferFromEconomy(
      ServerLevel level,
      Village buyer,
      UUID sellerId,
      Item item,
      int amount,
      long gameTime
   ) {
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      Village seller = null;
      for (Village v : manager.getAllVillages()) {
         if (v.getId().uuid().equals(sellerId)) {
            seller = v;
            break;
         }
      }
      if (seller == null) {
         return;
      }
      int relation = buyer.getRelation(seller.getId());
      if (relation < 0) {
         return;
      }
      proposeTradePact(level, buyer, seller, item, amount, gameTime);
   }
}
