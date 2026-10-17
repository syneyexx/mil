package org.millenaire.labyrinth.portal;

import java.util.Locale;
import java.util.Optional;

/**
 * Active geometry of the Configuratie — determines portal vs puzzle behaviour.
 * Stored on the item via CustomData; cycle with sneak-use inside the Labyrinth.
 */
public enum ConfiguratieVorm {
   /** Enter / leave the Labyrinth (default). */
   SCHARNIER,
   /** Rotate walkways in ROTARY chambers. */
   WIEL,
   /** Opens the Zegel gate. */
   SLEUTEL_A,
   /** Opens the Masker gate. */
   SLEUTEL_B,
   /** Opens the Koord gate. */
   SLEUTEL_C,
   /** Short chain-pull / combat pulse (Labyrinth only). */
   KETTING;

   public ConfiguratieVorm next() {
      ConfiguratieVorm[] values = values();
      return values[(ordinal() + 1) % values.length];
   }

   public String id() {
      return name().toLowerCase(Locale.ROOT);
   }

   public static Optional<ConfiguratieVorm> byId(String id) {
      if (id == null || id.isBlank()) {
         return Optional.empty();
      }
      try {
         return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
         return Optional.empty();
      }
   }

   public static ConfiguratieVorm byIdOrDefault(String id) {
      return byId(id).orElse(SCHARNIER);
   }
}
