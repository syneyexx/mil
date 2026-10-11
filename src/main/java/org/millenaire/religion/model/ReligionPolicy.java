package org.millenaire.religion.model;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Village religious visitor/participation policy. Does not imply aggression.
 */
public enum ReligionPolicy {
   /** Any tradition present may practice at suitable sites. */
   OPEN,
   /** Host tradition preferred for communal ceremonies; others may practice privately. */
   HOST_PREFERRED,
   /** Only host tradition communal ceremonies; private practice still allowed for affiliations. */
   HOST_ONLY;

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static ReligionPolicy fromId(@Nullable String id) {
      if (id == null || id.isEmpty()) {
         return OPEN;
      }
      try {
         return valueOf(id.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return OPEN;
      }
   }
}
