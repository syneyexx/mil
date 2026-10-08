package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.diagnostics.NavigationEventLog;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public final class NavDiagCommand {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int FIND_STUCK_WINDOW_TICKS = 1200;
   private static final int FIND_STUCK_MIN_TELEPORTS = 2;

   private NavDiagCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         ((LiteralArgumentBuilder)Commands.literal("nav-trace").executes(ctx -> runTrace(ctx, "nearest")))
            .then(Commands.argument("selector", StringArgumentType.string()).executes(ctx -> runTrace(ctx, StringArgumentType.getString(ctx, "selector"))))
      );
      parent.then(Commands.literal("nav-counters").executes(NavDiagCommand::runCounters));
      parent.then(Commands.literal("nav-counters-reset").executes(ctx -> {
         NavigationCounters.resetAll();
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Nav counters reset."), false);
         return 1;
      }));
      parent.then(
         Commands.literal("nav-watch")
            .then(Commands.argument("selector", StringArgumentType.string()).executes(ctx -> runWatch(ctx, StringArgumentType.getString(ctx, "selector"))))
      );
      parent.then(Commands.literal("find-stuck").executes(NavDiagCommand::runFindStuck));
      parent.then(
         Commands.literal("nav-probe")
            .then(
               Commands.argument("from", BlockPosArgument.blockPos())
                  .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(NavDiagCommand::runProbe))
            )
      );
      parent.then(
         Commands.literal("block-scan")
            .then(
               Commands.argument("center", BlockPosArgument.blockPos())
                  .then(
                     Commands.argument("radius", IntegerArgumentType.integer(1, 24))
                        .then(
                           Commands.argument("yBelow", IntegerArgumentType.integer(0, 16))
                              .then(Commands.argument("yAbove", IntegerArgumentType.integer(0, 16)).executes(NavDiagCommand::runBlockScan))
                        )
                  )
            )
      );
   }

   private static int runBlockScan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BlockPos center = BlockPosArgument.getLoadedBlockPos(ctx, "center");
      int radius = IntegerArgumentType.getInteger(ctx, "radius");
      int yBelow = IntegerArgumentType.getInteger(ctx, "yBelow");
      int yAbove = IntegerArgumentType.getInteger(ctx, "yAbove");
      source.sendSuccess(
         () -> Component.literal(
            "block-scan center="
               + center.toShortString()
               + " r="
               + radius
               + " (X "
               + (center.getX() - radius)
               + ".."
               + (center.getX() + radius)
               + ", Z "
               + (center.getZ() - radius)
               + ".."
               + (center.getZ() + radius)
               + ") legend: .=air s=botSlab S=topSlab >=stairs f=fence #=full ,=other M=center"
         ),
         false
      );
      MutableBlockPos cursor = new MutableBlockPos();

      for (int y = center.getY() + yAbove; y >= center.getY() - yBelow; y--) {
         int fy = y;
         source.sendSuccess(() -> Component.literal("Y=" + fy + (fy == center.getY() ? " (counter)" : "")), false);

         for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            StringBuilder row = new StringBuilder();

            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
               cursor.set(x, y, z);
               char c;
               if (x == center.getX() && y == center.getY() && z == center.getZ()) {
                  c = 'M';
               } else {
                  c = classifyBlock(level, cursor);
               }

               row.append(c);
            }

            String line = "X" + x + " " + row;
            source.sendSuccess(() -> Component.literal(line), false);
         }
      }

      return 1;
   }

   private static char classifyBlock(ServerLevel level, BlockPos pos) {
      BlockState st = level.getBlockState(pos);
      if (st.isAir()) {
         return '.';
      } else if (st.getBlock() instanceof SlabBlock) {
         SlabType t = (SlabType)st.getValue(SlabBlock.TYPE);
         return (char)(t == SlabType.TOP ? 'S' : (t == SlabType.BOTTOM ? 's' : '#'));
      } else if (st.getBlock() instanceof StairBlock) {
         return '>';
      } else if (st.is(BlockTags.FENCES) || st.is(BlockTags.WALLS) || st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.DOORS)) {
         return 'f';
      } else {
         return (char)(st.isSolidRender(level, pos) ? '#' : ',');
      }
   }

   private static int runProbe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
      BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, "to");
      MillVillager v = nearestVillagerTo(level, from);
      if (v == null) {
         source.sendFailure(Component.literal("No loaded villager to use as probe."));
         return 0;
      }

      v.getNavigation().stop();
      v.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
      BlockPos actualStart = v.blockPosition();
      Path path = v.getNavigation().createPath(to, 1);
      StringBuilder sb = new StringBuilder();
      sb.append("nav-probe ")
         .append(describe(v))
         .append(" from=")
         .append(from.toShortString())
         .append(" (landed=")
         .append(actualStart.toShortString())
         .append(")")
         .append(" to=")
         .append(to.toShortString())
         .append(" → ");
      if (path == null) {
         sb.append("NO PATH (createPath returned null)");
      } else {
         Node end = path.getEndNode();
         BlockPos endPos = end != null ? end.asBlockPos() : null;
         sb.append("canReach=").append(path.canReach()).append(" nodes=").append(path.getNodeCount());
         if (endPos != null) {
            sb.append(" end=")
               .append(endPos.toShortString())
               .append(" endDy=")
               .append(endPos.getY() - to.getY())
               .append(" endHorizDist=")
               .append(String.format(Locale.ROOT, "%.1f", Math.sqrt(NavDiagHorizSq(endPos, to))));
         }
      }

      String line = sb.toString();
      source.sendSuccess(() -> Component.literal(line), false);
      return 1;
   }

   private static double NavDiagHorizSq(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   private static MillVillager nearestVillagerTo(ServerLevel level, BlockPos pos) {
      MillVillager best = null;
      double bestSq = Double.MAX_VALUE;

      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         for (UUID uuid : village.getVillagerUuids()) {
            if (level.getEntity(uuid) instanceof MillVillager v) {
               double ds = v.blockPosition().distSqr(pos);
               if (ds < bestSq) {
                  bestSq = ds;
                  best = v;
               }
            }
         }
      }

      return best;
   }

   private static int runTrace(CommandContext<CommandSourceStack> ctx, String selector) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      MillVillager v = resolveVillager(source, level, selector);
      if (v == null) {
         source.sendFailure(Component.literal("Villager not found: " + selector));
         return 0;
      }

      NavigationEventLog log = v.getNavEventLog();
      List<NavEvent> events = log.snapshot();
      long now = level.getGameTime();
      String header = "Nav trace — "
         + describe(v)
         + " @ "
         + v.blockPosition().toShortString()
         + " (events="
         + events.size()
         + "/256, lastEvent t-"
         + (log.lastEventTick() < 0L ? "∞" : now - log.lastEventTick())
         + ")";
      source.sendSuccess(() -> Component.literal(header), false);
      if (events.isEmpty()) {
         source.sendSuccess(() -> Component.literal("  (no events recorded)"), false);
         return 1;
      }

      for (NavEvent e : events) {
         long age = now - e.tick();
         String line = String.format(Locale.ROOT, "  t-%-5d %s/%s %s", age, e.layer(), e.type(), e.detail());
         source.sendSuccess(() -> Component.literal(line), false);
      }

      GoalScheduler scheduler = v.getGoalScheduler();
      if (scheduler != null) {
         VillagerGoal goal = scheduler.getCurrentGoal();
         String goalId = goal != null ? goal.id().toString() : "idle";
         VillagerNavDriver.NavDiagnostics diag = v.getNavManager().getDiagnostics();
         BlockPos dest = v.getNavManager().getDestination();
         String tail = "Goal="
            + goalId
            + " localStuck="
            + diag.localStuck()
            + " longDistStuck="
            + diag.longDistanceStuck()
            + " teleports="
            + diag.teleportCount()
            + (dest != null ? " dest=" + dest.toShortString() : "");
         source.sendSuccess(() -> Component.literal(tail), false);
      }

      return 1;
   }

   private static int runCounters(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      StringBuilder sb = new StringBuilder();
      sb.append("Nav counters — ");
      sb.append("teleport.total=").append(NavigationCounters.teleportTotal()).append(" ");

      for (NavEvent.Layer l : NavEvent.Layer.values()) {
         long c = NavigationCounters.teleportFor(l);
         if (c > 0L) {
            sb.append("tp.").append(l).append("=").append(c).append(" ");
         }
      }

      sb.append("short-jump=").append(NavigationCounters.shortJump()).append(" ");
      sb.append("goal.abandoned=").append(NavigationCounters.goalAbandoned()).append(" ");
      sb.append("target.invalid=").append(NavigationCounters.targetInvalid()).append(" ");
      sb.append("reload.pose_restored=").append(NavigationCounters.poseSleepingRestored()).append(" ");
      sb.append("reload.pose_cleared=").append(NavigationCounters.poseSleepingCleared()).append(" ");
      sb.append("bed.suffocation=").append(NavigationCounters.bedSuffocation()).append(" ");
      sb.append("leaf_clear.skipped_in_building=").append(NavigationCounters.leafClearSkippedInBuilding()).append(" ");
      sb.append("astar.search=").append(NavigationCounters.astarSearch()).append(" ");
      sb.append("astar.create_path=").append(NavigationCounters.astarCreatePath()).append(" ");
      sb.append("astar.ms=").append(String.format(Locale.ROOT, "%.1f", NavigationCounters.astarSearchNanos() / 1000000.0));
      String line = sb.toString();
      source.sendSuccess(() -> Component.literal(line), false);
      return 1;
   }

   private static int runWatch(CommandContext<CommandSourceStack> ctx, String selector) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      MillVillager v = resolveVillager(source, level, selector);
      if (v == null) {
         source.sendFailure(Component.literal("Villager not found: " + selector));
         return 0;
      }

      NavigationEventLog log = v.getNavEventLog();
      if (log.isWatched()) {
         log.disableWatch();
         source.sendSuccess(() -> Component.literal("Watch OFF — " + describe(v)), false);
      } else {
         log.enableWatch(LOGGER, "[nav-watch " + describe(v) + "]");
         source.sendSuccess(() -> Component.literal("Watch ON — " + describe(v) + " (see server log)"), false);
      }

      return 1;
   }

   private static int runFindStuck(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerLevel level = source.getLevel();
      if (level.dimension() != Level.OVERWORLD) {
         source.sendFailure(Component.literal("Run this command in the Overworld."));
         return 0;
      }

      long now = level.getGameTime();
      List<MillVillager> flagged = new ArrayList<>();
      int scanned = 0;

      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         for (UUID uuid : village.getVillagerUuids()) {
            if (level.getEntity(uuid) instanceof MillVillager v) {
               scanned++;
               NavigationEventLog log = v.getNavEventLog();
               int recentTps = log.countRecent(NavEvent.Type.TELEPORT, now, 1200L);
               int recentAbandons = log.countRecent(NavEvent.Type.GOAL_ABANDONED, now, 1200L);
               boolean flag = recentTps >= 2 || recentAbandons > 0 || log.hasStuckSinceLastStart();
               if (flag) {
                  flagged.add(v);
               }
            }
         }
      }

      int scannedFinal = scanned;
      source.sendSuccess(() -> Component.literal("Scanned " + scannedFinal + " loaded villagers — " + flagged.size() + " flagged"), false);

      for (MillVillager v : flagged) {
         NavigationEventLog log = v.getNavEventLog();
         int tp = log.countRecent(NavEvent.Type.TELEPORT, now, 1200L);
         int ab = log.countRecent(NavEvent.Type.GOAL_ABANDONED, now, 1200L);
         String line = "  "
            + describe(v)
            + " @ "
            + v.blockPosition().toShortString()
            + " tps20s="
            + tp
            + " abandons20s="
            + ab
            + " stuckSig="
            + log.hasStuckSinceLastStart();
         source.sendSuccess(() -> Component.literal(line), false);
      }

      return 1;
   }

   private static MillVillager resolveVillager(CommandSourceStack source, ServerLevel level, String selector) {
      if (selector != null && !selector.isBlank() && !"-".equals(selector) && !selector.equalsIgnoreCase("nearest")) {
         String needle = selector.toLowerCase(Locale.ROOT);

         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            for (UUID uuid : village.getVillagerUuids()) {
               if (level.getEntity(uuid) instanceof MillVillager v) {
                  if (uuid.toString().startsWith(needle)) {
                     return v;
                  }

                  String typeName = v.getVillagerTypeId() != null ? v.getVillagerTypeId().getPath() : "";
                  if (typeName.toLowerCase(Locale.ROOT).contains(needle)) {
                     return v;
                  }

                  if (v.getName() != null && v.getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                     return v;
                  }
               }
            }
         }

         return null;
      } else {
         BlockPos origin = BlockPos.containing(source.getPosition());
         MillVillager best = null;
         double bestSq = Double.MAX_VALUE;

         for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
            for (UUID uuid : village.getVillagerUuids()) {
               if (level.getEntity(uuid) instanceof MillVillager v) {
                  double ds = v.blockPosition().distSqr(origin);
                  if (ds < bestSq) {
                     bestSq = ds;
                     best = v;
                  }
               }
            }
         }

         return best;
      }
   }

   private static String describe(MillVillager v) {
      String type = v.getVillagerTypeId() != null ? v.getVillagerTypeId().getPath() : "?";
      String uuid = v.getUUID().toString().substring(0, 8);
      return type + "[" + uuid + "]";
   }
}
