package org.millenaire.activity;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;

public final class ActivityDiagnostics {
   private static final ActivityDiagnostics INSTANCE = new ActivityDiagnostics();
   private final Map<ActivityReasonCode, Integer> reasonCounts = new EnumMap<>(ActivityReasonCode.class);
   private long coordinatorNanos;
   private long lastCoordinatorNanos;
   private boolean runtimeEnabled = true;

   private ActivityDiagnostics() {}

   public static ActivityDiagnostics get() {
      return INSTANCE;
   }

   public void countReason(ActivityReasonCode code) {
      if (code == null || code == ActivityReasonCode.NONE) return;
      this.reasonCounts.merge(code, 1, Integer::sum);
   }

   public void addCoordinatorNanos(long nanos) {
      this.coordinatorNanos += nanos;
      this.lastCoordinatorNanos = nanos;
   }

   public long lastCoordinatorNanos() {
      return this.lastCoordinatorNanos;
   }

   public Map<ActivityReasonCode, Integer> reasonCounts() {
      return Map.copyOf(this.reasonCounts);
   }

   public boolean runtimeEnabled() {
      return this.runtimeEnabled;
   }

   public void setRuntimeEnabled(boolean v) {
      this.runtimeEnabled = v;
   }

   public void resetRuntime() {
      this.reasonCounts.clear();
      this.coordinatorNanos = 0L;
      this.lastCoordinatorNanos = 0L;
   }

   public String status(ServerLevel level) {
      ActivityPressureMonitor p = ActivityPressureMonitor.get();
      ActivityAdmissionController a = RegionalActivityService.get().admission();
      return "enabled="
         + (this.runtimeEnabled && org.millenaire.config.MillenaireServerConfig.SERVER.regionalActivityEnabled.getAsBoolean())
         + " pressure="
         + p.state()
         + " ewma="
         + String.format("%.2f", p.ewma())
         + " p95="
         + String.format("%.2f", p.p95())
         + " samples="
         + p.sampleCount()
         + " "
         + a.statusLine()
         + " legacyLoad="
         + ActivityAdmissionController.countLegacyLoad(level)
         + " coordMs="
         + String.format("%.3f", this.lastCoordinatorNanos / 1_000_000.0)
         + " navMs="
         + String.format("%.3f", ActivityNavigationService.get().navNanosThisTick() / 1_000_000.0);
   }

   public String metrics() {
      StringBuilder sb = new StringBuilder("reasons:");
      for (var e : this.reasonCounts.entrySet()) {
         sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
      }
      return sb.toString();
   }

   @Nullable
   public String inspect(ServerLevel level, UUID opId) {
      ActivityOperation op = ActivitySavedData.get(level).getOperation(opId);
      if (op == null) return null;
      return "op="
         + op.operationId()
         + " kind="
         + op.kind()
         + " state="
         + op.state()
         + " source="
         + op.sourceId()
         + " target="
         + op.targetSettlementId()
         + " members="
         + op.participantIds().size()
         + " lost="
         + op.lostMembers()
         + " defer="
         + op.deferReason()
         + " raidMode="
         + op.raidMode()
         + " outcome="
         + op.outcomeApplied()
         + " wp="
         + op.waypointIndex()
         + "/"
         + op.coarseRoute().size();
   }

   public String explain(ServerLevel level, UUID settlementOrCampId) {
      ActivitySavedData data = ActivitySavedData.get(level);
      ActivityAdmissionController admission = RegionalActivityService.get().admission();
      StringBuilder sb = new StringBuilder();
      sb.append("id=").append(settlementOrCampId);
      if (data.sourceOnCooldown(settlementOrCampId.toString(), level.getGameTime())) {
         sb.append(" sourceCooldown=yes");
      }
      if (data.targetOnCooldown(settlementOrCampId.toString(), level.getGameTime())) {
         sb.append(" targetCooldown=yes");
      }
      if (data.inGrace(settlementOrCampId, level.getGameTime())) {
         sb.append(" grace=yes");
      }
      int active = 0;
      for (ActivityOperation op : data.nonTerminalOperations()) {
         if (settlementOrCampId.equals(op.sourceId()) || settlementOrCampId.equals(op.targetSettlementId())) {
            active++;
            sb.append(" op=").append(op.kind()).append('/').append(op.state()).append('/').append(op.deferReason());
         }
      }
      if (active == 0) {
         sb.append(" activeOps=0 lastDefer=").append(admission.lastDefer());
         ActivitySpatialIndex.IndexedSettlement s = RegionalActivityService.get().indexFor(level).getSettlement(settlementOrCampId);
         if (s != null && !s.camp()) {
            boolean anyCamp = false;
            for (ActivitySpatialIndex.IndexedSettlement o : RegionalActivityService.get().indexFor(level).allSettlements()) {
               if (o.camp()) {
                  anyCamp = true;
                  break;
               }
            }
            if (!anyCamp) {
               sb.append(" ").append(ActivityReasonCode.NO_SOURCE_CAMP);
            }
         }
         sb.append(" tip=check reason codes via /millenaire activity metrics");
      }
      return sb.toString();
   }
}
