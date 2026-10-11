package org.millenaire.worldboss;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
   /** Idle day-cadence checks amortize eligibility scans. */
   private static final int IDLE_CHECK_INTERVAL = 100;

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

      ActiveWorldBossEvent active = data.active();
      if (active != null) {
         // Active events stay responsive; spawn/structure work still slices internally.
         tickActive(server, data, active);
         return;
      }

      if (!WorldBossEligibility.eventsEnabled()) {
         return;
      }

      // Idle cadence is deferrable under tick budget.
      if (!org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
         org.millenaire.perf.tick.ServerTickGovernor.noteDefer(org.millenaire.perf.tick.TickPhase.BOSS_QUEST_MAP);
         return;
      }

      if (isDayCadence()) {
         tickIdleDayCadence(server, overworld, data);
      } else {
         tickIdlePlaytimeCadence(server, data);
      }
   }

   private static boolean isDayCadence() {
      try {
         String mode = MillenaireServerConfig.SERVER.worldBossCadenceMode.get();
         return mode == null || !"ACTIVE_PLAYTIME".equalsIgnoreCase(mode.trim());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static boolean quietSpawn() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldBossQuietSpawn.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static int intervalDays() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.worldBossIntervalDays.getAsInt());
      } catch (IllegalStateException e) {
         return 5;
      }
   }

   private static void tickIdleDayCadence(MinecraftServer server, ServerLevel overworld, WorldBossEventSavedData data) {
      long currentDay = WorldBossTimerLogic.dayIndex(overworld.getDayTime());
      ensureDayCadence(data, currentDay);
      // Amortize: only poll eligibility near due / every N ticks.
      if (overworld.getGameTime() % IDLE_CHECK_INTERVAL != 0L
         && !WorldBossTimerLogic.isDayDue(currentDay, data.nextDueDayIndex())) {
         return;
      }
      if (!WorldBossEligibility.anyEligibleOnline(server)) {
         return;
      }
      if (WorldBossTimerLogic.isDayDue(currentDay, data.nextDueDayIndex())) {
         beginSelection(server, data);
      }
   }

   private static void tickIdlePlaytimeCadence(MinecraftServer server, WorldBossEventSavedData data) {
      ensurePlaytimeInterval(data);
      boolean eligibleOnline = WorldBossEligibility.anyEligibleOnline(server);
      int elapsed = WorldBossTimerLogic.advance(data.elapsedActiveTicks(), data.nextIntervalTicks(), eligibleOnline);
      boolean due = WorldBossTimerLogic.isDue(elapsed, data.nextIntervalTicks());
      if (elapsed != data.elapsedActiveTicks()) {
         if (due || elapsed % 20 == 0) {
            data.setElapsedActiveTicks(elapsed);
         } else {
            data.setElapsedActiveTicksSoft(elapsed);
         }
      }
      if (eligibleOnline && due) {
         data.setElapsedActiveTicks(elapsed);
         beginSelection(server, data);
      }
   }

   private static void ensureDayCadence(WorldBossEventSavedData data, long currentDay) {
      if (data.dayCadenceChosen()) {
         return;
      }
      if (data.intervalChosen()) {
         long due = WorldBossTimerLogic.migrateTickTimerToDueDay(
            currentDay, data.elapsedActiveTicks(), data.nextIntervalTicks(), intervalDays()
         );
         data.setNextDueDayIndex(due);
         LOGGER.info("[WorldBoss] Migrated tick timer to day cadence; next due day {}", due);
         return;
      }
      data.setNextDueDayIndex(WorldBossTimerLogic.nextDueDay(currentDay, intervalDays()));
      LOGGER.info("[WorldBoss] Day cadence armed; next due day {}", data.nextDueDayIndex());
   }

   private static void ensurePlaytimeInterval(WorldBossEventSavedData data) {
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

   /**
    * Admin helper: immediately start a world boss event (same path as {@link #forceStart}).
    */
   public static boolean trigger(MinecraftServer server) {
      return forceStart(server, null, null);
   }

   public static boolean trigger(MinecraftServer server, @Nullable ServerPlayer preferredAnchor) {
      return forceStart(server, null, preferredAnchor);
   }

   private static boolean hasActive(MinecraftServer server) {
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      return overworld != null && WorldBossEventSavedData.get(overworld).active() != null;
   }

   private static net.minecraft.util.RandomSource overworldRandom(WorldBossEventSavedData data) {
      return net.minecraft.util.RandomSource.create(System.nanoTime() ^ data.elapsedActiveTicks() ^ data.nextDueDayIndex());
   }

   private static void beginSelection(MinecraftServer server, WorldBossEventSavedData data) {
      List<ServerPlayer> eligible = WorldBossEligibility.eligiblePlayers(server);
      if (eligible.isEmpty()) {
         return;
      }
      Optional<WorldBossDefinition> bossOpt = WorldBossDefinitionRegistry.get().select(server.overworld(), data.lastBossId(), server.overworld().getRandom());
      if (bossOpt.isEmpty()) {
         LOGGER.info("[WorldBoss] No available bosses; delaying event");
         delayCadence(data, server.overworld(), 0);
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
         delayCadence(data, server.overworld(), 0);
         return;
      }
      UUID anchorId = WorldBossPlayerSelection.select(candidates, server.overworld().getRandom());
      ServerPlayer anchor = server.getPlayerList().getPlayer(anchorId);
      if (anchor == null) {
         return;
      }
      // Consume due slot immediately so we never backlog multiple events.
      scheduleNextAfterStart(data, server.overworld());
      ActiveWorldBossEvent event = new ActiveWorldBossEvent(UUID.randomUUID(), boss.id(), server.overworld().getGameTime());
      event.setAnchorPlayerId(anchorId);
      event.setDimension(anchor.level().dimension());
      event.setState(WorldBossEventState.SELECTING, server.overworld().getGameTime());
      data.setActive(event);
      data.rememberAnchor(anchorId);
      LOGGER.info("[WorldBoss] Event {} selecting boss {} near {}", event.eventId(), boss.id(), anchor.getGameProfile().getName());
   }

   private static void delayCadence(WorldBossEventSavedData data, ServerLevel overworld, int extraDays) {
      if (isDayCadence()) {
         long day = WorldBossTimerLogic.dayIndex(overworld.getDayTime());
         data.setNextDueDayIndex(day + Math.max(0, extraDays) + 1);
      } else {
         data.setElapsedActiveTicks(Math.max(0, data.nextIntervalTicks() - 1200));
      }
   }

   private static void scheduleNextAfterStart(WorldBossEventSavedData data, ServerLevel overworld) {
      if (isDayCadence()) {
         long day = WorldBossTimerLogic.dayIndex(overworld.getDayTime());
         data.setNextDueDayIndex(WorldBossTimerLogic.nextDueDay(day, intervalDays()));
      } else {
         data.setElapsedActiveTicks(0);
      }
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
         return;
      }
      ServerPlayer anchor = event.anchorPlayerId() != null ? server.getPlayerList().getPlayer(event.anchorPlayerId()) : null;
      if (anchor == null || !WorldBossEligibility.isUsableAnchor(anchor)) {
         List<ServerPlayer> candidates = WorldBossEligibility.anchorCandidates(server);
         if (candidates.isEmpty()) {
            event.setSpawnBackoffTicks(100);
            data.markDirtyPublic();
            return;
         }
         anchor = candidates.get(server.overworld().getRandom().nextInt(candidates.size()));
         event.setAnchorPlayerId(anchor.getUUID());
         event.setDimension(anchor.level().dimension());
      }
      Optional<BlockPos> spawn = def.spawnStrategy().findSpawn(anchor.serverLevel(), anchor, anchor.getRandom());
      if (spawn.isEmpty() && event.spawnAttempts() + 1 >= MAX_SPAWN_ATTEMPTS) {
         spawn = emergencySpawnNearAnchor(anchor);
      }
      if (spawn.isEmpty()) {
         event.incrementSpawnAttempts();
         if (event.spawnAttempts() >= MAX_SPAWN_ATTEMPTS) {
            failEvent(data, event, now, "spawn_failed");
            notifyOps(server, Component.translatable("millenaire.worldboss.command.spawn_failed"));
            return;
         }
         event.setSpawnBackoffTicks(40 + event.spawnAttempts() * 20);
         data.markDirtyPublic();
         return;
      }
      event.setSpawnPos(spawn.get());
      event.setDimension(anchor.level().dimension());
      if (quietSpawn() || def.announceTicks() <= 0) {
         spawnBossEntity(server, data, event, def, now, spawn.get());
      } else {
         event.setState(WorldBossEventState.ANNOUNCING, now);
         WorldBossAnnouncementService.announceKey(anchor.serverLevel(), spawn.get(), def.announceKey());
         WorldBossAnnouncementService.softSignal(anchor.serverLevel(), spawn.get().above(12));
         data.markDirtyPublic();
      }
   }

   private static void tickAnnouncing(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      if (now - event.stateEnteredGameTime() < def.announceTicks()) {
         return;
      }
      BlockPos spawn = event.spawnPos();
      if (spawn == null) {
         event.setState(WorldBossEventState.SELECTING, now);
         event.setSpawnBackoffTicks(60);
         data.markDirtyPublic();
         return;
      }
      spawnBossEntity(server, data, event, def, now, spawn);
   }

   private static void spawnBossEntity(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now, BlockPos spawn
   ) {
      ServerLevel level = WorldBossEligibility.resolveLevel(server, event.dimension() != null ? event.dimension() : Level.OVERWORLD);
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
      mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(mob.position())), MobSpawnType.TRIGGERED, null);
      level.addFreshEntityWithPassengers(mob);
      event.setBossEntityUuid(mob.getUUID());
      event.setState(WorldBossEventState.ARRIVING, now);
      if (!quietSpawn()) {
         WorldBossAnnouncementService.announceKey(level, spawn, def.arriveKey());
      }
      data.markDirtyPublic();
      LOGGER.info("[WorldBoss] Spawned {} as {} (quiet={})", def.id(), mob.getUUID(), quietSpawn());
   }

   private static void tickWithEntity(
      MinecraftServer server, WorldBossEventSavedData data, ActiveWorldBossEvent event, WorldBossDefinition def, long now
   ) {
      Entity entity = findBossEntity(server, event);
      if (entity == null) {
         if (event.bossEntityUuid() != null && event.dimension() != null) {
            ServerLevel level = server.getLevel(event.dimension());
            if (level != null && event.spawnPos() != null && !level.hasChunk(event.spawnPos().getX() >> 4, event.spawnPos().getZ() >> 4)) {
               return;
            }
            if (now - event.stateEnteredGameTime() > 200 && event.state() != WorldBossEventState.DEPARTING) {
               failEvent(data, event, now, "entity_missing");
            }
         }
         return;
      }
      if (!(entity instanceof HemelwachterEntity hemel)) {
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
      if (isDayCadence()) {
         // next due already advanced at start; if somehow unset, arm from game time day.
         if (!data.dayCadenceChosen()) {
            long day = WorldBossTimerLogic.dayIndex(Math.max(0L, now));
            data.setNextDueDayIndex(WorldBossTimerLogic.nextDueDay(day, intervalDays()));
         }
         LOGGER.info("[WorldBoss] Event {} finished ({}); next due day {}", event.eventId(), outcome, data.nextDueDayIndex());
      } else {
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
   }

   public static boolean forceStart(MinecraftServer server, @Nullable ResourceLocation bossId) {
      return forceStart(server, bossId, null);
   }

   public static boolean forceStart(MinecraftServer server, @Nullable ResourceLocation bossId, @Nullable ServerPlayer preferredAnchor) {
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      if (overworld == null) {
         return false;
      }
      WorldBossEventSavedData data = WorldBossEventSavedData.get(overworld);
      ActiveWorldBossEvent existing = data.active();
      if (existing != null) {
         if (!existing.state().hasActiveEntity() && existing.bossEntityUuid() == null) {
            LOGGER.info("[WorldBoss] Clearing stalled pre-spawn event {} ({})", existing.eventId(), existing.state());
            data.clearActive();
         } else {
            return false;
         }
      }
      List<ServerPlayer> candidates = WorldBossEligibility.anchorCandidates(server);
      if (candidates.isEmpty()) {
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
            Collection<WorldBossDefinition> all = WorldBossDefinitionRegistry.get().all();
            if (all.isEmpty()) {
               return false;
            }
            def = all.iterator().next();
         } else {
            def = picked.get();
         }
      }
      ServerPlayer anchor = preferredAnchor != null && WorldBossEligibility.isUsableAnchor(preferredAnchor)
         ? preferredAnchor
         : candidates.get(overworld.getRandom().nextInt(candidates.size()));
      scheduleNextAfterStart(data, overworld);
      if (!isDayCadence()) {
         ensurePlaytimeInterval(data);
         data.setElapsedActiveTicks(data.nextIntervalTicks());
      }
      ActiveWorldBossEvent event = new ActiveWorldBossEvent(UUID.randomUUID(), def.id(), overworld.getGameTime());
      event.setAnchorPlayerId(anchor.getUUID());
      event.setDimension(anchor.level().dimension());
      event.setState(WorldBossEventState.SELECTING, overworld.getGameTime());
      data.setActive(event);
      data.rememberAnchor(anchor.getUUID());
      LOGGER.info("[WorldBoss] Force-started {} near {}", def.id(), anchor.getGameProfile().getName());
      return true;
   }

   private static Optional<BlockPos> emergencySpawnNearAnchor(ServerPlayer anchor) {
      ServerLevel level = anchor.serverLevel();
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      int hover;
      try {
         hover = cfg.worldBossHoverHeight.getAsInt();
      } catch (IllegalStateException e) {
         hover = 28;
      }
      BlockPos feet = anchor.blockPosition();
      int x = feet.getX() + 24;
      int z = feet.getZ();
      if (!level.hasChunk(x >> 4, z >> 4)) {
         x = feet.getX();
         z = feet.getZ() + 24;
      }
      if (!level.hasChunk(x >> 4, z >> 4)) {
         x = feet.getX();
         z = feet.getZ();
      }
      int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      int y = Math.min(level.getMaxBuildHeight() - 8, Math.max(groundY, feet.getY()) + hover);
      return Optional.of(new BlockPos(x, y, z));
   }

   private static void notifyOps(MinecraftServer server, Component message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (server.getPlayerList().isOp(player.getGameProfile()) || player.hasPermissions(2)) {
            player.displayClientMessage(message, false);
         }
      }
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
