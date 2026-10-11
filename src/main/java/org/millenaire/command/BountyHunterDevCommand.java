package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.faction.crime.CultureBountyManager;
import org.millenaire.faction.crime.CultureBountyRecord;
import org.millenaire.faction.crime.CultureBountySavedData;
import org.millenaire.faction.crime.hunter.BountyEligibilityService;
import org.millenaire.faction.crime.hunter.BountyHunterContract;
import org.millenaire.faction.crime.hunter.BountyHunterContractSavedData;
import org.millenaire.faction.crime.hunter.BountyHunterPhase;

/**
 * Dev helpers for end-to-end bounty hunter validation.
 * Usage: /millenaire dev bountyhunter grant|status|force_dispatch
 */
public final class BountyHunterDevCommand {
   private BountyHunterDevCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("bountyhunter")
            .then(Commands.literal("grant")
               .then(Commands.argument("score", IntegerArgumentType.integer(40, 2000))
                  .executes(BountyHunterDevCommand::grant)))
            .then(Commands.literal("status").executes(BountyHunterDevCommand::status))
            .then(Commands.literal("force_dispatch").executes(BountyHunterDevCommand::forceDispatch))
            .then(Commands.literal("clear").executes(BountyHunterDevCommand::clear))
      );
   }

   private static int grant(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      ServerLevel level = ctx.getSource().getLevel();
      int score = IntegerArgumentType.getInteger(ctx, "score");
      ResourceLocation culture = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      CultureBountyRecord record = manager.getOrCreate(player.getUUID(), culture);
      record.setBountyScore(score);
      record.setLastCrimeGameTime(level.getGameTime());
      CultureBountySavedData.get(level).setDirty();
      org.millenaire.faction.crime.BountyHudSync.sync(level, player);
      ctx.getSource().sendSuccess(
         () -> Component.literal("Granted bounty score " + score + " for " + culture + ". Eligible="
            + BountyEligibilityService.hasActiveEnforceableBounty(level, player.getUUID())),
         true
      );
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      ServerLevel level = ctx.getSource().getLevel();
      var primary = BountyEligibilityService.selectPrimaryBounty(level, player.getUUID());
      BountyHunterContract contract = BountyHunterContractSavedData.get(level).getForTarget(player.getUUID());
      String bounty = primary.map(r -> r.cultureId() + " score=" + r.bountyScore() + " tier=" + r.tier()).orElse("none");
      String c = contract == null
         ? "none"
         : "phase=" + contract.phase() + " hunter=" + contract.hunterEntityUuid() + " settled=" + contract.settled();
      ctx.getSource().sendSuccess(() -> Component.literal("Bounty: " + bounty + " | Contract: " + c), false);
      return 1;
   }

   private static int forceDispatch(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      ServerLevel level = ctx.getSource().getLevel();
      var primary = BountyEligibilityService.selectPrimaryBounty(level, player.getUUID());
      if (primary.isEmpty()) {
         ctx.getSource().sendFailure(Component.literal("No enforceable bounty — use bountyhunter grant first"));
         return 0;
      }
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      BountyHunterContract existing = data.getForTarget(player.getUUID());
      long now = level.getGameTime();
      if (existing == null) {
         String key = BountyEligibilityService.bountyKey(player.getUUID(), primary.get().cultureId());
         existing = new BountyHunterContract(
            java.util.UUID.randomUUID(), player.getUUID(), primary.get().cultureId(), key, now, now
         );
      }
      existing.setPhase(BountyHunterPhase.DISPATCH_PENDING);
      existing.setDispatchAtGameTime(now);
      existing.setNextRedispatchGameTime(0L);
      existing.setHunterEntityUuid(null);
      existing.setSettled(false);
      data.put(existing);
      ctx.getSource().sendSuccess(() -> Component.literal("Forced dispatch pending — hunter should spawn within ~2s"), true);
      return 1;
   }

   private static int clear(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ctx.getSource().getPlayer();
      if (player == null) {
         ctx.getSource().sendFailure(Component.literal("Player only"));
         return 0;
      }
      ServerLevel level = ctx.getSource().getLevel();
      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      for (CultureBountyRecord record : manager.getRecordsForPlayer(player.getUUID())) {
         manager.reduceBounty(level, player.getUUID(), record.cultureId(), record.bountyScore());
      }
      BountyHunterContractSavedData.get(level).remove(player.getUUID());
      ctx.getSource().sendSuccess(() -> Component.literal("Cleared bounties and hunter contract"), true);
      return 1;
   }
}
