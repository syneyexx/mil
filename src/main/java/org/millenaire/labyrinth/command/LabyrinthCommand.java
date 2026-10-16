package org.millenaire.labyrinth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;
import org.millenaire.labyrinth.entity.LabyrinthOrdeData;
import org.millenaire.labyrinth.portal.LabyrinthTeleporter;

/**
 * {@code /millenaire labyrinth enter|leave|status|spawn <kind>}
 */
public final class LabyrinthCommand {
   private static final SuggestionProvider<CommandSourceStack> KIND_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      Arrays.stream(GebondeneKind.values()).map(GebondeneKind::id),
      builder
   );

   private LabyrinthCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("millenaire")
            .then(
               ((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("labyrinth").requires(source -> source.hasPermission(2)))
                  .then(Commands.literal("enter").executes(LabyrinthCommand::enter))
                  .then(Commands.literal("leave").executes(LabyrinthCommand::leave))
                  .then(Commands.literal("status").executes(LabyrinthCommand::status))
                  .then(
                     Commands.literal("spawn")
                        .then(
                           Commands.argument("kind", StringArgumentType.word())
                              .suggests(KIND_SUGGESTIONS)
                              .executes(LabyrinthCommand::spawn)
                        )
                  )
            )
      );
   }

   private static int enter(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }
      return LabyrinthTeleporter.enter(player) ? 1 : 0;
   }

   private static int leave(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }
      return LabyrinthTeleporter.leave(player) ? 1 : 0;
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      boolean enabled = LabyrinthTeleporter.isLabyrinthEnabled(player);
      boolean inside = player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL);
      ServerLevel labyrinth = LabyrinthTeleporter.getOrCreateLabyrinth(player.server);
      long nearby = 0;
      if (player.level() instanceof ServerLevel serverLevel) {
         AABB box = player.getBoundingBox().inflate(48.0);
         nearby = serverLevel.getEntitiesOfClass(GebondeneEntity.class, box).size();
      }

      boolean hostile = false;
      int reputation = 0;
      if (player.level() instanceof ServerLevel serverLevel) {
         LabyrinthOrdeData data = LabyrinthOrdeData.get(serverLevel);
         hostile = data.isHostile(player.getUUID());
         int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
         int regionX = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
         int regionZ = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
         reputation = data.getReputation(player.getUUID(), regionX, regionZ);
      }

      String dimPath = player.level().dimension().location().toString();
      boolean labyrinthLoaded = labyrinth != null;
      source.sendSuccess(
         () -> Component.translatable(
            "command.millenaire.labyrinth.status",
            enabled,
            inside,
            dimPath,
            labyrinthLoaded,
            nearby,
            hostile,
            reputation
         ),
         false
      );
      // Also print a readable fallback line for when translations are missing.
      source.sendSuccess(
         () -> Component.literal(
            String.format(
               Locale.ROOT,
               "Labyrinth enabled=%s inside=%s dim=%s loaded=%s nearbyGebondene=%d hostile=%s reputation=%d",
               enabled,
               inside,
               dimPath,
               labyrinthLoaded,
               nearby,
               hostile,
               reputation
            )
         ),
         false
      );
      return 1;
   }

   private static int spawn(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }
      if (!(player.level() instanceof ServerLevel serverLevel)) {
         source.sendFailure(Component.literal("Server level required"));
         return 0;
      }

      String kindArg = StringArgumentType.getString(ctx, "kind");
      GebondeneKind kind = GebondeneKind.byId(kindArg).orElse(null);
      if (kind == null) {
         source.sendFailure(Component.translatable("command.millenaire.labyrinth.unknown_kind", kindArg));
         return 0;
      }

      var typeOpt = GebondeneEntity.entityType();
      if (typeOpt.isEmpty()) {
         source.sendFailure(Component.literal("Gebondene entity type is not registered yet (millenaire:gebondene)"));
         return 0;
      }

      GebondeneEntity entity = typeOpt.get().create(serverLevel);
      if (entity == null) {
         source.sendFailure(Component.literal("Failed to create Gebondene"));
         return 0;
      }
      entity.setKind(kind);
      entity.setAttitude(GebondeneAttitude.GASTVRIJ);
      entity.moveTo(player.getX() + 1.5, player.getY(), player.getZ() + 1.5, player.getYRot(), 0.0F);
      serverLevel.addFreshEntity(entity);
      source.sendSuccess(
         () -> Component.translatable("command.millenaire.labyrinth.spawned", kind.id()),
         true
      );
      return 1;
   }
}
