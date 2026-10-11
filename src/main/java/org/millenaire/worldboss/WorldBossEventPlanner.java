package org.millenaire.worldboss;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.HemelwachterEntity;
import org.slf4j.Logger;

/**
 * Server-side world boss orchestrator. One timer / one active event per world storage.
 */
public final class WorldBossEventPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_SPAWN_ATTEMPTS = 8;

   private WorldBossEventPlanner() {
   }

   public static void tick(MinecraftServer server) {
      if (!WorldBossEligibility.eventsEnabled() && !hasActive(server)) {
         return;
      }
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      ensureInterval(data);

      ActiveWorldBossEvent active = data.active();
      if (active != null) {
         tickActive(server, data, active);
         return;
      }

      if (!WorldBossEligibility.eventsEnabled()) {
         return;
      }

      boolean eligibleOnline = WorldBossEligibility.anyEligibleOnline(server);
      int elapsed = WorldBossTimerLogic.advance(data.elapsedActiveTicks(), data.nextIntervalTicks(), eligibleOnline);
      if (elapsed != data.elapsedActiveTicks()) {
         data.setElapsedActiveTicks(elapsed);
      }
      if (eligibleOnline && WorldBossTimerLogic.isDue(elapsed, data.nextIntervalTicks())) {
         beginSelection(server, data);
      }
   }

   private static boolean hasActive(MinecraftServer server) {
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      return overworld != null && WorldBossEventSavedData.get(overworld).active() != null;
   }

   private static void ensureInterval(WorldBossEventSavedData data) {
      if (data.intervalChosen() && data.nextIntervalTicks() > 0) {
         return;
      }
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      int interval = WorldBossTimerLogic.rollInterval(
         cfg.worldBossIntervalMinTicks.getAsInt(),
         cfg.worldBossIntervalMaxTicks.getAsInt(),
         overworldRandom(data)
      );
      data.setNextIntervalTicks(interval);
      data.setElapsedActiveTicks(0);
   }

   private static net.minecraft.util.RandomSource overworldRandom(WorldBossEventSavedData data) {
      return net.minecraft.util.RandomSource.create(System.nanoTime() ^ data.elapsedActiveTicks());
   }

   private static void beginSelection(MinecraftServer server, WorldBossEventSavedData data) {
      List<ServerPlayer> eligible = WorldBossEligibility.eligiblePlayers(server);
      if (eligible.isEmpty()) {
         return;
      }
      Optional<WorldBossDefinition> bossOpt = WorldBossDefinitionRegistry.get().select(server.overworld(), data.lastBossId(), server.overworld().getRandom());
      if (bossOpt.isEmpty()) {
         LOGGER.info("[WorldBoss] No available bosses; delaying event");
         data.setElapsedActiveTicks(Math.max(0, data.nextIntervalTicks() - 1200));
         return;
      }
      WorldBossDefinition boss = bossOpt.get();
      List<WorldBossPlayerSelection.Candidate> candidates = new ArrayList<>();
      for (ServerPlayer player : eligible) {
         if (!boss.canStartNear(player.serverLevel(), player)) {
            continue;
         }
         candidates.add(new WorldBossPlayerSelection.Candidate(player.getUUID(), data.recentPenalty(player.getUUID())));
      }
      if (candidates.isEmpty()) {
         data.setElapsedActiveTicks(Math.max(0, data.nextIntervalTicks() - 600));
         return;
      }
      UUID anchorId = WorldBossPlayerSelection.select(candidates, server.overworld().getRandom());
      ServerPlayer anchor = server.getPlayerList().getPlayer(anchorId);
      if (anchor == null) {
         return;
      }
      ActiveWorldBossEvent event = new ActiveWorldBossEvent(UUID.randomUUID(), boss.id(), server.overworld().getGameTime());
      event.setAnchorPlayerId(anchorId);
      event.setDimension(anchor.level().dimension());
      event.setState(WorldBossEventState.SELECTING, server.overworld().getGameTime());
      data.setActive(event);
      data.rememberAnchor(anchorId);
      LOGGER.info("[WorldBoss] Event {} selecting boss {} near {}", event.eventId(), boss.id(), anchor.getGameProfile().getName());
   }

   private static void tickActive(MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event) {
      long now = server.overworld().getGameTime();
      WorldBossDefinition def = WorldBossDefinitionRegistry.get().getOrNull(event.bossId());
      if (def == null) {
         failEvent(data, event, now, "missing_definition");
         return;
      }

      if (!WorldBossEligibility.eventsEnabled()) {
         handleDisabledWithActive(server, data, event, def, now);
         return;
      }

      switch (event.state()) {
         case SELECTING -> tickSelecting(server, data, event, def, now);
         case ANNOUNCING -> tickAnnouncing(server, data, event, def, now);
         case ARRIVING, NEUTRAL, ENGAGED, DISENGAGING, DEPARTING -> tickWithEntity(server, data, event, def, now);
         case DEFEATED, FAILED -> finishEvent(data, event, now, event.lastOutcome() != null ? event.lastOutcome() : event.state().name().toLowerCase());
         case FINISHED, WAITING -> data.clearActive();
         default -> failEvent(data, event, now, "invalid_state");
      }
   }

   private static void handleDisabledWithActive(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      String policy;
      try {
         policy = MillenaireServerConfig.SERVER.worldBossDisableActivePolicy.get();
      } catch (IllegalStateException e) {
         policy = "force_depart";
      }
      if ("leave".equalsIgnoreCase(policy)) {
         return;
      }
      Entity entity = findBossEntity(server, event);
      if (entity instanceof HemelwachterEntity hemel) {
         hemel.beginDeparture("config_disabled");
      } else if (event.state().hasActiveEntity()) {
         event.setState(WorldBossEventState.DEPARTING, now);
         data.markDirtyPublic();
      } else {
         finishEvent(data, event, now, "config_disabled");
      }
   }

   private static void tickSelecting(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      if (event.spawnBackoffTicks() > 0) {
         event.setSpawnBackoffTicks(event.spawnBackoffTicks() - 1);
         data.markDirtyPublic();
         return;
      }
      ServerPlayer anchor = event.anchorPlayerId() != null ? server.getPlayerList().getPlayer(event.anchorPlayerId()) : null;
      if (anchor == null || !WorldBossEligibility.isEligiblePlayer(anchor)) {
         List<ServerPlayer> eligible = WorldBossEligibility.eligiblePlayers(server);
         if (eligible.isEmpty()) {
            event.setSpawnBackoffTicks(100);
            data.markDirtyPublic();
            return;
         }
         anchor = eligible.get(server.overworld().getRandom().nextInt(eligible.size()));
         event.setAnchorPlayerId(anchor.getUUID());
         event.setDimension(anchor.level().dimension());
      }
      Optional<BlockPos> spawn = def.spawnStrategy().findSpawn(anchor.serverLevel(), anchor, anchor.getRandom());
      if (spawn.isEmpty()) {
         event.incrementSpawnAttempts();
         if (event.spawnAttempts() >= MAX_SPAWN_ATTEMPTS) {
            failEvent(data, event, now, "spawn_failed");
            return;
         }
         event.setSpawnBackoffTicks(40 + event.spawnAttempts() * 20);
         data.markDirtyPublic();
         return;
      }
      event.setSpawnPos(spawn.get());
      event.setDimension(anchor.level().dimension());
      event.setState(WorldBossEventState.ANNOUNCING, now);
      WorldBossAnnouncementService.announceKey(anchor.serverLevel(), spawn.get(), def.announceKey());
      WorldBossAnnouncementService.softSignal(anchor.serverLevel(), spawn.get().above(12));
      data.markDirtyPublic();
   }

   private static void tickAnnouncing(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      if (now - event.stateEnteredGameTime() < def.announceTicks()) {
         return;
      }
      ServerLevel level = WorldBossEligibility.resolveLevel(server, event.dimension() != null ? event.dimension() : Level.OVERWORLD);
      BlockPos spawn = event.spawnPos();
      if (spawn == null || !level.hasChunk(spawn.getX() >> 4, spawn.getZ() >> 4)) {
         event.setState(WorldBossEventState.SELECTING, now);
         event.setSpawnBackoffTicks(60);
         data.markDirtyPublic();
         return;
      }
      Mob mob = def.entityType().create(level);
      if (mob == null) {
         failEvent(data, event, now, "entity_create_failed");
         return;
      }
      double startY = spawn.getY() + 18.0;
      mob.moveTo(spawn.getX() + 0.5, startY, spawn.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
      mob.setPersistenceRequired();
      if (mob instanceof HemelwachterEntity hemel) {
         hemel.bindWorldBossEvent(event.eventId(), def.id());
         hemel.beginArrival(spawn.getY());
      }
      if (mob instanceof Mob m) {
         m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(mob.position())), MobSpawnType.TRIGGERED, null);
      }
      level.addFreshEntityWithPassengers(mob);
      event.setBossEntityUuid(mob.getUUID());
      event.setState(WorldBossEventState.ARRIVING, now);
      WorldBossAnnouncementService.announceKey(level, spawn, def.arriveKey());
      data.markDirtyPublic();
      LOGGER.info("[WorldBoss] Spawned {} as {}", def.id(), mob.getUUID());
   }

   private static void tickWithEntity(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      Entity entity = findBossEntity(server, event);
      if (entity == null) {
         // Conservative: unloaded chunk does not prove absence.
         if (event.bossEntityUuid() != null && event.dimension() != null) {
            ServerLevel level = server.getLevel(event.dimension());
            if (level != null && event.spawnPos() != null && !level.hasChunk(event.spawnPos().getX() >> 4, event.spawnPos().getZ() >> 4)) {
               return;
            }
            // Entity missing in loaded area — treat as unexpected loss after a grace.
            if (now - event.stateEnteredGameTime() > 200 && event.state() != WorldBossEventState.DEPARTING) {
               failEvent(data, event, now, "entity_missing");
            }
         }
         return;
      }
      if (!(entity instanceof HemelwachterEntity hemel)) {
         // Generic mob support: sync state from alive/dead only.
         if (!entity.isAlive()) {
            event.setState(WorldBossEventState.DEFEATED, now);
            event.setLastOutcome("defeated");
            data.markDirtyPublic();
         }
         return;
      }
      WorldBossEventState reported = hemel.getEventState();
      if (reported != null && reported != event.state()) {
         event.setState(reported, now);
         data.markDirtyPublic();
      }
      if (hemel.getProvokerId() != null) {
         event.setProvokerPlayerId(hemel.getProvokerId());
         event.addParticipant(hemel.getProvokerId());
      }
      for (UUID id : hemel.getParticipantIds()) {
         event.addParticipant(id);
      }
      if (reported == WorldBossEventState.DEFEATED) {
         if (!event.rewardsGranted()) {
            hemel.grantDefeatRewards(event);
            event.setRewardsGranted(true);
         }
         event.setLastOutcome("defeated");
         finishEvent(data, event, now, "defeated");
         return;
      }
      if (reported == WorldBossEventState.FINISHED || (reported == WorldBossEventState.DEPARTING && !entity.isAlive())) {
         finishEvent(data, event, now, event.lastOutcome() != null ? event.lastOutcome() : "departed");
      }
      if (reported == WorldBossEventState.DEPARTING && hemel.hasFinishedDeparture()) {
         event.setLastOutcome("departed");
         finishEvent(data, event, now, "departed");
      }
   }

   @Nullable
   private static Entity findBossEntity(MinecraftServer server, ActiveWorldBossEvent event) {
      if (event.bossEntityUuid() == null) {
         return null;
      }
      if (event.dimension() != null) {
         ServerLevel level = server.getLevel(event.dimension());
         if (level != null) {
            Entity e = level.getEntity(event.bossEntityUuid());
            if (e != null) {
               return e;
            }
         }
      }
      for (ServerLevel level : server.getAllLevels()) {
         Entity e = level.getEntity(event.bossEntityUuid());
         if (e != null) {
            return e;
         }
      }
      return null;
   }

   private static void failEvent(WorldBossEventSavedData data, ActiveWorldBossEvent event, long now, String reason) {
      event.setState(WorldBossEventState.FAILED, now);
      event.setLastOutcome(reason);
      finishEvent(data, event, now, reason);
   }

   private static void finishEvent(WorldBossEventSavedData data, ActiveWorldBossEvent event, long now, String outcome) {
      data.setLastBossId(event.bossId());
      data.setLastOutcome(outcome, now);
      data.clearActive();
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      int interval = WorldBossTimerLogic.rollInterval(
         cfg.worldBossIntervalMinTicks.getAsInt(),
         cfg.worldBossIntervalMaxTicks.getAsInt(),
         net.minecraft.util.RandomSource.create(now ^ event.eventId().getMostSignificantBits())
      );
      data.setNextIntervalTicks(interval);
      data.setElapsedActiveTicks(0);
      LOGGER.info("[WorldBoss] Event {} finished ({}); next in {} ticks", event.eventId(), outcome, interval);
   }

   public static boolean forceStart(MinecraftServer server, @Nullable ResourceLocation bossId) {
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return false;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      if (data.active() != null) {
         return false;
      }
      List<ServerPlayer> eligible = WorldBossEligibility.eligiblePlayers(server);
      if (eligible.isEmpty()) {
         // Allow ops to force near any non-spectator overworld player
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && WorldBossEligibility.isAllowedDimension(player.level().dimension())) {
               eligible.add(player);
            }
         }
      }
      if (eligible.isEmpty()) {
         return false;
      }
      WorldBossDefinition def;
      if (bossId != null) {
         def = WorldBossDefinitionRegistry.get().getOrNull(bossId);
         if (def == null) {
            return false;
         }
      } else {
         Optional<WorldBossDefinition> picked = WorldBossDefinitionRegistry.get().select(overworld, data.lastBossId(), overworld.getRandom());
         if (picked.isEmpty()) {
            return false;
         }
         def = picked.get();
      }
      ServerPlayer anchor = eligible.get(overworld.getRandom().nextInt(eligible.size()));
      ActiveWorldBossEvent event = new ActiveWorldBossEvent(UUID.randomUUID(), def.id(), overworld.getGameTime());
      event.setAnchorPlayerId(anchor.getUUID());
      event.setDimension(anchor.level().dimension());
      event.setState(WorldBossEventState.SELECTING, overworld.getGameTime());
      data.setActive(event);
      data.rememberAnchor(anchor.getUUID());
      data.setElapsedActiveTicks(data.nextIntervalTicks());
      return true;
   }

   public static boolean forceEnd(MinecraftServer server) {
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return false;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      ActiveWorldBossEvent event = data.active();
      if (event == null) {
         return false;
      }
      Entity entity = findBossEntity(server, event);
      if (entity instanceof HemelwachterEntity hemel) {
         hemel.beginDeparture("admin_end");
         event.setState(WorldBossEventState.DEPARTING, overworld.getGameTime());
         data.markDirtyPublic();
         return true;
      }
      if (entity != null) {
         entity.discard();
      }
      finishEvent(data, event, overworld.getGameTime(), "admin_end");
      return true;
   }

   public static void onBossDeparted(ServerLevel level, HemelwachterEntity boss, String reason) {
      WorldBossEventSavedData data = WorldBossEventSavedData.get(level);
      ActiveWorldBossEvent event = data.active();
      if (event == null || event.bossEntityUuid() == null || !event.bossEntityUuid().equals(boss.getUUID())) {
         return;
      }
      event.setLastOutcome(reason != null ? reason : "departed");
      finishEvent(data, event, level.getGameTime(), event.lastOutcome());
   }

   public static void onBossDefeated(ServerLevel level, HemelwachterEntity boss) {
      WorldBossEventSavedData data = WorldBossEventSavedData.get(level);
      ActiveWorldBossEvent event = data.active();
      if (event == null || event.bossEntityUuid() == null || !event.bossEntityUuid().equals(boss.getUUID())) {
         return;
      }
      event.setState(WorldBossEventState.DEFEATED, level.getGameTime());
      if (!event.rewardsGranted()) {
         boss.grantDefeatRewards(event);
         event.setRewardsGranted(true);
      }
      finishEvent(data, event, level.getGameTime(), "defeated");
   }
}
