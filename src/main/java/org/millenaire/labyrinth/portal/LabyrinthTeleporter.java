package org.millenaire.labyrinth.portal;

import java.lang.reflect.Field;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.LabyrinthOrdeData;

/**
 * Enter / leave helpers for the Millénaire Labyrinth dimension.
 */
public final class LabyrinthTeleporter {
   private static final String PERSIST_ROOT = "MillenaireLabyrinth";
   private static final String TAG_DIM = "ReturnDim";
   private static final String TAG_X = "ReturnX";
   private static final String TAG_Y = "ReturnY";
   private static final String TAG_Z = "ReturnZ";
   private static final String TAG_YROT = "ReturnYRot";
   private static final String TAG_XROT = "ReturnXRot";
   private static final String TAG_HAS = "HasReturn";

   private LabyrinthTeleporter() {
   }

   /**
    * Tries {@code MillenaireServerConfig.SERVER.labyrinthEnabled} when present;
    * otherwise defaults to {@code true} for creative players (and {@code true} when
    * no player context is available, so worldgen/spawn can proceed during development).
    */
   public static boolean isLabyrinthEnabled(@Nullable Player player) {
      Boolean configured = readLabyrinthEnabledConfig();
      if (configured != null) {
         return configured;
      }
      if (player != null) {
         return player.getAbilities().instabuild;
      }
      return true;
   }

   @Nullable
   private static Boolean readLabyrinthEnabledConfig() {
      try {
         Field field = MillenaireServerConfig.SERVER.getClass().getField("labyrinthEnabled");
         Object value = field.get(MillenaireServerConfig.SERVER);
         if (value instanceof BooleanValue booleanValue) {
            return booleanValue.getAsBoolean();
         }
         if (value instanceof Boolean boxed) {
            return boxed;
         }
      } catch (ReflectiveOperationException ignored) {
         // Field not present yet — fall through to creative default.
      }
      return null;
   }

   public static boolean enter(ServerPlayer player) {
      if (!isLabyrinthEnabled(player)) {
         player.displayClientMessage(Component.translatable("labyrinth.teleport.disabled"), true);
         return false;
      }
      if (player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         player.displayClientMessage(Component.translatable("labyrinth.teleport.already_inside"), true);
         return false;
      }

      MinecraftServer server = player.server;
      ServerLevel labyrinth = getOrCreateLabyrinth(server);
      if (labyrinth == null) {
         player.displayClientMessage(Component.translatable("labyrinth.teleport.missing_dimension"), true);
         return false;
      }

      storeReturn(player);
      playDepartureEffects(player);

      double x = 4.5;
      double y = LabyrinthKeys.FLOOR_Y + 1.0;
      double z = 4.5;
      player.teleportTo(labyrinth, x, y, z, Set.<RelativeMovement>of(), 0.0F, 0.0F);
      playArrivalEffects(player);
      player.displayClientMessage(Component.translatable("labyrinth.teleport.enter"), true);
      return true;
   }

   public static boolean leave(ServerPlayer player) {
      if (!player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         player.displayClientMessage(Component.translatable("labyrinth.teleport.not_inside"), true);
         return false;
      }

      MinecraftServer server = player.server;
      ReturnAnchor anchor = readReturn(player);
      ServerLevel dest = resolveReturnLevel(server, anchor);
      if (dest == null) {
         dest = server.overworld();
      }

      double x;
      double y;
      double z;
      float yRot;
      float xRot;
      if (anchor != null && anchor.hasReturn) {
         x = anchor.x;
         y = anchor.y;
         z = anchor.z;
         yRot = anchor.yRot;
         xRot = anchor.xRot;
      } else {
         BlockPos spawn = dest.getSharedSpawnPos();
         x = spawn.getX() + 0.5;
         y = spawn.getY();
         z = spawn.getZ() + 0.5;
         yRot = dest.getSharedSpawnAngle();
         xRot = 0.0F;
      }

      playDepartureEffects(player);
      player.teleportTo(dest, x, y, z, Set.<RelativeMovement>of(), yRot, xRot);
      clearReturn(player);
      playArrivalEffects(player);
      player.displayClientMessage(Component.translatable("labyrinth.teleport.leave"), true);
      return true;
   }

   @Nullable
   public static ServerLevel getOrCreateLabyrinth(MinecraftServer server) {
      ServerLevel existing = server.getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (existing != null) {
         return existing;
      }
      // Datapack dimensions are created at server start; surface a null so callers can report.
      return server.getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
   }

   private static void storeReturn(ServerPlayer player) {
      Level level = player.level();
      CompoundTag root = player.getPersistentData().getCompound(PERSIST_ROOT);
      root.putBoolean(TAG_HAS, true);
      root.putString(TAG_DIM, level.dimension().location().toString());
      root.putDouble(TAG_X, player.getX());
      root.putDouble(TAG_Y, player.getY());
      root.putDouble(TAG_Z, player.getZ());
      root.putFloat(TAG_YROT, player.getYRot());
      root.putFloat(TAG_XROT, player.getXRot());
      player.getPersistentData().put(PERSIST_ROOT, root);

      if (level instanceof ServerLevel serverLevel) {
         LabyrinthOrdeData.get(serverLevel).storeReturnPosition(
            player.getUUID(),
            level.dimension(),
            player.blockPosition(),
            player.getYRot(),
            player.getXRot()
         );
      }
   }

   private static void clearReturn(ServerPlayer player) {
      CompoundTag root = player.getPersistentData().getCompound(PERSIST_ROOT);
      root.putBoolean(TAG_HAS, false);
      player.getPersistentData().put(PERSIST_ROOT, root);
      if (player.level() instanceof ServerLevel serverLevel) {
         LabyrinthOrdeData.get(serverLevel).clearReturnPosition(player.getUUID());
      }
   }

   @Nullable
   private static ReturnAnchor readReturn(ServerPlayer player) {
      CompoundTag root = player.getPersistentData().getCompound(PERSIST_ROOT);
      if (root.getBoolean(TAG_HAS)) {
         return new ReturnAnchor(
            true,
            root.getString(TAG_DIM),
            root.getDouble(TAG_X),
            root.getDouble(TAG_Y),
            root.getDouble(TAG_Z),
            root.getFloat(TAG_YROT),
            root.getFloat(TAG_XROT)
         );
      }

      if (player.level() instanceof ServerLevel serverLevel) {
         LabyrinthOrdeData.PlayerOrde orde = LabyrinthOrdeData.get(serverLevel).get(player.getUUID());
         if (orde != null && orde.hasReturn) {
            return new ReturnAnchor(
               true,
               orde.returnDimension,
               orde.returnX,
               orde.returnY,
               orde.returnZ,
               orde.returnYRot,
               orde.returnXRot
            );
         }
      }
      return null;
   }

   @Nullable
   private static ServerLevel resolveReturnLevel(MinecraftServer server, @Nullable ReturnAnchor anchor) {
      if (anchor == null || !anchor.hasReturn || anchor.dimension == null || anchor.dimension.isBlank()) {
         return server.overworld();
      }
      try {
         ResourceKey<Level> key = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(anchor.dimension)
         );
         ServerLevel level = server.getLevel(key);
         return level != null ? level : server.overworld();
      } catch (RuntimeException ignored) {
         return server.overworld();
      }
   }

   private static void playDepartureEffects(ServerPlayer player) {
      if (!(player.level() instanceof ServerLevel serverLevel)) {
         return;
      }
      serverLevel.playSound(
         null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8F, 1.1F
      );
      for (int i = 0; i < 24; i++) {
         serverLevel.sendParticles(
            ParticleTypes.PORTAL,
            player.getX(),
            player.getY() + player.getBbHeight() * 0.5,
            player.getZ(),
            1,
            0.4,
            0.6,
            0.4,
            0.15
         );
      }
   }

   private static void playArrivalEffects(ServerPlayer player) {
      if (!(player.level() instanceof ServerLevel serverLevel)) {
         return;
      }
      serverLevel.playSound(
         null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 1.0F
      );
      for (int i = 0; i < 18; i++) {
         serverLevel.sendParticles(
            ParticleTypes.REVERSE_PORTAL,
            player.getX(),
            player.getY() + 1.0,
            player.getZ(),
            1,
            0.35,
            0.5,
            0.35,
            0.05
         );
      }
   }

   private record ReturnAnchor(
      boolean hasReturn,
      String dimension,
      double x,
      double y,
      double z,
      float yRot,
      float xRot
   ) {
   }
}
