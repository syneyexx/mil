package org.millenaire.faction;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Extensible faction classification for Phase 1.
 * <p>
 * Additional types may be introduced in later phases by adding constants and persistence handling.
 * Culture and political identity remain separate: a {@link #CULTURE} faction references a Millénaire
 * culture but is not itself a culture definition.
 */
public enum FactionType {
   /**
    * NPC / culture-backed political entity. Typically auto-created per culture for default
    * settlement association, but multiple factions may share one culture.
    */
   CULTURE,

   /**
    * Player-owned political entity. Architecture support only in Phase 1; settlement creation
    * for player factions belongs to later phases.
    */
   PLAYER,

   /**
    * Breakaway / rebel faction created by Phase 9 secession or revolutionary split.
    * Culture is inherited from the parent population; identity is political.
    */
   BREAKAWAY;

   @Nullable
   public static FactionType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return FactionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
