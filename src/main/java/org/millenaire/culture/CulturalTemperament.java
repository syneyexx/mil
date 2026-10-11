package org.millenaire.culture;

import com.google.gson.JsonObject;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.GsonHelper;

/**
 * Data-driven foreign-policy DNA for a culture. Axes are 0–100; 50 is neutral default.
 * Biases diplomatic AI, immigration hospitality, festivals, and grudge memory — never a second score track.
 */
public record CulturalTemperament(
   int mercantile,
   int martial,
   int isolation,
   int expansive,
   int honor,
   int hospitality,
   int grudge,
   int festivalAffinity
) {
   public static final CulturalTemperament DEFAULT = new CulturalTemperament(50, 50, 50, 50, 50, 50, 50, 50);

   public CulturalTemperament {
      mercantile = clamp(mercantile);
      martial = clamp(martial);
      isolation = clamp(isolation);
      expansive = clamp(expansive);
      honor = clamp(honor);
      hospitality = clamp(hospitality);
      grudge = clamp(grudge);
      festivalAffinity = clamp(festivalAffinity);
   }

   public static CulturalTemperament parse(@Nullable JsonObject json) {
      if (json == null) {
         return DEFAULT;
      }
      return new CulturalTemperament(
         readAxis(json, "mercantile", 50),
         readAxis(json, "martial", 50),
         readAxis(json, "isolation", 50),
         readAxis(json, "expansive", 50),
         readAxis(json, "honor", 50),
         readAxis(json, "hospitality", 50),
         readAxis(json, "grudge", 50),
         readAxis(json, "festival_affinity", 50)
      );
   }

   /** Short UI labels for World Status culture cards. */
   public String primaryLabel() {
      if (mercantile >= 65 && mercantile >= martial) {
         return "mercantile";
      }
      if (martial >= 65) {
         return "martial";
      }
      if (isolation >= 65) {
         return "isolationist";
      }
      if (expansive >= 65) {
         return "expansive";
      }
      if (honor >= 65) {
         return "honorbound";
      }
      if (hospitality >= 65) {
         return "hospitable";
      }
      return "balanced";
   }

   public String secondaryLabel() {
      int best = -1;
      String label = "pragmatic";
      if (honor >= 60 && !"honorbound".equals(primaryLabel())) {
         best = honor;
         label = "honorbound";
      }
      if (grudge >= 60 && grudge > best) {
         best = grudge;
         label = "grudging";
      }
      if (festivalAffinity >= 60 && festivalAffinity > best) {
         label = "festive";
      }
      if (best < 0 && hospitality >= 55 && !"hospitable".equals(primaryLabel())) {
         label = "hospitable";
      }
      return label;
   }

   /** Trade agreement score threshold bias (negative = easier). */
   public int tradeScoreBias() {
      return (50 - mercantile) / 5 + isolation / 20;
   }

   /** Embargo score threshold bias (positive = needs more hostility). */
   public int embargoScoreBias() {
      return (honor - 50) / 10 - martial / 25;
   }

   /** War score threshold bias (positive = needs more hostility). */
   public int warScoreBias() {
      return (50 - martial) / 4 + honor / 25;
   }

   /** Multiplier for relationship decay toward zero after insults/raids (1.0 = normal). */
   public double grudgeDecayMultiplier() {
      return 1.0 + (grudge - 50) / 100.0;
   }

   /** Immigration pull weight for OPEN player cities of acceptable standing. */
   public double hospitalityWeight() {
      return 0.5 + hospitality / 100.0;
   }

   /** Chance weight for starting a hospitality festival window. */
   public double festivalWeight() {
      return 0.25 + festivalAffinity / 100.0;
   }

   public boolean prefersNapBeforeWar() {
      return isolation >= 55 || honor >= 60;
   }

   public String i18nPrimaryKey() {
      return "civilization.millenaire.temperament." + primaryLabel();
   }

   public String i18nSecondaryKey() {
      return "civilization.millenaire.temperament." + secondaryLabel();
   }

   private static int readAxis(JsonObject json, String key, int fallback) {
      if (!json.has(key)) {
         return fallback;
      }
      return clamp(GsonHelper.getAsInt(json, key, fallback));
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(100, value));
   }

   @Override
   public String toString() {
      return primaryLabel() + "/" + secondaryLabel();
   }

   public static String sanitizeLabel(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return "balanced";
      }
      return raw.trim().toLowerCase(Locale.ROOT);
   }
}
