package org.millenaire.knowledge;

import java.util.List;

/**
 * Content-driven knowledge node. Bonuses are hard-capped; no unbounded stacking.
 */
public record KnowledgeNode(
   String id,
   List<String> requires,
   KnowledgeScope scope,
   String sourceKind,
   String unlockHint,
   float bonusCap,
   String skillKey
) {
   public enum KnowledgeScope {
      LOCAL,
      REGIONAL
   }
}
