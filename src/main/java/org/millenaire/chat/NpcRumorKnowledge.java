package org.millenaire.chat;

import java.util.UUID;
import javax.annotation.Nullable;

public record NpcRumorKnowledge(
   long rumorId,
   UUID villager,
   @Nullable UUID toldBy,
   int hop,
   float confidence,
   long learnedGameTime,
   long learnedDay
) {
}
