package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum CrisisStage {
   NORMAL,
   TENSION,
   CRISIS,
   SEVERE_CRISIS,
   INTERNAL_CONFLICT;

   @Nullable
   public static CrisisStage tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return CrisisStage.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isCrisis() {
      return this == CRISIS || this == SEVERE_CRISIS || this == INTERNAL_CONFLICT;
   }

   public CrisisStage escalate() {
      return switch (this) {
         case NORMAL -> TENSION;
         case TENSION -> CRISIS;
         case CRISIS -> SEVERE_CRISIS;
         case SEVERE_CRISIS, INTERNAL_CONFLICT -> INTERNAL_CONFLICT;
      };
   }

   public CrisisStage deescalate() {
      return switch (this) {
         case INTERNAL_CONFLICT -> SEVERE_CRISIS;
         case SEVERE_CRISIS -> CRISIS;
         case CRISIS -> TENSION;
         case TENSION, NORMAL -> NORMAL;
      };
   }
}
