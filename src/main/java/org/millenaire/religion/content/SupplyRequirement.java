package org.millenaire.religion.content;

import javax.annotation.Nullable;

public record SupplyRequirement(String itemId, int count, boolean optional) {
   public SupplyRequirement(String itemId, int count) {
      this(itemId, count, false);
   }

   public static SupplyRequirement parse(@Nullable String itemId, int count, boolean optional) {
      return new SupplyRequirement(itemId == null ? "" : itemId, Math.max(0, count), optional);
   }
}
