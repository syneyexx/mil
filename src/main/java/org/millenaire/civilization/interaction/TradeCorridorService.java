package org.millenaire.civilization.interaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

/**
 * Tracks successful cross-faction settlement transfers as trade corridors.
 * Recognition biases {@link org.millenaire.civilization.bridge.NeedsTradeBridge} only.
 */
public final class TradeCorridorService {
   private TradeCorridorService() {
   }

   public static void recordSuccess(
      ServerLevel level,
      FactionId sourceFaction,
      FactionId targetFaction,
      VillageId sourceVillage,
      VillageId targetVillage
   ) {
      if (level == null || sourceFaction == null || targetFaction == null || sourceVillage == null || targetVillage == null) {
         return;
      }
      if (sourceFaction.equals(targetFaction)) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      String key = corridorKey(sourceFaction, targetFaction, sourceVillage, targetVillage);
      TradeCorridorRecord existing = data.getCorridor(key);
      int deliveries = existing == null ? 1 : existing.successfulDeliveries() + 1;
      boolean recognized = existing != null && existing.recognized();
      boolean disrupted = existing != null && existing.disrupted();
      if (!recognized && deliveries >= InteractionConfig.CORRIDOR_RECOGNITION_THRESHOLD) {
         recognized = true;
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .modifyRelationship(
               level,
               sourceFaction,
               targetFaction,
               InteractionConfig.CORRIDOR_RECOGNITION_RELATIONSHIP_BOOST,
               RelationshipChangeReason.TRADE
            );
         data.addTheaterStory(new TheaterStory(
            "corridor",
            "civilization.millenaire.theater.corridor_recognized",
            sourceFaction.asString(),
            targetFaction.asString(),
            2,
            level.getGameTime()
         ));
      }
      data.putCorridor(new TradeCorridorRecord(
         sourceFaction.asString(),
         targetFaction.asString(),
         sourceVillage.uuid().toString(),
         targetVillage.uuid().toString(),
         deliveries,
         recognized,
         disrupted,
         recognized ? "civilization.millenaire.corridor.named" : null
      ));
   }

   /** Convenience when villages are available. */
   public static void recordSuccess(ServerLevel level, Village exporter, Village importer) {
      if (exporter == null || importer == null || exporter.getFactionId() == null || importer.getFactionId() == null) {
         return;
      }
      recordSuccess(level, exporter.getFactionId(), importer.getFactionId(), exporter.getId(), importer.getId());
   }

   public static boolean hasRecognizedCorridor(ServerLevel level, FactionId a, FactionId b) {
      if (level == null || a == null || b == null || a.equals(b)) {
         return false;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      for (TradeCorridorRecord c : data.corridors().values()) {
         if (!c.recognized() || c.disrupted()) {
            continue;
         }
         boolean match = (a.asString().equals(c.sourceFaction()) && b.asString().equals(c.targetFaction()))
            || (b.asString().equals(c.sourceFaction()) && a.asString().equals(c.targetFaction()));
         if (match) {
            return true;
         }
      }
      return false;
   }

   public static boolean isRecognizedCorridorBias(ServerLevel level, FactionId exporter, FactionId importer) {
      return hasRecognizedCorridor(level, exporter, importer);
   }

   public static void refreshDisruptions(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      var war = WarSavedData.get(level).getWarManager();
      var diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      for (TradeCorridorRecord c : new ArrayList<>(data.corridors().values())) {
         FactionId a = FactionId.tryParse(c.sourceFaction());
         FactionId b = FactionId.tryParse(c.targetFaction());
         if (a == null || b == null) {
            continue;
         }
         boolean shouldDisrupt = war.areAtWar(a, b) || diplomacy.isEmbargoed(a, b) || diplomacy.isEmbargoed(b, a);
         if (shouldDisrupt != c.disrupted()) {
            data.putCorridor(c.withDisrupted(shouldDisrupt));
            if (shouldDisrupt) {
               data.addTheaterStory(new TheaterStory(
                  "corridor",
                  "civilization.millenaire.theater.corridor_disrupted",
                  c.sourceFaction(),
                  c.targetFaction(),
                  3,
                  level.getGameTime()
               ));
            }
         }
      }
   }

   public static List<TradeCorridorRecord> listForPresentation(ServerLevel level, int max) {
      if (level == null) {
         return List.of();
      }
      List<TradeCorridorRecord> out = new ArrayList<>(InteractionSavedData.get(level).corridors().values());
      out.sort(Comparator
         .comparingInt(TradeCorridorRecord::successfulDeliveries).reversed()
         .thenComparing(TradeCorridorRecord::sourceFaction));
      if (out.size() > max) {
         return List.copyOf(out.subList(0, max));
      }
      return List.copyOf(out);
   }

   /** Pure helper for unit tests — recognition threshold logic without Minecraft. */
   public static TradeCorridorRecord incrementPure(
      @Nullable TradeCorridorRecord existing,
      String sourceFaction,
      String targetFaction,
      String sourceVillage,
      String targetVillage
   ) {
      int deliveries = existing == null ? 1 : existing.successfulDeliveries() + 1;
      boolean recognized = existing != null && existing.recognized()
         || deliveries >= InteractionConfig.CORRIDOR_RECOGNITION_THRESHOLD;
      boolean disrupted = existing != null && existing.disrupted();
      return new TradeCorridorRecord(
         sourceFaction, targetFaction, sourceVillage, targetVillage, deliveries, recognized, disrupted,
         recognized ? "civilization.millenaire.corridor.named" : null
      );
   }

   private static String corridorKey(FactionId source, FactionId target, VillageId sourceVillage, VillageId targetVillage) {
      return source.asString() + "|" + target.asString() + "|" + sourceVillage.uuid() + "|" + targetVillage.uuid();
   }
}
