package org.millenaire.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.faction.politics.Government;
import org.millenaire.faction.politics.GovernmentPolicy;
import org.millenaire.faction.politics.PoliticalCoalition;
import org.millenaire.faction.politics.PoliticalCrisis;
import org.millenaire.faction.politics.PoliticalGroup;
import org.millenaire.faction.politics.PoliticalHistoryEntry;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.politics.PolicyType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

/**
 * Read-only admin politics diagnostics under {@code /millenaire dev politics}.
 */
public final class PoliticsCommand {
   private static final Gson GSON = new GsonBuilder().create();
   private static final double SEARCH_RADIUS = 5000.0;

   private PoliticsCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> politics = Commands.literal("politics");
      politics.executes(PoliticsCommand::infoNearest);
      politics.then(Commands.literal("info").executes(PoliticsCommand::infoNearest));
      politics.then(Commands.literal("government").executes(PoliticsCommand::governmentNearest));
      politics.then(Commands.literal("leader").executes(PoliticsCommand::leaderNearest));
      politics.then(Commands.literal("groups").executes(PoliticsCommand::groupsNearest));
      politics.then(Commands.literal("stability").executes(PoliticsCommand::stabilityNearest));
      politics.then(Commands.literal("crisis").executes(PoliticsCommand::crisisNearest));
      politics.then(Commands.literal("history").executes(PoliticsCommand::historyNearest));
      parent.then(politics);
   }

   private static int infoNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("faction", payload.factionId());
         Government government = PoliticsQueries.government(payload.level(), payload.village().getFactionId());
         PoliticalLeader leader = PoliticsQueries.leader(payload.level(), payload.village().getFactionId());
         out.put("government_type", government == null ? null : government.getType().name());
         out.put("leader", leader == null ? null : leader.getDisplayName());
         out.put("legitimacy", PoliticsQueries.legitimacy(payload.level(), payload.village().getFactionId()));
         out.put("stability", PoliticsQueries.stability(payload.level(), payload.village().getFactionId()));
         out.put("pressure", PoliticsQueries.politicalPressure(payload.level(), payload.village().getFactionId()));
         out.put("polarization", PoliticsQueries.polarization(payload.level(), payload.village().getFactionId()));
         out.put("rebellion_pressure", PoliticsQueries.rebellionPressure(payload.level(), payload.village().getFactionId()));
         out.put("conflict", PoliticsQueries.conflictState(payload.level(), payload.village().getFactionId()).name());
         out.put("succession_pending", PoliticsQueries.successionPending(payload.level(), payload.village().getFactionId()));
         return out;
      });
   }

   private static int governmentNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Government government = PoliticsQueries.government(payload.level(), payload.village().getFactionId());
         Map<String, Object> out = new LinkedHashMap<>();
         if (government == null) {
            out.put("government", null);
            return out;
         }

         out.put("id", government.getId().asString());
         out.put("type", government.getType().name());
         out.put("legitimacy", government.getLegitimacy());
         out.put("stability", government.getStability());
         out.put("centralization", government.getCentralization());
         out.put("succession", government.getSuccessionMechanism().name());
         out.put("interim", government.getInterimType().name());
         Map<String, String> policies = new LinkedHashMap<>();
         for (Map.Entry<PolicyType, GovernmentPolicy> entry : government.getPolicies().entrySet()) {
            policies.put(entry.getKey().name(), entry.getValue().getStance().name());
         }

         out.put("policies", policies);
         PoliticalCoalition ruling = PoliticsQueries.rulingCoalition(payload.level(), payload.village().getFactionId());
         out.put("ruling_coalition_support", ruling == null ? 0 : ruling.getSupport());
         out.put("opposition_count", government.getOppositionGroupIds().size());
         return out;
      });
   }

   private static int leaderNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         PoliticalLeader leader = PoliticsQueries.leader(payload.level(), payload.village().getFactionId());
         Map<String, Object> out = new LinkedHashMap<>();
         if (leader == null) {
            out.put("leader", null);
            return out;
         }

         out.put("id", leader.getId().asString());
         out.put("name", leader.getDisplayName());
         out.put("role", leader.getRole().name());
         out.put("legitimacy", leader.getLegitimacy());
         out.put("popularity", leader.getPopularity());
         out.put("competence", leader.getCompetence());
         out.put("military_influence", leader.getMilitaryInfluence());
         out.put("economic_influence", leader.getEconomicInfluence());
         out.put("diplomatic_influence", leader.getDiplomaticInfluence());
         out.put("political_influence", leader.getPoliticalInfluence());
         out.put("linked_npc", leader.getLinkedNpcUuid() == null ? null : leader.getLinkedNpcUuid().toString());
         return out;
      });
   }

   private static int groupsNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> groups = new ArrayList<>();
         for (PoliticalGroup group : PoliticsQueries.groups(payload.level(), payload.village().getFactionId()).values()) {
            if (!group.isActive()) {
               continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", group.getType().name());
            row.put("support", group.getPopulationSupport());
            row.put("power", group.getPoliticalPower());
            row.put("approval", group.getApproval());
            row.put("foreign", group.getForeignPreference().name());
            groups.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("groups", groups);
         return out;
      });
   }

   private static int stabilityNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("stability", PoliticsQueries.stability(payload.level(), payload.village().getFactionId()));
         out.put("legitimacy", PoliticsQueries.legitimacy(payload.level(), payload.village().getFactionId()));
         out.put("popularity", PoliticsQueries.popularity(payload.level(), payload.village().getFactionId()));
         out.put("pressure", PoliticsQueries.politicalPressure(payload.level(), payload.village().getFactionId()));
         out.put("polarization", PoliticsQueries.polarization(payload.level(), payload.village().getFactionId()));
         out.put("rebellion_pressure", PoliticsQueries.rebellionPressure(payload.level(), payload.village().getFactionId()));
         out.put("stable", PoliticsQueries.isGovernmentStable(payload.level(), payload.village().getFactionId()));
         out.put("vulnerable", PoliticsQueries.isGovernmentVulnerable(payload.level(), payload.village().getFactionId()));
         return out;
      });
   }

   private static int crisisNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> crises = new ArrayList<>();
         for (PoliticalCrisis crisis : PoliticsQueries.crises(payload.level(), payload.village().getFactionId())) {
            if (!crisis.isActive()) {
               continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", crisis.getType().name());
            row.put("stage", crisis.getStage().name());
            row.put("severity", crisis.getSeverity());
            row.put("cause", crisis.getCause());
            crises.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("crises", crises);
         out.put("conflict", PoliticsQueries.conflictState(payload.level(), payload.village().getFactionId()).name());
         return out;
      });
   }

   private static int historyNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> history = new ArrayList<>();
         for (PoliticalHistoryEntry entry : PoliticsQueries.history(payload.level(), payload.village().getFactionId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", entry.getGameTime());
            row.put("event", entry.getEventType().name());
            row.put("detail", entry.getDetail());
            history.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("history", history);
         return out;
      });
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

      PoliticsQueries.manager(level).ensurePolitics(level, village.getFactionId());
      Map<String, Object> payload = mapper.map(new NearestPayload(level, village, village.getFactionId().asString()));
      source.sendSuccess(() -> Component.literal(GSON.toJson(payload)), false);
      return 1;
   }

   @FunctionalInterface
   private interface PayloadMapper {
      Map<String, Object> map(NearestPayload payload);
   }

   private record NearestPayload(ServerLevel level, Village village, String factionId) {
   }
}
