package org.millenaire.civilization.interaction;

/** Border pressure 0–100 between two factions. */
public record ClaimHeatEntry(String factionA, String factionB, int heat) {
   public ClaimHeatEntry {
      heat = Math.max(0, Math.min(InteractionConfig.CLAIM_HEAT_MAX, heat));
   }

   public String pairKey() {
      String a = factionA.compareTo(factionB) <= 0 ? factionA : factionB;
      String b = factionA.compareTo(factionB) <= 0 ? factionB : factionA;
      return a + "|" + b;
   }
}
