package org.millenaire.faction.relationship;

import java.util.Objects;

/**
 * One meaningful scored change in a directional faction relationship.
 * This history is the Phase 2 foundation for later political memory.
 */
public final class RelationshipHistoryEntry {
   private final long gameTime;
   private final RelationshipChangeReason reason;
   private final int delta;
   private final int previousScore;
   private final int newScore;

   public RelationshipHistoryEntry(long gameTime, RelationshipChangeReason reason, int delta, int previousScore, int newScore) {
      this.gameTime = gameTime;
      this.reason = Objects.requireNonNull(reason, "reason");
      this.delta = delta;
      this.previousScore = RelationshipConfig.clampScore(previousScore);
      this.newScore = RelationshipConfig.clampScore(newScore);
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public RelationshipChangeReason getReason() {
      return this.reason;
   }

   public int getDelta() {
      return this.delta;
   }

   public int getPreviousScore() {
      return this.previousScore;
   }

   public int getNewScore() {
      return this.newScore;
   }

   @Override
   public String toString() {
      return "RelationshipHistoryEntry{t="
         + this.gameTime
         + ", reason="
         + this.reason
         + ", delta="
         + this.delta
         + ", "
         + this.previousScore
         + "→"
         + this.newScore
         + "}";
   }
}
