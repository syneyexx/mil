package org.millenaire.living.events;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Canonical Living Villages event pipeline. AI output never emits events through this service.
 */
public final class LivingEventService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long DEFAULT_DEDUPE_TTL = 24000L; // one Minecraft day

   private static final List<Consumer<LivingEvent>> LISTENERS = new ArrayList<>();
   private static int processedThisTick;
   private static long lastTick = Long.MIN_VALUE;

   private LivingEventService() {
   }

   public static void addListener(Consumer<LivingEvent> listener) {
      if (listener != null) {
         LISTENERS.add(listener);
      }
   }

   public static void clearListeners() {
      LISTENERS.clear();
   }

   public static void beginTick(long gameTime) {
      if (gameTime != lastTick) {
         lastTick = gameTime;
         processedThisTick = 0;
      }
   }

   @Nullable
   public static LivingEvent emit(
      ServerLevel level,
      LivingEventType type,
      LivingEventSource source,
      Village village,
      @Nullable UUID actorId,
      @Nullable UUID targetId,
      String details,
      String visibility,
      String dedupeKey
   ) {
      if (level == null || village == null || type == null) {
         return null;
      }
      if (!isEnabled()) {
         return null;
      }
      beginTick(level.getGameTime());
      int budget = MillenaireServerConfig.SERVER.npcReactionProcessingBudget.getAsInt();
      if (processedThisTick >= budget) {
         if (MillenaireServerConfig.SERVER.livingVillagesDebugLogging.get()) {
            LOGGER.debug("[Living] Event budget exhausted; dropping {}", type);
         }
         return null;
      }

      LivingEventSavedData data = LivingEventSavedData.get(level);
      data.setDefaultMaxEvents(MillenaireServerConfig.SERVER.npcEventRetentionLimit.getAsInt());
      LivingEventJournal journal = data.journalFor(village.getId().uuid());

      String id = UUID.randomUUID().toString();
      ResourceLocation dimension = level.dimension().location();
      LivingEvent event = new LivingEvent(
         id,
         type,
         source,
         village.getId().uuid(),
         actorId,
         targetId,
         level.getGameTime(),
         dimension,
         details == null ? "" : details,
         visibility == null ? "public" : visibility,
         LivingEvent.SCHEMA_VERSION,
         dedupeKey == null ? "" : dedupeKey
      );

      if (!journal.tryAdd(event, DEFAULT_DEDUPE_TTL)) {
         return null;
      }
      data.setDirty();
      processedThisTick++;

      for (Consumer<LivingEvent> listener : LISTENERS) {
         try {
            listener.accept(event);
         } catch (Exception e) {
            LOGGER.warn("[Living] Event listener failed for {}: {}", type, e.toString());
         }
      }

      if (MillenaireServerConfig.SERVER.livingVillagesDebugLogging.get()) {
         LOGGER.debug("[Living] Event {} village={} details={}", type, village.getVillageName(), details);
      }
      return event;
   }

   public static List<LivingEvent> recentPublic(ServerLevel level, VillageId villageId, int limit) {
      LivingEventJournal journal = LivingEventSavedData.get(level).getJournal(villageId.uuid());
      if (journal == null) {
         return List.of();
      }
      List<LivingEvent> out = new ArrayList<>();
      for (LivingEvent event : journal.recent(limit * 2)) {
         if (event.isPublic()) {
            out.add(event);
            if (out.size() >= limit) {
               break;
            }
         }
      }
      return out;
   }

   public static void pruneVillage(ServerLevel level, UUID villageId) {
      LivingEventSavedData.get(level).removeVillage(villageId);
   }

   private static boolean isEnabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcEventMemoryEnabled.get();
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
