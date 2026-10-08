package org.millenaire.chat;

import java.util.Locale;

public final class NpcSecretDetector {
   private static final String[] PHRASES = {
      "geheim",
      "houd dit geheim",
      "hou dit geheim",
      "vertel het niemand",
      "zeg het tegen niemand",
      "niet doorvertellen",
      "niet verder vertellen",
      "keep this secret",
      "don't tell anyone",
      "dont tell anyone",
      "don't tell anybody",
      "this stays between us",
      "between us",
      "c'est un secret",
      "ne le dis à personne",
      "ne le dis a personne",
      "garde ça pour toi",
      "garde ca pour toi"
   };

   private NpcSecretDetector() {
   }

   public static boolean matches(String sanitizedPlayerText) {
      if (sanitizedPlayerText == null || sanitizedPlayerText.isBlank()) {
         return false;
      }
      String lower = sanitizedPlayerText.toLowerCase(Locale.ROOT);
      for (String phrase : PHRASES) {
         if (lower.contains(phrase)) {
            return true;
         }
      }
      return false;
   }
}
