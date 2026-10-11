package org.millenaire.civilization.interaction;

public record RumorEntry(
   String type,
   String summary,
   String aboutFactionId,
   long expiresAt,
   float reliability
) {
   public boolean isExpired(long gameTime) {
      return expiresAt > 0L && gameTime >= expiresAt;
   }
}
