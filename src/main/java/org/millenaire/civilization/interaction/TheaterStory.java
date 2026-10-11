package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;

/**
 * Chronicle theater entry for World Status / legitimacy narratives.
 */
public record TheaterStory(
   String type,
   String summaryKey,
   String factionA,
   @Nullable String factionB,
   int severity,
   long gameTime
) {
}
