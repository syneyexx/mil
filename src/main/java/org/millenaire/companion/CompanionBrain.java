package org.millenaire.companion;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcMindSavedData;
import org.millenaire.chat.NpcModelClient;
import org.millenaire.companion.capability.CompanionCapabilityRegistry;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpeech;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public final class CompanionBrain {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<UUID, Long> LAST_HEARTBEAT_MS = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> LAST_SPEECH_MS = new ConcurrentHashMap<>();
   private static final Map<UUID, AtomicInteger> GENERATION = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
   private static final Map<UUID, String> PENDING_TRIGGER = new ConcurrentHashMap<>();

   private CompanionBrain() {
   }

   public static void tickCompanion(
      ServerLevel level, ServerPlayer player, MillVillager companion, CompanionBond bond, CompanionBondSavedData data, @Nullable Village village
   ) {
      if (!CompanionTickBudget.hasTimeForCompanion()) {
         return;
      }
      CompanionTravelJournal.update(bond, level, player, companion, village, data);
      CompanionSharedEventLog.onLowHealth(player, companion);
      if (companion.getAttackTarget() != null) {
         CompanionSharedEventLog.onCombat(player, companion);
      }
      applyStanceMovement(player, companion, bond);

      long now = System.currentTimeMillis();
      int heartbeatSec = 30;
      try {
         heartbeatSec = MillenaireServerConfig.SERVER.companionHeartbeatSeconds.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      String pending = PENDING_TRIGGER.remove(companion.getUUID());
      long last = LAST_HEARTBEAT_MS.getOrDefault(companion.getUUID(), 0L);
      boolean due = pending != null || now - last >= heartbeatSec * 1000L;
      if (!due) {
         return;
      }
      if (Boolean.TRUE.equals(IN_FLIGHT.get(companion.getUUID()))) {
         if (pending != null) {
            PENDING_TRIGGER.put(companion.getUUID(), pending);
         }
         return;
      }
      if (!NpcModelClient.tryAcquire()) {
         if (pending != null) {
            PENDING_TRIGGER.put(companion.getUUID(), pending);
         }
         return;
      }
      LAST_HEARTBEAT_MS.put(companion.getUUID(), now);
      requestModel(level, player, companion, bond, data, village, pending != null ? pending : "heartbeat");
   }

   /**
    * Called after NPC chat UI records a player quote. Schedules the next companion brain turn
    * (avoids a second HTTP call while NPC chat already requested a model reply).
    */
   public static void onPlayerChat(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionBond bond, String speech) {
      PENDING_TRIGGER.put(companion.getUUID(), "player_said: " + speech);
      LAST_HEARTBEAT_MS.put(companion.getUUID(), 0L);
   }

   /**
    * Vanilla T-chat: remember the line and immediately ask the local LM for speech + intents.
    *
    * @return true if an LM request was started now
    */
   public static boolean handleVanillaChat(
      ServerLevel level, ServerPlayer player, MillVillager companion, CompanionBond bond, String speech
   ) {
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      NpcMindSavedData minds = NpcMindSavedData.get(level);
      NpcMind mind = minds.getOrCreateMind(companion.getUUID());
      mind.addQuote(player.getUUID(), speech, level.getGameTime());
      mind.addTurn(player.getUUID(), org.millenaire.chat.NpcTurn.ROLE_PLAYER, speech, level.getGameTime());
      minds.setDirty();

      if (Boolean.TRUE.equals(IN_FLIGHT.get(companion.getUUID()))) {
         PENDING_TRIGGER.put(companion.getUUID(), "player_chat_t: " + speech);
         LAST_HEARTBEAT_MS.put(companion.getUUID(), 0L);
         return false;
      }
      if (!NpcModelClient.tryAcquire()) {
         PENDING_TRIGGER.put(companion.getUUID(), "player_chat_t: " + speech);
         LAST_HEARTBEAT_MS.put(companion.getUUID(), 0L);
         return false;
      }
      LAST_HEARTBEAT_MS.put(companion.getUUID(), System.currentTimeMillis());
      Village village = companion.getVillageId() != null ? Village.resolve(level, companion.getVillageId()) : null;
      requestModel(level, player, companion, bond, data, village, "player_chat_t: " + speech);
      return true;
   }

   private static void requestModel(
      ServerLevel level,
      ServerPlayer player,
      MillVillager companion,
      CompanionBond bond,
      CompanionBondSavedData data,
      @Nullable Village village,
      String trigger
   ) {
      UUID id = companion.getUUID();
      IN_FLIGHT.put(id, true);
      int gen = GENERATION.computeIfAbsent(id, u -> new AtomicInteger()).incrementAndGet();
      NpcMindSavedData minds = NpcMindSavedData.get(level);
      NpcMind mind = minds.getOrCreateMind(id);
      List<String> sensors = CompanionWorldSensor.sense(level, player, companion, village);
      List<String> caps = CompanionCapabilityRegistry.sensorLines(level, player, companion);
      String user = CompanionPromptBuilder.buildUserPrompt(bond, mind, player.getUUID(), "en_us", trigger, sensors, caps);

      NpcModelClient.sendRawAsync(CompanionPromptBuilder.SYSTEM_PROMPT, user, id, 220).whenComplete((raw, err) -> {
         NpcModelClient.release();
         IN_FLIGHT.put(id, false);
         if (err != null) {
            LOGGER.debug("Companion LM error {}", err.toString());
            return;
         }
         level.getServer().execute(() -> applyReply(level, player, companion, bond, data, village, mind, minds, raw.orElse(""), gen, trigger));
      });
   }

   private static void applyReply(
      ServerLevel level,
      ServerPlayer player,
      MillVillager companion,
      CompanionBond bond,
      CompanionBondSavedData data,
      @Nullable Village village,
      NpcMind mind,
      NpcMindSavedData minds,
      String raw,
      int gen,
      String trigger
   ) {
      if (gen != GENERATION.getOrDefault(companion.getUUID(), new AtomicInteger()).get()) {
         return;
      }
      if (!companion.isAlive() || companion.isRemoved()) {
         return;
      }
      var parsed = CompanionReplyParser.parse(raw);
      if (parsed.isEmpty()) {
         bond.addActionResult("lm reply unparseable");
         data.markChanged();
         return;
      }
      CompanionReply reply = parsed.get();
      if (!reply.thought().isBlank()) {
         mind.setLastThought(reply.thought());
      }
      if (!reply.mood().isBlank()) {
         mind.setMood(reply.mood());
      }
      for (String mem : reply.newMemories()) {
         mind.addFact(mem, level.getGameTime(), 1, player.getUUID());
         String lower = mem.toLowerCase();
         if (lower.contains("prefer") || lower.contains("like") || lower.contains("hate")) {
            bond.addPreference(mem);
         }
      }
      mind.applyOpinionDelta(player.getUUID(), reply.trustDelta(), reply.fearDelta(), reply.affectionDelta());
      if (reply.stance() != null) {
         bond.setStance(reply.stance());
      }
      int maxIntents = 4;
      try {
         maxIntents = MillenaireServerConfig.SERVER.companionMaxIntentsPerReply.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      int n = 0;
      for (CompanionIntent intent : reply.intents()) {
         if (n++ >= maxIntents) {
            break;
         }
         if (!CompanionTickBudget.hasTime()) {
            bond.addActionResult("deferred remaining intents");
            break;
         }
         String result = CompanionActionExecutor.execute(level, player, companion, bond, intent, village);
         bond.addActionResult(result);
         if ("SAY_ALOUD".equals(intent.type()) && intent.arg() != null && !intent.arg().isBlank() && canSpeak(companion.getUUID())) {
            speak(companion, intent.arg());
            LAST_SPEECH_MS.put(companion.getUUID(), System.currentTimeMillis());
         }
      }
      boolean fromNpcChatUi = trigger != null && trigger.startsWith("player_said");
      boolean fromVanillaT = trigger != null && trigger.startsWith("player_chat_t");
      if (!reply.say().isBlank()) {
         if (fromVanillaT) {
            // Always answer T-chat in the chat HUD + overhead bubble.
            speak(companion, reply.say());
            LAST_SPEECH_MS.put(companion.getUUID(), System.currentTimeMillis());
            player.sendSystemMessage(
               Component.translatable("message.millenaire.companion.chat_reply", companion.getName(), reply.say())
                  .withStyle(ChatFormatting.GRAY)
            );
         } else if (!fromNpcChatUi && canSpeak(companion.getUUID())) {
            speak(companion, reply.say());
            LAST_SPEECH_MS.put(companion.getUUID(), System.currentTimeMillis());
         }
      }
      minds.setDirty();
      data.markChanged();
   }

   private static boolean canSpeak(UUID companionId) {
      int maxPerMin = 3;
      try {
         maxPerMin = MillenaireServerConfig.SERVER.companionMaxProactiveLinesPerMinute.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      long last = LAST_SPEECH_MS.getOrDefault(companionId, 0L);
      long minGap = Math.max(1000L, 60_000L / Math.max(1, maxPerMin));
      return System.currentTimeMillis() - last >= minGap;
   }

   private static void speak(MillVillager companion, String text) {
      VillagerSpeech speech = companion.getSpeech();
      if (speech != null) {
         speech.setSpeechText(text);
      }
   }

   private static void applyStanceMovement(ServerPlayer player, MillVillager companion, CompanionBond bond) {
      double dist = Math.sqrt(companion.distanceToSqr(player));
      int maxDist = 48;
      try {
         maxDist = MillenaireServerConfig.SERVER.companionMaxDistanceBeforeReturn.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      if (dist > maxDist) {
         companion.getNavManager().navigateTo(companion, player.blockPosition(), 0.6);
         return;
      }
      if (bond.stance() == CompanionStance.STICK_CLOSE && dist > 4.0) {
         companion.getNavManager().navigateTo(companion, player.blockPosition(), 0.5);
      }
   }

   public static void clear(UUID companionId) {
      LAST_HEARTBEAT_MS.remove(companionId);
      LAST_SPEECH_MS.remove(companionId);
      GENERATION.remove(companionId);
      IN_FLIGHT.remove(companionId);
      PENDING_TRIGGER.remove(companionId);
      CompanionWorldSensor.clear(companionId);
   }
}
