package org.millenaire.faction.relationship;

/**
 * Descriptive bands on the -100/+100 scale.
 * <p>
 * Categories never trigger wars, alliances, or other political consequences by themselves.
 */
public enum RelationshipCategory {
   VERY_HOSTILE,
   HOSTILE,
   NEGATIVE,
   NEUTRAL,
   POSITIVE,
   FRIENDLY,
   VERY_FRIENDLY;

   public static RelationshipCategory fromScore(int score) {
      int clamped = RelationshipConfig.clampScore(score);
      if (clamped <= RelationshipConfig.THRESHOLD_VERY_HOSTILE) {
         return VERY_HOSTILE;
      }

      if (clamped <= RelationshipConfig.THRESHOLD_HOSTILE) {
         return HOSTILE;
      }

      if (clamped <= RelationshipConfig.THRESHOLD_NEGATIVE) {
         return NEGATIVE;
      }

      if (clamped >= RelationshipConfig.THRESHOLD_VERY_FRIENDLY) {
         return VERY_FRIENDLY;
      }

      if (clamped >= RelationshipConfig.THRESHOLD_FRIENDLY) {
         return FRIENDLY;
      }

      if (clamped >= RelationshipConfig.THRESHOLD_POSITIVE) {
         return POSITIVE;
      }

      return NEUTRAL;
   }

   public boolean isPositive() {
      return this == POSITIVE || this == FRIENDLY || this == VERY_FRIENDLY;
   }

   public boolean isNegative() {
      return this == NEGATIVE || this == HOSTILE || this == VERY_HOSTILE;
   }

   public boolean isHostile() {
      return this == HOSTILE || this == VERY_HOSTILE;
   }

   public boolean isFriendly() {
      return this == FRIENDLY || this == VERY_FRIENDLY;
   }
}
