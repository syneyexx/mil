package org.millenaire.chat;

import java.util.List;

public record NpcModelReply(
   String thought,
   String say,
   String mood,
   List<String> newMemories,
   float trustDelta,
   float fearDelta,
   float affectionDelta,
   boolean keepSecret,
   int gossipWorthy,
   String gossipLine
) {
}
