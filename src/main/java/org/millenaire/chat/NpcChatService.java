package org.millenaire.chat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.chat.dialogue.NpcDialogueAdapterRegistry;
import org.millenaire.chat.dialogue.NpcDialogueCategory;
import org.millenaire.chat.dialogue.NpcDialogueContext;
import org.millenaire.chat.dialogue.NpcDialogueTarget;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.network.ServerPayloadGuards;
import org.millenaire.village.Village;

/**
 * Single authoritative NPC dialogue orchestration. Entity diversity lives in adapters.
 */
public final class NpcChatService {
   private static final Map<UUID, NpcChatSession> SESSIONS = new ConcurrentHashMap<>();
   public static final byte SPEAKER_NPC = 0;
   public static final byte SPEAKER_PLAYER = 1;
   public static final byte SPEAKER_SYSTEM = 2;

   private NpcChatService() {
   }

   public static void bootstrap() {
      NpcDialogueAdapterRegistry.bootstrap();
      if (NpcModelResolver.usesLmStudio()) {
         LmStudioModelDiscovery.refreshAsync(true);
      }
   }

   public static void tickSessions(MinecraftServer server) {
      // Periodic LM Studio discovery so model load/unload switches are noticed without chat traffic.
      if (server.getTickCount() % 200 == 40 && NpcModelResolver.usesLmStudio()) {
         LmStudioModelDiscovery.refreshAsync(false);
      }
      if (SESSIONS.isEmpty()) {
         return;
      }
      double leaveRange = MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange();
      Iterator<Map.Entry<UUID, NpcChatSession>> it = SESSIONS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, NpcChatSession> entry = it.next();
         ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
         NpcChatSession session = entry.getValue();
         if (player == null) {
            it.remove();
            continue;
         }
         Entity entity = player.level().getEntity(session.entityId());
         if (!(entity instanceof LivingEntity living) || !living.isAlive() || !living.getUUID().equals(session.targetUuid())) {
            closeSession(player, session.entityId(), "gui.millenaire.npcchat.closed");
            continue;
         }
         if (player.level() != living.level()) {
            closeSession(player, session.entityId(), "gui.millenaire.npcchat.closed");
            continue;
         }
         if (!ServerPayloadGuards.isWithinDistance(player, living, leaveRange)) {
            closeSession(player, session.entityId(), "gui.millenaire.npcchat.too_far");
            continue;
         }
         if (session.lookAtPlayer() && living instanceof Mob mob) {
            mob.getLookControl().setLookAt(player);
         }
         if (session.stopNavigation() && living instanceof MillVillager villager) {
            GoalScheduler scheduler = villager.getGoalScheduler();
            boolean leisure = scheduler == null || scheduler.getCurrentTask() == null || isLeisureGoal(villager);
            if (leisure) {
               villager.getNavigation().stop();
            }
         }
      }

      // Bounded transient mind pruning occasionally (not every tick).
      if (server.getTickCount() % 1200 == 0) {
         ServerLevel overworld = server.overworld();
         if (overworld != null) {
            NpcMindSavedData.get(overworld).pruneTransientMinds(overworld.getGameTime());
         }
      }
   }

   private static boolean isLeisureGoal(MillVillager villager) {
      GoalScheduler scheduler = villager.getGoalScheduler();
      if (scheduler == null || scheduler.getCurrentGoal() == null) {
         return true;
      }
      return scheduler.getCurrentGoal().isLeisure();
   }

   public static void onPlayerLogout(ServerPlayer player) {
      NpcChatSession session = SESSIONS.remove(player.getUUID());
      if (session != null) {
         session.cancelPending();
         PacketDistributor.sendToPlayer(player, new NpcChatClosedPayload(session.entityId(), "gui.millenaire.npcchat.closed"), new CustomPacketPayload[0]);
      }
   }

   public static void onServerStopping() {
      for (NpcChatSession session : SESSIONS.values()) {
         session.cancelPending();
      }
      SESSIONS.clear();
   }

   public static void handleOpen(ServerPlayer player, int entityId) {
      if (player.isSpectator()) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.refused");
         return;
      }
      if (!(Boolean)MillenaireServerConfig.SERVER.npcChatEnabled.get()) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.disabled");
         return;
      }
      Entity raw = player.level().getEntity(entityId);
      NpcDialogueTarget target = NpcDialogueAdapterRegistry.resolve(player, raw);
      if (target == null) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.unsupported");
         return;
      }
      if (!target.sameLevelAs(player)) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.unsupported");
         return;
      }
      double chatRange = MillenaireServerConfig.SERVER.npcChatChatRange.getAsInt();
      if (!ServerPayloadGuards.isWithinDistance(player, target.entity(), chatRange)) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.too_far");
         return;
      }
      if ((Boolean)MillenaireServerConfig.SERVER.npcChatRequireLineOfSight.get() && !hasLineOfSight(player, target.entity())) {
         rejectOpen(player, entityId, "gui.millenaire.npcchat.no_sight");
         return;
      }

      NpcChatSession previous = SESSIONS.remove(player.getUUID());
      if (previous != null) {
         previous.cancelPending();
         if (previous.entityId() != entityId) {
            PacketDistributor.sendToPlayer(
               player, new NpcChatClosedPayload(previous.entityId(), "gui.millenaire.npcchat.closed"), new CustomPacketPayload[0]
            );
         }
      }

      String refusal = target.adapter().refusalKey(player, target.entity());
      if (refusal != null) {
         sendRefusal(player, target, refusal);
         return;
      }

      NpcMindSavedData data = NpcMindSavedData.get(player.serverLevel());
      NpcMind mind = data.getOrCreateMind(target.identity().entityUuid());
      if (!target.identity().persistent()) {
         mind.touchTransient(player.serverLevel().getGameTime());
      }
      data.markChanged();

      List<NpcChatOpenPayload.HistoryLine> history = new ArrayList<>();
      for (NpcTurn turn : mind.recentTurns(player.getUUID(), 12)) {
         byte speaker = NpcTurn.ROLE_PLAYER.equals(turn.role()) ? SPEAKER_PLAYER : SPEAKER_NPC;
         history.add(new NpcChatOpenPayload.HistoryLine(speaker, turn.text()));
      }

      NpcDialogueContext preview = target.adapter().buildContext(player, target.entity(), mind, data, "", "en_us");
      NpcChatSession session = new NpcChatSession(
         player.getUUID(),
         target.identity().entityUuid(),
         entityId,
         target.category(),
         target.policy().persistMind(),
         target.policy().canGossip(),
         target.policy().stopNavigationWhileTalking(),
         target.policy().lookAtPlayer(),
         target.policy().bossNonIntrusive(),
         player.serverLevel().getGameTime()
      );
      SESSIONS.put(player.getUUID(), session);
      PacketDistributor.sendToPlayer(
         player,
         new NpcChatOpenPayload(entityId, preview.displayName(), preview.roleLine(), preview.reputationLabel(), history),
         new CustomPacketPayload[0]
      );
   }

   public static void handleClose(ServerPlayer player, int entityId) {
      NpcChatSession session = SESSIONS.get(player.getUUID());
      if (session != null && session.entityId() == entityId) {
         session.cancelPending();
         SESSIONS.remove(player.getUUID());
      }
   }

   public static void handleSend(ServerPlayer player, int entityId, String text, String languageCode) {
      if (player.isSpectator()) {
         sendReject(player, entityId, "gui.millenaire.npcchat.refused");
         return;
      }
      if (!NpcChatSanitize.isValidLanguageCode(languageCode)) {
         sendReject(player, entityId, "gui.millenaire.npcchat.invalid");
         return;
      }
      String sanitized = NpcChatSanitize.sanitize(text);
      if (sanitized.isEmpty()) {
         sendReject(player, entityId, "gui.millenaire.npcchat.invalid");
         return;
      }

      NpcChatSession session = SESSIONS.get(player.getUUID());
      if (session == null || session.entityId() != entityId) {
         sendReject(player, entityId, "gui.millenaire.npcchat.session_expired");
         return;
      }

      Entity raw = player.level().getEntity(entityId);
      NpcDialogueTarget target = NpcDialogueAdapterRegistry.resolve(player, raw);
      if (target == null || !target.identity().entityUuid().equals(session.targetUuid())) {
         sendReject(player, entityId, "gui.millenaire.npcchat.unavailable");
         closeSession(player, entityId, "gui.millenaire.npcchat.unavailable");
         return;
      }

      double leaveRange = MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange();
      if (!ServerPayloadGuards.isWithinDistance(player, target.entity(), leaveRange)) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.too_far", false);
         closeSession(player, entityId, "gui.millenaire.npcchat.too_far");
         return;
      }
      if ((Boolean)MillenaireServerConfig.SERVER.npcChatRequireLineOfSight.get() && !hasLineOfSight(player, target.entity())) {
         sendReject(player, entityId, "gui.millenaire.npcchat.no_sight");
         return;
      }

      String refusal = target.adapter().refusalKey(player, target.entity());
      if (refusal != null) {
         sendSystemLine(player, entityId, refusal, false);
         return;
      }

      if (session.pendingRequest()) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.busy", false);
         return;
      }

      ServerLevel level = player.serverLevel();
      long gameTime = level.getGameTime();
      NpcMindSavedData data = NpcMindSavedData.get(level);
      NpcMind mind = data.getOrCreateMind(session.targetUuid());
      if (!session.persistMind()) {
         mind.touchTransient(gameTime);
      }
      mind.addQuote(player.getUUID(), sanitized, gameTime);
      mind.addTurn(player.getUUID(), NpcTurn.ROLE_PLAYER, sanitized, gameTime);
      data.markChanged();

      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(entityId, SPEAKER_PLAYER, sanitized, false), new CustomPacketPayload[0]);

      NpcDialogueContext ctx = target.adapter().buildContext(player, target.entity(), mind, data, sanitized, languageCode);

      if (!NpcModelClient.isConfigured()) {
         String fallback = ctx.fallbackLine().isBlank()
            ? Component.translatable("gui.millenaire.npcchat.no_model").getString()
            : ctx.fallbackLine();
         deliverNpcLine(player, target, session, mind, data, fallback, null, sanitized, false);
         return;
      }

      if (!NpcModelClient.tryAcquire()) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.busy", false);
         return;
      }

      long generation = session.beginRequest();
      sendSystemLine(player, entityId, "gui.millenaire.npcchat.thinking", true);

      List<String> rumorLines = buildRumorLinesFor(mind, data, target);
      String systemPrompt = ctx.systemPromptOverride().isBlank()
         ? (target.adapter().defaultSystemPrompt().isBlank() ? NpcPromptBuilder.SYSTEM_PROMPT : target.adapter().defaultSystemPrompt())
         : ctx.systemPromptOverride();
      String userPrompt = NpcPromptBuilder.buildUserPromptFromContext(ctx, mind, player.getUUID(), languageCode, sanitized, rumorLines);

      UUID targetUuid = session.targetUuid();
      UUID playerId = player.getUUID();
      int capturedEntityId = entityId;
      String capturedSpeech = sanitized;
      long capturedSessionId = session.sessionId();
      long capturedGeneration = generation;

      NpcModelClient.sendAsync(systemPrompt, userPrompt, targetUuid).whenComplete((reply, error) -> {
         MinecraftServer server = player.getServer();
         if (server == null) {
            NpcModelClient.release();
            return;
         }
         server.execute(() -> {
            try {
               NpcChatSession current = SESSIONS.get(playerId);
               ServerPlayer online = server.getPlayerList().getPlayer(playerId);
               boolean sessionMatches = current != null
                  && current.sessionId() == capturedSessionId
                  && current.requestGeneration() == capturedGeneration
                  && current.matches(playerId, targetUuid, capturedEntityId);
               Entity entity = online != null ? online.level().getEntity(capturedEntityId) : null;
               NpcDialogueTarget livingTarget = online != null ? NpcDialogueAdapterRegistry.resolve(online, entity) : null;
               boolean valid = online != null
                  && sessionMatches
                  && livingTarget != null
                  && livingTarget.identity().entityUuid().equals(targetUuid)
                  && livingTarget.isAlive()
                  && online.level() == livingTarget.entity().level()
                  && ServerPayloadGuards.isWithinDistance(online, livingTarget.entity(), MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange());
               if (!valid) {
                  if (current != null && current.sessionId() == capturedSessionId) {
                     current.endRequest(capturedGeneration);
                  }
                  return;
               }
               NpcMindSavedData mindData = NpcMindSavedData.get(online.serverLevel());
               NpcMind livingMind = mindData.getOrCreateMind(targetUuid);
               if (reply == null || reply.isEmpty()) {
                  String resolveFail = NpcModelClient.resolveFailureMessageKey();
                  if (resolveFail != null) {
                     current.endRequest(capturedGeneration);
                     sendSystemLine(online, capturedEntityId, resolveFail, false);
                     return;
                  }
                  NpcDialogueContext failCtx = livingTarget.adapter().buildContext(online, livingTarget.entity(), livingMind, mindData, capturedSpeech, languageCode);
                  String fallback = failCtx.fallbackLine().isBlank()
                     ? Component.translatable("gui.millenaire.npcchat.no_model").getString()
                     : failCtx.fallbackLine();
                  deliverNpcLine(online, livingTarget, current, livingMind, mindData, fallback, null, capturedSpeech, true);
                  sendSystemLine(online, capturedEntityId, "gui.millenaire.npcchat.failed", false);
                  return;
               }
               deliverNpcLine(online, livingTarget, current, livingMind, mindData, reply.get().say(), reply.get(), capturedSpeech, true);
            } finally {
               NpcModelClient.release();
            }
         });
      });
   }

   private static void deliverNpcLine(
      ServerPlayer player,
      NpcDialogueTarget target,
      NpcChatSession session,
      NpcMind mind,
      NpcMindSavedData data,
      String say,
      @Nullable NpcModelReply reply,
      String playerSpeech,
      boolean fromModel
   ) {
      session.setPendingRequest(false);
      String sanitizedSay = NpcChatSanitize.sanitize(say);
      if (sanitizedSay.isEmpty()) {
         sanitizedSay = Component.translatable("gui.millenaire.npcchat.no_model").getString();
      }
      long gameTime = player.serverLevel().getGameTime();
      mind.addTurn(player.getUUID(), NpcTurn.ROLE_NPC, sanitizedSay, gameTime);
      if (reply != null) {
         if (reply.mood() != null && !reply.mood().isBlank() && NpcMind.VALID_MOODS.contains(NpcMind.normalizeMood(reply.mood()))) {
            mind.setMood(reply.mood());
         }
         mind.setLastThought(reply.thought());
         mind.applyOpinionDelta(player.getUUID(), reply.trustDelta(), reply.fearDelta(), reply.affectionDelta());
         for (String memory : reply.newMemories()) {
            mind.addFact(memory, gameTime, 2, player.getUUID());
         }
         if (session.canGossip()) {
            maybeCreateRumor(player, target, mind, data, reply, playerSpeech, gameTime);
         }
         maybeConsolidate(player.serverLevel(), session.targetUuid(), mind, data);
         target.adapter().onModelReplyAccepted(player, target.entity(), reply);
      }
      data.markChanged();

      target.adapter().applySpokenLine(player, target.entity(), sanitizedSay);
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(target.entityId(), SPEAKER_NPC, sanitizedSay, false), new CustomPacketPayload[0]);
   }

   private static List<String> buildRumorLinesFor(NpcMind mind, NpcMindSavedData data, NpcDialogueTarget target) {
      if (target.category() != NpcDialogueCategory.MILLENAIRE_VILLAGER) {
         return List.of();
      }
      if (!(target.entity() instanceof MillVillager villager)) {
         return List.of();
      }
      Village village = villager.getVillageId() != null ? Village.resolve((ServerLevel)villager.level(), villager.getVillageId()) : null;
      return NpcPersonaBuilder.buildRumorLinesPublic(mind, data, village);
   }

   private static void maybeCreateRumor(
      ServerPlayer player,
      NpcDialogueTarget target,
      NpcMind mind,
      NpcMindSavedData data,
      NpcModelReply reply,
      String playerSpeech,
      long gameTime
   ) {
      if (!(target.entity() instanceof MillVillager villager)) {
         return;
      }
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      boolean hasCulture = village != null && village.getCultureId() != null;
      boolean child = vType != null && vType.isChild();
      if (
         !NpcGossipRules.canCreateGossip(
            (Boolean)MillenaireServerConfig.SERVER.npcChatGossip.get(),
            hasCulture,
            child,
            NpcSecretDetector.matches(playerSpeech),
            reply.keepSecret(),
            reply.gossipWorthy(),
            playerSpeech
         )
      ) {
         return;
      }

      ResourceLocation cultureId = village.getCultureId();
      String canonical = NpcGossipRules.buildCanonical(reply.gossipLine(), player.getName().getString(), playerSpeech);
      long day = player.serverLevel().getDayTime() / 24000L;
      List<NpcRumor> active = data.activeRumorsForCulture(cultureId);
      NpcRumor dedup = NpcGossipRules.findDedup(active, cultureId, player.getUUID(), canonical, day);
      if (dedup != null) {
         if (!mind.knowsRumor(dedup.id())) {
            mind.addOrRefreshKnowledge(
               new NpcRumorKnowledge(dedup.id(), villager.getUUID(), null, 0, 1.0F, gameTime, day)
            );
            data.markChanged();
         }
         return;
      }

      NpcRumor correction = NpcGossipRules.findCorrectionTarget(active, cultureId, player.getUUID(), canonical, reply.gossipWorthy());
      if (!NpcGossipRules.canAcceptNewActiveRumor(active, data.allKnowledge(), day)) {
         NpcRumor evict = NpcGossipRules.findEvictableRumor(active, data.allKnowledge(), day);
         if (evict == null) {
            return;
         }
         data.replaceRumor(evict.withExpired(true));
      } else {
         long activeCount = active.stream().filter(NpcRumor::isActive).count();
         if (activeCount >= NpcGossipRules.MAX_ACTIVE_RUMORS_PER_CULTURE) {
            NpcRumor evict = NpcGossipRules.findEvictableRumor(active, data.allKnowledge(), day);
            if (evict != null) {
               data.replaceRumor(evict.withExpired(true));
            }
         }
      }

      long id = data.nextRumorId();
      NpcRumor rumor = new NpcRumor(
         id,
         cultureId,
         villager.getUUID(),
         village.getId(),
         player.getUUID(),
         player.getName().getString(),
         canonical,
         gameTime,
         day,
         reply.gossipWorthy(),
         0L,
         false
      );
      data.addRumor(rumor);
      if (correction != null) {
         data.replaceRumor(correction.withSupersededBy(id));
         mind.removeKnowledge(correction.id());
      }
      mind.addOrRefreshKnowledge(new NpcRumorKnowledge(id, villager.getUUID(), null, 0, 1.0F, gameTime, day));
      data.markChanged();
   }

   private static void maybeConsolidate(ServerLevel level, UUID mindId, NpcMind mind, NpcMindSavedData data) {
      if (mind.getFacts().size() <= 24) {
         return;
      }
      if (!NpcModelClient.isConfigured() || !NpcModelClient.tryAcquire()) {
         mind.pruneOldestFactsAbove(NpcMind.MAX_FACTS);
         data.markChanged();
         return;
      }
      String system = "You consolidate long-term notes. Output only JSON {\"summary\":\"...\",\"facts\":[{\"text\":\"...\",\"importance\":1}] } with max 8 facts. Never invent events. Never include exact quotes.";
      StringBuilder user = new StringBuilder();
      user.append("SUMMARY:\n").append(mind.getSummary()).append("\nFACTS:\n");
      for (NpcFact fact : mind.getFacts()) {
         user.append("- ").append(fact.text()).append(" [").append(fact.importance()).append("]\n");
      }
      user.append("OLD TURNS:\n");
      for (List<NpcTurn> turns : mind.getTurns().values()) {
         int from = Math.max(0, turns.size() - 8);
         for (int i = 0; i < from; i++) {
            NpcTurn t = turns.get(i);
            user.append(t.role()).append(": ").append(t.text()).append('\n');
         }
      }
      NpcModelClient.sendRawAsync(system, user.toString(), mindId, 400).whenComplete((raw, err) -> {
         MinecraftServer server = level.getServer();
         if (server == null) {
            NpcModelClient.release();
            return;
         }
         server.execute(() -> {
            try {
               NpcMindSavedData mindData = NpcMindSavedData.get(level);
               NpcMind living = mindData.getOrCreateMind(mindId);
               if (raw == null || raw.isEmpty()) {
                  living.pruneOldestFactsAbove(NpcMind.MAX_FACTS);
               } else {
                  applyConsolidationJson(living, raw.get());
               }
               mindData.markChanged();
            } finally {
               NpcModelClient.release();
            }
         });
      });
   }

   static void applyConsolidationJson(NpcMind mind, String raw) {
      try {
         String json = NpcReplyParser.extractJsonObject(raw.trim());
         if (json == null) {
            mind.pruneOldestFactsAbove(NpcMind.MAX_FACTS);
            return;
         }
         com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
         if (obj.has("summary")) {
            mind.setSummary(obj.get("summary").getAsString());
         }
         List<NpcFact> facts = new ArrayList<>();
         if (obj.has("facts") && obj.get("facts").isJsonArray()) {
            long id = 1L;
            for (com.google.gson.JsonElement el : obj.getAsJsonArray("facts")) {
               if (facts.size() >= 8) {
                  break;
               }
               if (!el.isJsonObject()) {
                  continue;
               }
               com.google.gson.JsonObject f = el.getAsJsonObject();
               String text = f.has("text") ? NpcMind.truncate(f.get("text").getAsString(), 140) : "";
               int importance = f.has("importance") ? Math.max(1, Math.min(3, f.get("importance").getAsInt())) : 1;
               if (!text.isBlank()) {
                  facts.add(new NpcFact(id++, text, 0L, importance, null));
               }
            }
         }
         if (!facts.isEmpty()) {
            mind.replaceFacts(facts);
            mind.setNextFactId(facts.size() + 1L);
         }
      } catch (Exception e) {
         mind.pruneOldestFactsAbove(NpcMind.MAX_FACTS);
      }
   }

   private static boolean hasLineOfSight(ServerPlayer player, LivingEntity target) {
      Vec3 from = player.getEyePosition();
      Vec3 to = target.getEyePosition();
      return player.level().clip(
            new net.minecraft.world.level.ClipContext(
               from,
               to,
               net.minecraft.world.level.ClipContext.Block.COLLIDER,
               net.minecraft.world.level.ClipContext.Fluid.NONE,
               player
            )
         )
         .getType()
         == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   private static void sendRefusal(ServerPlayer player, NpcDialogueTarget target, String key) {
      String text = Component.translatable(key).getString();
      List<NpcChatOpenPayload.HistoryLine> history = List.of(new NpcChatOpenPayload.HistoryLine(SPEAKER_SYSTEM, text));
      NpcDialogueContext preview = target.adapter()
         .buildContext(player, target.entity(), NpcMindSavedData.get(player.serverLevel()).getOrCreateMind(target.identity().entityUuid()), NpcMindSavedData.get(player.serverLevel()), "", "en_us");
      NpcChatSession session = new NpcChatSession(
         player.getUUID(),
         target.identity().entityUuid(),
         target.entityId(),
         target.category(),
         target.policy().persistMind(),
         false,
         false,
         false,
         target.policy().bossNonIntrusive(),
         player.serverLevel().getGameTime()
      );
      SESSIONS.put(player.getUUID(), session);
      PacketDistributor.sendToPlayer(
         player,
         new NpcChatOpenPayload(target.entityId(), preview.displayName(), preview.roleLine(), preview.reputationLabel(), history),
         new CustomPacketPayload[0]
      );
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(target.entityId(), SPEAKER_SYSTEM, text, false), new CustomPacketPayload[0]);
   }

   private static void rejectOpen(ServerPlayer player, int entityId, String key) {
      // Client may not have a screen yet; send a closed payload so any pending UI clears, plus a system message.
      PacketDistributor.sendToPlayer(player, new NpcChatClosedPayload(entityId, key), new CustomPacketPayload[0]);
   }

   private static void sendReject(ServerPlayer player, int entityId, String key) {
      sendSystemLine(player, entityId, key, false);
   }

   private static void sendSystemLine(ServerPlayer player, int entityId, String key, boolean pending) {
      String text = Component.translatable(key).getString();
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(entityId, SPEAKER_SYSTEM, text, pending), new CustomPacketPayload[0]);
   }

   private static void closeSession(ServerPlayer player, int entityId, String reasonKey) {
      NpcChatSession removed = SESSIONS.remove(player.getUUID());
      if (removed != null) {
         removed.cancelPending();
      }
      PacketDistributor.sendToPlayer(player, new NpcChatClosedPayload(entityId, reasonKey), new CustomPacketPayload[0]);
   }
}
