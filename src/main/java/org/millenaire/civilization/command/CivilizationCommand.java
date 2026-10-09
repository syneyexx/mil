package org.millenaire.civilization.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationOrchestrator;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.CivilizationStage;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.civilization.settlement.SettlementDevelopmentClassifier;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

/**
 * Read-only civilization diagnostics under {@code /millenaire dev civilization}.
 */
public final class CivilizationCommand {
   private CivilizationCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("civilization");
      root.executes(CivilizationCommand::statusNearest);
      root.then(Commands.literal("status").executes(CivilizationCommand::statusNearest));
      root.then(Commands.literal("metrics").executes(CivilizationCommand::metrics));
      root.then(Commands.literal("stages").executes(CivilizationCommand::stages));
      parent.then(root);
   }

   private static int statusNearest(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.translatable("command.millenaire.error.not_in_overworld"));
         return 0;
      }
      Village village = VillageSavedData.get(level)
         .getVillageManager()
         .findNearestVillage(BlockPos.containing(source.getPosition()), 5000.0);
      if (village == null) {
         source.sendFailure(Component.translatable("command.millenaire.civilization.no_village"));
         return 0;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("orchestrator_enabled", CivilizationOrchestrator.isEnabled());
      out.put("auto_trade", MillenaireServerConfig.SERVER.civilizationAutoTradeEnabled.get());
      out.put("offscreen", MillenaireServerConfig.SERVER.civilizationOffscreenEnabled.get());
      out.put("village", village.getVillageName());
      out.put("village_id", village.getId().uuid().toString());
      FactionId factionId = village.getFactionId();
      out.put("faction_id", factionId == null ? null : factionId.asString());
      if (factionId != null) {
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
         out.put("settlements", faction == null ? 0 : faction.getSettlementIds().size());
         PoliticalLeader leader = PoliticsQueries.leader(level, factionId);
         out.put("leader", leader == null ? null : leader.getDisplayName());
         out.put("legitimacy", PoliticsQueries.legitimacy(level, factionId));
         out.put("succession_pending", PoliticsQueries.successionPending(level, factionId));
         out.put("at_war", WarSavedData.get(level).getWarManager().isAtWar(factionId));
         FactionEconomicProfile profile = EconomySavedData.get(level).getTradeManager().getProfile(factionId);
         out.put("economy_balance", profile == null ? null : profile.getBalanceLevel().name());
         CompoundTag popTag = CivilizationSavedData.get(level).getPopulationSnapshot(factionId.asString());
         if (popTag != null) {
            PopulationSnapshot pop = PopulationSnapshot.load(popTag);
            out.put("population_residents", pop.residents());
            out.put("population_housing", pop.housingCapacity());
         }
      }
      out.put("development", SettlementDevelopmentClassifier.classify(village, level.getGameTime()));
      String stored = CivilizationSavedData.get(level).getDevelopmentLabel(village.getId().uuid());
      out.put("development_stored", stored);
      source.sendSuccess(() -> Component.literal(out.toString()), false);
      return 1;
   }

   private static int metrics(CommandContext<CommandSourceStack> ctx) {
      ctx.getSource().sendSuccess(() -> Component.literal(CivilizationMetrics.snapshot()), false);
      return 1;
   }

   private static int stages(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         ctx.getSource().sendFailure(Component.translatable("command.millenaire.error.not_in_overworld"));
         return 0;
      }
      CivilizationSavedData state = CivilizationSavedData.get(level);
      Map<String, Long> out = new LinkedHashMap<>();
      for (CivilizationStage stage : CivilizationStage.values()) {
         out.put(stage.name(), state.lastStageGameTime(stage));
      }
      ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
      return 1;
   }
}
