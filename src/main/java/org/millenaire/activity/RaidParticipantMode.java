package org.millenaire.activity;

/** Explicit raider representation for village raids. Never mix both in one operation. */
public enum RaidParticipantMode {
   LEGACY_CLONES,
   EXPEDITION_MEMBERS;

   public static RaidParticipantMode fromId(String id) {
      if (id != null && id.equalsIgnoreCase("EXPEDITION_MEMBERS")) {
         return EXPEDITION_MEMBERS;
      }
      return LEGACY_CLONES;
   }
}
