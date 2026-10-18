package org.millenaire.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.activity.ActivityDiagnostics;
import org.millenaire.activity.ActivityKind;
import org.millenaire.activity.ActivityLifecycleService;
import org.millenaire.activity.ActivityOperation;
import org.millenaire.activity.ActivityPlanner;
import org.millenaire.activity.ActivityProfile;
import org.millenaire.activity.ActivityProfileLoader;
import org.millenaire.activity.ActivityReasonCode;
import org.millenaire.activity.ActivitySavedData;
import org.millenaire.activity.ActivitySpatialIndex;
import org.millenaire.activity.RegionalActivityService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * {@code /millenaire activity status|metrics|inspect|explain|enable|disable|request}
 */
public final class ActivityCommand {
   private ActivityCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("activity").requires(s -> s.hasPermission(2)))
                  .then(Commands.literal("status").executes(ActivityCommand::status))
                  .then(Commands.literal("metrics").executes(ActivityCommand::metrics))
                  .then(
                     Commands.literal("inspect")
                        .then(Commands.argument("operationId", StringArgumentType.string()).executes(ActivityCommand::inspect))
                  )
                  .then(
                     Commands.literal("explain")
                        .then(Commands.argument("villageOrCampId", StringArgumentType.string()).executes(ActivityCommand::explain))
                  )
                  .then(Commands.literal("enable").executes(ctx -> setEnabled(ctx, true)))
                  .then(Commands.literal("disable").executes(ctx -> setEnabled(ctx, false)))
                  .then(
                     Commands.literal("request")
                        .then(
                           Commands.argument("kind", StringArgumentType.word())
                              .then(Commands.argument("villageId", StringArgumentType.string()).executes(ActivityCommand::request))
                        )
                  )
            )
      );
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      ctx.getSource().sendSuccess(() -> Component.literal(ActivityDiagnostics.get().status(level)), false);
      return 1;
   }

   private static int metrics(CommandContext<CommandSourceStack> ctx) {
      ctx.getSource().sendSuccess(() -> Component.literal(ActivityDiagnostics.get().metrics()), false);
      return 1;
   }

   private static int inspect(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      String raw = StringArgumentType.getString(ctx, "operationId");
      try {
         UUID id = UUID.fromString(raw);
         String text = ActivityDiagnostics.get().inspect(level, id);
         if (text == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown operation " + raw));
            return 0;
         }
         ctx.getSource().sendSuccess(() -> Component.literal(text), false);
         return 1;
      } catch (IllegalArgumentException e) {
         ctx.getSource().sendFailure(Component.literal("Invalid UUID"));
         return 0;
      }
   }

   private static int explain(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      String raw = StringArgumentType.getString(ctx, "villageOrCampId");
      try {
         UUID id = UUID.fromString(raw);
         String text = ActivityDiagnostics.get().explain(level, id);
         ctx.getSource().sendSuccess(() -> Component.literal(text), false);
         return 1;
      } catch (IllegalArgumentException e) {
         ctx.getSource().sendFailure(Component.literal("Invalid UUID"));
         return 0;
      }
   }

   private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
      ActivityDiagnostics.get().setRuntimeEnabled(enabled);
      ctx.getSource()
         .sendSuccess(() -> Component.literal("Regional activity runtime " + (enabled ? "enabled" : "disabled") + " (config master switch still applies)"), true);
      return 1;
   }

   /**
    * Admin debug request — still respects source, safety, and budget gates (no forced spawn bypass).
    */
   private static int request(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      ActivityKind kind = ActivityKind.fromId(StringArgumentType.getString(ctx, "kind"));
      UUID villageId;
      try {
         villageId = UUID.fromString(StringArgumentType.getString(ctx, "villageId"));
      } catch (IllegalArgumentException e) {
         ctx.getSource().sendFailure(Component.literal("Invalid village UUID"));
         return 0;
      }
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageId));
      if (village == null) {
         ctx.getSource().sendFailure(Component.literal("Village not found"));
         return 0;
      }
      ActivityProfile profile = null;
      for (ActivityProfile p : ActivityProfileLoader.forCulture(village.getCultureId())) {
         if (p.allows(kind)) {
            profile = p;
            break;
         }
      }
      if (profile == null) {
         ctx.getSource().sendFailure(Component.literal("No profile allows " + kind + " (" + ActivityReasonCode.NO_ELIGIBLE_MEMBERS + ")"));
         return 0;
      }
      var admission = RegionalActivityService.get().admission();
      ActivitySpatialIndex index = RegionalActivityService.get().indexFor(level);
      index.upsertSettlement(village);
      ActivitySavedData data = ActivitySavedData.get(level);
      ActivityPlanner.Proposal proposal = null;
      // Run a bounded plan slice focused by temporarily using fairness near this village
      for (ActivityPlanner.Proposal p : ActivityPlanner.planSlice(level, data, index, admission, 0, 16)) {
         if (villageId.equals(p.sourceId()) && p.kind() == kind) {
            proposal = p;
            break;
         }
      }
      if (proposal == null) {
         ctx.getSource()
            .sendFailure(
               Component.literal(
                  "Request deferred: " + admission.lastDefer() + " (no forced bypass; see /millenaire activity explain " + villageId + ")"
               )
            );
         return 0;
      }
      ActivityOperation op = ActivityLifecycleService.createFromProposal(level, proposal);
      // Force requested kind if planner returned compatible proposal from same source
      data.putOperation(op);
      ActivityLifecycleService.advance(level, data, admission, index, op);
      ctx.getSource().sendSuccess(() -> Component.literal("Requested " + kind + " as " + op.operationId() + " state=" + op.state()), true);
      return 1;
   }
}
