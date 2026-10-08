package org.millenaire.chat;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.dialogue.SentenceLoader;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInteraction;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.network.ServerPayloadGuards;
import org.millenaire.network.SpeechChatPayload;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public final class NpcChatService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<UUID, NpcChatSession> SESSIONS = new ConcurrentHashMap<>();
   public static final byte SPEAKER_NPC = 0;
   public static final byte SPEAKER_PLAYER = 1;
   public static final byte SPEAKER_SYSTEM = 2;

   private NpcChatService() {
   }

   public static void tickSessions(MinecraftServer server) {
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
         if (!(entity instanceof MillVillager villager) || !villager.isAlive()) {
            closeSession(player, session.entityId(), "gui.millenaire.npcchat.closed");
            continue;
         }
         if (!ServerPayloadGuards.isWithinDistance(player, villager, leaveRange)) {
            closeSession(player, session.entityId(), "gui.millenaire.npcchat.too_far");
            continue;
         }
         villager.getLookControl().setLookAt(player);
         GoalScheduler scheduler = villager.getGoalScheduler();
         boolean leisure = scheduler == null || scheduler.getCurrentTask() == null || isLeisureGoal(villager);
         if (leisure) {
            villager.getNavigation().stop();
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
         PacketDistributor.sendToPlayer(player, new NpcChatClosedPayload(session.entityId(), "gui.millenaire.npcchat.closed"), new CustomPacketPayload[0]);
      }
   }

   public static void handleOpen(ServerPlayer player, int entityId) {
      if (player.isSpectator()) {
         return;
      }
      MillVillager villager = resolveVillager(player, entityId);
      if (villager == null) {
         return;
      }
      double chatRange = MillenaireServerConfig.SERVER.npcChatChatRange.getAsInt();
      if (!ServerPayloadGuards.isWithinDistance(player, villager, chatRange)) {
         return;
      }

      NpcChatSession previous = SESSIONS.remove(player.getUUID());
      if (previous != null && previous.entityId() != entityId) {
         PacketDistributor.sendToPlayer(
            player, new NpcChatClosedPayload(previous.entityId(), "gui.millenaire.npcchat.closed"), new CustomPacketPayload[0]
         );
      }

      String refusal = refusalKey(player, villager);
      if (refusal != null) {
         sendRefusal(player, villager, refusal);
         return;
      }

      NpcMindSavedData data = NpcMindSavedData.get(player.serverLevel());
      NpcMind mind = data.getOrCreateMind(villager.getUUID());
      data.markChanged();

      List<NpcChatOpenPayload.HistoryLine> history = new ArrayList<>();
      for (NpcTurn turn : mind.recentTurns(player.getUUID(), 12)) {
         byte speaker = NpcTurn.ROLE_PLAYER.equals(turn.role()) ? SPEAKER_PLAYER : SPEAKER_NPC;
         history.add(new NpcChatOpenPayload.HistoryLine(speaker, turn.text()));
      }

      String roleLine = buildRoleLine(villager);
      String repLabel = resolveRepLabel(player, villager);
      SESSIONS.put(player.getUUID(), new NpcChatSession(player.getUUID(), villager.getUUID(), entityId));
      PacketDistributor.sendToPlayer(
         player,
         new NpcChatOpenPayload(entityId, villager.getVillagerDisplayName(), roleLine, repLabel, history),
         new CustomPacketPayload[0]
      );
   }

   public static void handleClose(ServerPlayer player, int entityId) {
      NpcChatSession session = SESSIONS.get(player.getUUID());
      if (session != null && session.entityId() == entityId) {
         SESSIONS.remove(player.getUUID());
      }
   }

   public static void handleSend(ServerPlayer player, int entityId, String text, String languageCode) {
      if (player.isSpectator()) {
         return;
      }
      if (!NpcChatSanitize.isValidLanguageCode(languageCode)) {
         return;
      }
      String sanitized = NpcChatSanitize.sanitize(text);
      if (sanitized.isEmpty()) {
         return;
      }
      MillVillager villager = resolveVillager(player, entityId);
      if (villager == null) {
         return;
      }
      double leaveRange = MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange();
      if (!ServerPayloadGuards.isWithinDistance(player, villager, leaveRange)) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.too_far", false);
         closeSession(player, entityId, "gui.millenaire.npcchat.too_far");
         return;
      }

      NpcChatSession session = SESSIONS.get(player.getUUID());
      if (session == null || session.entityId() != entityId || !session.villagerUuid().equals(villager.getUUID())) {
         session = new NpcChatSession(player.getUUID(), villager.getUUID(), entityId);
         SESSIONS.put(player.getUUID(), session);
      }

      String refusal = refusalKey(player, villager);
      if (refusal != null) {
         sendSystemLine(player, entityId, refusal, false);
         speakTranslated(player, villager, refusal);
         return;
      }

      if (session.pendingRequest()) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.busy", false);
         return;
      }

      ServerLevel level = player.serverLevel();
      long gameTime = level.getGameTime();
      NpcMindSavedData data = NpcMindSavedData.get(level);
      NpcMind mind = data.getOrCreateMind(villager.getUUID());
      mind.addQuote(player.getUUID(), sanitized, gameTime);
      mind.addTurn(player.getUUID(), NpcTurn.ROLE_PLAYER, sanitized, gameTime);
      data.markChanged();

      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(entityId, SPEAKER_PLAYER, sanitized, false), new CustomPacketPayload[0]);

      if (!NpcModelClient.isConfigured()) {
         String fallback = fallbackLine(villager, languageCode);
         deliverNpcLine(player, villager, session, mind, data, fallback, null, sanitized, false);
         return;
      }

      if (!NpcModelClient.tryAcquire()) {
         sendSystemLine(player, entityId, "gui.millenaire.npcchat.busy", false);
         return;
      }

      session.setPendingRequest(true);
      sendSystemLine(player, entityId, "gui.millenaire.npcchat.thinking", true);

      int reputation = 0;
      String repLabel = "Unknown";
      Village village = villager.getVillageId() != null ? Village.resolve(level, villager.getVillageId()) : null;
      if (village != null) {
         reputation = village.getCombinedReputation(level, player.getUUID());
         repLabel = VillagerInteraction.resolveReputationLabelPublic(reputation, village.getCultureId());
      }

      NpcPersona persona = NpcPersonaBuilder.build(player, villager, mind, data, sanitized, languageCode, reputation, repLabel);
      String userPrompt = NpcPromptBuilder.buildUserPrompt(persona);
      UUID villagerUuid = villager.getUUID();
      UUID playerId = player.getUUID();
      int capturedEntityId = entityId;
      String capturedSpeech = sanitized;

      NpcModelClient.sendAsync(NpcPromptBuilder.SYSTEM_PROMPT, userPrompt, villagerUuid).whenComplete((reply, error) -> {
         MinecraftServer server = player.getServer();
         if (server == null) {
            NpcModelClient.release();
            return;
         }
         server.execute(() -> {
            try {
               NpcChatSession current = SESSIONS.get(playerId);
               ServerPlayer online = server.getPlayerList().getPlayer(playerId);
               Entity entity = online != null ? online.level().getEntity(capturedEntityId) : null;
               boolean valid = online != null
                  && current != null
                  && current.entityId() == capturedEntityId
                  && current.villagerUuid().equals(villagerUuid)
                  && entity instanceof MillVillager living
                  && living.isAlive()
                  && ServerPayloadGuards.isWithinDistance(online, living, MillenaireServerConfig.SERVER.effectiveNpcChatLeaveRange());
               if (!valid) {
                  if (current != null) {
                     current.setPendingRequest(false);
                  }
                  return;
               }
               MillVillager livingVillager = (MillVillager)entity;
               NpcMindSavedData mindData = NpcMindSavedData.get(online.serverLevel());
               NpcMind livingMind = mindData.getOrCreateMind(villagerUuid);
               if (reply == null || reply.isEmpty()) {
                  String fallback = fallbackLine(livingVillager, languageCode);
                  deliverNpcLine(online, livingVillager, current, livingMind, mindData, fallback, null, capturedSpeech, true);
                  sendSystemLine(online, capturedEntityId, "gui.millenaire.npcchat.failed", false);
                  return;
               }
               deliverNpcLine(online, livingVillager, current, livingMind, mindData, reply.get().say(), reply.get(), capturedSpeech, true);
            } finally {
               NpcModelClient.release();
            }
         });
      });
   }

   private static void deliverNpcLine(
      ServerPlayer player,
      MillVillager villager,
      NpcChatSession session,
      NpcMind mind,
      NpcMindSavedData data,
      String say,
      @Nullable NpcModelReply reply,
      String playerSpeech,
      boolean fromModel
   ) {
      session.setPendingRequest(false);
      long gameTime = player.serverLevel().getGameTime();
      mind.addTurn(player.getUUID(), NpcTurn.ROLE_NPC, say, gameTime);
      if (reply != null) {
         if (reply.mood() != null && !reply.mood().isBlank() && NpcMind.VALID_MOODS.contains(NpcMind.normalizeMood(reply.mood()))) {
            mind.setMood(reply.mood());
         }
         mind.setLastThought(reply.thought());
         mind.applyOpinionDelta(player.getUUID(), reply.trustDelta(), reply.fearDelta(), reply.affectionDelta());
         for (String memory : reply.newMemories()) {
            mind.addFact(memory, gameTime, 2, player.getUUID());
         }
         maybeCreateRumor(player, villager, mind, data, reply, playerSpeech, gameTime);
         maybeConsolidate(player.serverLevel(), villager.getUUID(), mind, data);
      }
      data.markChanged();

      villager.getSpeech().setSpeechText(say);
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(villager.getId(), SPEAKER_NPC, say, false), new CustomPacketPayload[0]);
      broadcastSpeechChat(player, villager, say);
   }

   private static void maybeCreateRumor(
      ServerPlayer player,
      MillVillager villager,
      NpcMind mind,
      NpcMindSavedData data,
      NpcModelReply reply,
      String playerSpeech,
      long gameTime
   ) {
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

   private static void maybeConsolidate(ServerLevel level, UUID villagerId, NpcMind mind, NpcMindSavedData data) {
      if (mind.getFacts().size() <= 24) {
         return;
      }
      if (!NpcModelClient.isConfigured() || !NpcModelClient.tryAcquire()) {
         mind.pruneOldestFactsAbove(NpcMind.MAX_FACTS);
         data.markChanged();
         return;
      }
      String system = "You consolidate a villager's long-term notes. Output only JSON {\"summary\":\"...\",\"facts\":[{\"text\":\"...\",\"importance\":1}] } with max 8 facts. Never invent events. Never include exact quotes.";
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
      NpcModelClient.sendRawAsync(system, user.toString(), villagerId, 400).whenComplete((raw, err) -> {
         MinecraftServer server = level.getServer();
         server.execute(() -> {
            try {
               NpcMindSavedData mindData = NpcMindSavedData.get(level);
               NpcMind living = mindData.getOrCreateMind(villagerId);
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

   private static void broadcastSpeechChat(ServerPlayer player, MillVillager villager, String say) {
      String cultureKey = "";
      int languageScore = 0;
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (village != null && village.getCultureId() != null) {
         cultureKey = village.getCultureId().getPath();
         languageScore = org.millenaire.village.PlayerCultureReputation.get(player.serverLevel())
            .getLanguageKnowledge(player.getUUID(), village.getCultureId());
      }
      PacketDistributor.sendToPlayer(
         player, new SpeechChatPayload(villager.getVillagerDisplayName(), say, cultureKey, languageScore, ""), new CustomPacketPayload[0]
      );
   }

   private static String fallbackLine(MillVillager villager, String languageCode) {
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      ResourceLocation culture = vType != null ? vType.culture() : null;
      String role = villager.getRoleName();
      if (role == null || role.isBlank()) {
         role = "villager";
      }
      if (culture != null) {
         List<String> sentences = SentenceLoader.getSentences(culture, languageCode, role.toLowerCase(), "greeting");
         if (sentences.isEmpty()) {
            sentences = SentenceLoader.getSentences(culture, "en_us", role.toLowerCase(), "greeting");
         }
         if (!sentences.isEmpty()) {
            return sentences.get(Math.floorMod((int)villager.getUUID().getLeastSignificantBits(), sentences.size()));
         }
      }
      return Component.translatable("gui.millenaire.npcchat.no_model").getString();
   }

   @Nullable
   private static String refusalKey(ServerPlayer player, MillVillager villager) {
      if (villager.isSleeping() || villager.isVillagerSleeping()) {
         return "gui.millenaire.npcchat.asleep";
      }
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (villager.getTarget() != null || village != null && village.isUnderAttack()) {
         return "gui.millenaire.npcchat.combat";
      }
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (vType != null && vType.hasTag("hostile")) {
         boolean targetingPlayer = villager.getTarget() instanceof Player p && p.getUUID().equals(player.getUUID());
         int rep = village != null ? village.getCombinedReputation(player.serverLevel(), player.getUUID()) : 0;
         if (targetingPlayer || rep < 0) {
            return "gui.millenaire.npcchat.refused";
         }
      }
      return null;
   }

   private static void sendRefusal(ServerPlayer player, MillVillager villager, String key) {
      String text = Component.translatable(key).getString();
      List<NpcChatOpenPayload.HistoryLine> history = List.of(new NpcChatOpenPayload.HistoryLine(SPEAKER_SYSTEM, text));
      SESSIONS.put(player.getUUID(), new NpcChatSession(player.getUUID(), villager.getUUID(), villager.getId()));
      PacketDistributor.sendToPlayer(
         player,
         new NpcChatOpenPayload(villager.getId(), villager.getVillagerDisplayName(), buildRoleLine(villager), resolveRepLabel(player, villager), history),
         new CustomPacketPayload[0]
      );
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(villager.getId(), SPEAKER_SYSTEM, text, false), new CustomPacketPayload[0]);
      speakTranslated(player, villager, key);
   }

   private static void speakTranslated(ServerPlayer player, MillVillager villager, String key) {
      String text = Component.translatable(key).getString();
      villager.getSpeech().setSpeechText(text);
   }

   private static void sendSystemLine(ServerPlayer player, int entityId, String key, boolean pending) {
      String text = Component.translatable(key).getString();
      PacketDistributor.sendToPlayer(player, new NpcChatLinePayload(entityId, SPEAKER_SYSTEM, text, pending), new CustomPacketPayload[0]);
   }

   private static void closeSession(ServerPlayer player, int entityId, String reasonKey) {
      SESSIONS.remove(player.getUUID());
      PacketDistributor.sendToPlayer(player, new NpcChatClosedPayload(entityId, reasonKey), new CustomPacketPayload[0]);
   }

   @Nullable
   private static MillVillager resolveVillager(ServerPlayer player, int entityId) {
      Entity entity = player.level().getEntity(entityId);
      return entity instanceof MillVillager villager && villager.level() == player.level() ? villager : null;
   }

   private static String buildRoleLine(MillVillager villager) {
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      String role = vType != null && vType.nativeName() != null && !vType.nativeName().isBlank() ? vType.nativeName() : villager.getRoleName();
      Village village = villager.getVillageId() != null ? Village.resolve((ServerLevel)villager.level(), villager.getVillageId()) : null;
      String villageName = village != null && village.getVillageName() != null ? village.getVillageName() : "";
      if (villageName.isBlank()) {
         return role;
      }
      return role + " — " + villageName;
   }

   private static String resolveRepLabel(ServerPlayer player, MillVillager villager) {
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      if (village == null) {
         return "Unknown";
      }
      int rep = village.getCombinedReputation(player.serverLevel(), player.getUUID());
      return VillagerInteraction.resolveReputationLabelPublic(rep, village.getCultureId());
   }
}
