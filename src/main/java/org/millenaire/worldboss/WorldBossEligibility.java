package org.millenaire.worldboss;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;

public final class WorldBossEligibility {
   private WorldBossEligibility() {
   }

   public static boolean eventsEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldBossEventsEnabled.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean isAllowedDimension(ResourceKey<Level> dimension) {
      String raw;
      try {
         raw = MillenaireServerConfig.SERVER.worldBossAllowedDimensions.get();
      } catch (IllegalStateException e) {
         raw = "minecraft:overworld";
      }
      if (raw == null || raw.isBlank()) {
         return dimension == Level.OVERWORLD;
      }
      for (String part : raw.split(",")) {
         String trimmed = part.trim();
         if (trimmed.isEmpty()) {
            continue;
         }
         ResourceLocation id = ResourceLocation.tryParse(trimmed);
         if (id != null && dimension.location().equals(id)) {
            return true;
         }
      }
      return false;
   }

   public static boolean isEligiblePlayer(ServerPlayer player) {
      if (player == null || player.isSpectator()) {
         return false;
      }
      if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
         return false;
      }
      boolean allowCreative;
      boolean allowAdventure;
      try {
         allowCreative = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldBossAllowCreativeAnchors.get());
         allowAdventure = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldBossAllowAdventureAnchors.get());
      } catch (IllegalStateException e) {
         allowCreative = false;
         allowAdventure = true;
      }
      GameType type = player.gameMode.getGameModeForPlayer();
      if (type == GameType.CREATIVE && !allowCreative) {
         return false;
      }
      if (type == GameType.ADVENTURE && !allowAdventure) {
         return false;
      }
      if (type == GameType.SURVIVAL || (type == GameType.ADVENTURE && allowAdventure) || (type == GameType.CREATIVE && allowCreative)) {
         return isAllowedDimension(player.level().dimension());
      }
      return false;
   }

   public static boolean isCombatTarget(ServerPlayer player) {
      if (player == null || player.isSpectator()) {
         return false;
      }
      GameType type = player.gameMode.getGameModeForPlayer();
      return type == GameType.SURVIVAL || type == GameType.ADVENTURE;
   }

   public static List<ServerPlayer> eligiblePlayers(MinecraftServer server) {
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (isEligiblePlayer(player)) {
            out.add(player);
         }
      }
      return out;
   }

   /**
    * Admin/force-start anchors: any non-spectator in an allowed dimension (includes creative).
    */
   public static boolean isAdminAnchorCandidate(ServerPlayer player) {
      return player != null
         && !player.isSpectator()
         && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
         && isAllowedDimension(player.level().dimension());
   }

   public static List<ServerPlayer> adminAnchorCandidates(MinecraftServer server) {
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (isAdminAnchorCandidate(player)) {
            out.add(player);
         }
      }
      return out;
   }

   /** Eligible first; if none, admin candidates (so creative ops can force events). */
   public static List<ServerPlayer> anchorCandidates(MinecraftServer server) {
      List<ServerPlayer> eligible = eligiblePlayers(server);
      return eligible.isEmpty() ? adminAnchorCandidates(server) : eligible;
   }

   public static boolean isUsableAnchor(ServerPlayer player) {
      return isEligiblePlayer(player) || isAdminAnchorCandidate(player);
   }

   public static boolean anyEligibleOnline(MinecraftServer server) {
      return !eligiblePlayers(server).isEmpty();
   }

   public static ServerLevel resolveLevel(MinecraftServer server, ResourceKey<Level> dimension) {
      ServerLevel level = server.getLevel(dimension);
      return level != null ? level : server.getLevel(Level.OVERWORLD);
   }
}
