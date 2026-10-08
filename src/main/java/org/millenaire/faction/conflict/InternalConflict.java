package org.millenaire.faction.conflict;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.WarId;

/**
 * Authoritative internal conflict record. Military battles reuse Phase 6 via linked {@link WarId}.
 */
public final class InternalConflict {
   private final ConflictId id;
   private final FactionId originFactionId;
   private ConflictType type;
   private ConflictStage stage = ConflictStage.POLITICAL_TENSION;
   private final long startedGameTime;
   private long lastUpdatedGameTime;
   private long endedGameTime;
   private final Map<ConflictSideId, ConflictSide> sides = new LinkedHashMap<>();
   @Nullable
   private ConflictSideId governmentSideId;
   @Nullable
   private ConflictSideId primaryRebelSideId;
   @Nullable
   private FactionId rebelFactionId;
   @Nullable
   private WarId linkedWarId;
   @Nullable
   private ConflictResolution resolution;
   @Nullable
   private String causeDetail;
   private boolean negotiating;
   private boolean active = true;
   private final Set<UnrestId> relatedUnrestIds = new LinkedHashSet<>();
   private final Set<PostConflictModifier> postModifiers = new LinkedHashSet<>();
   private final Deque<ConflictHistoryEntry> history = new ArrayDeque<>();
   private long recoveryUntilGameTime;

   public InternalConflict(ConflictId id, FactionId originFactionId, ConflictType type, long gameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.originFactionId = Objects.requireNonNull(originFactionId, "originFactionId");
      this.type = type == null ? ConflictType.REBELLION : type;
      this.startedGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   public ConflictId getId() {
      return this.id;
   }

   public FactionId getOriginFactionId() {
      return this.originFactionId;
   }

   public ConflictType getType() {
      return this.type;
   }

   public ConflictStage getStage() {
      return this.stage;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   public Map<ConflictSideId, ConflictSide> getSides() {
      return Collections.unmodifiableMap(this.sides);
   }

   @Nullable
   public ConflictSideId getGovernmentSideId() {
      return this.governmentSideId;
   }

   @Nullable
   public ConflictSideId getPrimaryRebelSideId() {
      return this.primaryRebelSideId;
   }

   @Nullable
   public FactionId getRebelFactionId() {
      return this.rebelFactionId;
   }

   @Nullable
   public WarId getLinkedWarId() {
      return this.linkedWarId;
   }

   @Nullable
   public ConflictResolution getResolution() {
      return this.resolution;
   }

   @Nullable
   public String getCauseDetail() {
      return this.causeDetail;
   }

   public boolean isNegotiating() {
      return this.negotiating;
   }

   public boolean isActive() {
      return this.active && this.stage.isActive();
   }

   /** Raw persistence flag (distinct from {@link #isActive()}, which also requires an active stage). */
   boolean isMarkedActive() {
      return this.active;
   }

   public Set<UnrestId> getRelatedUnrestIds() {
      return Collections.unmodifiableSet(this.relatedUnrestIds);
   }

   public Set<PostConflictModifier> getPostModifiers() {
      return Collections.unmodifiableSet(this.postModifiers);
   }

   public List<ConflictHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public long getRecoveryUntilGameTime() {
      return this.recoveryUntilGameTime;
   }

   @Nullable
   public ConflictSide getGovernmentSide() {
      return this.governmentSideId == null ? null : this.sides.get(this.governmentSideId);
   }

   @Nullable
   public ConflictSide getPrimaryRebelSide() {
      return this.primaryRebelSideId == null ? null : this.sides.get(this.primaryRebelSideId);
   }

   void setType(ConflictType type) {
      this.type = type == null ? ConflictType.REBELLION : type;
   }

   void setStage(ConflictStage stage, long gameTime) {
      this.stage = stage == null ? ConflictStage.UNREST : stage;
      this.lastUpdatedGameTime = gameTime;
   }

   void putSide(ConflictSide side) {
      if (side == null) {
         return;
      }

      this.sides.put(side.getId(), side);
      if (side.isGovernmentSide()) {
         this.governmentSideId = side.getId();
      } else if (this.primaryRebelSideId == null) {
         this.primaryRebelSideId = side.getId();
      }
   }

   void setRebelFactionId(@Nullable FactionId rebelFactionId) {
      this.rebelFactionId = rebelFactionId;
   }

   void setLinkedWarId(@Nullable WarId warId) {
      this.linkedWarId = warId;
   }

   void setCauseDetail(@Nullable String detail) {
      this.causeDetail = detail;
   }

   void setNegotiating(boolean negotiating) {
      this.negotiating = negotiating;
   }

   void addRelatedUnrest(UnrestId unrestId) {
      if (unrestId != null) {
         this.relatedUnrestIds.add(unrestId);
      }
   }

   void addPostModifier(PostConflictModifier modifier) {
      if (modifier != null) {
         this.postModifiers.add(modifier);
      }
   }

   void resolve(ConflictResolution resolution, long gameTime) {
      this.resolution = resolution;
      this.active = false;
      this.negotiating = false;
      this.stage = ConflictStage.POST_CONFLICT_SETTLEMENT;
      this.endedGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
      this.recoveryUntilGameTime = gameTime + ConflictConfig.POST_CONFLICT_RECOVERY_TICKS;
   }

   void markEnded(long gameTime) {
      this.active = false;
      this.stage = ConflictStage.ENDED;
      this.lastUpdatedGameTime = gameTime;
   }

   void recordHistory(ConflictHistoryEntry entry) {
      if (entry == null) {
         return;
      }

      this.history.addLast(entry);
      while (this.history.size() > 256) {
         this.history.removeFirst();
      }
   }

   void loadState(
      ConflictType type,
      ConflictStage stage,
      long lastUpdated,
      long ended,
      @Nullable ConflictSideId governmentSideId,
      @Nullable ConflictSideId primaryRebelSideId,
      @Nullable FactionId rebelFactionId,
      @Nullable WarId linkedWarId,
      @Nullable ConflictResolution resolution,
      @Nullable String causeDetail,
      boolean negotiating,
      boolean active,
      Set<UnrestId> unrestIds,
      Set<PostConflictModifier> modifiers,
      long recoveryUntil,
      List<ConflictHistoryEntry> history,
      Map<ConflictSideId, ConflictSide> sides
   ) {
      this.type = type == null ? ConflictType.REBELLION : type;
      this.stage = stage == null ? ConflictStage.UNREST : stage;
      this.lastUpdatedGameTime = Math.max(0L, lastUpdated);
      this.endedGameTime = Math.max(0L, ended);
      this.governmentSideId = governmentSideId;
      this.primaryRebelSideId = primaryRebelSideId;
      this.rebelFactionId = rebelFactionId;
      this.linkedWarId = linkedWarId;
      this.resolution = resolution;
      this.causeDetail = causeDetail;
      this.negotiating = negotiating;
      this.active = active;
      this.relatedUnrestIds.clear();
      if (unrestIds != null) {
         this.relatedUnrestIds.addAll(unrestIds);
      }

      this.postModifiers.clear();
      if (modifiers != null) {
         this.postModifiers.addAll(modifiers);
      }

      this.recoveryUntilGameTime = Math.max(0L, recoveryUntil);
      this.history.clear();
      if (history != null) {
         for (ConflictHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }

      this.sides.clear();
      if (sides != null) {
         this.sides.putAll(sides);
      }
   }
}
