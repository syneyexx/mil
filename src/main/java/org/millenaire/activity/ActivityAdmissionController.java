package org.millenaire.activity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.combat.war.WarCampaignService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

/**
 * Global/local leases for new regional activity. Caps are not stacked on top of legacy raid/war load —
 * legacy load reduces remaining capacity.
 */
public final class ActivityAdmissionController {
   private int activeGroups;
   private int activeNpcs;
   private int activeViolent;
   private int activeVillageRaids;
   private int activationsThisWindow;
   private long activationWindowStartTick;
   private int materializationsThisTick;
   private int navRequestsThisTick;
   private int candidatesThisTick;
   private int commitsThisTick;
   private int pendingProposals;
   private int pendingRouteJobs;
   private int inFlightJobs;
   private ActivityReasonCode lastDefer = ActivityReasonCode.NONE;
   private final Set<UUID> reservedParticipants = new HashSet<>();
   private final Set<UUID> reservedTargets = new HashSet<>();

   public void resetTickCounters() {
      this.materializationsThisTick = 0;
      this.navRequestsThisTick = 0;
      this.candidatesThisTick = 0;
      this.commitsThisTick = 0;
   }

   public void syncFromSaved(ActivitySavedData data) {
      this.activeGroups = 0;
      this.activeNpcs = 0;
      this.activeViolent = 0;
      this.activeVillageRaids = 0;
      this.reservedParticipants.clear();
      for (ActivityOperation op : data.nonTerminalOperations()) {
         if (op.state().isTerminal()) {
            continue;
         }
         if (op.state().isActivePhysical() || op.state() == ActivityState.RESERVED || op.state() == ActivityState.ASSEMBLING) {
            this.activeGroups++;
            this.activeNpcs += op.participantIds().size();
            if (op.kind().isViolent()) {
               this.activeViolent++;
            }
            if (op.kind().isVillageRaid()) {
               this.activeVillageRaids++;
            }
         }
         this.reservedParticipants.addAll(op.participantIds());
         if (op.targetReservationId() != null) {
            this.reservedTargets.add(op.targetReservationId());
         } else if (op.targetSettlementId() != null && op.kind().isVillageRaid()) {
            this.reservedTargets.add(op.targetSettlementId());
         }
      }
      this.pendingProposals = data.pendingProposalCount();
   }

   public ActivityReasonCode lastDefer() {
      return this.lastDefer;
   }

   public void setLastDefer(ActivityReasonCode code) {
      this.lastDefer = code == null ? ActivityReasonCode.NONE : code;
   }

   public boolean trySpendCandidate() {
      int max = effectiveCandidateCap();
      if (this.candidatesThisTick >= max) {
         this.lastDefer = ActivityReasonCode.TIME_BUDGET;
         return false;
      }
      if (!CommitBudget.get().trySpendCandidate(1)) {
         this.lastDefer = ActivityReasonCode.GLOBAL_BUDGET;
         return false;
      }
      this.candidatesThisTick++;
      return true;
   }

   public boolean trySpendCommit() {
      int max = MillenaireServerConfig.SERVER.regionalActivityMaxCommitStepsPerTick.getAsInt();
      if (this.commitsThisTick >= max) {
         this.lastDefer = ActivityReasonCode.TIME_BUDGET;
         return false;
      }
      if (!CommitBudget.get().trySpendCommit(1)) {
         this.lastDefer = ActivityReasonCode.GLOBAL_BUDGET;
         return false;
      }
      this.commitsThisTick++;
      return true;
   }

   public boolean tryMaterialize() {
      if (!ActivityPressureMonitor.get().state().allowsMaterialization()) {
         this.lastDefer = ActivityReasonCode.SERVER_PRESSURE;
         return false;
      }
      if (this.materializationsThisTick >= MillenaireServerConfig.SERVER.regionalActivityMaxMaterializationsPerTick.getAsInt()) {
         this.lastDefer = ActivityReasonCode.TIME_BUDGET;
         return false;
      }
      this.materializationsThisTick++;
      return true;
   }

   public boolean tryNavRequest() {
      if (this.navRequestsThisTick >= MillenaireServerConfig.SERVER.regionalActivityMaxNavRequestsPerTick.getAsInt()) {
         this.lastDefer = ActivityReasonCode.TIME_BUDGET;
         return false;
      }
      this.navRequestsThisTick++;
      return true;
   }

   public boolean canActivateNewGroup(ServerLevel level, ActivityKind kind, BlockPos regionPos, int memberCount) {
      if (!MillenaireServerConfig.SERVER.regionalActivityEnabled.getAsBoolean()) {
         this.lastDefer = ActivityReasonCode.FEATURE_DISABLED;
         return false;
      }
      ActivityPressureMonitor pressure = ActivityPressureMonitor.get();
      if (pressure.spikeBlocksAdmission() || !pressure.state().allowsNewGroups()) {
         this.lastDefer = ActivityReasonCode.SERVER_PRESSURE;
         return false;
      }
      if (kind.isVillageRaid() && !pressure.state().allowsNewVillageRaids()) {
         this.lastDefer = ActivityReasonCode.SERVER_PRESSURE;
         return false;
      }
      long tick = pressure.serverTickCounter();
      int window = MillenaireServerConfig.SERVER.regionalActivityActivationWindowTicks.getAsInt();
      if (tick - this.activationWindowStartTick >= window) {
         this.activationWindowStartTick = tick;
         this.activationsThisWindow = 0;
      }
      if (this.activationsThisWindow >= MillenaireServerConfig.SERVER.regionalActivityMaxActivationsPerWindow.getAsInt()) {
         this.lastDefer = ActivityReasonCode.ACTIVATION_RATE;
         return false;
      }
      int legacyUniqueRaids = RaidManager.countUniqueActiveRaids(VillageSavedData.get(level).getVillageManager());
      int warLoad = WarCampaignService.countActiveCampaignLoad(level);
      int groupCap = MillenaireServerConfig.SERVER.regionalActivityMaxActiveGroups.getAsInt();
      int npcCap = MillenaireServerConfig.SERVER.regionalActivityMaxActiveNpcs.getAsInt();
      int violentCap = MillenaireServerConfig.SERVER.regionalActivityMaxViolentIncidents.getAsInt();
      int raidCap = Math.min(
         MillenaireServerConfig.SERVER.regionalActivityMaxVillageRaids.getAsInt(),
         MillenaireServerConfig.SERVER.maxConcurrentRaids.getAsInt()
      );
      int occupiedGroups = this.activeGroups + Math.min(legacyUniqueRaids + warLoad, groupCap);
      if (occupiedGroups >= groupCap) {
         this.lastDefer = ActivityReasonCode.GROUP_CAP;
         return false;
      }
      if (this.activeNpcs + memberCount > npcCap) {
         this.lastDefer = ActivityReasonCode.ENTITY_CAP;
         return false;
      }
      if (kind.isViolent() && this.activeViolent + legacyUniqueRaids >= violentCap) {
         this.lastDefer = ActivityReasonCode.RAID_CAP;
         return false;
      }
      if (kind.isVillageRaid() && (this.activeVillageRaids + legacyUniqueRaids) >= raidCap) {
         this.lastDefer = ActivityReasonCode.RAID_CAP;
         return false;
      }
      if (regionPos != null) {
         ActivitySpatialIndex index = RegionalActivityService.get().indexFor(level);
         int regionCap = MillenaireServerConfig.SERVER.regionalActivityMaxGroupsPerRegion.getAsInt();
         if (index.countGroupsInCellAndNeighbors(regionPos) >= regionCap) {
            this.lastDefer = ActivityReasonCode.REGION_CAP;
            return false;
         }
      }
      if (this.pendingProposals >= MillenaireServerConfig.SERVER.regionalActivityMaxPendingProposals.getAsInt()) {
         this.lastDefer = ActivityReasonCode.QUEUE_FULL;
         return false;
      }
      return true;
   }

   public void noteActivation() {
      this.activationsThisWindow++;
      this.activeGroups++;
   }

   public boolean isParticipantReserved(UUID id) {
      return this.reservedParticipants.contains(id);
   }

   public boolean reserveParticipant(UUID id) {
      return this.reservedParticipants.add(id);
   }

   public void releaseParticipant(UUID id) {
      this.reservedParticipants.remove(id);
   }

   public boolean isTargetReserved(UUID id) {
      return this.reservedTargets.contains(id);
   }

   public boolean reserveTarget(UUID id) {
      return this.reservedTargets.add(id);
   }

   public void releaseTarget(UUID id) {
      this.reservedTargets.remove(id);
   }

   public int activeGroups() {
      return this.activeGroups;
   }

   public int activeNpcs() {
      return this.activeNpcs;
   }

   public int activeViolent() {
      return this.activeViolent;
   }

   public int activeVillageRaids() {
      return this.activeVillageRaids;
   }

   public int candidatesThisTick() {
      return this.candidatesThisTick;
   }

   public int commitsThisTick() {
      return this.commitsThisTick;
   }

   public int materializationsThisTick() {
      return this.materializationsThisTick;
   }

   public int navRequestsThisTick() {
      return this.navRequestsThisTick;
   }

   private int effectiveCandidateCap() {
      int base = MillenaireServerConfig.SERVER.regionalActivityMaxCandidatesPerTick.getAsInt();
      int scale = ActivityPressureMonitor.get().state().candidateScalePercent();
      return Math.max(0, base * scale / 100);
   }

   public static int countLegacyLoad(ServerLevel level) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      return RaidManager.countUniqueActiveRaids(vm) + WarCampaignService.countActiveCampaignLoad(level);
   }

   public String statusLine() {
      return "groups="
         + this.activeGroups
         + "/"
         + MillenaireServerConfig.SERVER.regionalActivityMaxActiveGroups.getAsInt()
         + " npcs="
         + this.activeNpcs
         + "/"
         + MillenaireServerConfig.SERVER.regionalActivityMaxActiveNpcs.getAsInt()
         + " violent="
         + this.activeViolent
         + " raids="
         + this.activeVillageRaids
         + " defer="
         + this.lastDefer;
   }
}
