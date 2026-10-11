package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;

public record FosterageBond(
   String hostFaction,
   String guestFaction,
   @Nullable String villagerUuid,
   long expiresAt,
   Kind kind
) {
   public enum Kind {
      GUEST,
      FOSTER,
      HOSTAGE
   }

   public boolean isExpired(long gameTime) {
      return expiresAt > 0L && gameTime >= expiresAt;
   }
}
