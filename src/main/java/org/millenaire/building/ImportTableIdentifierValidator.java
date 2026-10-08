package org.millenaire.building;

import java.util.regex.Pattern;

public final class ImportTableIdentifierValidator {
   private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");
   private static final Pattern CULTURE_KEY_COMPONENT = Pattern.compile("[a-z0-9_-]+");
   private static final Pattern CULTURE_KEY_NAMESPACED = Pattern.compile("[a-z0-9_-]+:[a-z0-9_-]+");
   public static final int MAX_IDENTIFIER_LENGTH = 64;
   public static final int MAX_CULTURE_KEY_LENGTH = 96;

   private ImportTableIdentifierValidator() {
   }

   public static boolean isValidIdentifier(String s) {
      return s != null && !s.isEmpty() && s.length() <= 64 ? IDENTIFIER.matcher(s).matches() : false;
   }

   public static boolean isValidOptionalIdentifier(String s) {
      if (s == null) {
         return false;
      } else {
         return s.isEmpty() ? true : isValidIdentifier(s);
      }
   }

   public static boolean isValidCultureKey(String s) {
      if (s == null) {
         return false;
      } else if (s.isEmpty()) {
         return true;
      } else {
         return s.length() > 96 ? false : CULTURE_KEY_COMPONENT.matcher(s).matches() || CULTURE_KEY_NAMESPACED.matcher(s).matches();
      }
   }
}
