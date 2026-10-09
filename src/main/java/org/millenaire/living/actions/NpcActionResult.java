package org.millenaire.living.actions;

import javax.annotation.Nullable;

public record NpcActionResult(boolean success, String messageKey, @Nullable String detail, @Nullable String followUpId) {
   public static NpcActionResult ok(String key) {
      return new NpcActionResult(true, key, null, null);
   }

   public static NpcActionResult ok(String key, String detail) {
      return new NpcActionResult(true, key, detail, null);
   }

   public static NpcActionResult okWithFollowUp(String key, String followUpId) {
      return new NpcActionResult(true, key, null, followUpId);
   }

   public static NpcActionResult fail(String key) {
      return new NpcActionResult(false, key, null, null);
   }

   public static NpcActionResult fail(String key, String detail) {
      return new NpcActionResult(false, key, detail, null);
   }
}
