package org.millenaire.faction.economy;

import java.util.Objects;
import org.millenaire.faction.FactionId;

/** Directional trade connection key: exporter → importer. */
public record TradeRouteKey(FactionId source, FactionId target) {
   public TradeRouteKey {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
   }

   public static TradeRouteKey of(FactionId source, FactionId target) {
      return new TradeRouteKey(source, target);
   }

   public boolean isSelf() {
      return this.source.equals(this.target);
   }
}
