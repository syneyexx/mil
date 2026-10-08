package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PoliticalCrisis {
   private final CrisisId id;
   private final FactionId factionId;
   private final CrisisType type;
   private final String cause;
   private final Set<PoliticalGroupId> involvedGroupIds = new LinkedHashSet<>();
   private int severity;
   private CrisisStage stage = CrisisStage.TENSION;
   private final long startedGameTime;
   private long resolvedGameTime;
   private boolean active = true;
   @Nullable
   private String resolutionPath;
   @Nullable
   private String outcome;

   public PoliticalCrisis(CrisisId id, FactionId factionId, CrisisType type, String cause, long gameTime, int severity) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.type = type == null ? CrisisType.LEGITIMACY_CRISIS : type;
      this.cause = cause == null ? "unspecified" : cause;
      this.startedGameTime = gameTime;
      this.severity = PoliticsConfig.clamp(severity);
   }

   public CrisisId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public CrisisType getType() {
      return this.type;
   }

   public String getCause() {
      return this.cause;
   }

   public Set<PoliticalGroupId> getInvolvedGroupIds() {
      return Collections.unmodifiableSet(this.involvedGroupIds);
   }

   public int getSeverity() {
      return this.severity;
   }

   public CrisisStage getStage() {
      return this.stage;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getResolvedGameTime() {
      return this.resolvedGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   @Nullable
   public String getResolutionPath() {
      return this.resolutionPath;
   }

   @Nullable
   public String getOutcome() {
      return this.outcome;
   }

   boolean addInvolvedGroup(PoliticalGroupId groupId) {
      return groupId != null && this.involvedGroupIds.add(groupId);
   }

   void setSeverity(int severity) {
      this.severity = PoliticsConfig.clamp(severity);
   }

   void setStage(CrisisStage stage) {
      this.stage = stage == null ? CrisisStage.TENSION : stage;
   }

   void escalate() {
      this.stage = this.stage.escalate();
      this.severity = PoliticsConfig.clamp(this.severity + 10);
   }

   void deescalate() {
      this.stage = this.stage.deescalate();
      this.severity = PoliticsConfig.clamp(this.severity - 10);
   }

   void resolve(long gameTime, @Nullable String resolutionPath, @Nullable String outcome) {
      this.active = false;
      this.resolvedGameTime = gameTime;
      this.resolutionPath = resolutionPath;
      this.outcome = outcome;
      this.stage = CrisisStage.NORMAL;
   }

   void loadState(
      Set<PoliticalGroupId> involved,
      int severity,
      CrisisStage stage,
      boolean active,
      long resolved,
      @Nullable String resolutionPath,
      @Nullable String outcome
   ) {
      this.involvedGroupIds.clear();
      if (involved != null) {
         this.involvedGroupIds.addAll(involved);
      }

      this.severity = PoliticsConfig.clamp(severity);
      this.stage = stage == null ? CrisisStage.TENSION : stage;
      this.active = active;
      this.resolvedGameTime = Math.max(0L, resolved);
      this.resolutionPath = resolutionPath;
      this.outcome = outcome;
   }
}
