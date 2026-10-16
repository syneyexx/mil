package org.millenaire.labyrinth.entity;

/**
 * Disposition of a Gebondene toward players in the Labyrinth.
 * GASTVRIJ is hospitable; GEALARMEERD is wary; JACHT is hunting.
 */
public enum GebondeneAttitude {
   GASTVRIJ,
   GEALARMEERD,
   JACHT;

   public static GebondeneAttitude byName(String name) {
      if (name == null || name.isBlank()) {
         return GASTVRIJ;
      }
      try {
         return valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
         return GASTVRIJ;
      }
   }
}
