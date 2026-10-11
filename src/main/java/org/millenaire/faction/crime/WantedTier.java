package org.millenaire.faction.crime;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Escalating wanted status derived from culture bounty score.
 */
public enum WantedTier {
   NONE(0),
   SUSPECT(40),
   WANTED(120),
   PUBLIC_ENEMY(250),
   SCOURGE(400);

   private final int minScore;

   WantedTier(int minScore) {
      this.minScore = minScore;
   }

   public int minScore() {
      return this.minScore;
   }

   public boolean isAtLeast(WantedTier other) {
      return other != null && this.ordinal() >= other.ordinal();
   }

   public static WantedTier fromScore(int score) {
      WantedTier best = NONE;
      for (WantedTier tier : values()) {
         if (score >= tier.minScore && tier.ordinal() >= best.ordinal()) {
            best = tier;
         }
      }
      return best;
   }

   public String translationKey() {
      return "millenaire.crime.wanted." + this.name().toLowerCase(Locale.ROOT);
   }

   public static WantedTier tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return NONE;
      }
      try {
         return WantedTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return NONE;
      }
   }
}
