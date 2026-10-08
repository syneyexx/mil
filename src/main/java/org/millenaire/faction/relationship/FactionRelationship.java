package org.millenaire.faction.relationship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.millenaire.faction.FactionId;

/**
 * Persistent directional political opinion of {@code source} toward {@code target}.
 * <p>
 * Mutated only through {@link RelationshipManager}. Distinct from Millénaire village/player
 * reputation systems.
 */
public final class FactionRelationship {
   private final FactionId sourceFactionId;
   private final FactionId targetFactionId;
   private int score;
   private long lastMeaningfulChangeGameTime;
   private long lastUpdatedGameTime;
   private long lastDecayGameTime;
   private final Deque<RelationshipHistoryEntry> history = new ArrayDeque<>();

   FactionRelationship(FactionId sourceFactionId, FactionId targetFactionId, int score, long gameTime) {
      this.sourceFactionId = Objects.requireNonNull(sourceFactionId, "sourceFactionId");
      this.targetFactionId = Objects.requireNonNull(targetFactionId, "targetFactionId");
      if (sourceFactionId.equals(targetFactionId)) {
         throw new IllegalArgumentException("Self-relationships are not allowed: " + sourceFactionId);
      }

      this.score = RelationshipConfig.clampScore(score);
      this.lastMeaningfulChangeGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
      this.lastDecayGameTime = gameTime;
   }

   public FactionId getSourceFactionId() {
      return this.sourceFactionId;
   }

   public FactionId getTargetFactionId() {
      return this.targetFactionId;
   }

   public RelationshipKey getKey() {
      return RelationshipKey.of(this.sourceFactionId, this.targetFactionId);
   }

   public int getScore() {
      return this.score;
   }

   public RelationshipCategory getCategory() {
      return RelationshipCategory.fromScore(this.score);
   }

   public long getLastMeaningfulChangeGameTime() {
      return this.lastMeaningfulChangeGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public long getLastDecayGameTime() {
      return this.lastDecayGameTime;
   }

   public List<RelationshipHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public int getHistorySize() {
      return this.history.size();
   }

   public boolean hasHistory() {
      return !this.history.isEmpty();
   }

   void applyScoreChange(int newScore, long gameTime, boolean meaningful) {
      this.score = RelationshipConfig.clampScore(newScore);
      this.lastUpdatedGameTime = gameTime;
      if (meaningful) {
         this.lastMeaningfulChangeGameTime = gameTime;
      }
   }

   void setDecayBookmark(long gameTime) {
      this.lastDecayGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   void recordHistory(RelationshipHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);

      while (this.history.size() > RelationshipConfig.MAX_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void replaceHistory(List<RelationshipHistoryEntry> entries) {
      this.history.clear();
      if (entries == null) {
         return;
      }

      for (RelationshipHistoryEntry entry : entries) {
         if (entry != null) {
            this.history.addLast(entry);
         }
      }

      while (this.history.size() > RelationshipConfig.MAX_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadTimestamps(long lastMeaningfulChangeGameTime, long lastUpdatedGameTime, long lastDecayGameTime) {
      this.lastMeaningfulChangeGameTime = lastMeaningfulChangeGameTime;
      this.lastUpdatedGameTime = lastUpdatedGameTime;
      this.lastDecayGameTime = lastDecayGameTime;
   }

   @Override
   public String toString() {
      return "FactionRelationship{" + this.sourceFactionId + "→" + this.targetFactionId + "=" + this.score + "}";
   }
}
