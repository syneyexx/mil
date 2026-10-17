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
import org.millenaire.entity.ModEntities;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;
import org.millenaire.labyrinth.entity.LabyrinthOrdeData;
import org.millenaire.labyrinth.gen.LabyrinthMaze;
import org.millenaire.labyrinth.portal.LabyrinthTeleporter;

/**
 * {@code /millenaire labyrinth enter|leave|status|resetdim|spawn <kind>}
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
                  .then(Commands.literal("resetdim").executes(LabyrinthCommand::resetDimHint))
                  .then(
                     Commands.literal("spawn")
                        .then(
                           Commands.argument("kind", StringArgumentType.word())
                              .suggests(KIND_SUGGESTIONS)
                              .executes(LabyrinthCommand::spawn)
                        )
                  )
                  .then(
                     Commands.literal("vorm")
                        .then(
                           Commands.argument("vorm", StringArgumentType.word())
                              .suggests(VORM_SUGGESTIONS)
                              .executes(LabyrinthCommand::setVorm)
                        )
                  )
                  .then(
                     Commands.literal("relic")
                        .then(
                           Commands.argument("relic", StringArgumentType.word())
                              .suggests(RELIC_SUGGESTIONS)
                              .executes(LabyrinthCommand::giveRelic)
                        )
                  )
            )
      );
   }

   private static final SuggestionProvider<CommandSourceStack> VORM_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      Arrays.stream(org.millenaire.labyrinth.portal.ConfiguratieVorm.values()).map(org.millenaire.labyrinth.portal.ConfiguratieVorm::id),
      builder
   );

   private static final SuggestionProvider<CommandSourceStack> RELIC_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      java.util.List.of("zegel", "masker", "koord"),
      builder
   );

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

   /**
    * Clears in-memory maze cache and prints player-facing migration instructions.
    * Does not delete world files (operators must wipe the dim folder offline).
    */
   private static int resetDimHint(CommandContext<CommandSourceStack> ctx) {
      LabyrinthMaze.clearCache();
      ctx.getSource().sendSuccess(
         () -> Component.translatable("labyrinth.command.resetdim", LabyrinthKeys.LAYOUT_VERSION),
         true
      );
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }

      final boolean enabled = LabyrinthTeleporter.isLabyrinthEnabled(player);
      final boolean inside = player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL);
      ServerLevel labyrinth = LabyrinthTeleporter.getOrCreateLabyrinth(player.server);
      long nearbyCount = 0;
      boolean hostileFlag = false;
      int reputationValue = 0;
      if (player.level() instanceof ServerLevel serverLevel) {
         AABB box = player.getBoundingBox().inflate(48.0);
         nearbyCount = serverLevel.getEntitiesOfClass(GebondeneEntity.class, box).size();
         LabyrinthOrdeData data = LabyrinthOrdeData.get(serverLevel);
         hostileFlag = data.isHostile(player.getUUID());
         int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
         int regionX = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
         int regionZ = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
         reputationValue = data.getReputation(player.getUUID(), regionX, regionZ);
      }

      final String dimPath = player.level().dimension().location().toString();
      final boolean labyrinthLoaded = labyrinth != null;
      final long nearby = nearbyCount;
      final boolean hostile = hostileFlag;
      final int reputation = reputationValue;
      int[] statusCell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(
         player.blockPosition().getX(),
         player.blockPosition().getZ()
      );
      final LabyrinthKeys.Zone zone = LabyrinthKeys.zoneAt(statusCell[0], statusCell[1]);
      source.sendSuccess(
         () -> Component.literal(
            String.format(
               Locale.ROOT,
               "Labyrinth v%d enabled=%s inside=%s zone=%s dim=%s loaded=%s nearbyGebondene=%d hostile=%s reputation=%d",
               LabyrinthKeys.LAYOUT_VERSION,
               enabled,
               inside,
               zone,
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

      GebondeneEntity entity = ModEntities.GEBONDENE.get().create(serverLevel);
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

   private static int setVorm(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }
      String vormArg = StringArgumentType.getString(ctx, "vorm");
      var vorm = org.millenaire.labyrinth.portal.ConfiguratieVorm.byId(vormArg).orElse(null);
      if (vorm == null) {
         source.sendFailure(Component.literal("Unknown vorm: " + vormArg));
         return 0;
      }
      var stack = player.getMainHandItem();
      if (!(stack.getItem() instanceof org.millenaire.labyrinth.portal.ConfiguratieItem)) {
         source.sendFailure(Component.literal("Hold a Configuratie in your main hand"));
         return 0;
      }
      org.millenaire.labyrinth.portal.ConfiguratieItem.setVorm(stack, vorm);
      source.sendSuccess(() -> Component.literal("Configuratie vorm → " + vorm.id()), true);
      return 1;
   }

   private static int giveRelic(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(Component.translatable("command.millenaire.error.player_required"));
         return 0;
      }
      String relic = StringArgumentType.getString(ctx, "relic").toLowerCase(Locale.ROOT);
      net.minecraft.world.item.ItemStack stack = switch (relic) {
         case "zegel" -> new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.LABYRINTH_ZEGEL.get());
         case "masker" -> new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.LABYRINTH_MASKER.get());
         case "koord" -> new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.LABYRINTH_KOORD.get());
         default -> null;
      };
      if (stack == null) {
         source.sendFailure(Component.literal("Unknown relic: " + relic));
         return 0;
      }
      if (!player.addItem(stack)) {
         player.drop(stack, false);
      }
      source.sendSuccess(() -> Component.literal("Gave labyrinth relic " + relic), true);
      return 1;
   }
}
