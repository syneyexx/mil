package org.millenaire.faction.politics;

import java.util.Objects;

public final class PoliticalInterest {
   private final InterestType type;
   private int strength;

   public PoliticalInterest(InterestType type, int strength) {
      this.type = Objects.requireNonNull(type, "type");
      this.strength = PoliticsConfig.clamp(strength);
   }

   public InterestType getType() {
      return this.type;
   }

   public int getStrength() {
      return this.strength;
   }

   void setStrength(int strength) {
      this.strength = PoliticsConfig.clamp(strength);
   }
}
