package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.conflict.ConflictHistoryEntry;
import org.millenaire.faction.conflict.ConflictQueries;
import org.millenaire.faction.conflict.ConflictSide;
import org.millenaire.faction.conflict.ConflictType;
import org.millenaire.faction.conflict.InternalConflict;
import org.millenaire.faction.conflict.NegotiatedSettlement;
import org.millenaire.faction.conflict.PostConflictRecovery;
import org.millenaire.faction.conflict.Unrest;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

/**
 * Read-only admin conflict diagnostics under {@code /millenaire dev conflict}.
 */
public final class ConflictCommand {
   private static final Gson GSON = new GsonBuilder().create();
   private static final double SEARCH_RADIUS = 5000.0;

   private ConflictCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> conflict = Commands.literal("conflict");
      conflict.executes(ConflictCommand::listNearest);
      conflict.then(Commands.literal("list").executes(ConflictCommand::listNearest));
      conflict.then(Commands.literal("info").executes(ConflictCommand::infoNearest));
      conflict.then(Commands.literal("history").executes(ConflictCommand::historyNearest));
      conflict.then(Commands.literal("rebellion").then(Commands.literal("info").executes(ctx -> typedInfoNearest(ctx, ConflictType.REBELLION))));
      conflict.then(Commands.literal("civilwar").then(Commands.literal("info").executes(ctx -> typedInfoNearest(ctx, ConflictType.CIVIL_WAR))));
      conflict.then(Commands.literal("revolution").then(Commands.literal("info").executes(ctx -> typedInfoNearest(ctx, ConflictType.REVOLUTION))));
      conflict.then(Commands.literal("secession").then(Commands.literal("info").executes(ctx -> typedInfoNearest(ctx, ConflictType.SECESSION))));
      parent.then(conflict);
   }

   private static int listNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> active = new ArrayList<>();
         for (InternalConflict conflict : ConflictQueries.activeConflicts(payload.level(), payload.factionId())) {
            active.add(summarizeConflict(payload.level(), conflict));
         }

         List<Map<String, Object>> unrestRows = new ArrayList<>();
         for (Unrest unrest : ConflictQueries.unrest(payload.level(), payload.factionId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", unrest.getId().asString());
            row.put("cause", unrest.getCause().name());
            row.put("scope", unrest.getScope().name());
            row.put("severity", unrest.getSeverity());
            row.put("escalation", unrest.getEscalationPressure());
            unrestRows.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("faction", payload.factionKey());
         out.put("active_conflicts", active);
         out.put("active_unrest", unrestRows);
         out.put("historical_count", countHistorical(payload.level(), payload.factionId()));
         return out;
      });
   }

   private static int infoNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         InternalConflict primary = firstActive(payload.level(), payload.factionId(), null);
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("faction", payload.factionKey());
         if (primary == null) {
            out.put("conflict", null);
            out.put("active_count", ConflictQueries.activeConflicts(payload.level(), payload.factionId()).size());
            return out;
         }

         out.put("conflict", detailConflict(payload.level(), primary));
         return out;
      });
   }

   private static int historyNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         InternalConflict primary = firstActive(payload.level(), payload.factionId(), null);
         if (primary == null) {
            primary = firstHistorical(payload.level(), payload.factionId());
         }

         List<Map<String, Object>> history = new ArrayList<>();
         if (primary != null) {
            for (ConflictHistoryEntry entry : ConflictQueries.history(payload.level(), primary.getId())) {
               Map<String, Object> row = new LinkedHashMap<>();
               row.put("time", entry.getGameTime());
               row.put("event", entry.getEventType().name());
               row.put("faction", entry.getFactionId().asString());
               row.put("quantity", entry.getQuantity());
               row.put("detail", entry.getDetail());
               history.add(row);
            }
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("faction", payload.factionKey());
         out.put("conflict_id", primary == null ? null : primary.getId().asString());
         out.put("history", history);
         return out;
      });
   }

   private static int typedInfoNearest(CommandContext<CommandSourceStack> ctx, ConflictType type) {
      return emit(ctx, payload -> {
         InternalConflict match = firstActive(payload.level(), payload.factionId(), type);
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("faction", payload.factionKey());
         out.put("type", type.name());
         if (match == null) {
            out.put("conflict", null);
            return out;
         }

         out.put("conflict", detailConflict(payload.level(), match));
         return out;
      });
   }

   private static Map<String, Object> summarizeConflict(ServerLevel level, InternalConflict conflict) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", conflict.getId().asString());
      row.put("type", conflict.getType().name());
      row.put("stage", conflict.getStage().name());
      row.put("origin", conflict.getOriginFactionId().asString());
      row.put("rebel_faction", conflict.getRebelFactionId() == null ? null : conflict.getRebelFactionId().asString());
      row.put("linked_war", conflict.getLinkedWarId() == null ? null : conflict.getLinkedWarId().asString());
      row.put("score", ConflictQueries.conflictScore(level, conflict.getId()));
      row.put("negotiating", conflict.isNegotiating());
      row.put("sides", conflict.getSides().size());
      return row;
   }

   private static Map<String, Object> detailConflict(ServerLevel level, InternalConflict conflict) {
      Map<String, Object> out = summarizeConflict(level, conflict);
      out.put("started", conflict.getStartedGameTime());
      out.put("last_updated", conflict.getLastUpdatedGameTime());
      out.put("ended", conflict.getEndedGameTime());
      out.put("cause", conflict.getCauseDetail());
      out.put("resolution", conflict.getResolution() == null ? null : conflict.getResolution().name());
      out.put("active", conflict.isActive());
      out.put("recovery_until", conflict.getRecoveryUntilGameTime());
      out.put("government_score", ConflictQueries.storedGovernmentConflictScore(level, conflict.getId()));
      out.put("rebel_score", ConflictQueries.storedRebelConflictScore(level, conflict.getId()));

      List<Map<String, Object>> sides = new ArrayList<>();
      for (ConflictSide side : conflict.getSides().values()) {
         Map<String, Object> s = new LinkedHashMap<>();
         s.put("id", side.getId().asString());
         s.put("faction", side.getFactionId().asString());
         s.put("government", side.isGovernmentSide());
         s.put("name", side.getDisplayName());
         s.put("support", side.getSupport());
         s.put("legitimacy", side.getLegitimacy());
         s.put("exhaustion", side.getExhaustion());
         s.put("casualties", side.getCasualties());
         s.put("conflict_score", side.getConflictScore());
         s.put("objective", side.getPrimaryObjective().name());
         s.put("armies", side.getArmyIds().size());
         s.put("territories", side.getControlledTerritoryIds().size());
         s.put("settlements", side.getControlledSettlementIds().size());
         s.put("active", side.isActive());
         sides.add(s);
      }
      out.put("sides", sides);

      List<String> recognizers = new ArrayList<>();
      for (FactionId recognizer : ConflictQueries.recognitions(level, conflict.getId())) {
         recognizers.add(recognizer.asString());
      }
      out.put("foreign_recognition", recognizers);

      PostConflictRecovery recovery = ConflictQueries.recovery(level, conflict.getId());
      if (recovery != null) {
         Map<String, Object> r = new LinkedHashMap<>();
         r.put("active", recovery.isActive());
         r.put("ends", recovery.getEndsGameTime());
         r.put("stability_penalty", recovery.getRemainingStabilityPenalty());
         r.put("pressure_bonus", recovery.getRemainingPressureBonus());
         r.put("resolved_as", recovery.getResolvedAs() == null ? null : recovery.getResolvedAs().name());
         out.put("recovery", r);
      } else {
         out.put("recovery", null);
      }

      NegotiatedSettlement settlement = ConflictQueries.settlement(level, conflict.getId());
      if (settlement != null) {
         Map<String, Object> s = new LinkedHashMap<>();
         s.put("outcome", settlement.getOutcome().name());
         s.put("settled", settlement.getSettledGameTime());
         s.put("autonomy", settlement.getAutonomyGranted());
         s.put("leadership_changed", settlement.isLeadershipChanged());
         s.put("government_reformed", settlement.isGovernmentReformed());
         s.put("territory_ceded", settlement.isTerritoryCeded());
         s.put("terms", settlement.getTerms());
         s.put("detail", settlement.getDetail());
         out.put("settlement", s);
      } else {
         out.put("settlement", null);
      }

      return out;
   }

   @Nullable
   private static InternalConflict firstActive(ServerLevel level, FactionId factionId, @Nullable ConflictType type) {
      for (InternalConflict conflict : ConflictQueries.activeConflicts(level, factionId)) {
         if (type == null || conflict.getType() == type) {
            return conflict;
         }
      }

      return null;
   }

   @Nullable
   private static InternalConflict firstHistorical(ServerLevel level, FactionId factionId) {
      for (InternalConflict conflict : ConflictQueries.historicalConflicts(level)) {
         if (factionId.equals(conflict.getOriginFactionId()) || factionId.equals(conflict.getRebelFactionId())) {
            return conflict;
         }
      }

      return null;
   }

   private static int countHistorical(ServerLevel level, FactionId factionId) {
      int count = 0;
      for (InternalConflict conflict : ConflictQueries.historicalConflicts(level)) {
         if (factionId.equals(conflict.getOriginFactionId()) || factionId.equals(conflict.getRebelFactionId())) {
            count++;
         }
      }

      return count;
   }

   private static int emit(CommandContext<CommandSourceStack> ctx, PayloadMapper mapper) {
      CommandSourceStack source = ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.not_in_overworld"));
         return 0;
      }

      BlockPos searchPos = BlockPos.containing(source.getPosition());
      VillageSavedData savedData = VillageSavedData.get(level);
      VillageManager villageManager = savedData.getVillageManager();
      Village village = villageManager.findNearestVillage(searchPos, SEARCH_RADIUS);
      if (village == null) {
         source.sendSuccess(() -> Component.translatable("command.millenaire.status.no_village_5000"), false);
         return 1;
      }

      if (village.getFactionId() == null) {
         source.sendSuccess(() -> Component.literal("{\"error\":\"no_faction\"}"), false);
         return 1;
      }

      Map<String, Object> payload = mapper.map(new NearestPayload(level, village, village.getFactionId(), village.getFactionId().asString()));
      source.sendSuccess(() -> Component.literal(GSON.toJson(payload)), false);
      return 1;
   }

   @FunctionalInterface
   private interface PayloadMapper {
      Map<String, Object> map(NearestPayload payload);
   }

   private record NearestPayload(ServerLevel level, Village village, FactionId factionId, String factionKey) {
   }
}
