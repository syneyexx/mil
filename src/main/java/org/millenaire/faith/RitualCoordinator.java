package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ItemHelper;
import org.millenaire.item.ModItems;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Ritual state machine transitions on the server thread. Presence requires real arrival
 * (abandoned navigation does not count).
 */
public final class RitualCoordinator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_ACTIVE_TICKS_PER = 2;

   private RitualCoordinator() {
   }

   public static boolean scheduleFromPlan(
      ServerLevel level, ReligionSavedData data, Village village, ReligionPlanResult plan
   ) {
      if (plan.ritualToSchedule() == null) {
         return false;
      }
      RitualDefinition def = RitualRegistry.get(plan.ritualToSchedule());
      if (def == null) {
         return false;
      }
      Chapter chapter = data.getChapterForVillage(village.getId());
      if (chapter == null) {
         return false;
      }
      if (data.findActiveRitualForChapter(chapter.id()) != null) {
         return false;
      }
      long now = level.getGameTime();
      if (chapter.lastRitualGameTime() >= 0L) {
         long cooldownTicks = def.cooldownDays() * 24000L;
         if (now - chapter.lastRitualGameTime() < cooldownTicks) {
            return false;
         }
      }
      RitualInstance ritual = new RitualInstance(UUID.randomUUID(), def.id(), chapter.id());
      ritual.setPrepareGameTime(now);
      ritual.setLeaderRecordUuid(plan.suggestedLeaderUuid());
      ritual.participantRecordUuids().addAll(plan.inviteRecordUuids());
      if (plan.suggestedLeaderUuid() != null && !ritual.participantRecordUuids().contains(plan.suggestedLeaderUuid())) {
         ritual.participantRecordUuids().add(plan.suggestedLeaderUuid());
      }
      // Cap capacity
      while (ritual.participantRecordUuids().size() > def.capacity()) {
         ritual.participantRecordUuids().removeLast();
      }
      ritual.setState(RitualState.PREPARING);
      if (!reserveGoods(level, village, chapter, def, ritual)) {
         LOGGER.debug("[Religion] Ritual {} skipped: could not reserve goods", def.id());
         return false;
      }
      ritual.setState(RitualState.GATHERING);
      long startAt = plan.ritualStartGameTime() > now ? plan.ritualStartGameTime() : now + 100L;
      ritual.setStartDeadline(startAt + def.gatherDeadlineTicks());
      data.putRitual(ritual);
      ReligionService.deadlines().schedule(startAt, ReligionDeadlineQueue.Kind.RITUAL_START, ritual.id());
      ReligionService.deadlines().schedule(ritual.startDeadline(), ReligionDeadlineQueue.Kind.RITUAL_END, ritual.id());
      village.markDirty();
      return true;
   }

   public static RitualInstance startPlayerRitual(
      ServerLevel level,
      Village village,
      ResourceLocation ritualId,
      UUID playerId,
      @Nullable UUID leaderRecordUuid
   ) {
      ReligionSavedData data = ReligionSavedData.get(level);
      Chapter chapter = data.getChapterForVillage(village.getId());
      if (chapter == null) {
         return null;
      }
      RitualDefinition def = RitualRegistry.get(ritualId);
      if (def == null) {
         return null;
      }
      if (data.findActiveRitualForChapter(chapter.id()) != null) {
         return null;
      }
      RitualInstance ritual = new RitualInstance(UUID.randomUUID(), ritualId, chapter.id());
      ritual.setPrepareGameTime(level.getGameTime());
      ritual.setLeaderRecordUuid(leaderRecordUuid);
      ritual.playerParticipantUuids().add(playerId);
      if (leaderRecordUuid != null) {
         ritual.participantRecordUuids().add(leaderRecordUuid);
      }
      ritual.setState(RitualState.PREPARING);
      if (!reserveGoods(level, village, chapter, def, ritual)) {
         return null;
      }
      ritual.setState(RitualState.GATHERING);
      long startAt = level.getGameTime() + 200L;
      ritual.setStartDeadline(startAt + def.gatherDeadlineTicks());
      data.putRitual(ritual);
      ReligionService.deadlines().schedule(startAt, ReligionDeadlineQueue.Kind.RITUAL_START, ritual.id());
      return ritual;
   }

   public static void handleDeadline(ServerLevel level, ReligionSavedData data, ReligionDeadlineQueue.Entry entry) {
      switch (entry.kind()) {
         case RITUAL_START -> tryStartRunning(level, data, entry.targetId());
         case RITUAL_END -> tryCompleteOrExpire(level, data, entry.targetId());
         case PLAN_VILLAGE -> ReligionPlanner.get().markDirty(entry.targetId());
         case TRAVEL_COMPLETE -> completeTravel(level, data, entry.targetId());
         case DEVOTION_DECAY -> applyDevotionDecay(data, entry.targetId());
      }
   }

   public static void tickActiveRituals(ServerLevel level, ReligionSavedData data, int budget) {
      int n = 0;
      for (RitualInstance ritual : List.copyOf(data.allActiveRituals())) {
         if (n >= budget) {
            break;
         }
         if (ritual.state().isTerminal()) {
            data.archiveRitual(ritual);
            continue;
         }
         if (ritual.state() == RitualState.RUNNING) {
            if (!leaderPresent(level, data, ritual) && ritual.leaderRecordUuid() != null) {
               cancelRitual(level, data, ritual, RitualState.INTERRUPTED, true);
            }
            n++;
         } else if (ritual.state() == RitualState.GATHERING) {
            n++;
         }
      }
   }

   private static void tryStartRunning(ServerLevel level, ReligionSavedData data, UUID ritualId) {
      RitualInstance ritual = data.getRitual(ritualId);
      if (ritual == null || ritual.state() != RitualState.GATHERING) {
         return;
      }
      RitualDefinition def = RitualRegistry.get(ritual.definitionId());
      if (def == null) {
         cancelRitual(level, data, ritual, RitualState.CANCELLED, true);
         return;
      }
      Chapter chapter = data.getChapter(ritual.chapterId());
      if (chapter == null) {
         cancelRitual(level, data, ritual, RitualState.CANCELLED, true);
         return;
      }
      Village village = findVillage(level, chapter.villageId().uuid());
      if (village == null) {
         cancelRitual(level, data, ritual, RitualState.EXPIRED, true);
         return;
      }
      BlockPos shrine = shrinePos(village, chapter);
      int present = countPresentParticipants(level, village, ritual, shrine, def.presenceRadius());
      int playersPresent = countPresentPlayers(level, ritual, shrine, def.presenceRadius());
      if (present + playersPresent <= 0) {
         cancelRitual(level, data, ritual, RitualState.EXPIRED, true);
         return;
      }
      // Consume reserved goods once
      if (!ritual.consumed()) {
         consumeReserved(level, village, chapter, ritual);
         ritual.setConsumed(true);
      }
      ritual.setState(RitualState.RUNNING);
      ritual.setEndDeadline(level.getGameTime() + def.durationTicks());
      ReligionService.deadlines().schedule(ritual.endDeadline(), ReligionDeadlineQueue.Kind.RITUAL_END, ritual.id());
      data.setDirty();
      if (MillenaireServerConfig.SERVER.religionVisualEffectsEnabled.getAsBoolean()) {
         spawnVfx(level, shrine);
      }
      notifyNearby(level, shrine, Component.translatable("message.millenaire.religion.ritual_started", def.displayName()));
   }

   private static void tryCompleteOrExpire(ServerLevel level, ReligionSavedData data, UUID ritualId) {
      RitualInstance ritual = data.getRitual(ritualId);
      if (ritual == null) {
         return;
      }
      if (ritual.state() == RitualState.GATHERING) {
         cancelRitual(level, data, ritual, RitualState.EXPIRED, true);
         return;
      }
      if (ritual.state() != RitualState.RUNNING) {
         return;
      }
      RitualDefinition def = RitualRegistry.get(ritual.definitionId());
      Chapter chapter = data.getChapter(ritual.chapterId());
      Village village = chapter != null ? findVillage(level, chapter.villageId().uuid()) : null;
      if (def == null || chapter == null || village == null) {
         cancelRitual(level, data, ritual, RitualState.INTERRUPTED, false);
         return;
      }
      BlockPos shrine = shrinePos(village, chapter);
      int present = countPresentParticipants(level, village, ritual, shrine, def.presenceRadius());
      int playersPresent = countPresentPlayers(level, ritual, shrine, def.presenceRadius());
      if (present + playersPresent <= 0) {
         cancelRitual(level, data, ritual, RitualState.INTERRUPTED, false);
         return;
      }
      succeed(level, data, village, chapter, ritual, def);
   }

   private static void succeed(
      ServerLevel level,
      ReligionSavedData data,
      Village village,
      Chapter chapter,
      RitualInstance ritual,
      RitualDefinition def
   ) {
      if (!ritual.rewarded()) {
         ritual.setRewarded(true);
         for (UUID uuid : ritual.participantRecordUuids()) {
            Membership m = data.getMembership(Membership.SubjectType.VILLAGER, uuid);
            if (m != null && m.chapterId().equals(chapter.id())) {
               m.addDevotion(def.devotionReward());
               VillagerRecord rec = village.getVillagerRecord(uuid);
               if (rec != null) {
                  rec.setDevotion(m.devotion());
               }
            }
         }
         for (UUID playerId : ritual.playerParticipantUuids()) {
            Membership m = data.getMembership(Membership.SubjectType.PLAYER, playerId);
            if (m != null && m.chapterId().equals(chapter.id())) {
               m.addDevotion(def.devotionReward());
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
               player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
               // Relic restore reward
               if (def.id().getPath().equals("relic_restore")) {
                  player.getInventory().add(ModItems.MEMORY_BOOK.get().getDefaultInstance());
               }
            }
         }
         chapter.addCohesion(def.cohesionReward());
         chapter.addSecrecy(def.secrecyDelta());
         chapter.setTensionContribution(chapter.tensionContribution() + Math.max(0, def.tensionDelta()));
         village.addReligionTension(def.tensionDelta());
         if (chapter.stage() == Chapter.Stage.SHRINE_READY || chapter.stage() == Chapter.Stage.SEEDED) {
            chapter.setStage(Chapter.Stage.ACTIVE);
         }
         chapter.setLastRitualGameTime(level.getGameTime());
         village.recordChronicleEvent(level, VillageEventType.RITUAL, def.displayName(), chapter.id().toString());
      }
      ritual.setState(RitualState.SUCCEEDED);
      data.archiveRitual(ritual);
      village.markDirty();
      data.setDirty();
      BlockPos shrine = shrinePos(village, chapter);
      if (MillenaireServerConfig.SERVER.religionVisualEffectsEnabled.getAsBoolean()) {
         spawnVfx(level, shrine);
      }
   }

   public static void cancelRitual(
      ServerLevel level, ReligionSavedData data, RitualInstance ritual, RitualState terminal, boolean refund
   ) {
      if (ritual.state().isTerminal()) {
         return;
      }
      Chapter chapter = data.getChapter(ritual.chapterId());
      Village village = chapter != null ? findVillage(level, chapter.villageId().uuid()) : null;
      if (refund && terminal.allowsRefund() && ritual.goodsReserved() && !ritual.consumed() && village != null && chapter != null) {
         refundGoods(level, village, chapter, ritual);
      }
      ritual.setState(terminal);
      data.archiveRitual(ritual);
      if (village != null) {
         village.markDirty();
      }
      data.setDirty();
   }

   /** Helper: INTERRUPTED/EXPIRED/CANCELLED before consume may refund. */
   private static boolean reserveGoods(
      ServerLevel level, Village village, Chapter chapter, RitualDefinition def, RitualInstance ritual
   ) {
      if (def.requiredGoods().isEmpty()) {
         ritual.setGoodsReserved(true);
         return true;
      }
      BuildingInstance building = chapter.shrineBuildingId() != null
         ? village.getBuilding(chapter.shrineBuildingId())
         : null;
      if (building == null) {
         // Soft-allow empty shrine reservation for player-led first rituals
         ritual.reservedGoods().putAll(def.requiredGoods());
         ritual.setGoodsReserved(true);
         return true;
      }
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         ritual.reservedGoods().putAll(def.requiredGoods());
         ritual.setGoodsReserved(true);
         return true;
      }
      for (Map.Entry<String, Integer> e : def.requiredGoods().entrySet()) {
         Item item = ItemHelper.resolve(e.getKey());
         if (item == null) {
            continue;
         }
         int have = inv.getCount(level, item);
         if (have < e.getValue()) {
            return false;
         }
      }
      // Soft reservation: remember amounts; actual remove on consume
      ritual.reservedGoods().putAll(def.requiredGoods());
      ritual.setGoodsReserved(true);
      return true;
   }

   private static void consumeReserved(ServerLevel level, Village village, Chapter chapter, RitualInstance ritual) {
      BuildingInstance building = chapter.shrineBuildingId() != null
         ? village.getBuilding(chapter.shrineBuildingId())
         : null;
      if (building == null || building.getInventory() == null) {
         return;
      }
      BuildingInventory inv = building.getInventory();
      for (Map.Entry<String, Integer> e : ritual.reservedGoods().entrySet()) {
         Item item = ItemHelper.resolve(e.getKey());
         if (item != null) {
            inv.remove(level, item, e.getValue());
         }
      }
   }

   private static void refundGoods(ServerLevel level, Village village, Chapter chapter, RitualInstance ritual) {
      // Soft reservation: nothing removed yet, so nothing to refund to chests.
      ritual.reservedGoods().clear();
      ritual.setGoodsReserved(false);
   }

   private static int countPresentParticipants(
      ServerLevel level, Village village, RitualInstance ritual, BlockPos shrine, double radius
   ) {
      double r2 = radius * radius;
      int count = 0;
      for (UUID uuid : ritual.participantRecordUuids()) {
         VillagerRecord rec = village.getVillagerRecord(uuid);
         if (rec == null || rec.isKilled()) {
            continue;
         }
         // Prefer live entity position
         var entity = level.getEntity(uuid);
         BlockPos pos = entity != null ? entity.blockPosition() : rec.getLastKnownPos();
         if (pos != null && pos.distSqr(shrine) <= r2) {
            count++;
         }
      }
      return count;
   }

   private static int countPresentPlayers(ServerLevel level, RitualInstance ritual, BlockPos shrine, double radius) {
      double r2 = radius * radius;
      int count = 0;
      for (UUID playerId : ritual.playerParticipantUuids()) {
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
         if (player != null && player.blockPosition().distSqr(shrine) <= r2) {
            count++;
         }
      }
      return count;
   }

   private static boolean leaderPresent(ServerLevel level, ReligionSavedData data, RitualInstance ritual) {
      if (ritual.leaderRecordUuid() == null) {
         return true;
      }
      Chapter chapter = data.getChapter(ritual.chapterId());
      if (chapter == null) {
         return false;
      }
      Village village = findVillage(level, chapter.villageId().uuid());
      if (village == null) {
         return false;
      }
      RitualDefinition def = RitualRegistry.get(ritual.definitionId());
      double radius = def != null ? def.presenceRadius() : 8.0;
      BlockPos shrine = shrinePos(village, chapter);
      VillagerRecord rec = village.getVillagerRecord(ritual.leaderRecordUuid());
      if (rec == null || rec.isKilled()) {
         return false;
      }
      var entity = level.getEntity(ritual.leaderRecordUuid());
      BlockPos pos = entity != null ? entity.blockPosition() : rec.getLastKnownPos();
      return pos != null && pos.distSqr(shrine) <= radius * radius;
   }

   private static BlockPos shrinePos(Village village, Chapter chapter) {
      if (chapter.shrineBuildingId() != null) {
         BuildingInstance b = village.getBuilding(chapter.shrineBuildingId());
         if (b != null) {
            return b.getOrigin();
         }
      }
      return FaithCalendarService.resolveChurchCenter(village);
   }

   private static void spawnVfx(ServerLevel level, BlockPos center) {
      for (int i = 0; i < 8; i++) {
         level.sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            center.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 4.0,
            center.getY() + 1.2,
            center.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 4.0,
            1,
            0.0,
            0.05,
            0.0,
            0.0
         );
      }
      level.playSound(null, center, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 0.4F, 0.8F);
   }

   private static void notifyNearby(ServerLevel level, BlockPos center, Component msg) {
      double rangeSq = 48.0 * 48.0;
      for (ServerPlayer player : level.players()) {
         if (player.blockPosition().distSqr(center) <= rangeSq) {
            player.sendSystemMessage(msg);
         }
      }
   }

   private static void completeTravel(ServerLevel level, ReligionSavedData data, UUID travelId) {
      TravelRecord travel = data.getTravel(travelId);
      if (travel == null) {
         return;
      }
      long day = level.getDayTime() / 24000L;
      if (day >= travel.completeByDay()) {
         travel.setStatus(TravelRecord.Status.COMPLETED);
         data.setDirty();
      }
   }

   private static void applyDevotionDecay(ReligionSavedData data, UUID chapterId) {
      Chapter chapter = data.getChapter(chapterId);
      if (chapter == null) {
         return;
      }
      for (Membership m : data.membershipsForChapter(chapterId)) {
         if (m.devotion() > 0) {
            m.addDevotion(-1);
         }
      }
      data.setDirty();
   }

   @Nullable
   private static Village findVillage(ServerLevel level, UUID villageId) {
      return org.millenaire.village.VillageSavedData.get(level)
         .getVillageManager()
         .getVillage(new org.millenaire.village.VillageId(villageId));
   }
}
