package org.millenaire.chat;

public final class NpcChatSanitize {
   private NpcChatSanitize() {
   }

   public static String sanitize(String raw) {
      if (raw == null) {
         return "";
      }
      StringBuilder sb = new StringBuilder(Math.min(raw.length(), 400));
      for (int i = 0; i < raw.length() && sb.length() < 400; i++) {
         char c = raw.charAt(i);
         if (c == '\u0000' || c == '\u00a7') {
            continue;
         }
         if (c < 32 && c != ' ') {
            continue;
         }
         sb.append(c);
      }
      return sb.toString().trim();
   }

   public static boolean isValidLanguageCode(String code) {
      if (code == null || code.isEmpty() || code.length() > 16) {
         return false;
      }
      for (int i = 0; i < code.length(); i++) {
         char c = code.charAt(i);
         if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
            return false;
         }
      }
      return true;
   }
}
