package org.millenaire.map.sync;

import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.network.SettlementMapSnapshotPayload;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Server-side push sync of discovery-filtered settlement map DTOs.
 */
public final class SettlementMapSyncService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int COALESCE_INTERVAL_TICKS = 40;
   private static final int MAX_ENTRIES_PER_PACKET = 200;

   private static int revision;
   private static boolean dirty;
   private static final Set<UUID> removedIds = new HashSet<>();
   private static String worldToken = "";

   private SettlementMapSyncService() {
   }

   public static void onServerStarting(MinecraftServer server) {
      revision = 1;
      dirty = true;
      removedIds.clear();
      worldToken = buildWorldToken(server);
   }

   public static void onServerStopping() {
      dirty = false;
      removedIds.clear();
      worldToken = "";
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void markRemoved(VillageId id) {
      if (id != null) {
         removedIds.add(id.uuid());
         markDirty();
      }
   }

   public static void sendSnapshot(ServerPlayer player) {
      if (!isEnabled() || player == null) {
         return;
      }
      ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return;
      }
      List<SettlementMapEntry> entries = SettlementMapDtoBuilder.buildForPlayer(overworld, player, revision);
      sendChunkedSnapshot(player, entries);
   }

   public static void tick(MinecraftServer server) {
      if (!isEnabled() || server == null || !dirty) {
         return;
      }
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null || overworld.getGameTime() % COALESCE_INTERVAL_TICKS != 0L) {
         return;
      }
      removedIds.clear();
      dirty = false;
      revision++;
      // Full per-player snapshot avoids stale client markers when visibility shrinks.
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         try {
            sendSnapshot(player);
         } catch (Exception e) {
            LOGGER.warn("[Millenaire] Settlement map sync failed for {}: {}", player.getGameProfile().getName(), e.toString());
         }
      }
   }

   private static void sendChunkedSnapshot(ServerPlayer player, List<SettlementMapEntry> entries) {
      int total = entries.size();
      if (total == 0) {
         PacketDistributor.sendToPlayer(
            player,
            new SettlementMapSnapshotPayload(worldToken, revision, 0, 1, List.of()),
            new CustomPacketPayload[0]
         );
         return;
      }
      int parts = (total + MAX_ENTRIES_PER_PACKET - 1) / MAX_ENTRIES_PER_PACKET;
      for (int part = 0; part < parts; part++) {
         int from = part * MAX_ENTRIES_PER_PACKET;
         int to = Math.min(from + MAX_ENTRIES_PER_PACKET, total);
         PacketDistributor.sendToPlayer(
            player,
            new SettlementMapSnapshotPayload(worldToken, revision, part, parts, entries.subList(from, to)),
            new CustomPacketPayload[0]
         );
      }
   }

   private static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.mapSyncEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static String buildWorldToken(MinecraftServer server) {
      try {
         String name = server.getWorldData() != null ? server.getWorldData().getLevelName() : "world";
         return name + ":" + System.currentTimeMillis();
      } catch (Exception e) {
         return "world:" + System.currentTimeMillis();
      }
   }
}
