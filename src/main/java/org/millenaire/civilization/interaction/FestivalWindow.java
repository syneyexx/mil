package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;

public record FestivalWindow(
   String cultureId,
   String factionId,
   long startGameTime,
   long endGameTime,
   @Nullable String hostVillage
) {
   public boolean isActive(long gameTime) {
      return gameTime >= startGameTime && gameTime < endGameTime;
   }
}
