package org.millenaire.civilization.interaction;

/**
 * Justifications available when declaring war. Strength is scored by {@link CasusBelliEvaluator}.
 */
public enum CasusBelliType {
   BOUNTY_UNPAID,
   CORRIDOR_DISRUPTION,
   CLAIM_HEAT,
   RAID_REPRISAL,
   BROKEN_TREATY,
   FAMINE_BLOCKADE,
   HONOR_INSULT,
   EXPANSION_PRESSURE
}
