package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * War goals. Territorial conquest goals are resolved by Phase 7 peace/transfer systems.
 */
public enum WarGoalType {
   DEFEAT_ENEMY_ARMY,
   DEFEND_SETTLEMENT,
   DESTROY_MILITARY_FORCE,
   FORCE_PEACE,
   MILITARY_SUPERIORITY,
   /** Capture/occupy a settlement; permanent transfer only via Phase 7 peace. */
   CONQUER_SETTLEMENT,
   /** Liberate occupied friendly territory. */
   LIBERATE_SETTLEMENT,
   /** Raid for spoils without lasting annexation. */
   PLUNDER,
   /** Force a public concession / prestige loss. */
   HUMILIATION,
   /** Compel a trade agreement as peace terms. */
   FORCE_TRADE;

   @Nullable
   public static WarGoalType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return WarGoalType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
