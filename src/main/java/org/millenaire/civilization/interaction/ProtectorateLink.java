package org.millenaire.civilization.interaction;

public record ProtectorateLink(
   String suzerain,
   String vassal,
   long tributeDueGameTime,
   boolean active
) {
   public ProtectorateLink withTributeDue(long next) {
      return new ProtectorateLink(suzerain, vassal, next, active);
   }

   public ProtectorateLink withActive(boolean value) {
      return new ProtectorateLink(suzerain, vassal, tributeDueGameTime, value);
   }
}
