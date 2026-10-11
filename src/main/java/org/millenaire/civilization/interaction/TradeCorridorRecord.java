package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;

/**
 * Accumulated successful cross-faction transfers between two settlements.
 */
public record TradeCorridorRecord(
   String sourceFaction,
   String targetFaction,
   String sourceVillage,
   String targetVillage,
   int successfulDeliveries,
   boolean recognized,
   boolean disrupted,
   @Nullable String nameKey
) {
   public TradeCorridorRecord withDeliveries(int deliveries) {
      return new TradeCorridorRecord(
         sourceFaction, targetFaction, sourceVillage, targetVillage, deliveries, recognized, disrupted, nameKey
      );
   }

   public TradeCorridorRecord withRecognized(boolean value) {
      return new TradeCorridorRecord(
         sourceFaction, targetFaction, sourceVillage, targetVillage, successfulDeliveries, value, disrupted, nameKey
      );
   }

   public TradeCorridorRecord withDisrupted(boolean value) {
      return new TradeCorridorRecord(
         sourceFaction, targetFaction, sourceVillage, targetVillage, successfulDeliveries, recognized, value, nameKey
      );
   }

   public String pairKey() {
      return sourceFaction + "|" + targetFaction + "|" + sourceVillage + "|" + targetVillage;
   }

   public String factionPairKey() {
      String a = sourceFaction.compareTo(targetFaction) <= 0 ? sourceFaction : targetFaction;
      String b = sourceFaction.compareTo(targetFaction) <= 0 ? targetFaction : sourceFaction;
      return a + "|" + b;
   }
}
