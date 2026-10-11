package org.millenaire.civilization.interaction;

public record EnvoyMission(
   String cultureFaction,
   String playerFaction,
   Agenda agenda,
   long expiresAt,
   boolean pending
) {
   public enum Agenda {
      CHARTER,
      DIASPORA,
      JOINT_RAID,
      TRIBUTE,
      RUMOR
   }

   public EnvoyMission withPending(boolean value) {
      return new EnvoyMission(cultureFaction, playerFaction, agenda, expiresAt, value);
   }

   public boolean isExpired(long gameTime) {
      return expiresAt > 0L && gameTime >= expiresAt;
   }
}
