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
import org.millenaire.faction.territory.OccupationAdministration;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryClaim;
import org.millenaire.faction.territory.TerritoryHistoryEntry;
import org.millenaire.faction.territory.TerritoryQueries;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

/**
 * Read-only admin territory diagnostics under {@code /millenaire dev territory}.
 */
public final class TerritoryCommand {
   private static final Gson GSON = new GsonBuilder().create();
   private static final double SEARCH_RADIUS = 5000.0;

   private TerritoryCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> territory = Commands.literal("territory");
      territory.executes(TerritoryCommand::infoNearest);
      territory.then(Commands.literal("info").executes(TerritoryCommand::infoNearest));
      territory.then(Commands.literal("owner").executes(TerritoryCommand::ownerNearest));
      territory.then(Commands.literal("claims").executes(TerritoryCommand::claimsNearest));
      territory.then(Commands.literal("occupation").executes(TerritoryCommand::occupationNearest));
      territory.then(Commands.literal("history").executes(TerritoryCommand::historyNearest));
      parent.then(territory);
   }

   private static int infoNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Territory territory = payload.territory();
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("settlement", payload.village().getId().uuid().toString());
         out.put("territory", territory.getId().asString());
         out.put("state", territory.getState().name());
         out.put("owner", territory.getOwnerFactionId().asString());
         out.put("previous_owner", territory.getPreviousOwnerFactionId() == null ? null : territory.getPreviousOwnerFactionId().asString());
         out.put("controller", territory.getControllerFactionId() == null ? null : territory.getControllerFactionId().asString());
         out.put("occupier", territory.getOccupierFactionId() == null ? null : territory.getOccupierFactionId().asString());
         out.put("resistance", territory.getResistance());
         out.put("stability", territory.getStability());
         out.put("integration", territory.getIntegrationProgress());
         out.put("strategic_value", territory.getStrategicValue());
         out.put("economic_value", territory.getEconomicValue());
         out.put("economic_efficiency_permille", territory.getEconomicEfficiencyPermille());
         out.put("neighbors", territory.getNeighborIds().size());
         return out;
      });
   }

   private static int ownerNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Territory territory = payload.territory();
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("owner", territory.getOwnerFactionId().asString());
         out.put("controller", territory.getControllerFactionId() == null ? null : territory.getControllerFactionId().asString());
         out.put("occupier", territory.getOccupierFactionId() == null ? null : territory.getOccupierFactionId().asString());
         out.put("previous_owner", territory.getPreviousOwnerFactionId() == null ? null : territory.getPreviousOwnerFactionId().asString());
         out.put("owned_since", territory.getOwnedSinceGameTime());
         return out;
      });
   }

   private static int claimsNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> claims = new ArrayList<>();
         for (TerritoryClaim claim : TerritoryQueries.claims(payload.level(), payload.territory().getOwnerFactionId())) {
            if (!claim.getTerritoryId().equals(payload.territory().getId())) {
               continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", claim.getId().asString());
            row.put("claimant", claim.getClaimantFactionId().asString());
            row.put("type", claim.getType().name());
            row.put("strength", claim.getStrength());
            row.put("active", claim.isActive());
            claims.add(row);
         }

         for (TerritoryClaim claim : TerritoryQueries.allClaims(payload.level())) {
            if (!claim.isActive() || !claim.getTerritoryId().equals(payload.territory().getId())) {
               continue;
            }

            boolean already = claims.stream().anyMatch(existing -> claim.getId().asString().equals(existing.get("id")));
            if (already) {
               continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", claim.getId().asString());
            row.put("claimant", claim.getClaimantFactionId().asString());
            row.put("type", claim.getType().name());
            row.put("strength", claim.getStrength());
            row.put("active", claim.isActive());
            claims.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("territory", payload.territory().getId().asString());
         out.put("claims", claims);
         return out;
      });
   }

   private static int occupationNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         Territory territory = payload.territory();
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("occupied", territory.isOccupied());
         OccupationAdministration occupation = territory.getOccupation();
         if (occupation != null) {
            out.put("occupier", occupation.getOccupierFactionId().asString());
            out.put("policy", occupation.getPolicy().name());
            out.put("started", occupation.getStartedGameTime());
            out.put("duration", occupation.getDurationTicks(payload.level().getGameTime()));
            out.put("required_garrison", occupation.getRequiredGarrison());
            out.put("present_garrison", occupation.getPresentGarrison());
            out.put("rebellion_pressure", occupation.getRebellionPressure());
            out.put("active", occupation.isActive());
         }

         out.put("resistance", territory.getResistance());
         out.put("stability", territory.getStability());
         return out;
      });
   }

   private static int historyNearest(CommandContext<CommandSourceStack> ctx) {
      return emit(ctx, payload -> {
         List<Map<String, Object>> history = new ArrayList<>();
         for (TerritoryHistoryEntry entry : payload.territory().getHistory()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", entry.getGameTime());
            row.put("event", entry.getEventType().name());
            row.put("old_owner", entry.getOldOwner() == null ? null : entry.getOldOwner().asString());
            row.put("new_owner", entry.getNewOwner() == null ? null : entry.getNewOwner().asString());
            row.put("detail", entry.getDetail());
            history.add(row);
         }

         Map<String, Object> out = new LinkedHashMap<>();
         out.put("territory", payload.territory().getId().asString());
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

      Territory territory = TerritoryQueries.forSettlement(level, village.getId());
      if (territory == null) {
         territory = TerritoryQueries.manager(level).ensureTerritory(level, village);
      }

      Map<String, Object> payload = mapper.map(new NearestPayload(level, village, territory));
      source.sendSuccess(() -> Component.literal(GSON.toJson(payload)), false);
      return 1;
   }

   @FunctionalInterface
   private interface PayloadMapper {
      Map<String, Object> map(NearestPayload payload);
   }

   private record NearestPayload(ServerLevel level, Village village, Territory territory) {
   }
}
