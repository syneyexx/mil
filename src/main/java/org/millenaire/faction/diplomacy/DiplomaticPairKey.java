package org.millenaire.faction.diplomacy;

import java.util.Objects;
import org.millenaire.faction.FactionId;

/**
 * Canonical unordered pair key for bilateral agreements.
 * Embargoes use ordered keys via {@link #ordered(FactionId, FactionId)}.
 */
public record DiplomaticPairKey(String key) {
   public DiplomaticPairKey {
      Objects.requireNonNull(key, "key");
   }

   public static DiplomaticPairKey unordered(FactionId a, FactionId b) {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(b, "b");
      String left = a.asString();
      String right = b.asString();
      if (left.compareTo(right) <= 0) {
         return new DiplomaticPairKey(left + "|" + right);
      }

      return new DiplomaticPairKey(right + "|" + left);
   }

   public static DiplomaticPairKey ordered(FactionId source, FactionId target) {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
      return new DiplomaticPairKey(source.asString() + ">" + target.asString());
   }

   public static DiplomaticPairKey forType(AgreementType type, FactionId primary, FactionId secondary) {
      return type.isDirectional() ? ordered(primary, secondary) : unordered(primary, secondary);
   }
}
