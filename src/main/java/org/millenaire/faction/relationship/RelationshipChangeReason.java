package org.millenaire.faction.relationship;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Extensible categories describing why a relationship score changed.
 * <p>
 * These are infrastructure labels for history and future political systems.
 * Defining {@link #WAR} or {@link #ALLIANCE} does <strong>not</strong> implement war or alliance gameplay.
 */
public enum RelationshipChangeReason {
   GENERIC,
   TRADE,
   GIFT,
   INSULT,
   BORDER_INCIDENT,
   RAID,
   WAR,
   PEACE,
   ALLIANCE,
   DEFENSIVE_AID,
   TERRITORY,
   /** Internal leadership, reform, or coup consequences (Phase 8). */
   POLITICS,
   PLAYER_ACTION,
   SCRIPTED_EVENT,
   /** Authoritative overwrite via {@link RelationshipManager#setRelationship}. */
   ADMIN_SET,
   /** Migration / world repair paths. */
   INITIALIZATION;

   @Nullable
   public static RelationshipChangeReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return RelationshipChangeReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
