package org.millenaire.religion.model;

import java.util.Locale;
import javax.annotation.Nullable;

public enum CeremonyState {
   PLANNED,
   PREPARING,
   GATHERING,
   ACTIVE,
   COMPLETED,
   DEFERRED,
   INTERRUPTED,
   CANCELLED;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean isTerminal() {
      return this == COMPLETED || this == CANCELLED;
   }

   public boolean isActivePhase() {
      return this == PREPARING || this == GATHERING || this == ACTIVE || this == INTERRUPTED;
   }

   @Nullable
   public static CeremonyState fromId(@Nullable String id) {
      if (id == null || id.isEmpty()) {
         return null;
      }
      try {
         return valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
