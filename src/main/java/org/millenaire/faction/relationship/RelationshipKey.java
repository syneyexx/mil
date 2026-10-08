package org.millenaire.faction.relationship;

import java.util.Objects;
import org.millenaire.faction.FactionId;

/**
 * Stable map key for a single directional relationship {@code source → target}.
 */
public record RelationshipKey(FactionId source, FactionId target) {
   public RelationshipKey {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
   }

   public static RelationshipKey of(FactionId source, FactionId target) {
      return new RelationshipKey(source, target);
   }

   public boolean isSelf() {
      return this.source.equals(this.target);
   }
}
