package org.millenaire.faith;

import java.util.Locale;
import javax.annotation.Nullable;

/** Membership role within a secret order chapter. */
public enum OrderRole {
   INTERESTED,
   INITIATE,
   KEEPER,
   LEADER;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static OrderRole fromId(@Nullable String id) {
      if (id == null || id.isBlank()) {
         return null;
      }
      try {
         return valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean canLeadRitual() {
      return this == KEEPER || this == LEADER;
   }

   public boolean isMember() {
      return this != INTERESTED;
   }
}
