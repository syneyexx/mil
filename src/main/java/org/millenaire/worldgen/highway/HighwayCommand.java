package org.millenaire.worldgen.highway;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.worldgen.highway.travel.RoadJourney;
import org.millenaire.worldgen.highway.travel.RoadJourneySavedData;

public final class HighwayCommand {
   private HighwayCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("highway")
         .requires(cs -> cs.hasPermission(2))
         .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
         .then(Commands.literal("audit").executes(ctx -> audit(ctx.getSource())));
      dispatcher.register(Commands.literal("millenaire").then(root));
   }

   private static int status(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      boolean enabled = HighwayEngine.isEnabled();
      HighwayNodeSavedData nodes = HighwayNodeSavedData.get(level);
      HighwayRouteSavedData routes = HighwayRouteSavedData.get(level);
      RoadJourneySavedData journeys = RoadJourneySavedData.get(level);
      int connectedCount = 0;
      int blockedCount = 0;
      int partialCount = 0;
      for (HighwayRoute r : routes.allRoutes()) {
         switch (r.status()) {
            case CONNECTED -> connectedCount++;
            case BLOCKED, FAILED -> blockedCount++;
            case PARTIAL, WAITING_CHUNKS, PLANNED -> partialCount++;
         }
      }
      final int connected = connectedCount;
      final int blocked = blockedCount;
      final int partial = partialCount;
      final int routeCount = routes.size();
      final int journeyCount = journeys.size();
      final int nodeCount = nodes.size();
      final int pendingChunks = nodes.pendingChunkCount();
      source.sendSuccess(
         () -> Component.literal(
            "[Millenaire] Highway enabled="
               + enabled
               + " nodes="
               + nodeCount
               + " pendingChunks="
               + pendingChunks
               + " routes="
               + routeCount
               + " (connected="
               + connected
               + " active="
               + partial
               + " blocked="
               + blocked
               + ") journeys="
               + journeyCount
         ),
         false
      );
      return 1;
   }

   private static int audit(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      HighwayNodeSavedData nodes = HighwayNodeSavedData.get(level);
      int mill = 0;
      int vanilla = 0;
      int mod = 0;
      int hubs = 0;
      for (HighwayNode n : nodes.allNodes()) {
         switch (n.kind()) {
            case MILLENAIRE, MILLENAIRE_PLAN -> mill++;
            case VANILLA_VILLAGE -> vanilla++;
            case MOD_SETTLEMENT -> mod++;
         }
         if (n.factionHub()) {
            hubs++;
         }
      }
      int enRoute = 0;
      for (RoadJourney j : RoadJourneySavedData.get(level).all()) {
         if (j.phase() == RoadJourney.Phase.EN_ROUTE) {
            enRoute++;
         }
      }
      int finalMill = mill;
      int finalVanilla = vanilla;
      int finalMod = mod;
      int finalHubs = hubs;
      int finalEnRoute = enRoute;
      source.sendSuccess(
         () -> Component.literal(
            "[Millenaire] Highway audit nodes millenaire="
               + finalMill
               + " vanilla="
               + finalVanilla
               + " mod="
               + finalMod
               + " hubs="
               + finalHubs
               + " journeysEnRoute="
               + finalEnRoute
         ),
         false
      );
      for (HighwayRoute r : HighwayRouteSavedData.get(level).allRoutes()) {
         if (r.status() == HighwayRoute.Status.BLOCKED || r.status() == HighwayRoute.Status.FAILED) {
            String reason = r.blockReason();
            source.sendSuccess(
               () -> Component.literal(
                  "  blocked " + r.fromNodeKey() + " -> " + r.toNodeKey() + " reason=" + reason
               ),
               false
            );
         }
      }
      return 1;
   }
}
