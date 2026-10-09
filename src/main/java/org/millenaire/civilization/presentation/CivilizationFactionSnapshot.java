package org.millenaire.civilization.presentation;

import java.util.List;
import javax.annotation.Nullable;

/** Server-derived faction overview for panels and NPC context. */
public record CivilizationFactionSnapshot(
   String factionId,
   String displayName,
   @Nullable String leaderName,
   int settlementCount,
   int residents,
   int housingCapacity,
   boolean housingCapacityKnown,
   boolean housingPressure,
   String economyLabelKey,
   boolean atWar,
   int activeTreaties,
   int activeColonizations,
   int activeMigrations,
   List<String> settlementNames,
   List<CivilizationVillageSnapshot.HistoryLine> recentEvents,
   long capturedAtGameTime
) {
}
