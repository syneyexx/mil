package org.millenaire.civilization.interaction;

public record MercenaryContract(
   String employerFaction,
   String providerFaction,
   long expiresAt,
   boolean active
) {
   public MercenaryContract withActive(boolean value) {
      return new MercenaryContract(employerFaction, providerFaction, expiresAt, value);
   }
}
