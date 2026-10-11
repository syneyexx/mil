package org.millenaire.civilization.interaction;

public record CallToArmsOffer(
   String allyFaction,
   String enemyFaction,
   String targetPlayerFaction,
   long expiresAt,
   Status status
) {
   public enum Status {
      PENDING,
      ACCEPTED,
      REFUSED,
      EXPIRED
   }

   public CallToArmsOffer withStatus(Status next) {
      return new CallToArmsOffer(allyFaction, enemyFaction, targetPlayerFaction, expiresAt, next);
   }
}
