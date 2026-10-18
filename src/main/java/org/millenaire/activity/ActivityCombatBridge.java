package org.millenaire.activity;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/** Connects hostile target selection / outcomes to existing combat AI without replacing it. */
public final class ActivityCombatBridge {
   private ActivityCombatBridge() {}

   public static void tickGroupCombat(
      ServerLevel level,
      ActivitySavedData data,
      ActivitySpatialIndex index,
      ActivityOperation op,
      ActivityGroup group,
      ActivityProfile profile
   ) {
      if (group == null || profile == null) return;
      long now = level.getGameTime();
      if (now - group.lastScanGameTime() < 20L) {
         return;
      }
      group.setLastScanGameTime(now);
      UUID leaderId = group.leaderId() != null ? group.leaderId() : (group.memberIds().isEmpty() ? null : group.memberIds().getFirst());
      if (leaderId == null) return;
      if (!(level.getEntity(leaderId) instanceof MillVillager leader)) {
         return;
      }
      BlockPos pos = leader.blockPosition();
      index.upsertGroup(group.groupId(), pos);
      // Local encounter with rival groups
      List<UUID> nearbyGroups = index.groupsNear(pos, 0);
      for (UUID otherGid : nearbyGroups) {
         if (otherGid.equals(group.groupId())) continue;
         ActivityGroup other = data.getGroup(otherGid);
         if (other == null) continue;
         ActivityOperation otherOp = data.getOperation(other.operationId());
         if (otherOp == null || otherOp.state().isTerminal()) continue;
         if (!areHostile(data, op, otherOp, profile)) continue;
         if (pos.distSqr(other.rallyPos() != null ? other.rallyPos() : pos) > 32 * 32) continue;
         engage(level, op, otherOp, leader);
         maybeRetreat(op, profile, now);
         maybeRetreat(otherOp, ActivityProfileLoader.byId(otherOp.profileId()), now);
         break;
      }
      // Bandits may pursue nearby travellers (MillVillagers not in same org)
      if (op.kind() == ActivityKind.BANDIT_ROAM || op.kind() == ActivityKind.AMBUSH) {
         AABB box = leader.getBoundingBox().inflate(24.0);
         for (MillVillager other : level.getEntitiesOfClass(MillVillager.class, box, e -> e.isAlive() && e != leader)) {
            if (op.participantIds().contains(other.getUUID())) continue;
            if (other.getVillageId() != null && other.getVillageId().equals(leader.getVillageId())) continue;
            leader.setTarget(other);
            op.setChaseDeadlineGameTime(now + 400L);
            break;
         }
      }
      if (op.chaseDeadlineGameTime() > 0 && now > op.chaseDeadlineGameTime()) {
         leader.setTarget(null);
         op.setChaseDeadlineGameTime(0L);
      }
   }

   private static boolean areHostile(ActivitySavedData data, ActivityOperation a, ActivityOperation b, ActivityProfile profileA) {
      if (a.operationId().equals(b.operationId())) return false;
      ActivityProfile profileB = ActivityProfileLoader.byId(b.profileId());
      if (profileA != null && profileB != null) {
         if (profileA.rivalOrganizations().contains(profileB.organizationId())) return true;
         if (profileB.rivalOrganizations().contains(profileA.organizationId())) return true;
         if (data.rivalry(profileA.organizationId(), profileB.organizationId()) > 0) return true;
      }
      if ("active_attack".equals(a.hostilityReason()) || "active_attack".equals(b.hostilityReason())) return true;
      return a.kind().isViolent() && b.kind().isViolent() && !sameOrg(a, b);
   }

   private static boolean sameOrg(ActivityOperation a, ActivityOperation b) {
      return a.organizationId() != null && !a.organizationId().isEmpty() && a.organizationId().equals(b.organizationId());
   }

   private static void engage(ServerLevel level, ActivityOperation a, ActivityOperation b, MillVillager leader) {
      a.setHostilityReason("active_attack");
      b.setHostilityReason("active_attack");
      if (a.state() != ActivityState.EXECUTING && a.kind() != ActivityKind.VILLAGE_RAID) {
         a.enterState(ActivityState.EXECUTING, level.getGameTime());
      }
      if (b.state() != ActivityState.EXECUTING && b.kind() != ActivityKind.VILLAGE_RAID) {
         b.enterState(ActivityState.EXECUTING, level.getGameTime());
      }
      // Existing HostileVillagerGoal / EngageTargetGoal apply real damage.
      for (UUID id : b.participantIds()) {
         if (level.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
            leader.setTarget(living);
            break;
         }
      }
   }

   private static void maybeRetreat(ActivityOperation op, ActivityProfile profile, long now) {
      if (op == null || profile == null) return;
      if (op.initialMemberCount() <= 0) return;
      float lostFrac = op.lostMembers() / (float) op.initialMemberCount();
      if (lostFrac >= profile.retreatThreshold()) {
         op.enterState(ActivityState.RETREATING, now);
      }
   }

   private static VillagerRecord findRecord(ServerLevel level, UUID id) {
      for (Village v : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         VillagerRecord r = v.getVillagerRecords().get(id);
         if (r != null) return r;
      }
      return null;
   }

   public static void onParticipantDeath(ServerLevel level, UUID villagerId, ActivitySavedData data) {
      for (ActivityOperation op : data.nonTerminalOperations()) {
         if (!op.participantIds().contains(villagerId)) continue;
         op.incrementLostMembers();
         ActivityProfile profile = ActivityProfileLoader.byId(op.profileId());
         maybeRetreat(op, profile, level.getGameTime());
         // Release reservation for the dead member; no clone replacement
         VillagerRecord rec = findRecord(level, villagerId);
         if (rec != null) {
            rec.setActivityOperationId(null);
         }
         data.setDirty();
         ActivityDiagnostics.get().countReason(ActivityReasonCode.RECOVERY_REQUIRED);
         break;
      }
   }
}
