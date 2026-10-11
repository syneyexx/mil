package org.millenaire.companion;

import java.util.Locale;
import javax.annotation.Nullable;

public record CompanionIntent(String type, @Nullable String target, @Nullable String arg) {
   public CompanionIntent {
      type = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
      target = target == null || target.isBlank() ? null : target.trim();
      arg = arg == null || arg.isBlank() ? null : arg.trim();
   }

   public static CompanionIntent of(String type) {
      return new CompanionIntent(type, null, null);
   }
}
