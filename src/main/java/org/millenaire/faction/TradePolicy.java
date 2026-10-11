package org.millenaire.faction;

import javax.annotation.Nullable;

/**
 * Controls automatic settlement logistics for a PLAYER faction.
 * <ul>
 *   <li>{@link #OPEN} — may import and export via auto-trade</li>
 *   <li>{@link #INBOUND_ONLY} — may receive goods; chests are not auto-exported</li>
 *   <li>{@link #CLOSED} — no automatic settlement trade</li>
 * </ul>
 */
public enum TradePolicy {
   OPEN,
   INBOUND_ONLY,
   CLOSED;

   public static TradePolicy tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return INBOUND_ONLY;
      }
      try {
         return valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return INBOUND_ONLY;
      }
   }

   public boolean allowsImport() {
      return this == OPEN || this == INBOUND_ONLY;
   }

   public boolean allowsExport() {
      return this == OPEN;
   }
}
