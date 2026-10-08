package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum CommanderRank {
   SOLDIER(0),
   OFFICER(1),
   CAPTAIN(2),
   COMMANDER(3),
   GENERAL(4);

   private final int tier;

   CommanderRank(int tier) {
      this.tier = tier;
   }

   public int getTier() {
      return this.tier;
   }

   @Nullable
   public static CommanderRank tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return CommanderRank.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
