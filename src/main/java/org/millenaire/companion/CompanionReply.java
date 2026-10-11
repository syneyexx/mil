package org.millenaire.companion;

import java.util.List;
import javax.annotation.Nullable;

public record CompanionReply(
   String thought,
   String say,
   String mood,
   List<String> newMemories,
   float trustDelta,
   float fearDelta,
   float affectionDelta,
   @Nullable CompanionStance stance,
   List<CompanionIntent> intents
) {
   public CompanionReply {
      thought = thought == null ? "" : thought;
      say = say == null ? "" : say;
      mood = mood == null ? "" : mood;
      newMemories = newMemories == null ? List.of() : List.copyOf(newMemories);
      intents = intents == null ? List.of() : List.copyOf(intents);
   }
}
