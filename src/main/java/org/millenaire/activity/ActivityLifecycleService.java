package org.millenaire.activity;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/** Advances operation state machines in small commit steps. */
public final class ActivityLifecycleService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ActivityLifecycleService() {}

   public static void advance(
      ServerLevel level,
      ActivitySavedData data,
      ActivityAdmissionController admission,
      ActivitySpatialIndex index,
      ActivityOperation op
   ) {
      if (op.state().isTerminal()) {
         return;
      }
      long now = level.getGameTime();
      if (op.deadlineGameTime() > 0L && now > op.deadlineGameTime() && op.state() != ActivityState.SETTLING) {
         beginReturn(level, data, admission, op, "deadline");
         return;
      }
      switch (op.state()) {
         case PROPOSED -> tryReserve(level, data, admission, index, op);
         case RESERVED -> enterAssembling(level, data, admission, op);
         case ASSEMBLING -> tickAssemble(level, data, admission, op);
         case TRAVELLING -> tickTravel(level, data, admission, index, op);
         case WAITING_CHUNKS -> tickWaitingChunks(level, data, admission, op);
         case EXECUTING -> tickExecute(level, data, admission, index, op);
         case RETURNING, RETREATING -> tickReturn(level, data, admission, op);
         case SETTLING -> tickSettle(level, data, admission, op);
         case RECOVERY_REQUIRED -> tickRecovery(level, data, admission, op);
         default -> {}
      }
   }

   public static ActivityOperation createFromProposal(ServerLevel level, ActivityPlanner.Proposal proposal) {
      ActivityOperation op = new ActivityOperation(
         UUID.randomUUID(),
         level.dimension().location(),
         proposal.kind(),
         level.getGameTime()
      );
      if (proposal.sourceIsCamp()) {
         op.setSourceCampId(proposal.sourceId());
      } else {
         op.setSourceSettlementId(proposal.sourceId());
      }
      op.setTargetSettlementId(proposal.targetSettlementId());
      op.setTargetOperationId(proposal.targetOperationId());
      op.setProfileId(proposal.profileId());
      op.setOrganizationId(proposal.organizationId());
      op.setHostilityReason(proposal.hostilityReason());
      op.setCoarseRoute(proposal.route());
      op.setLastValidPos(proposal.assemblePos());
      op.setDeadlineGameTime(level.getGameTime() + 24000L);
      if (proposal.kind().isVillageRaid()) {
         op.setRaidMode(RaidParticipantMode.EXPEDITION_MEMBERS);
      }
      op.enterState(ActivityState.PROPOSED, level.getGameTime());
      // Stash candidates in participant list only after reserve; keep in temporary via profile until reserved
      op.setParticipantIds(proposal.candidateMembers());
      return op;
   }

   private static void tryReserve(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivitySpatialIndex index, ActivityOperation op
   ) {
      if (!admission.trySpendCommit()) {
         op.setNextDueGameTime(level.getGameTime() + 20L);
         data.reschedule(op);
         return;
      }
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) {
         cancel(data, admission, op, ActivityReasonCode.NO_ELIGIBLE_MEMBERS, level.getGameTime());
         return;
      }
      ActivityProfile profile = ActivityProfileLoader.byId(op.profileId());
      if (profile == null) {
         cancel(data, admission, op, ActivityReasonCode.STALE_RESULT, level.getGameTime());
         return;
      }
      // Re-check members atomically
      List<UUID> still = new ArrayList<>();
      for (UUID id : op.participantIds()) {
         VillagerRecord r = source.getVillagerRecords().get(id);
         if (r == null || r.isKilled() || r.isAwayHired() || r.isAwayRaiding() || r.isRaidingVillage()) continue;
         if (r.getActivityOperationId() != null) continue;
         if (admission.isParticipantReserved(id)) continue;
         VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
         if (type == null || type.isChild()) continue;
         still.add(id);
      }
      if (still.size() < profile.groupSizeMin()) {
         op.setDeferReason(ActivityReasonCode.NO_ELIGIBLE_MEMBERS);
         op.setNextDueGameTime(level.getGameTime() + 100L);
         data.reschedule(op);
         ActivityDiagnostics.get().countReason(ActivityReasonCode.NO_ELIGIBLE_MEMBERS);
         return;
      }
      if (!admission.canActivateNewGroup(level, op.kind(), source.getCenter(), still.size())) {
         op.setDeferReason(admission.lastDefer());
         op.setNextDueGameTime(level.getGameTime() + 100L);
         data.reschedule(op);
         return;
      }
      if (op.targetSettlementId() != null) {
         if (admission.isTargetReserved(op.targetSettlementId()) || data.targetOnCooldown(op.targetSettlementId().toString(), level.getGameTime())) {
            op.setDeferReason(ActivityReasonCode.TARGET_RESERVED);
            op.setNextDueGameTime(level.getGameTime() + 100L);
            data.reschedule(op);
            return;
         }
         if (!admission.reserveTarget(op.targetSettlementId())) {
            op.setDeferReason(ActivityReasonCode.TARGET_RESERVED);
            data.reschedule(op);
            return;
         }
         op.setTargetReservationId(op.targetSettlementId());
      }
      List<UUID> reserved = new ArrayList<>();
      for (UUID id : still) {
         if (reserved.size() >= profile.groupSizeMax()) break;
         VillagerRecord r = source.getVillagerRecords().get(id);
         if (r == null) continue;
         if (!admission.reserveParticipant(id)) {
            // rollback
            for (UUID done : reserved) {
               admission.releaseParticipant(done);
               VillagerRecord rr = source.getVillagerRecords().get(done);
               if (rr != null) rr.setActivityOperationId(null);
            }
            if (op.targetReservationId() != null) {
               admission.releaseTarget(op.targetReservationId());
               op.setTargetReservationId(null);
            }
            op.setDeferReason(ActivityReasonCode.ENTITY_CAP);
            op.setNextDueGameTime(level.getGameTime() + 40L);
            data.reschedule(op);
            return;
         }
         r.setActivityOperationId(op.operationId());
         reserved.add(id);
      }
      if (reserved.size() < profile.groupSizeMin()) {
         for (UUID done : reserved) {
            admission.releaseParticipant(done);
            VillagerRecord rr = source.getVillagerRecords().get(done);
            if (rr != null) rr.setActivityOperationId(null);
         }
         if (op.targetReservationId() != null) {
            admission.releaseTarget(op.targetReservationId());
            op.setTargetReservationId(null);
         }
         op.setDeferReason(ActivityReasonCode.NO_ELIGIBLE_MEMBERS);
         op.setNextDueGameTime(level.getGameTime() + 100L);
         data.reschedule(op);
         return;
      }
      op.setParticipantIds(reserved);
      op.setInitialMemberCount(reserved.size());
      UUID groupId = UUID.randomUUID();
      ActivityGroup group = new ActivityGroup(groupId, op.operationId());
      group.setMembers(reserved);
      group.setRallyPos(source.getCenter());
      op.addGroupId(groupId);
      data.putGroup(group);
      admission.noteActivation();
      op.enterState(ActivityState.RESERVED, level.getGameTime());
      data.clearProposal(op.sourceId() + ":" + op.kind().name());
      data.reschedule(op);
      source.markDirty();
      LOGGER.info("[Activity] Reserved {} {} from {} ({} members)", op.kind(), op.operationId(), source.getVillageName(), reserved.size());
   }

   private static void enterAssembling(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      if (!admission.trySpendCommit()) {
         op.setNextDueGameTime(level.getGameTime() + 10L);
         data.reschedule(op);
         return;
      }
      op.enterState(ActivityState.ASSEMBLING, level.getGameTime());
      op.setPhysicalActive(true);
      data.reschedule(op);
   }

   private static void tickAssemble(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) {
         failRecovery(data, admission, op, "source_missing", level.getGameTime());
         return;
      }
      BlockPos rally = op.lastValidPos() != null ? op.lastValidPos() : source.getCenter();
      int ready = 0;
      for (UUID id : op.participantIds()) {
         VillagerRecord rec = source.getVillagerRecords().get(id);
         if (rec == null || rec.isKilled()) continue;
         MillVillager mv = ActivityProjectionService.ensurePresent(level, admission, source, rec, rally, op);
         if (mv == null) {
            if (op.state() == ActivityState.WAITING_CHUNKS) {
               data.reschedule(op);
               return;
            }
            continue;
         }
         if (mv.blockPosition().distSqr(rally) > 16.0) {
            ActivityNavigationService.get().requestNavigate(level, admission, mv, rally, 0.55, op);
         } else {
            ready++;
         }
      }
      if (ready >= Math.max(1, op.participantIds().size() / 2) || level.getGameTime() - op.stateEnteredGameTime() > 400L) {
         // Source cooldown when departed
         long cd = MillenaireServerConfig.SERVER.regionalActivitySourceCooldownTicks.getAsInt();
         long jitter = (op.seed() & 0x3FF);
         data.setSourceCooldown(op.sourceId().toString(), level.getGameTime() + cd + jitter);
         op.enterState(ActivityState.TRAVELLING, level.getGameTime());
         data.reschedule(op);
      } else {
         op.setNextDueGameTime(level.getGameTime() + 20L);
         data.reschedule(op);
      }
   }

   private static void tickTravel(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivitySpatialIndex index, ActivityOperation op
   ) {
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) {
         failRecovery(data, admission, op, "source_missing", level.getGameTime());
         return;
      }
      ActivityGroup group = op.groupIds().isEmpty() ? null : data.getGroup(op.groupIds().getFirst());
      BlockPos wp = op.currentWaypoint();
      if (wp == null) {
         op.enterState(ActivityState.EXECUTING, level.getGameTime());
         data.reschedule(op);
         return;
      }
      if (!level.hasChunkAt(wp)) {
         op.enterState(ActivityState.WAITING_CHUNKS, level.getGameTime());
         data.reschedule(op);
         return;
      }
      UUID leaderId = group != null && group.leaderId() != null ? group.leaderId() : op.participantIds().getFirst();
      VillagerRecord leaderRec = source.getVillagerRecords().get(leaderId);
      MillVillager leader = leaderRec == null ? null : ActivityProjectionService.ensurePresent(level, admission, source, leaderRec, wp, op);
      if (leader == null) {
         op.setNextDueGameTime(level.getGameTime() + 40L);
         data.reschedule(op);
         return;
      }
      op.setLastValidPos(leader.blockPosition());
      if (group != null) {
         group.setRallyPos(leader.blockPosition());
         index.upsertGroup(group.groupId(), leader.blockPosition());
         ActivityCombatBridge.tickGroupCombat(level, data, index, op, group, ActivityProfileLoader.byId(op.profileId()));
         if (op.state() == ActivityState.RETREATING) {
            beginReturn(level, data, admission, op, "retreat");
            return;
         }
      }
      if (leader.blockPosition().distSqr(wp) <= 16.0) {
         op.advanceWaypoint();
         if (op.currentWaypoint() == null) {
            op.enterState(ActivityState.EXECUTING, level.getGameTime());
         }
         data.reschedule(op);
         return;
      }
      long now = level.getGameTime();
      if (group == null || now - group.lastDecisionGameTime() >= 40L) {
         if (group != null) group.setLastDecisionGameTime(now);
         ActivityNavigationService.get().requestNavigate(level, admission, leader, wp, 0.55, op);
         if (op.deferReason() == ActivityReasonCode.PATH_FAILED && op.consecutivePathFails() >= 3) {
            beginReturn(level, data, admission, op, "path_failed");
            return;
         }
      }
      // Followers
      int followerIdx = 0;
      for (UUID id : op.participantIds()) {
         if (id.equals(leaderId)) continue;
         VillagerRecord rec = source.getVillagerRecords().get(id);
         if (rec == null || rec.isKilled()) continue;
         BlockPos form = leader.blockPosition().offset((followerIdx % 3) - 1, 0, (followerIdx / 3) + 1);
         followerIdx++;
         MillVillager mv = ActivityProjectionService.ensurePresent(level, admission, source, rec, form, op);
         if (mv == null) continue;
         if (now % 40L == Math.floorMod(id.hashCode(), 40L)) {
            ActivityNavigationService.get().requestNavigate(level, admission, mv, form, 0.55, op);
         }
      }
      op.setNextDueGameTime(level.getGameTime() + 10L);
      data.reschedule(op);
   }

   private static void tickWaitingChunks(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      BlockPos wp = op.currentWaypoint() != null ? op.currentWaypoint() : op.lastValidPos();
      if (wp != null && level.hasChunkAt(wp) && level.isPositionEntityTicking(wp)) {
         op.enterState(ActivityState.TRAVELLING, level.getGameTime());
      } else {
         // Abstract travel progress only — no fabricated loot/combat
         op.setNextDueGameTime(level.getGameTime() + 100L);
      }
      data.reschedule(op);
   }

   private static void tickExecute(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivitySpatialIndex index, ActivityOperation op
   ) {
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) {
         failRecovery(data, admission, op, "source_missing", level.getGameTime());
         return;
      }
      ActivityProfile profile = ActivityProfileLoader.byId(op.profileId());
      ActivityGroup group = op.groupIds().isEmpty() ? null : data.getGroup(op.groupIds().getFirst());
      if (group != null) {
         ActivityCombatBridge.tickGroupCombat(level, data, index, op, group, profile);
         if (op.state() == ActivityState.RETREATING) {
            beginReturn(level, data, admission, op, "retreat");
            return;
         }
      }
      switch (op.kind()) {
         case HUNT -> executeHunt(level, data, admission, source, op, profile);
         case PATROL -> executePatrol(level, data, admission, source, op);
         case BANDIT_ROAM, AMBUSH -> executeBandit(level, data, admission, source, op, profile);
         case VILLAGE_RAID -> ActivityRaidBridge.tickExpeditionRaid(level, data, admission, source, op);
         case SKIRMISH, RESPONSE -> {
            if (level.getGameTime() - op.stateEnteredGameTime() > 600L) {
               beginReturn(level, data, admission, op, "skirmish_done");
            } else {
               op.setNextDueGameTime(level.getGameTime() + 20L);
               data.reschedule(op);
            }
         }
         default -> beginReturn(level, data, admission, op, "unknown_kind");
      }
   }

   private static void executeHunt(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, Village source, ActivityOperation op, ActivityProfile profile
   ) {
      if (op.preyTaken() >= MillenaireServerConfig.SERVER.regionalActivityMaxPreyPerHunt.getAsInt()) {
         beginReturn(level, data, admission, op, "prey_cap");
         return;
      }
      UUID leaderId = op.participantIds().isEmpty() ? null : op.participantIds().getFirst();
      if (leaderId == null) {
         beginReturn(level, data, admission, op, "no_members");
         return;
      }
      VillagerRecord leaderRec = source.getVillagerRecords().get(leaderId);
      BlockPos around = op.lastValidPos() != null ? op.lastValidPos() : source.getCenter();
      MillVillager leader = leaderRec == null ? null : ActivityProjectionService.ensurePresent(level, admission, source, leaderRec, around, op);
      if (leader == null) {
         op.setNextDueGameTime(level.getGameTime() + 40L);
         data.reschedule(op);
         return;
      }
      op.setLastValidPos(leader.blockPosition());
      // Collect nearby real drops into cargo (no duplicate vanilla loot fabrication)
      AABB lootBox = leader.getBoundingBox().inflate(6.0);
      for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, lootBox)) {
         ItemStack stack = item.getItem();
         if (stack.isEmpty()) continue;
         op.cargo().add(stack.copy());
         item.discard();
      }
      AABB box = leader.getBoundingBox().inflate(16.0, 8.0, 16.0);
      Animal prey = null;
      for (Animal animal : level.getEntitiesOfClass(Animal.class, box, a -> isValidPrey(a, profile, source))) {
         prey = animal;
         break;
      }
      if (prey == null) {
         if (level.getGameTime() - op.stateEnteredGameTime() > 800L) {
            op.setDeferReason(ActivityReasonCode.NO_PREY);
            beginReturn(level, data, admission, op, "no_prey");
         } else {
            op.setNextDueGameTime(level.getGameTime() + 40L);
            data.reschedule(op);
         }
         return;
      }
      leader.setTarget(prey);
      if (!prey.isAlive()) {
         op.setPreyTaken(op.preyTaken() + 1);
         String areaKey = "hunt:" + (around.getX() >> 6) + ":" + (around.getZ() >> 6);
         data.setAreaCooldown(areaKey, level.getGameTime() + 6000L);
      } else {
         ActivityNavigationService.get().requestNavigate(level, admission, leader, prey.blockPosition(), 0.6, op);
      }
      if (op.preyTaken() >= MillenaireServerConfig.SERVER.regionalActivityMaxPreyPerHunt.getAsInt()
         || level.getGameTime() - op.stateEnteredGameTime() > 1200L) {
         beginReturn(level, data, admission, op, "hunt_done");
      } else {
         op.setNextDueGameTime(level.getGameTime() + 20L);
         data.reschedule(op);
      }
   }

   private static boolean isValidPrey(Animal animal, ActivityProfile profile, Village source) {
      if (animal == null || !animal.isAlive()) return false;
      if (animal.isBaby()) return false;
      if (animal.hasCustomName()) return false;
      if (animal.isLeashed()) return false;
      if (animal.isVehicle() || animal.isPassenger()) return false;
      if (animal instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame()) return false;
      if (animal.getType().getCategory() == null) {
         // ignore
      }
      // Conservative: skip animals inside settlement footprint
      BlockPos p = animal.blockPosition();
      int radius = 48;
      var vt = ModCultures.getVillageType(source.getVillageTypeId());
      if (vt != null) radius = vt.radius() + 16;
      if (p.closerThan(source.getCenter(), radius)) return false;
      // Prey tags: accept common food animals when tag list empty or contains "animal"
      String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString();
      if (profile != null && !profile.huntingPreyTags().isEmpty()) {
         if (profile.huntingPreyTags().contains("animal")) return true;
         return profile.huntingPreyTags().contains(key);
      }
      return animal instanceof net.minecraft.world.entity.animal.Cow
         || animal instanceof net.minecraft.world.entity.animal.Pig
         || animal instanceof net.minecraft.world.entity.animal.Sheep
         || animal instanceof net.minecraft.world.entity.animal.Chicken
         || animal instanceof net.minecraft.world.entity.animal.Rabbit;
   }

   private static void executePatrol(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, Village source, ActivityOperation op) {
      if (op.waypointIndex() >= op.coarseRoute().size()) {
         beginReturn(level, data, admission, op, "patrol_done");
         return;
      }
      // Continue along remaining patrol points via travel loop
      op.enterState(ActivityState.TRAVELLING, level.getGameTime());
      data.reschedule(op);
      if (level.getGameTime() - op.stateEnteredGameTime() > 2000L) {
         beginReturn(level, data, admission, op, "patrol_timeout");
      }
   }

   private static void executeBandit(
      ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, Village source, ActivityOperation op, ActivityProfile profile
   ) {
      if (level.getGameTime() - op.stateEnteredGameTime() > 1600L) {
         // Opportunistic village raid upgrade only if caps allow and target reserved none
         if (profile != null && profile.allows(ActivityKind.VILLAGE_RAID) && op.targetSettlementId() == null) {
            // stay roam; raids are separate proposals
         }
         beginReturn(level, data, admission, op, "roam_done");
         return;
      }
      op.setNextDueGameTime(level.getGameTime() + 40L);
      data.reschedule(op);
   }

   private static void beginReturn(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op, String why) {
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      BlockPos home = source != null ? source.getCenter() : op.lastValidPos();
      if (home != null) {
         op.setCoarseRoute(ActivityPlanner.coarseRoute(op.lastValidPos() != null ? op.lastValidPos() : home, home));
      }
      op.setDeferReason(ActivityReasonCode.NONE);
      op.enterState("retreat".equals(why) ? ActivityState.RETREATING : ActivityState.RETURNING, level.getGameTime());
      data.reschedule(op);
   }

   private static void tickReturn(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      // Reuse travel toward home
      if (op.currentWaypoint() == null) {
         op.enterState(ActivityState.SETTLING, level.getGameTime());
         data.reschedule(op);
         return;
      }
      // Temporarily treat as travelling
      ActivityState prev = op.state();
      op.enterState(ActivityState.TRAVELLING, op.stateEnteredGameTime());
      tickTravel(level, data, admission, RegionalActivityService.get().indexFor(level), op);
      if (op.state() == ActivityState.EXECUTING) {
         op.enterState(ActivityState.SETTLING, level.getGameTime());
         data.reschedule(op);
      } else if (op.state() == ActivityState.TRAVELLING) {
         op.enterState(prev, op.stateEnteredGameTime());
         op.setNextDueGameTime(level.getGameTime() + 10L);
         data.reschedule(op);
      }
   }

   private static void tickSettle(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      if (!admission.trySpendCommit()) {
         op.setNextDueGameTime(level.getGameTime() + 10L);
         data.reschedule(op);
         return;
      }
      if (op.outcomeApplied()) {
         complete(data, admission, op, level.getGameTime(), "already_applied");
         return;
      }
      if (op.cargoAmbiguous()) {
         op.enterState(ActivityState.RECOVERY_REQUIRED, level.getGameTime());
         op.setRecoveryReason("ambiguous_cargo");
         data.reschedule(op);
         ActivityDiagnostics.get().countReason(ActivityReasonCode.RECOVERY_REQUIRED);
         return;
      }
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) {
         failRecovery(data, admission, op, "source_missing_settle", level.getGameTime());
         return;
      }
      String transferId = op.transferId().isEmpty() ? op.operationId().toString() + ":settle" : op.transferId();
      op.setTransferId(transferId);
      // Transfer cargo once into townhall / leader inventory via villager records
      if (!op.cargo().isEmpty()) {
         VillagerRecord carrier = null;
         for (UUID id : op.participantIds()) {
            VillagerRecord r = source.getVillagerRecords().get(id);
            if (r != null && !r.isKilled()) {
               carrier = r;
               break;
            }
         }
         if (carrier != null) {
            for (ItemStack stack : op.cargo()) {
               if (!stack.isEmpty()) {
                  carrier.getInventory().add(stack.getItem(), stack.getCount());
               }
            }
            op.cargo().clear();
         }
      }
      if (op.kind().isVillageRaid()) {
         ActivityRaidBridge.finishExpeditionIfNeeded(level, data, source, op);
      }
      op.setOutcomeApplied(true);
      source.recordChronicleEvent(
         level,
         VillageEventType.STORY,
         "Activity " + op.kind().name(),
         "Completed with " + op.lostMembers() + " losses, prey=" + op.preyTaken()
      );
      data.recordOutcome(
         new ActivityOutcomeSummary(
            op.operationId(),
            op.kind(),
            "completed",
            level.getGameTime(),
            op.sourceId() != null ? op.sourceId().toString() : "",
            op.targetSettlementId() != null ? op.targetSettlementId().toString() : ""
         ),
         MillenaireServerConfig.SERVER.regionalActivityMaxOutcomeHistory.getAsInt()
      );
      complete(data, admission, op, level.getGameTime(), "settled");
      source.markDirty();
   }

   private static void tickRecovery(ServerLevel level, ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      // Idempotent: do not re-pay ambiguous cargo; release reservations and cancel safely
      if (!admission.trySpendCommit()) {
         op.setNextDueGameTime(level.getGameTime() + 40L);
         data.reschedule(op);
         return;
      }
      releaseAll(data, admission, op);
      op.setOutcomeApplied(true);
      op.enterState(ActivityState.CANCELLED, level.getGameTime());
      op.setDeferReason(ActivityReasonCode.RECOVERY_REQUIRED);
      data.reschedule(op);
      data.setDirty();
   }

   private static void complete(ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op, long now, String why) {
      releaseAll(data, admission, op);
      op.enterState(ActivityState.COMPLETED, now);
      data.reschedule(op);
      data.setDirty();
      LOGGER.debug("[Activity] Completed {} ({})", op.operationId(), why);
   }

   private static void cancel(ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op, ActivityReasonCode reason, long now) {
      releaseAll(data, admission, op);
      op.setDeferReason(reason);
      op.enterState(ActivityState.CANCELLED, now);
      data.reschedule(op);
      ActivityDiagnostics.get().countReason(reason);
   }

   private static void failRecovery(ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op, String why, long now) {
      op.setRecoveryReason(why);
      op.enterState(ActivityState.RECOVERY_REQUIRED, now);
      data.reschedule(op);
      ActivityDiagnostics.get().countReason(ActivityReasonCode.RECOVERY_REQUIRED);
   }

   public static void releaseAll(ActivitySavedData data, ActivityAdmissionController admission, ActivityOperation op) {
      Village source = null;
      // Best-effort release without level: admission + operation fields
      for (UUID id : op.participantIds()) {
         admission.releaseParticipant(id);
      }
      if (op.targetReservationId() != null) {
         admission.releaseTarget(op.targetReservationId());
      }
      for (UUID gid : op.groupIds()) {
         data.removeGroup(gid);
         RegionalActivityService.get().removeGroupFromIndexes(gid);
      }
   }

   public static void releaseParticipantsOnLevel(ServerLevel level, ActivityOperation op) {
      Village source = ActivityProjectionService.resolveVillage(level, op.sourceId());
      if (source == null) return;
      for (UUID id : op.participantIds()) {
         VillagerRecord r = source.getVillagerRecords().get(id);
         if (r != null && op.operationId().equals(r.getActivityOperationId())) {
            r.setActivityOperationId(null);
         }
      }
      source.markDirty();
   }
}
