package org.millenaire.activity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Expedition-backed village raids: real reserved travellers, no legacy clones for the same operation.
 */
public final class ActivityRaidBridge {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ActivityRaidBridge() {}

   public static void tickExpeditionRaid(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, Village source, ActivityOperation op
   ) {
      if (op.raidMode() != RaidParticipantMode.EXPEDITION_MEMBERS) {
         op.setDeferReason(ActivityReasonCode.STALE_RESULT);
         ActivityLifecycleService.releaseAll(data, admission, op);
         op.enterState(ActivityState.CANCELLED, level.getGameTime());
         data.reschedule(op);
         return;
      }
      if (op.targetSettlementId() == null) {
         ActivityLifecycleService.releaseAll(data, admission, op);
         op.enterState(ActivityState.CANCELLED, level.getGameTime());
         data.reschedule(op);
         return;
      }
      Village target = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.targetSettlementId()));
      if (target == null) {
         op.enterState(ActivityState.RETURNING, level.getGameTime());
         data.reschedule(op);
         return;
      }
      // Defeated en route: no members left
      int live = countLive(level, source, op);
      if (live == 0) {
         abortWithoutAttack(level, data, admission, source, target, op);
         return;
      }
      BlockPos defend = RaidManager.resolveDefendingPos(target);
      // Start physical underAttack only when near defending position
      boolean near = false;
      for (UUID id : op.participantIds()) {
         if (level.getEntity(id) instanceof MillVillager mv && mv.blockPosition().distSqr(defend) < 48 * 48) {
            near = true;
            break;
         }
      }
      if (!near) {
         // Keep travelling toward defend
         op.setCoarseRoute(ActivityPlanner.coarseRoute(op.lastValidPos() != null ? op.lastValidPos() : source.getCenter(), defend));
         op.enterState(ActivityState.TRAVELLING, level.getGameTime());
         data.reschedule(op);
         return;
      }
      if (!target.isUnderAttack()) {
         if (!RaidManager.beginExpeditionRaid(source, target, level, op.operationId(), op.participantIds())) {
            op.enterState(ActivityState.RETURNING, level.getGameTime());
            data.reschedule(op);
            return;
         }
         LOGGER.info("[Activity] Expedition raid begun {} -> {}", source.getVillageName(), target.getVillageName());
      }
      // Drive participants to defend pos; existing combat goals handle fighting
      for (UUID id : op.participantIds()) {
         VillagerRecord rec = source.getVillagerRecords().get(id);
         if (rec == null || rec.isKilled()) continue;
         MillVillager mv = ActivityProjectionService.ensurePresent(level, admission, source, rec, defend, op);
         if (mv == null) continue;
         if (mv.blockPosition().distSqr(defend) > 9.0) {
            ActivityNavigationService.get().requestNavigate(level, admission, mv, defend, 0.65, op);
         }
      }
      // Check end conditions using expedition members (no clones)
      int liveAttackers = countLiveNear(level, source, op, defend, 64);
      int liveDefenders = 0;
      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isKilled() || r.isRaidingVillage()) continue;
         VillagerType t = ModCultures.getVillagerType(r.getVillagerTypeId());
         if (t != null && t.isHelpInAttacks()) liveDefenders++;
      }
      boolean raiderAtPos = false;
      for (UUID id : op.participantIds()) {
         if (level.getEntity(id) instanceof MillVillager mv && mv.blockPosition().distSqr(defend) < 25.0) {
            raiderAtPos = true;
            break;
         }
      }
      if (RaidManager.shouldEndRaid(liveAttackers, liveDefenders, raiderAtPos)
         || level.getGameTime() - op.stateEnteredGameTime() > 6000L) {
         finishExpeditionBattle(level, data, admission, source, target, op, liveAttackers > 0 && liveDefenders == 0);
      } else {
         op.setNextDueGameTime(level.getGameTime() + 20L);
         data.reschedule(op);
      }
   }

   private static void abortWithoutAttack(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, Village source, Village target, ActivityOperation op
   ) {
      if (target.isUnderAttack() && op.operationId().equals(source.getExpeditionOperationId())) {
         target.setUnderAttack(false);
         source.clearRaid();
         source.clearExpeditionRaid();
      }
      long cd = MillenaireServerConfig.SERVER.regionalActivityTargetCooldownTicks.getAsInt();
      data.setTargetCooldown(target.getId().uuid().toString(), level.getGameTime() + cd);
      op.enterState(ActivityState.RETURNING, level.getGameTime());
      data.reschedule(op);
      LOGGER.info("[Activity] Expedition raid aborted before arrival: {}", op.operationId());
   }

   private static void finishExpeditionBattle(
      ServerLevel level,
      ActivitySavedData data,
      ActivityAdmissionController admission,
      Village source,
      Village target,
      ActivityOperation op,
      boolean attackersWonHint
   ) {
      if (op.outcomeApplied()) {
         return;
      }
      boolean won = attackersWonHint || RaidManager.resolveAttackersWin(
         target.getVillageDefendingStrength(),
         Math.max(1, op.initialMemberCount() - op.lostMembers()),
         1.0F,
         1.2F
      );
      if (won) {
         applyLimitedLoot(level, source, target, op);
         source.addRaidsPerformed("expedition_success;" + target.getVillageName());
         target.addRaidsSuffered("expedition_success;" + source.getVillageName());
      } else {
         source.addRaidsPerformed("expedition_failure;" + target.getVillageName());
         target.addRaidsSuffered("expedition_failure;" + source.getVillageName());
      }
      long tcd = MillenaireServerConfig.SERVER.regionalActivityTargetCooldownTicks.getAsInt();
      long jitter = op.seed() & 0x7FF;
      data.setTargetCooldown(target.getId().uuid().toString(), level.getGameTime() + tcd + jitter);
      target.setUnderAttack(false);
      source.clearRaid();
      source.clearExpeditionRaid();
      op.enterState(ActivityState.RETURNING, level.getGameTime());
      data.reschedule(op);
      source.markDirty();
      target.markDirty();
   }

   public static void finishExpeditionIfNeeded(ServerLevel level, ActivitySavedData data, Village source, ActivityOperation op) {
      if (op.targetSettlementId() == null) return;
      Village target = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.targetSettlementId()));
      if (target != null && target.isUnderAttack() && op.operationId().equals(source.getExpeditionOperationId())) {
         target.setUnderAttack(false);
         source.clearRaid();
         source.clearExpeditionRaid();
         target.markDirty();
      }
   }

   private static void applyLimitedLoot(ServerLevel level, Village attacker, Village target, ActivityOperation op) {
      if (op.cargoAmbiguous()) {
         return;
      }
      BuildingInstance th = attacker.getTownhall();
      if (th == null || th.getInventory() == null) {
         return;
      }
      int maxItems = MillenaireServerConfig.SERVER.regionalActivityRaidLootAbsoluteMax.getAsInt();
      int percent = MillenaireServerConfig.SERVER.regionalActivityRaidLootPercent.getAsInt();
      int taken = 0;
      // Prefer townhall stock only — never scan all chests city-wide
      BuildingInstance targetTh = target.getTownhall();
      if (targetTh == null || targetTh.getInventory() == null) {
         return;
      }
      BuildingInventory inv = targetTh.getInventory();
      var contents = inv.scanChests(level);
      int totalAvailable = 0;
      for (int v : contents.values()) totalAvailable += v;
      int budget = Math.min(maxItems, Math.max(0, totalAvailable * percent / 100));
      if (budget <= 0) return;
      String transferId = op.operationId() + ":raidloot";
      op.setTransferId(transferId);
      for (var e : contents.entrySet()) {
         if (taken >= budget) break;
         Item item = e.getKey();
         if (item == null) continue;
         // Skip special/quest-ish by refusing named custom data-heavy stacks via count-only inventory API
         int want = Math.min(e.getValue(), budget - taken);
         int removed = inv.remove(level, item, want);
         if (removed > 0) {
            int added = th.getInventory().add(level, item, removed);
            taken += added;
            if (added < removed) {
               inv.add(level, item, removed - added);
               op.setCargoAmbiguous(true);
               break;
            }
         }
      }
      LOGGER.info("[Activity] Expedition loot {} items {} -> {}", taken, target.getVillageName(), attacker.getVillageName());
   }

   private static int countLive(ServerLevel level, Village source, ActivityOperation op) {
      int n = 0;
      for (UUID id : op.participantIds()) {
         VillagerRecord r = source.getVillagerRecords().get(id);
         if (r != null && !r.isKilled()) n++;
      }
      return n;
   }

   private static int countLiveNear(ServerLevel level, Village source, ActivityOperation op, BlockPos pos, int range) {
      int n = 0;
      double r2 = (double) range * range;
      for (UUID id : op.participantIds()) {
         VillagerRecord r = source.getVillagerRecords().get(id);
         if (r == null || r.isKilled()) continue;
         if (level.getEntity(id) instanceof MillVillager mv) {
            if (mv.blockPosition().distSqr(pos) <= r2) n++;
         } else {
            n++; // abstract member still counts until death recorded
         }
      }
      return n;
   }
}
