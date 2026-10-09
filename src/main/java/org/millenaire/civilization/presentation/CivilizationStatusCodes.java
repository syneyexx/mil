package org.millenaire.civilization.presentation;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Maps technical civilization reason codes to localization keys for players.
 * Never send raw technical identifiers as ordinary UI text.
 */
public final class CivilizationStatusCodes {
   private CivilizationStatusCodes() {
   }

   public static String statusLabelKey(String statusCode) {
      String code = normalize(statusCode);
      return "civilization.millenaire.status." + code;
   }

   public static String reasonLabelKey(@Nullable String reason) {
      String code = normalizeReason(reason);
      return "civilization.millenaire.reason." + code;
   }

   public static String reasonDetailKey(@Nullable String reason) {
      return reasonLabelKey(reason) + ".detail";
   }

   public static String nextStepKey(@Nullable String reason) {
      String code = normalizeReason(reason);
      return "civilization.millenaire.next." + code;
   }

   public static String normalize(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return "unknown";
      }
      String s = raw.trim().toLowerCase(Locale.ROOT);
      int semi = s.indexOf(';');
      if (semi >= 0) {
         s = s.substring(0, semi);
      }
      int eq = s.indexOf('=');
      if (eq >= 0 && s.startsWith("partial_migrants")) {
         return "partial_migrants";
      }
      if (s.startsWith("migration_failed")) {
         return "migration_failed";
      }
      if (s.startsWith("failed:")) {
         return normalize(s.substring("failed:".length()));
      }
      s = s.replace(':', '_').replace('-', '_').replace(' ', '_');
      if (s.isEmpty()) {
         return "unknown";
      }
      return s;
   }

   public static String normalizeReason(@Nullable String reason) {
      if (reason == null || reason.isBlank()) {
         return "none";
      }
      String primary = reason;
      if (reason.contains("refund_outstanding")) {
         return "refund_outstanding";
      }
      if (reason.contains(";")) {
         primary = reason.substring(0, reason.indexOf(';'));
      }
      return normalize(primary);
   }

   /** Known technical → player reason codes used in docs and lang. */
   public static boolean isKnownReason(String code) {
      return switch (normalizeReason(code)) {
         case "chunk_not_loaded",
            "no_suitable_founders",
            "insufficient_resources",
            "refund_outstanding",
            "obstacle_protected",
            "village_building",
            "planned_building",
            "road_protected",
            "parent_vanished",
            "faction_missing",
            "layout_pending_or_failed",
            "village_not_ready",
            "partial_migrants",
            "materializing",
            "founder_temp_blocked",
            "founder_missing",
            "migration_failed",
            "no_housing",
            "destination_full",
            "at_war",
            "cooldown",
            "population_low",
            "no_housing_pressure",
            "already_colonizing",
            "no_faction",
            "blocked_worldgen_disabled",
            "new_proposals_disabled",
            "none",
            "unknown" -> true;
         default -> false;
      };
   }
}
