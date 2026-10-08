package org.millenaire.faction.conflict;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PostConflictRecovery {
   private final ConflictId conflictId;
   private final FactionId factionId;
   private final long startedGameTime;
   private long endsGameTime;
   private int remainingStabilityPenalty;
   private int remainingPressureBonus;
   private final Set<PostConflictModifier> modifiers = new LinkedHashSet<>();
   private boolean active = true;
   @Nullable
   private ConflictResolution resolvedAs;

   public PostConflictRecovery(ConflictId conflictId, FactionId factionId, long gameTime, long durationTicks) {
      this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.startedGameTime = gameTime;
      this.endsGameTime = gameTime + Math.max(0L, durationTicks);
   }

   public ConflictId getConflictId() {
      return this.conflictId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getEndsGameTime() {
      return this.endsGameTime;
   }

   public int getRemainingStabilityPenalty() {
      return this.remainingStabilityPenalty;
   }

   public int getRemainingPressureBonus() {
      return this.remainingPressureBonus;
   }

   public Set<PostConflictModifier> getModifiers() {
      return Collections.unmodifiableSet(this.modifiers);
   }

   public boolean isActive() {
      return this.active;
   }

   @Nullable
   public ConflictResolution getResolvedAs() {
      return this.resolvedAs;
   }

   public boolean isComplete(long gameTime) {
      return !this.active || gameTime >= this.endsGameTime;
   }

   void setRemainingStabilityPenalty(int penalty) {
      this.remainingStabilityPenalty = ConflictConfig.clamp(penalty);
   }

   void setRemainingPressureBonus(int bonus) {
      this.remainingPressureBonus = ConflictConfig.clamp(bonus);
   }

   void addModifier(PostConflictModifier modifier) {
      if (modifier != null) {
         this.modifiers.add(modifier);
         this.remainingStabilityPenalty = ConflictConfig.clamp(this.remainingStabilityPenalty + modifier.stabilityPenalty());
         this.remainingPressureBonus = ConflictConfig.clamp(this.remainingPressureBonus + modifier.stabilityPenalty() / 2);
      }
   }

   void setResolvedAs(@Nullable ConflictResolution resolution) {
      this.resolvedAs = resolution;
   }

   void complete() {
      this.active = false;
      this.remainingStabilityPenalty = 0;
      this.remainingPressureBonus = 0;
   }

   void tickDecay() {
      this.remainingStabilityPenalty = Math.max(0, this.remainingStabilityPenalty - 1);
      this.remainingPressureBonus = Math.max(0, this.remainingPressureBonus - 1);
   }

   void loadState(
      long ends,
      int stabilityPenalty,
      int pressureBonus,
      Set<PostConflictModifier> modifiers,
      boolean active,
      @Nullable ConflictResolution resolvedAs
   ) {
      this.endsGameTime = Math.max(0L, ends);
      this.remainingStabilityPenalty = ConflictConfig.clamp(stabilityPenalty);
      this.remainingPressureBonus = ConflictConfig.clamp(pressureBonus);
      this.modifiers.clear();
      if (modifiers != null) {
         this.modifiers.addAll(modifiers);
      }

      this.active = active;
      this.resolvedAs = resolvedAs;
   }
}
