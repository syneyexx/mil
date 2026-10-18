package org.millenaire.combat.raid;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.millenaire.activity.RaidParticipantMode;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.CultureStandingSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.Set;

public final class RaidManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private RaidManager() {
   }

   public static boolean resolveAttackersWin(int defendingStrength, int attackerStrength, float defendingFactor, float attackingFactor) {
      float defendingForce = defendingStrength * defendingFactor;
      float attackingForce = attackerStrength * attackingFactor;
      if (attackingForce == 0.0F) {
         return false;
      } else {
         return defendingForce == 0.0F ? true : attackingForce / defendingForce > 1.2F;
      }
   }

   public static boolean shouldEndRaid(int liveAttackers, int liveDefenders, boolean raiderAtDefendingPos) {
      if (liveAttackers == 0) {
         return true;
      } else {
         return liveDefenders == 0 ? raiderAtDefendingPos : false;
      }
   }

   public static int maxAcceptableTargetStrength(int attackerRaidingStrength) {
      return attackerRaidingStrength * 2;
   }

   static boolean frozenRaidTimedOut(long currentGameTime, long raidStartGameTime) {
      return currentGameTime - raidStartGameTime >= 23000L;
   }

   static boolean needsGameTimeBackfill(long raidStart, long raidStartGameTime) {
      return raidStart > 0L && raidStartGameTime == 0L;
   }

   public static boolean isBanditViableTarget(
      int targetDefendingStrength, int maxAcceptable, boolean targetIsLoneBuilding, boolean withinRadius, boolean targetUnderAttack
   ) {
      if (targetUnderAttack) {
         return false;
      } else if (targetIsLoneBuilding) {
         return false;
      } else {
         return !withinRadius ? false : targetDefendingStrength < maxAcceptable;
      }
   }

   public static boolean isRegularViableTarget(int targetDefendingStrength, int maxAcceptable, int relation, boolean targetUnderAttack) {
      if (targetUnderAttack) {
         return false;
      } else {
         return relation >= -90 ? false : targetDefendingStrength < maxAcceptable;
      }
   }

   public static boolean isRegularViableTargetForCultureWar(int targetDefendingStrength, int maxAcceptable, boolean targetUnderAttack) {
      if (targetUnderAttack) {
         return false;
      } else {
         return targetDefendingStrength < maxAcceptable;
      }
   }

   public static void attemptPlanNewRaid(Village attacker, ServerLevel level) {
      if (attacker != null && attacker.getRaidTarget() == null) {
         VillageType vt = ModCultures.getVillageType(attacker.getVillageTypeId());
         if (vt != null) {
            BlockPos c = attacker.getCenter();
            int bgRadius = (Integer)MillenaireServerConfig.SERVER.backgroundRadius.get();
            if (level.hasNearbyAlivePlayer(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, bgRadius)) {
               boolean isBandit = vt.loneBuilding() && (vt.carriesRaid() || "illager_camp".equals(vt.specialType()));
               if (vt.carriesRaid() || isBandit) {
                  int rate = (Integer)MillenaireServerConfig.SERVER.raidingRate.get();
                  if (rate > 0) {
                     if (ThreadLocalRandom.current().nextInt(100) < rate) {
                        int maxConcurrent = MillenaireServerConfig.SERVER.maxConcurrentRaids.getAsInt();
                        if (countUniqueActiveRaids(VillageSavedData.get(level).getVillageManager()) >= maxConcurrent) {
                           TickPerfCounters.RAID_PLANS_SKIPPED_CAP.incrementAndGet();
                           return;
                        }

                        for (VillagerRecord r : attacker.getVillagerRecords().values()) {
                           if (r.isRaidingVillage()) {
                              return;
                           }
                        }

                        Village target = selectRaidTarget(attacker, level, isBandit);
                        if (target != null) {
                           planRaid(attacker, target, level);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static Village selectRaidTarget(Village attacker, ServerLevel level, boolean isBandit) {
      int maxAcceptable = maxAcceptableTargetStrength(attacker.getVillageRaidingStrength());
      if (maxAcceptable <= 0) {
         return null;
      }

      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<Village> candidates = new ArrayList<>();
      if (isBandit) {
         int radius = (Integer)MillenaireServerConfig.SERVER.banditRaidRadius.get();
         long radiusSq = (long)radius * radius;

         for (Village v : vm.getAllVillages()) {
            if (v != attacker) {
               VillageType ovt = ModCultures.getVillageType(v.getVillageTypeId());
               if (ovt != null) {
                  boolean withinRadius = v.getCenter().distSqr(attacker.getCenter()) < radiusSq;
                  if (isBanditViableTarget(v.getVillageDefendingStrength(), maxAcceptable, ovt.loneBuilding(), withinRadius, v.isUnderAttack())) {
                     candidates.add(v);
                  }
               }
            }
         }
      } else {
         CultureStandingSavedData cultureStanding = CultureStandingSavedData.get(level);
         ResourceLocation attackerCulture = attacker.getCultureId();

         for (Entry<VillageId, Integer> entry : attacker.getRelations().entrySet()) {
            Village v = vm.getVillage(entry.getKey());
            if (v != null && isRegularViableTarget(v.getVillageDefendingStrength(), maxAcceptable, entry.getValue(), v.isUnderAttack())) {
               candidates.add(v);
            }
         }

         for (Village v : vm.getAllVillages()) {
            if (v != attacker
               && !candidates.contains(v)
               && cultureStanding.isAtWar(attackerCulture, v.getCultureId())
               && isRegularViableTargetForCultureWar(v.getVillageDefendingStrength(), maxAcceptable, v.isUnderAttack())) {
               candidates.add(v);
            }
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   public static void planRaid(Village attacker, Village target, ServerLevel level) {
      attacker.setRaidTarget(target.getId());
      attacker.setRaidPlanningStart(level.getDayTime());
      attacker.setRaidStart(0L);
      attacker.setRaidStartGameTime(0L);
      String attackerName = nameOf(attacker);
      String targetName = nameOf(target);
      broadcastNear(
         level,
         attacker.getCenter(),
         Component.translatable("millenaire.combat.raid_planned", new Object[]{Component.literal(attackerName), Component.literal(targetName)})
      );
      LOGGER.info("[Millenaire] Raid planned: {} -> {} at day-time {}.", new Object[]{attackerName, targetName, level.getDayTime()});
   }

   public static void tickRaid(Village village, ServerLevel level) {
      long now = level.getDayTime();
      if (village.isUnderAttack()) {
         materializeRaiders(village, level, now);
      }

      if (village.getRaidTarget() != null
         && village.getRaidStart() == 0L
         && village.getRaidPlanningStart() > 0L
         && now - village.getRaidPlanningStart() >= 24000L) {
         Village target = VillageSavedData.get(level).getVillageManager().getVillage(village.getRaidTarget());
         if (target == null) {
            village.clearRaid();
         } else {
            startRaid(village, target, level);
         }
      }

      if (village.getRaidTarget() != null && village.getRaidStart() > 0L) {
         migrateRaidStartGameTime(village, level);
         Village raidTarget = VillageSavedData.get(level).getVillageManager().getVillage(village.getRaidTarget());
         boolean targetGone = raidTarget == null;
         boolean clonesGone = !targetGone
            && frozenRaidTimedOut(level.getGameTime(), village.getRaidStartGameTime())
            && !hasRaiderClonesFrom(raidTarget, village.getId());
         if (targetGone || clonesGone) {
            LOGGER.warn(
               "[Millenaire] Raid aborted on attacker side: {} -> {} ({}).",
               new Object[]{nameOf(village), village.getRaidTarget(), targetGone ? "target gone" : "clones gone"}
            );
            abortRaidForAttacker(village, level);
         }
      }

      if (village.isUnderAttack() && level.getGameTime() % 10L == Math.floorMod(village.getId().uuid().hashCode(), 10L)) {
         checkBattleStatus(village, level);
      }

      if (village.isUnderAttack()) {
         VillageId attackerId = findAttackerId(village, level);
         Village attacker = attackerId != null ? VillageSavedData.get(level).getVillageManager().getVillage(attackerId) : null;
         if (attacker == null) {
            cleanupClones(village, level);
            village.setUnderAttack(false);
         } else if (attacker.getRaidStart() > 0L) {
            migrateRaidStartGameTime(attacker, level);
            if (frozenRaidTimedOut(level.getGameTime(), attacker.getRaidStartGameTime())) {
               LOGGER.warn(
                  "[Millenaire] Raid timeout on {} (under attack for {} game ticks)", nameOf(village), level.getGameTime() - attacker.getRaidStartGameTime()
               );
               endRaid(attacker, village, level);
            }
         }
      }
   }

   static void startRaid(Village attacker, Village target, ServerLevel level) {
      startRaidInternal(attacker, target, level, false);
   }

   public static void startRaidForced(Village attacker, Village target, ServerLevel level) {
      startRaidInternal(attacker, target, level, true);
   }

   static boolean shouldAbortDueToImprovedRelations(
      boolean isBandit, Integer relation, boolean skipRelationCheck, ServerLevel level, Village attacker, Village target
   ) {
      if (skipRelationCheck) {
         return false;
      } else if (isBandit) {
         return false;
      } else if (relation == null) {
         return false;
      } else if (relation > -90) {
         return !CultureStandingSavedData.get(level).isAtWar(attacker.getCultureId(), target.getCultureId());
      } else {
         return false;
      }
   }

   private static void startRaidInternal(Village attacker, Village target, ServerLevel level, boolean skipRelationCheck) {
      VillageType attackerVT = ModCultures.getVillageType(attacker.getVillageTypeId());
      boolean isBandit = attackerVT != null && attackerVT.loneBuilding();
      Integer rel = isBandit ? null : attacker.getRelations().get(target.getId());
      if (shouldAbortDueToImprovedRelations(isBandit, rel, skipRelationCheck, level, attacker, target)) {
         LOGGER.info("[Millenaire] Raid aborted (relation improved): {} -> {}.", nameOf(attacker), nameOf(target));
         attacker.clearRaid();
      } else if (target.isUnderAttack()) {
         LOGGER.info("[Millenaire] Raid aborted (target already under attack): {} -> {}.", nameOf(attacker), nameOf(target));
         attacker.clearRaid();
      } else {
         List<VillagerRecord> raiders = new ArrayList<>();

         for (VillagerRecord r : attacker.getVillagerRecords().values()) {
            if (!r.isKilled() && !r.isRaidingVillage() && !r.isAwayRaiding() && !r.isAwayHired()) {
               VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
               if (type != null && type.isRaider()) {
                  raiders.add(r);
               }
            }
         }

         int sent = 0;

         for (VillagerRecord r : raiders) {
            if (sendVillagerOnRaid(r, attacker, target, level)) {
               sent++;
            }
         }

         if (sent == 0) {
            attacker.clearRaid();
         } else {
            attacker.setRaidParticipantMode(RaidParticipantMode.LEGACY_CLONES);
            attacker.setExpeditionOperationId(null);
            target.setUnderAttack(true);
            attacker.setRaidStart(level.getDayTime());
            attacker.setRaidStartGameTime(level.getGameTime());
            if (shouldCancelTargetOwnRaid(target.getRaidStart())) {
               target.clearRaid();
            }

            attacker.invalidateDefenderCountAndReevaluate(level);
            broadcastNear(
               level,
               target.getCenter(),
               Component.translatable(
                  "millenaire.combat.raid_started",
                  new Object[]{Component.literal(nameOf(attacker)), Component.literal(nameOf(target)), Component.literal(String.valueOf(sent))}
               )
            );
            LOGGER.info("[Millenaire] Raid started: {} dispatched {} raiders to {}.", new Object[]{nameOf(attacker), sent, nameOf(target)});
            IllagerRaidSupport.onRaidStarted(attacker, target, level);
         }
      }
   }

   /**
    * Counts unique active raids by canonical attacking source (or expedition operation),
    * not by summing attacker+defender village flags.
    */
   public static int countUniqueActiveRaids(VillageManager vm) {
      Set<String> unique = new HashSet<>();
      Set<VillageId> defendedByKnownAttacker = new HashSet<>();
      for (Village v : vm.getAllVillages()) {
         if (v.getRaidStart() > 0L && v.getRaidTarget() != null) {
            if (v.getExpeditionOperationId() != null) {
               unique.add("exp:" + v.getExpeditionOperationId());
            } else {
               unique.add("atk:" + v.getId().uuid());
            }
            defendedByKnownAttacker.add(v.getRaidTarget());
         }
      }
      for (Village v : vm.getAllVillages()) {
         if (v.isUnderAttack() && !defendedByKnownAttacker.contains(v.getId())) {
            // Orphan underAttack flag without a matching attacker record
            unique.add("def:" + v.getId().uuid());
         }
      }
      return unique.size();
   }

   /** @deprecated use {@link #countUniqueActiveRaids(VillageManager)} */
   static int countActiveRaids(VillageManager vm) {
      return countUniqueActiveRaids(vm);
   }

   /**
    * Starts a physical village attack using already-reserved expedition members.
    * Does not spawn legacy raid clones. Marks underAttack only at call time.
    */
   public static boolean beginExpeditionRaid(
      Village attacker, Village target, ServerLevel level, UUID operationId, List<UUID> participantIds
   ) {
      if (attacker == null || target == null || operationId == null || participantIds == null || participantIds.isEmpty()) {
         return false;
      }
      if (target.isUnderAttack()) {
         return false;
      }
      if (attacker.getRaidParticipantMode() == RaidParticipantMode.LEGACY_CLONES
         && attacker.getRaidStart() > 0L
         && attacker.getExpeditionOperationId() == null) {
         // Already in a legacy clone raid — do not mix representations
         return false;
      }
      int marked = 0;
      for (UUID id : participantIds) {
         VillagerRecord r = attacker.getVillagerRecords().get(id);
         if (r == null || r.isKilled()) {
            continue;
         }
         r.setAwayRaiding(true);
         marked++;
      }
      if (marked == 0) {
         return false;
      }
      attacker.setRaidTarget(target.getId());
      attacker.setRaidParticipantMode(RaidParticipantMode.EXPEDITION_MEMBERS);
      attacker.setExpeditionOperationId(operationId);
      attacker.setRaidStart(level.getDayTime());
      attacker.setRaidStartGameTime(level.getGameTime());
      target.setUnderAttack(true);
      if (shouldCancelTargetOwnRaid(target.getRaidStart())) {
         target.clearRaid();
      }
      attacker.invalidateDefenderCountAndReevaluate(level);
      broadcastNear(
         level,
         target.getCenter(),
         Component.translatable(
            "millenaire.combat.raid_started",
            new Object[]{Component.literal(nameOf(attacker)), Component.literal(nameOf(target)), Component.literal(String.valueOf(marked))}
         )
      );
      LOGGER.info("[Millenaire] Expedition raid started: {} (op {}) with {} members -> {}.", nameOf(attacker), operationId, marked, nameOf(target));
      return true;
   }

   static boolean sendVillagerOnRaid(VillagerRecord original, Village attacker, Village target, ServerLevel level) {
      original.setAwayRaiding(true);
      VillagerRecord clone = new VillagerRecord(UUID.randomUUID(), original.getVillagerTypeId(), null);
      clone.copyAppearanceAndInventoryFrom(original);
      clone.setRaidingVillage(true);
      clone.setOriginalVillageId(attacker.getId());
      clone.setRaiderSpawn(level.getDayTime());
      target.addVillager(clone);
      if (level.getEntity(original.getUuid()) instanceof MillVillager v) {
         v.discard();
      }

      attacker.markDirty();
      target.markDirty();
      return true;
   }

   static void materializeRaiders(Village target, ServerLevel level, long now) {
      VillageId attackerId = findAttackerId(target, level);
      if (attackerId != null) {
         Village attacker = VillageSavedData.get(level).getVillageManager().getVillage(attackerId);
         if (attacker != null && attacker.getRaidParticipantMode() == RaidParticipantMode.EXPEDITION_MEMBERS) {
            // Expedition path never materializes legacy clones for the same operation.
            return;
         }
      }
      List<UUID> cloneUuids = new ArrayList<>();

      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            cloneUuids.add(r.getUuid());
         }
      }

      for (UUID uuid : cloneUuids) {
         VillagerRecord clone = target.getVillagerRecords().get(uuid);
         if (clone != null && !(level.getEntity(uuid) instanceof MillVillager) && !clone.isKilled() && now > clone.getRaiderSpawn() + 500L) {
            VillageId originalId = clone.getOriginalVillageId();
            BlockPos attackerCenter = originalId != null ? attackerCenterOf(originalId, target, level) : target.getCenter();
            BlockPos spawnPos = RaidSpawnLocator.findSpawnPoint(level, target, attackerCenter);
            if (!level.isPositionEntityTicking(spawnPos)) {
               BlockPos fallback = resolveDefendingPos(target);
               if (!level.isPositionEntityTicking(fallback)) {
                  continue;
               }

               spawnPos = fallback;
            }

            target.removeVillagerRecord(uuid);
            MillVillager spawned = VillagerSpawnFactory.spawnInVillage(level, target, clone.getVillagerTypeId(), spawnPos, null, clone);
            if (spawned == null) {
               clone.setUuid(uuid);
               target.addVillager(clone);
            } else {
               target.markDirty();
               LOGGER.info("[Millenaire] Raider clone materialized at village edge {} (pos {}).", nameOf(target), spawnPos.toShortString());
            }
         }
      }
   }

   private static BlockPos attackerCenterOf(VillageId attackerId, Village target, ServerLevel level) {
      Village attacker = VillageSavedData.get(level).getVillageManager().getVillage(attackerId);
      return attacker != null ? attacker.getCenter() : target.getCenter();
   }

   public static BlockPos resolveDefendingPos(Village target) {
      BuildingInstance th = target.getTownhall();
      if (th != null) {
         BlockPos p = firstPoint(th, "defendingPos", "sellingPos", "sleepingPos", "pathStartPos");
         if (p != null) {
            return p;
         }

         if (th.getOrigin() != null) {
            return th.getOrigin();
         }
      }

      return target.getCenter();
   }

   @Nullable
   static BlockPos firstPoint(BuildingInstance th, String... types) {
      for (String type : types) {
         BlockPos p = th.getFirstPointPos(type);
         if (p != null) {
            return p;
         }
      }

      return null;
   }

   static void checkBattleStatus(Village target, ServerLevel level) {
      VillageId attackerId = findAttackerId(target, level);
      Village attacker = attackerId != null ? VillageSavedData.get(level).getVillageManager().getVillage(attackerId) : null;
      // Expedition raids are ended by ActivityRaidBridge to keep a single outcome path.
      if (attacker != null && attacker.getRaidParticipantMode() == RaidParticipantMode.EXPEDITION_MEMBERS) {
         return;
      }

      int liveAttackers = 0;
      int liveDefenders = 0;
      boolean raiderAtPos = false;
      BlockPos defendingPos = resolveDefendingPos(target);

      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            if (!r.isKilled()) {
               liveAttackers++;
               if (level.getEntity(r.getUuid()) instanceof MillVillager mv && mv.blockPosition().distSqr(defendingPos) < 25.0) {
                  raiderAtPos = true;
               }
            }
         } else if (!r.isKilled()) {
            VillagerType t = ModCultures.getVillagerType(r.getVillagerTypeId());
            if (t != null && t.isHelpInAttacks()) {
               liveDefenders++;
            }
         }
      }

      switch (ChestLockDecision.decide(target.areChestsLocked(), liveDefenders)) {
         case LOCK:
            target.lockAllChestsPublic(level);
            break;
         case UNLOCK:
            target.unlockAllChestsPublic(level);
         case NOOP:
      }

      if (shouldEndRaid(liveAttackers, liveDefenders, raiderAtPos)) {
         if (attacker == null) {
            cleanupClones(target, level);
            target.setUnderAttack(false);
         } else {
            endRaid(attacker, target, level);
         }
      }
   }

   static Component raidSuccessMessage(String attackerName, String targetName, int totalStolen) {
      return Component.translatable(
         "millenaire.combat.raid_success",
         new Object[]{Component.literal(attackerName), Component.literal(targetName), Component.literal(String.valueOf(totalStolen))}
      );
   }

   static Component raidFailureMessage(String targetName, String attackerName) {
      return Component.translatable("millenaire.combat.raid_failure", new Object[]{Component.literal(targetName), Component.literal(attackerName)});
   }

   static void endRaid(Village attacker, Village target, ServerLevel level) {
      if (attacker.getRaidParticipantMode() == RaidParticipantMode.EXPEDITION_MEMBERS) {
         // Expedition outcome is owned by ActivityRaidBridge — avoid double loot/chronicle.
         cleanupClones(target, level);
         attacker.clearRaid();
         target.setUnderAttack(false);
         attacker.markDirty();
         target.markDirty();
         return;
      }
      int defending = target.getVillageDefendingStrength();
      int attacking = target.getVillageAttackerStrength();
      float defFactor = 1.0F + ThreadLocalRandom.current().nextFloat();
      float atkFactor = 1.0F + ThreadLocalRandom.current().nextFloat();
      boolean attackersWon = resolveAttackersWin(defending, attacking, defFactor, atkFactor);
      String attackerName = nameOf(attacker);
      String targetName = nameOf(target);
      if (attackersWon) {
         Map<Item, Integer> loot = applyLooting(attacker, target, level);
         int totalStolen = 0;

         for (int qty : loot.values()) {
            totalStolen += qty;
         }

         String lootDetail = formatLootHistory(loot);
         attacker.addRaidsPerformed("success;" + targetName + lootDetail);
         target.addRaidsSuffered("success;" + attackerName + lootDetail);
         broadcastNear(level, target.getCenter(), raidSuccessMessage(attackerName, targetName, totalStolen));
         LOGGER.info("[Millenaire] Raid SUCCESS: {} -> {} ({} goods stolen).", new Object[]{attackerName, targetName, totalStolen});
      } else {
         attacker.addRaidsPerformed("failure;" + targetName);
         target.addRaidsSuffered("failure;" + attackerName);
         broadcastNear(level, target.getCenter(), raidFailureMessage(targetName, attackerName));
         LOGGER.info("[Millenaire] Raid FAILURE: {} repulsed at {}.", attackerName, targetName);
      }

      grantRaidAdvancements(attacker, target, attackersWon, level);
      List<VillagerRecord> clones = new ArrayList<>();

      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            clones.add(r);
         }
      }

      for (VillagerRecord original : attacker.getVillagerRecords().values()) {
         if (original.isAwayRaiding()) {
            original.setAwayRaiding(false);

            for (VillagerRecord clone : clones) {
               if (clone.getVillagerTypeId() != null
                  && clone.getVillagerTypeId().equals(original.getVillagerTypeId())
                  && clone.getFirstName().equals(original.getFirstName())
                  && clone.getFamilyName().equals(original.getFamilyName())) {
                  if (clone.isKilled()) {
                     attacker.markVillagerKilled(original.getUuid());
                  }
                  break;
               }
            }
         }
      }

      attacker.invalidateDefenderCountAndReevaluate(level);
      cleanupClones(target, level);
      attacker.clearRaid();
      target.setUnderAttack(false);
      attacker.markDirty();
      target.markDirty();
   }

   private static void grantRaidAdvancements(Village attacker, Village target, boolean attackersWon, ServerLevel level) {
      List<RaidAdvancementResolver.Grant> grants = RaidAdvancementResolver.resolve(
         attacker.getCultureId(), target.getCultureId(), attacker.getOwnerUUID(), target.getOwnerUUID(), attackersWon
      );
      if (!grants.isEmpty()) {
         for (RaidAdvancementResolver.Grant grant : grants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(grant.playerUuid());
            if (player != null) {
               MillAdvancements.grant(player, grant.advancement());
            }
         }
      }
   }

   private static Map<Item, Integer> applyLooting(Village attacker, Village target, ServerLevel level) {
      BuildingInstance attackerTh = attacker.getTownhall();
      if (attackerTh != null && attackerTh.getInventory() != null) {
         List<TradeGood> goods = TradeGoodsLoader.getGoods(attacker.getCultureId());
         if (goods.isEmpty()) {
            return Map.of();
         }

         Map<Item, Integer> targetContents = new HashMap<>();

         for (BuildingInstance b : target.getBuildings()) {
            BuildingInventory inv = b.getInventory();
            if (inv != null) {
               for (Entry<Item, Integer> e : inv.scanChests(level).entrySet()) {
                  targetContents.merge(e.getKey(), e.getValue(), Integer::sum);
               }
            }
         }

         if (targetContents.isEmpty()) {
            return Map.of();
         }

         List<RaidLootPlanner.GoodNeed> needs = new ArrayList<>();

         for (TradeGood good : goods) {
            Item item = good.resolveItem();
            if (item != null) {
               needs.add(new RaidLootPlanner.GoodNeed(item, good.targetQuantity()));
            }
         }

         Map<Item, Integer> toSteal = RaidLootPlanner.plan(needs, targetContents, 1024);
         if (toSteal.isEmpty()) {
            LOGGER.info("[Millenaire] Loot: nothing to take from {} (no culture goods present).", nameOf(target));
            return Map.of();
         }

         Map<Item, Integer> actualStolen = new LinkedHashMap<>();
         int stolen = 0;

         for (Entry<Item, Integer> plan : toSteal.entrySet()) {
            Item item = plan.getKey();
            int wanted = plan.getValue();

            for (BuildingInstance b : target.getBuildings()) {
               if (wanted <= 0) {
                  break;
               }

               BuildingInventory inv = b.getInventory();
               if (inv != null) {
                  int removed = inv.remove(level, item, wanted);
                  if (removed > 0) {
                     int added = attackerTh.getInventory().add(level, item, removed);
                     wanted -= added;
                     stolen += added;
                     if (added > 0) {
                        actualStolen.merge(item, added, Integer::sum);
                     }

                     if (added < removed) {
                        int toRestore = removed - added;
                        int restored = inv.add(level, item, toRestore);
                        if (restored < toRestore) {
                           LOGGER.warn(
                              "[Millenaire] Loot rollback: {} x{} could not be restored to {} (source chests full) — items lost",
                              new Object[]{item, toRestore - restored, nameOf(target)}
                           );
                        }
                        break;
                     }
                  }
               }
            }
         }

         LOGGER.info("[Millenaire] Loot: {} culture-good units transferred from {} to {}.", new Object[]{stolen, nameOf(target), nameOf(attacker)});
         return actualStolen;
      } else {
         return Map.of();
      }
   }

   static String formatLootHistory(Map<Item, Integer> loot) {
      StringBuilder sb = new StringBuilder();

      for (Entry<Item, Integer> e : loot.entrySet()) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(e.getKey());
         sb.append(';').append(id).append('/').append(e.getValue());
      }

      return sb.toString();
   }

   private static void cleanupClones(Village target, ServerLevel level) {
      IllagerRaidSupport.cleanupRaidIllagers(target, level);
      List<UUID> toRemove = new ArrayList<>();

      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            if (level.getEntity(r.getUuid()) instanceof MillVillager mv) {
               mv.discard();
            }

            toRemove.add(r.getUuid());
         }
      }

      for (UUID id : toRemove) {
         target.removeVillagerRecord(id);
      }
   }

   static boolean shouldCancelTargetOwnRaid(long targetRaidStart) {
      return targetRaidStart == 0L;
   }

   static boolean releaseAwayRaidingOriginals(Collection<VillagerRecord> records) {
      boolean any = false;

      for (VillagerRecord r : records) {
         if (r.isAwayRaiding()) {
            r.setAwayRaiding(false);
            any = true;
         }
      }

      return any;
   }

   public static void abortRaidForAttacker(Village attacker, ServerLevel level) {
      if (releaseAwayRaidingOriginals(attacker.getVillagerRecords().values())) {
         attacker.invalidateDefenderCountAndReevaluate(level);
      }

      attacker.clearRaid();
      attacker.markDirty();
   }

   static void migrateRaidStartGameTime(Village village, ServerLevel level) {
      if (needsGameTimeBackfill(village.getRaidStart(), village.getRaidStartGameTime())) {
         village.setRaidStartGameTime(level.getGameTime());
      }
   }

   private static boolean hasRaiderClonesFrom(Village target, VillageId attackerId) {
      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage() && attackerId.equals(r.getOriginalVillageId())) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private static VillageId findAttackerId(Village target, ServerLevel level) {
      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage() && r.getOriginalVillageId() != null) {
            return r.getOriginalVillageId();
         }
      }
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      for (Village v : vm.getAllVillages()) {
         if (target.getId().equals(v.getRaidTarget()) && v.getRaidStart() > 0L) {
            return v.getId();
         }
      }
      return null;
   }

   private static String nameOf(Village v) {
      return v.getVillageName() != null ? v.getVillageName() : v.getVillageTypeId().getPath();
   }

   private static void broadcastNear(ServerLevel level, BlockPos center, Component msg) {
      int radius = (Integer)MillenaireServerConfig.SERVER.backgroundRadius.get();
      if (radius <= 0) {
         radius = 2000;
      }

      long radiusSq = (long)radius * radius;

      for (ServerPlayer p : level.players()) {
         if (p.blockPosition().distSqr(center) <= radiusSq) {
            p.sendSystemMessage(msg);
         }
      }
   }
}
