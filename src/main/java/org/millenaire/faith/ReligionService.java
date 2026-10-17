package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Server-thread religion orchestration: snapshots, deadline processing, result apply, membership.
 */
public final class ReligionService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ReligionIndexes INDEXES = new ReligionIndexes();
   private static final ReligionDeadlineQueue DEADLINES = new ReligionDeadlineQueue();
   private static final int COMMIT_BUDGET = 4;
   private static final int DEADLINE_BUDGET = 8;
   private static final int INDEX_REBUILD_BUDGET = 16;
   private static final int CATCHUP_LIMIT_DEFAULT = 16;

   private ReligionService() {
   }

   public static ReligionIndexes indexes() {
      return INDEXES;
   }

   public static ReligionDeadlineQueue deadlines() {
      return DEADLINES;
   }

   public static void onServerStarted(ServerLevel level) {
      ReligionPlanner.get().onServerStarted(MillenaireServerConfig.SERVER.religionPlannerThreads.getAsInt());
      ReligionSavedData data = ReligionSavedData.get(level);
      data.bumpSession();
      data.setContentGenerationSeen(OrderRegistry.contentGeneration());
      DEADLINES.clear();
      // Re-schedule active ritual deadlines from saved state.
      for (RitualInstance ritual : data.allActiveRituals()) {
         if (ritual.state() == RitualState.GATHERING && ritual.startDeadline() > 0L) {
            DEADLINES.schedule(ritual.startDeadline(), ReligionDeadlineQueue.Kind.RITUAL_START, ritual.id());
         }
         if (ritual.state() == RitualState.RUNNING && ritual.endDeadline() > 0L) {
            DEADLINES.schedule(ritual.endDeadline(), ReligionDeadlineQueue.Kind.RITUAL_END, ritual.id());
         }
      }
      LOGGER.info("[Religion] Session {} started", data.worldSessionId());
   }

   public static void onServerStopping() {
      ReligionPlanner.get().shutdown();
      DEADLINES.clear();
   }

   public static void tick(ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.religionEnabled.getAsBoolean()) {
         return;
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      long gameTime = level.getGameTime();

      // Deadlines
      List<ReligionDeadlineQueue.Entry> due = DEADLINES.pollDue(gameTime, DEADLINE_BUDGET);
      TickPerfCounters.RELIGION_DEADLINES.addAndGet(due.size());
      for (ReligionDeadlineQueue.Entry e : due) {
         RitualCoordinator.handleDeadline(level, data, e);
      }

      // Drain plan results
      List<ReligionPlanResult> results = ReligionPlanner.get().drainResults(COMMIT_BUDGET);
      TickPerfCounters.RELIGION_QUEUE_DEPTH.set(ReligionPlanner.get().resultQueueDepth());
      for (ReligionPlanResult result : results) {
         if (applyResult(level, data, result)) {
            TickPerfCounters.RELIGION_COMMITS.incrementAndGet();
         }
      }

      // Advance ritual gathering/running presence checks (bounded)
      RitualCoordinator.tickActiveRituals(level, data, 2);

      // Submit dirty village plans (coalesced, one inflight each). Candidate budget, not only successes.
      int submits = 0;
      int candidates = 0;
      for (UUID villageId : ReligionPlanner.get().pollDirtyVillages(8)) {
         if (candidates >= 8 || submits >= 2) {
            break;
         }
         candidates++;
         Village village = findVillage(level, villageId);
         if (village == null) {
            ReligionPlanner.get().clearDirty(villageId);
            continue;
         }
         if (!INDEXES.hasIndex(villageId)) {
            INDEXES.rebuildChunk(village, INDEX_REBUILD_BUDGET);
            if (!INDEXES.hasIndex(villageId)) {
               continue;
            }
         }
         if (ReligionPlanner.get().canSubmit(villageId)) {
            ReligionSnapshot snap = buildSnapshot(level, data, village);
            ReligionPlanner.get().submit(snap);
            submits++;
         }
      }

      // Light travel prune once per day edge
      long day = level.getDayTime() / 24000L;
      if (gameTime % 24000L == 100L) {
         data.pruneTravelOlderThan(day, 30);
      }
   }

   public static void onVillageNightEdge(ServerLevel level, Village village) {
      if (!MillenaireServerConfig.SERVER.religionEnabled.getAsBoolean()) {
         return;
      }
      ReligionPlanner.get().markDirty(village.getId().uuid());
      DEADLINES.schedule(
         level.getGameTime() + FaithCalendarService.staggerSlot(village),
         ReligionDeadlineQueue.Kind.PLAN_VILLAGE,
         village.getId().uuid()
      );
   }

   public static void onVillageLoaded(ServerLevel level, Village village) {
      ensureFaithInstance(level, village);
      FaithService.ensureWorshipInfrastructure(level, village);
      INDEXES.invalidateVillage(village.getId().uuid());
      ReligionPlanner.get().markDirty(village.getId().uuid());
   }

   /** Delegates to {@link FaithService#ensureWorshipInfrastructure}. */
   public static void ensureWorshipInfrastructure(ServerLevel level, Village village) {
      FaithService.ensureWorshipInfrastructure(level, village);
   }

   public static void processCatchupAfterSleep(ServerLevel level, long skippedTicks) {
      int limit = MillenaireServerConfig.SERVER.religionCatchupLimit.getAsInt();
      if (limit <= 0) {
         return;
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      long now = level.getGameTime();
      int processed = 0;
      List<ReligionDeadlineQueue.Entry> due = DEADLINES.pollDue(now, Math.min(limit, CATCHUP_LIMIT_DEFAULT));
      for (ReligionDeadlineQueue.Entry e : due) {
         // Expire gatherings that never ran; do not grant rewards for meetings that did not happen.
         if (e.kind() == ReligionDeadlineQueue.Kind.RITUAL_START || e.kind() == ReligionDeadlineQueue.Kind.RITUAL_END) {
            RitualInstance ritual = data.getRitual(e.targetId());
            if (ritual != null && (ritual.state() == RitualState.PREPARING || ritual.state() == RitualState.GATHERING)) {
               RitualCoordinator.cancelRitual(level, data, ritual, RitualState.EXPIRED, true);
               processed++;
            } else if (ritual != null && ritual.state() == RitualState.RUNNING) {
               // Time jumped past end: interrupt without full success
               RitualCoordinator.cancelRitual(level, data, ritual, RitualState.INTERRUPTED, true);
               processed++;
            }
         }
         if (processed >= limit) {
            break;
         }
      }
   }

   public static FaithInstance ensureFaithInstance(ServerLevel level, Village village) {
      ReligionSavedData data = ReligionSavedData.get(level);
      if (village.getFaithInstanceId() != null) {
         FaithInstance existing = data.getFaith(village.getFaithInstanceId());
         if (existing != null) {
            return existing;
         }
      }
      ResourceLocation template = village.getFaithId();
      if (template == null) {
         FaithService.applyDefaultFaith(village);
         template = village.getFaithId();
      }
      if (template == null) {
         template = FaithRegistry.playerCustomId();
      }
      FaithInstance inst = new FaithInstance(UUID.randomUUID(), template, village.getCustomFaithName());
      if (FaithRegistry.get(template) == null) {
         inst.setOrphanTemplate(true);
      }
      data.putFaith(inst);
      village.setFaithInstanceId(inst.id());
      village.setFaithId(template);
      return inst;
   }

   public static FaithInstance startPlayerFaithInstance(ServerLevel level, Village village, String customName) {
      ReligionSavedData data = ReligionSavedData.get(level);
      String name = customName != null && !customName.isBlank() ? customName.trim() : "Faith";
      FaithInstance inst = new FaithInstance(UUID.randomUUID(), FaithRegistry.playerCustomId(), name);
      data.putFaith(inst);
      village.setFaithInstanceId(inst.id());
      village.setFaithId(FaithRegistry.playerCustomId());
      village.setCustomFaithName(name);
      FaithService.enqueuePreferredShrine(village);
      return inst;
   }

   /**
    * Player city founding chose Cenobites / De Orde. Sets faith, queues a shrine, and
    * seeds an Orde chapter even when the settlement is still below the normal order pop gate.
    */
   public static FaithInstance startOrdeFaithInstance(ServerLevel level, Village village) {
      ReligionSavedData data = ReligionSavedData.get(level);
      FaithDefinition def = FaithRegistry.get(FaithRegistry.ordeId());
      String display = def != null ? def.displayName() : "Cenobites";
      FaithInstance inst = new FaithInstance(UUID.randomUUID(), FaithRegistry.ordeId(), display);
      data.putFaith(inst);
      village.setFaithInstanceId(inst.id());
      village.setFaithId(FaithRegistry.ordeId());
      village.setCustomFaithName(null);
      FaithService.enqueuePreferredShrine(village);
      seedOrdeChapterForPlayerCity(level, data, village);
      data.setDirty();
      village.markDirty();
      return inst;
   }

   /** Force-seed Orde chapter for a player-founded city (skips min-pop gate). */
   public static boolean seedOrdeChapterForPlayerCity(ServerLevel level, ReligionSavedData data, Village village) {
      if (!MillenaireServerConfig.SERVER.ordersEnabled.getAsBoolean()) {
         return false;
      }
      if (data.getChapterForVillage(village.getId()) != null) {
         return false;
      }
      ResourceLocation orderTemplate = OrderRegistry.ordeId();
      OrderDefinition def = OrderRegistry.get(orderTemplate);
      if (def == null || !def.allowsCulture(village.getCultureId())) {
         return false;
      }
      OrderInstance order = new OrderInstance(UUID.randomUUID(), orderTemplate);
      data.putOrder(order);
      Chapter chapter = new Chapter(UUID.randomUUID(), order.id(), village.getId());
      chapter.setSecrecy(def.secrecyDefault());
      chapter.setStage(Chapter.Stage.SEEDED);
      BuildingInstance shrine = findShrineBuilding(village, def);
      if (shrine != null) {
         chapter.setShrineBuildingId(shrine.getId());
         chapter.setStage(Chapter.Stage.SHRINE_READY);
      }
      if (!data.putChapter(chapter)) {
         return false;
      }
      village.setChapterId(chapter.id());
      if (MillenaireServerConfig.SERVER.npcOrderConversionEnabled.getAsBoolean()) {
         VillagerRecord leader = pickClergyLeader(village, def);
         if (leader != null) {
            joinChapter(data, village, leader, OrderRole.LEADER, 40, level.getGameTime());
         }
      }
      village.markDirty();
      data.setDirty();
      LOGGER.info("[Religion] Seeded Orde chapter {} for player city {}", chapter.id(), village.getVillageName());
      return true;
   }

   @Nullable
   private static VillagerRecord pickClergyLeader(Village village, OrderDefinition def) {
      VillagerRecord best = null;
      int bestScore = -1;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            continue;
         }
         int score = 0;
         ResourceLocation typeId = record.getVillagerTypeId();
         if (typeId != null) {
            VillagerType type = ModCultures.getVillagerType(typeId);
            if (type != null) {
               for (String tag : def.clergyPreferenceTags()) {
                  if (type.tags().contains(tag)) {
                     score += 20;
                  }
               }
            }
         }
         if (score > bestScore) {
            bestScore = score;
            best = record;
         }
      }
      return best != null ? best : village.getVillagerRecords().values().stream()
         .filter(r -> !r.isKilled())
         .findFirst()
         .orElse(null);
   }

   public static boolean applyResult(ServerLevel level, ReligionSavedData data, ReligionPlanResult result) {
      if (result.worldSessionId() != data.worldSessionId()) {
         return false;
      }
      if (result.contentGeneration() != OrderRegistry.contentGeneration()) {
         return false;
      }
      Village village = findVillage(level, result.villageId());
      if (village == null) {
         return false;
      }
      return switch (result.kind()) {
         case NONE, FAILED -> false;
         case SEED_CHAPTER -> seedChapter(level, data, village, result);
         case SCHEDULE_RITUAL -> RitualCoordinator.scheduleFromPlan(level, data, village, result);
         case PROMOTE_LEADER -> promoteLeader(data, village, result);
         case EXPIRE_HINT -> false;
      };
   }

   private static boolean seedChapter(ServerLevel level, ReligionSavedData data, Village village, ReligionPlanResult result) {
      if (!MillenaireServerConfig.SERVER.ordersEnabled.getAsBoolean()) {
         return false;
      }
      if (data.getChapterForVillage(village.getId()) != null) {
         return false;
      }
      if (village.getVillagerRecords().size() < MillenaireServerConfig.SERVER.religionMinPopForOrder.getAsInt()) {
         return false;
      }
      ResourceLocation orderTemplate = result.orderTemplateId() != null ? result.orderTemplateId() : OrderRegistry.lastFlameId();
      OrderDefinition def = OrderRegistry.get(orderTemplate);
      if (def == null || !def.allowsCulture(village.getCultureId())) {
         return false;
      }
      OrderInstance order = new OrderInstance(UUID.randomUUID(), orderTemplate);
      data.putOrder(order);
      Chapter chapter = new Chapter(UUID.randomUUID(), order.id(), village.getId());
      chapter.setSecrecy(def.secrecyDefault());
      chapter.setStage(Chapter.Stage.SEEDED);
      // Prefer religious building as shrine
      BuildingInstance shrine = findShrineBuilding(village, def);
      if (shrine != null) {
         chapter.setShrineBuildingId(shrine.getId());
         chapter.setStage(Chapter.Stage.SHRINE_READY);
      }
      if (!data.putChapter(chapter)) {
         return false;
      }
      village.setChapterId(chapter.id());
      // Seed a clergy leader if conversion enabled
      if (MillenaireServerConfig.SERVER.npcOrderConversionEnabled.getAsBoolean() && result.suggestedLeaderUuid() != null) {
         VillagerRecord record = village.getVillagerRecord(result.suggestedLeaderUuid());
         if (record != null) {
            joinChapter(data, village, record, OrderRole.LEADER, 40, level.getGameTime());
         }
      }
      village.markDirty();
      data.setDirty();
      LOGGER.info("[Religion] Seeded chapter {} ({}) in village {}", chapter.id(), orderTemplate, village.getVillageName());
      return true;
   }

   private static boolean promoteLeader(ReligionSavedData data, Village village, ReligionPlanResult result) {
      if (result.suggestedLeaderUuid() == null) {
         return false;
      }
      Chapter chapter = data.getChapterForVillage(village.getId());
      if (chapter == null) {
         return false;
      }
      VillagerRecord record = village.getVillagerRecord(result.suggestedLeaderUuid());
      if (record == null) {
         return false;
      }
      Membership existing = data.getMembership(Membership.SubjectType.VILLAGER, record.getUuid());
      if (existing != null && existing.chapterId().equals(chapter.id())) {
         existing.setRole(OrderRole.LEADER);
         record.setOrderRole(OrderRole.LEADER);
         data.setDirty();
         village.markDirty();
         return true;
      }
      return false;
   }

   public static void joinChapter(
      ReligionSavedData data,
      Village village,
      VillagerRecord record,
      OrderRole role,
      int devotion,
      long gameTime
   ) {
      Chapter chapter = data.getChapterForVillage(village.getId());
      if (chapter == null) {
         return;
      }
      Membership m = new Membership(
         record.getUuid(), Membership.SubjectType.VILLAGER, chapter.id(), role, devotion, gameTime
      );
      if (!data.putMembership(m)) {
         return;
      }
      record.setOrderChapterId(chapter.id());
      record.setOrderRole(role);
      record.setDevotion(devotion);
      if (record.getFaithInstanceId() == null) {
         record.setFaithInstanceId(village.getFaithInstanceId());
      }
      village.markDirty();
   }

   public static void joinPlayerChapter(
      ReligionSavedData data, Chapter chapter, UUID playerId, OrderRole role, int devotion, long gameTime
   ) {
      Membership m = new Membership(playerId, Membership.SubjectType.PLAYER, chapter.id(), role, devotion, gameTime);
      data.putMembership(m);
   }

   public static void leaveChapter(ReligionSavedData data, Village village, VillagerRecord record) {
      data.removeMembership(Membership.SubjectType.VILLAGER, record.getUuid());
      record.setOrderChapterId(null);
      record.setOrderRole(null);
      record.setDevotion(0);
      village.markDirty();
   }

   @Nullable
   private static BuildingInstance findShrineBuilding(Village village, OrderDefinition def) {
      for (BuildingInstance b : village.getBuildings()) {
         if (!b.isOperational()) {
            continue;
         }
         var planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
         if (planSet == null) {
            continue;
         }
         for (String tag : planSet.tags()) {
            if (def.buildingTags().contains(tag)
               || "praying".equals(tag)
               || "religious".equals(tag)
               || "pilgrimsite".equals(tag)) {
               return b;
            }
         }
      }
      return null;
   }

   public static ReligionSnapshot buildSnapshot(ServerLevel level, ReligionSavedData data, Village village) {
      ensureFaithInstance(level, village);
      Chapter chapter = village.getChapterId() != null
         ? data.getChapter(village.getChapterId())
         : data.getChapterForVillage(village.getId());
      OrderInstance order = chapter != null ? data.getOrder(chapter.orderInstanceId()) : null;
      BlockPos shrinePos = null;
      if (chapter != null && chapter.shrineBuildingId() != null) {
         BuildingInstance b = village.getBuilding(chapter.shrineBuildingId());
         if (b != null) {
            shrinePos = b.getOrigin();
         }
      }
      if (shrinePos == null) {
         shrinePos = FaithCalendarService.resolveChurchCenter(village);
      }
      List<ReligionSnapshot.MemberSnap> members = new ArrayList<>();
      if (chapter != null) {
         for (Membership m : data.membershipsForChapter(chapter.id())) {
            if (m.subjectType() != Membership.SubjectType.VILLAGER) {
               continue;
            }
            if (members.size() >= 32) {
               break;
            }
            VillagerRecord rec = village.getVillagerRecord(m.subjectId());
            boolean clergy = rec != null && isClergyPreferred(rec);
            members.add(new ReligionSnapshot.MemberSnap(m.subjectId(), m.role(), m.devotion(), clergy));
         }
      }
      FaithInstance faith = data.getFaith(village.getFaithInstanceId());
      long seed = level.getSeed() ^ village.getId().uuid().getMostSignificantBits();
      OrderDefinition orderDef = order != null ? order.definition() : OrderRegistry.get(OrderRegistry.lastFlameId());
      double chance = MillenaireServerConfig.SERVER.religionOrderSpawnChance.get();
      int minPop = MillenaireServerConfig.SERVER.religionMinPopForOrder.getAsInt();
      if (orderDef != null) {
         chance = Math.min(chance, orderDef.spawnChance());
         minPop = Math.max(minPop, orderDef.minVillagePop());
      }
      boolean hasRitual = chapter != null && data.findActiveRitualForChapter(chapter.id()) != null;
      int relBuildings = INDEXES.getReligiousBuildings(village.getId().uuid()).size();
      if (relBuildings == 0 && !INDEXES.hasIndex(village.getId().uuid())) {
         // Cheap estimate without full rebuild
         relBuildings = estimateReligiousBuildings(village);
      }
      return new ReligionSnapshot(
         village.getId().uuid(),
         village.getFaithInstanceId(),
         faith != null ? faith.templateId() : village.getFaithId(),
         chapter != null ? chapter.id() : null,
         order != null ? order.templateId() : null,
         shrinePos,
         members,
         village.getVillagerRecords().size(),
         relBuildings,
         level.getGameTime(),
         level.getDayTime(),
         level.getDayTime() / 24000L,
         data.worldSessionId(),
         OrderRegistry.contentGeneration(),
         seed,
         MillenaireServerConfig.SERVER.religionEnabled.getAsBoolean(),
         MillenaireServerConfig.SERVER.ordersEnabled.getAsBoolean(),
         MillenaireServerConfig.SERVER.npcOrderConversionEnabled.getAsBoolean(),
         chance,
         minPop,
         chapter != null ? chapter.stage() : Chapter.Stage.SEEDED,
         chapter != null ? chapter.secrecy() : 70,
         chapter != null ? chapter.cohesion() : 50,
         village.getReligionTension(),
         hasRitual
      );
   }

   private static int estimateReligiousBuildings(Village village) {
      int n = 0;
      FaithDefinition def = FaithService.resolve(village);
      for (BuildingInstance b : village.getBuildings()) {
         if (!b.isOperational()) {
            continue;
         }
         var planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
         if (planSet == null) {
            continue;
         }
         for (String tag : planSet.tags()) {
            if ((def != null && def.churchBuildingTags().contains(tag))
               || "praying".equals(tag)
               || "religious".equals(tag)) {
               n++;
               break;
            }
         }
         if (n >= 3) {
            break;
         }
      }
      return n;
   }

   private static boolean isClergyPreferred(VillagerRecord record) {
      VillagerType type = ModCultures.getVillagerType(record.getVillagerTypeId());
      if (type == null) {
         return false;
      }
      for (String tag : type.tags()) {
         if ("religious".equals(tag) || "priest".equals(tag) || "clergy".equals(tag)) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   private static Village findVillage(ServerLevel level, UUID villageId) {
      return org.millenaire.village.VillageSavedData.get(level).getVillageManager().getVillage(
         new org.millenaire.village.VillageId(villageId)
      );
   }

   public static void disableReligionCleanup(ServerLevel level) {
      ReligionSavedData data = ReligionSavedData.get(level);
      for (RitualInstance ritual : List.copyOf(data.allActiveRituals())) {
         RitualCoordinator.cancelRitual(level, data, ritual, RitualState.CANCELLED, true);
      }
   }
}
