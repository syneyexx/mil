package org.millenaire.civilization.presentation;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Server-derived presentation model. Owns no gameplay data and never mutates authoritative state.
 */
public record CivilizationVillageSnapshot(
   UUID villageId,
   String villageName,
   @Nullable String factionId,
   String factionDisplayName,
   String developmentCode,
   String developmentLabelKey,
   int residents,
   int housingCapacity,
   boolean overcrowded,
   boolean inventoryKnown,
   List<NeedLine> topNeeds,
   @Nullable ProjectLine currentProject,
   @Nullable TradeLine tradeSummary,
   @Nullable MigrationLine migration,
   @Nullable ColonizationLine colonization,
   @Nullable ConnectionLine connection,
   List<HistoryLine> recentHistory,
   List<AllowedAction> allowedActions,
   long capturedAtGameTime
) {
   public record NeedLine(String needId, String summaryKey, String detail, int shortage, String severityCode) {
   }

   public record ProjectLine(String nameKeyOrLiteral, boolean upgrading, int level, String statusCode) {
   }

   public record TradeLine(String statusCode, String partnerName, String itemLabel, String directionCode, long lastTransferGameTime) {
   }

   public record MigrationLine(
      String statusCode,
      String reasonCode,
      String originName,
      String destinationName,
      int migrated,
      int target
   ) {
   }

   public record ColonizationLine(
      UUID operationId,
      String statusCode,
      String reasonCode,
      String statusLabelKey,
      String reasonLabelKey,
      String nextStepKey,
      int migratedFounders,
      int founderTarget,
      boolean refundOutstanding,
      boolean materialsReserved,
      boolean costsConsumed,
      String destinationHint
   ) {
   }

   public record ConnectionLine(String statusCode, String endpointName, String reasonCode) {
   }

   public record HistoryLine(String typeKey, String summary, long gameTime) {
   }

   public record AllowedAction(
      CivilizationActionId actionId,
      boolean enabled,
      String labelKey,
      String blockedReasonKey,
      @Nullable String targetId
   ) {
   }
}
