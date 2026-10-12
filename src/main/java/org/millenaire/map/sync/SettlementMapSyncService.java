package org.millenaire.map.sync;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.network.SettlementMapDeltaPayload;
import org.millenaire.network.SettlementMapSnapshotPayload;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Server-side push sync of discovery-filtered settlement map DTOs.
 */
public final class SettlementMapSyncService {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Coalesce dirty map pushes; keeps bandwidth/CPU bounded under mass settlement churn. */
   private static final int COALESCE_INTERVAL_TICKS = 60;
   private static final int MAX_ENTRIES_PER_PACKET = 200;

   private static int revision;
   private static final SettlementMapPending pending = new SettlementMapPending();
   /** Per-player pending deltas whose send failed; merged into the next push so unsent players are not skipped. */
   private static final Map<UUID, SettlementMapPending> retryByPlayer = new HashMap<>();
   private static String worldToken = "";

   private SettlementMapSyncService() {
   }

   public static void onServerStarting(MinecraftServer server) {
      revision = 1;
      pending.clear();
      pending.markFull();
      retryByPlayer.clear();
      worldToken = buildWorldToken(server);
   }

   public static void onServerStopping() {
      pending.clear();
      retryByPlayer.clear();
      worldToken = "";
   }

   /** Full resync of every visible entry on the next push; prefer {@link #markDirty(VillageId)}. */
   public static void markDirty() {
      pending.markFull();
   }

   public static void markDirty(VillageId id) {
      if (id != null) {
         pending.markDirty(id.uuid());
      }
   }

   public static void markRemoved(VillageId id) {
      if (id != null) {
         pending.markRemoved(id.uuid());
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
      if (!isEnabled() || server == null || (pending.isEmpty() && retryByPlayer.isEmpty())) {
         return;
      }
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null || overworld.getGameTime() % COALESCE_INTERVAL_TICKS != 0L) {
         return;
      }
      List<ServerPlayer> players = server.getPlayerList().getPlayers();
      if (!retryByPlayer.isEmpty()) {
         Set<UUID> online = new HashSet<>();
         for (ServerPlayer player : players) {
            online.add(player.getUUID());
         }
         retryByPlayer.keySet().retainAll(online);
      }
      SettlementMapPending batch = pending.copy();
      revision++;
      // Periodic updates use dirty-id deltas; login/full resync still uses sendSnapshot.
      for (ServerPlayer player : players) {
         SettlementMapPending toSend = batch;
         SettlementMapPending retry = retryByPlayer.remove(player.getUUID());
         if (retry != null) {
            toSend = batch.copy();
            toSend.merge(retry);
         }
         if (toSend.isEmpty()) {
            continue;
         }
         try {
            sendDelta(overworld, player, toSend);
         } catch (Exception e) {
            retryByPlayer.put(player.getUUID(), toSend.copy());
            LOGGER.warn("[Millenaire] Settlement map sync failed for {}: {}", player.getGameProfile().getName(), e.toString());
         }
      }
      // Cleared only after every player was attempted; failures live in retryByPlayer.
      pending.clear();
   }

   private static void sendDelta(ServerLevel overworld, ServerPlayer player, SettlementMapPending toSend) {
      List<SettlementMapEntry> upserts;
      Set<UUID> removedSet = new java.util.LinkedHashSet<>(toSend.removedIds());
      if (toSend.isFull()) {
         upserts = SettlementMapDtoBuilder.buildForPlayer(overworld, player, revision);
      } else {
         SettlementMapDtoBuilder.Delta delta =
            SettlementMapDtoBuilder.buildDeltaForPlayer(overworld, player, revision, toSend.dirtyIds());
         upserts = delta.upserts();
         removedSet.addAll(delta.removed());
      }
      List<UUID> removed = new java.util.ArrayList<>(removedSet);
      if (upserts.isEmpty() && removed.isEmpty()) {
         return;
      }
      List<List<SettlementMapEntry>> upChunks = SettlementMapPending.chunk(upserts, MAX_ENTRIES_PER_PACKET);
      List<List<UUID>> rmChunks = SettlementMapPending.chunk(removed, MAX_ENTRIES_PER_PACKET);
      int parts = Math.max(upChunks.size(), rmChunks.size());
      for (int i = 0; i < parts; i++) {
         PacketDistributor.sendToPlayer(
            player,
            new SettlementMapDeltaPayload(
               worldToken,
               revision,
               i < upChunks.size() ? upChunks.get(i) : List.of(),
               i < rmChunks.size() ? rmChunks.get(i) : List.of()
            ),
            new CustomPacketPayload[0]
         );
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
