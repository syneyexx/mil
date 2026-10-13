package org.millenaire.faith;

import java.util.Locale;
import javax.annotation.Nullable;

public enum RitualState {
   PREPARING,
   GATHERING,
   RUNNING,
   SUCCEEDED,
   INTERRUPTED,
   EXPIRED,
   CANCELLED;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean isTerminal() {
      return this == SUCCEEDED || this == INTERRUPTED || this == EXPIRED || this == CANCELLED;
   }

   public boolean allowsRefund() {
      return this == CANCELLED || this == EXPIRED || this == INTERRUPTED;
   }

   @Nullable
   public static RitualState fromId(@Nullable String id) {
      if (id == null || id.isBlank()) {
         return null;
      }
      try {
         return valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
