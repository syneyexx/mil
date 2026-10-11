package org.millenaire.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingId;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

public final class NpcGossipScheduler {
   private NpcGossipScheduler() {
   }

   public static void tick(ServerLevel level) {
      NpcMindSavedData data = NpcMindSavedData.get(level);
      long currentDay = level.getDayTime() / 24000L;
      if (currentDay <= data.getLastGossipDay()) {
         return;
      }
      if (!runDay(level, data, currentDay)) {
         // Budget deferred — retry on a later tick the same day.
         return;
      }
      data.setLastGossipDay(currentDay);
   }

   public static void forceAdvanceDay(ServerLevel level) {
      NpcMindSavedData data = NpcMindSavedData.get(level);
      long forcedDay = Math.max(data.getLastGossipDay() + 1L, level.getDayTime() / 24000L + 1L);
      runDay(level, data, forcedDay);
      data.setLastGossipDay(forcedDay);
   }

   private static boolean runDay(ServerLevel level, NpcMindSavedData data, long currentDay) {
      // Day job is expensive — bail if the AI_SUPPORT budget is already spent.
      if (!org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
         org.millenaire.perf.tick.ServerTickGovernor.noteDefer(org.millenaire.perf.tick.TickPhase.AI_SUPPORT);
         return false;
      }
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      Map<ResourceLocation, List<NpcRumor>> byCulture = new HashMap<>();
      for (NpcRumor rumor : data.getRumors().values()) {
         if (rumor.isActive()) {
            byCulture.computeIfAbsent(rumor.cultureId(), k -> new ArrayList<>()).add(rumor);
         }
      }

      for (Map.Entry<ResourceLocation, List<NpcRumor>> entry : byCulture.entrySet()) {
         for (NpcRumor rumor : entry.getValue()) {
            if (!NpcGossipRules.maySpreadToday(currentDay, rumor.createdDay())) {
               continue;
            }
            spreadSameVillage(level, manager, data, rumor, currentDay);
            spreadCrossVillage(level, manager, data, rumor, currentDay);
         }
      }

      expireAndPrune(data, currentDay);
      data.markChanged();
      return true;
   }

   private static void spreadSameVillage(ServerLevel level, VillageManager manager, NpcMindSavedData data, NpcRumor rumor, long currentDay) {
      Map<VillageId, List<UUID>> knowersByVillage = new HashMap<>();
      for (Map.Entry<UUID, NpcMind> entry : data.getMinds().entrySet()) {
         NpcRumorKnowledge k = entry.getValue().getKnowledge(rumor.id());
         if (k == null) {
            continue;
         }
         Village village = findVillageForVillager(manager, entry.getKey());
         if (village == null || !village.getCultureId().equals(rumor.cultureId())) {
            continue;
         }
         VillagerRecord record = village.getVillagerRecord(entry.getKey());
         if (record == null || record.isKilled() || record.isAwayRaiding()) {
            continue;
         }
         if (isChildRecord(record) || !foreignMerchantAllowed(record, village, rumor)) {
            continue;
         }
         knowersByVillage.computeIfAbsent(village.getId(), id -> new ArrayList<>()).add(entry.getKey());
      }

      for (Map.Entry<VillageId, List<UUID>> entry : knowersByVillage.entrySet()) {
         Village village = manager.getVillage(entry.getKey());
         if (village == null) {
            continue;
         }
         List<UUID> knowers = NpcGossipRules.sortedUuids(entry.getValue());
         for (UUID knowerId : knowers) {
            VillagerRecord knower = village.getVillagerRecord(knowerId);
            if (knower == null) {
               continue;
            }
            NpcMind knowerMind = data.getOrCreateMind(knowerId);
            NpcRumorKnowledge knowerKnowledge = knowerMind.getKnowledge(rumor.id());
            if (knowerKnowledge == null) {
               continue;
            }
            UUID targetId = pickSameVillageTarget(village, data, rumor, knower);
            if (targetId == null) {
               continue;
            }
            VillagerRecord target = village.getVillagerRecord(targetId);
            boolean sameHome = knower.getHomeBuilding() != null
               && target != null
               && knower.getHomeBuilding().equals(target.getHomeBuilding());
            boolean influential = isInfluential(knower);
            double chance = NpcGossipRules.sameVillageChance(sameHome, influential);
            if (NpcGossipRules.roll(rumor.id(), currentDay, knowerId, chance)) {
               grantKnowledge(data, rumor, targetId, knowerId, knowerKnowledge, currentDay, level.getGameTime());
            }
            break;
         }
      }
   }

   private static void spreadCrossVillage(ServerLevel level, VillageManager manager, NpcMindSavedData data, NpcRumor rumor, long currentDay) {
      int radius = MillenaireServerConfig.SERVER.backgroundRadius.getAsInt();
      if (radius <= 0) {
         return;
      }
      Village origin = manager.getVillage(rumor.originVillage());
      if (origin == null) {
         return;
      }
      BlockPos originCenter = origin.getCenter();
      List<Village> candidates = new ArrayList<>();
      for (Village village : manager.getAllVillages()) {
         if (village.getId().equals(origin.getId())) {
            continue;
         }
         if (!village.getCultureId().equals(rumor.cultureId())) {
            continue;
         }
         double distSq = village.getCenter().distSqr(originCenter);
         if (distSq > (double)radius * (double)radius) {
            continue;
         }
         candidates.add(village);
      }
      if (candidates.isEmpty()) {
         return;
      }
      candidates.sort(Comparator.comparingDouble(v -> v.getCenter().distSqr(originCenter)));

      Village targetVillage = null;
      for (Village candidate : candidates) {
         if (!villageKnowsRumor(candidate, data, rumor.id())) {
            targetVillage = candidate;
            break;
         }
      }
      if (targetVillage == null) {
         targetVillage = candidates.get(0);
      }

      List<UUID> knowers = new ArrayList<>();
      for (Map.Entry<UUID, NpcMind> entry : data.getMinds().entrySet()) {
         if (entry.getValue().getKnowledge(rumor.id()) == null) {
            continue;
         }
         Village village = findVillageForVillager(manager, entry.getKey());
         if (village == null) {
            continue;
         }
         VillagerRecord record = village.getVillagerRecord(entry.getKey());
         if (record == null || record.isKilled() || isChildRecord(record)) {
            continue;
         }
         knowers.add(entry.getKey());
      }
      if (knowers.isEmpty()) {
         return;
      }
      knowers = NpcGossipRules.sortedUuids(knowers);
      Random random = new Random(rumor.id() * 31L + currentDay * 131L + knowers.get(0).hashCode());
      UUID knowerId = knowers.get(random.nextInt(knowers.size()));
      Village knowerVillage = findVillageForVillager(manager, knowerId);
      VillagerRecord knower = knowerVillage != null ? knowerVillage.getVillagerRecord(knowerId) : null;
      if (knower == null) {
         return;
      }
      UUID listenerId = pickCrossVillageListener(targetVillage, data, rumor);
      if (listenerId == null) {
         return;
      }
      boolean related = origin.getRelations().containsKey(targetVillage.getId()) || targetVillage.getRelations().containsKey(origin.getId());
      boolean influential = isInfluential(knower);
      double chance = NpcGossipRules.crossVillageChance(related, influential);
      if (!NpcGossipRules.roll(rumor.id(), currentDay, knowerId, chance)) {
         return;
      }
      NpcRumorKnowledge knowerKnowledge = data.getOrCreateMind(knowerId).getKnowledge(rumor.id());
      if (knowerKnowledge == null) {
         return;
      }
      grantKnowledge(data, rumor, listenerId, knowerId, knowerKnowledge, currentDay, level.getGameTime());
   }

   @Nullable
   private static UUID pickSameVillageTarget(Village village, NpcMindSavedData data, NpcRumor rumor, VillagerRecord knower) {
      List<UUID> targets = new ArrayList<>();
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.getUuid().equals(knower.getUuid())) {
            continue;
         }
         if (record.isKilled() || record.isAwayRaiding() || isChildRecord(record)) {
            continue;
         }
         if (!foreignMerchantAllowed(record, village, rumor)) {
            continue;
         }
         NpcMind mind = data.getMind(record.getUuid());
         if (mind != null && mind.knowsRumor(rumor.id())) {
            continue;
         }
         if (rumor.supersededBy() == 0L) {
            // also skip if they already know a superseder — handled when granting
         }
         // Skip if they know any superseding rumor of this one? Only when learning superseder.
         // Spec: first target that doesn't know this rumor and doesn't know the superseder.
         if (rumor.supersededBy() != 0L) {
            continue;
         }
         boolean knowsSuperseder = false;
         for (NpcRumor other : data.getRumors().values()) {
            if (other.id() == rumor.supersededBy() && mind != null && mind.knowsRumor(other.id())) {
               knowsSuperseder = true;
               break;
            }
         }
         // For active rumor supersededBy is 0; check if any active rumor supersedes this and is known
         for (NpcRumor other : data.activeRumorsForCulture(rumor.cultureId())) {
            if (other.id() != rumor.id() && findsSupersede(data, rumor.id(), other.id())) {
               NpcMind m = data.getMind(record.getUuid());
               if (m != null && m.knowsRumor(other.id())) {
                  knowsSuperseder = true;
                  break;
               }
            }
         }
         if (knowsSuperseder) {
            continue;
         }
         targets.add(record.getUuid());
      }
      targets = NpcGossipRules.sortedUuids(targets);
      return targets.isEmpty() ? null : targets.get(0);
   }

   private static boolean findsSupersede(NpcMindSavedData data, long oldId, long maybeNewId) {
      NpcRumor maybe = data.getRumor(maybeNewId);
      NpcRumor old = data.getRumor(oldId);
      return maybe != null && old != null && old.supersededBy() == maybeNewId;
   }

   @Nullable
   private static UUID pickCrossVillageListener(Village village, NpcMindSavedData data, NpcRumor rumor) {
      List<VillagerRecord> adults = new ArrayList<>();
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled() || isChildRecord(record)) {
            continue;
         }
         if (!foreignMerchantAllowed(record, village, rumor)) {
            continue;
         }
         NpcMind mind = data.getMind(record.getUuid());
         if (mind != null && mind.knowsRumor(rumor.id())) {
            continue;
         }
         adults.add(record);
      }
      adults.sort(Comparator.comparing(VillagerRecord::getUuid));
      for (VillagerRecord record : adults) {
         if (isInfluential(record)) {
            return record.getUuid();
         }
      }
      return adults.isEmpty() ? null : adults.get(0).getUuid();
   }

   private static void grantKnowledge(
      NpcMindSavedData data,
      NpcRumor rumor,
      UUID listenerId,
      UUID tellerId,
      NpcRumorKnowledge tellerKnowledge,
      long currentDay,
      long gameTime
   ) {
      NpcMind mind = data.getOrCreateMind(listenerId);
      // Remove knowledge of rumors superseded by this one
      for (NpcRumor other : new ArrayList<>(data.getRumors().values())) {
         if (other.supersededBy() == rumor.id()) {
            mind.removeKnowledge(other.id());
         }
      }
      mind.addOrRefreshKnowledge(
         new NpcRumorKnowledge(
            rumor.id(),
            listenerId,
            tellerId,
            NpcGossipRules.nextHop(tellerKnowledge.hop()),
            NpcGossipRules.nextConfidence(tellerKnowledge.confidence()),
            gameTime,
            currentDay
         )
      );
      data.markChanged();
   }

   private static boolean villageKnowsRumor(Village village, NpcMindSavedData data, long rumorId) {
      for (UUID id : village.getVillagerRecords().keySet()) {
         NpcMind mind = data.getMind(id);
         if (mind != null && mind.knowsRumor(rumorId)) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   private static Village findVillageForVillager(VillageManager manager, UUID villagerId) {
      for (Village village : manager.getAllVillages()) {
         if (village.getVillagerRecord(villagerId) != null) {
            return village;
         }
      }
      return null;
   }

   private static boolean isChildRecord(VillagerRecord record) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      return type != null && type.isChild();
   }

   private static boolean isInfluential(VillagerRecord record) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      return type != null && NpcGossipRules.isInfluential(type.hasTag("chief"), type.hasTag("seller"));
   }

   private static boolean foreignMerchantAllowed(VillagerRecord record, Village village, NpcRumor rumor) {
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      if (type == null || !type.hasTag("foreignmerchant")) {
         return true;
      }
      return village.getCultureId().equals(rumor.cultureId()) && type.culture().equals(rumor.cultureId());
   }

   private static void expireAndPrune(NpcMindSavedData data, long currentDay) {
      int expiryDays = MillenaireServerConfig.SERVER.npcChatGossipExpiryDays.getAsInt();
      List<Long> removeRumors = new ArrayList<>();
      for (NpcRumor rumor : new ArrayList<>(data.getRumors().values())) {
         long newest = rumor.createdDay();
         for (NpcRumorKnowledge k : data.allKnowledge()) {
            if (k.rumorId() == rumor.id() && k.learnedDay() > newest) {
               newest = k.learnedDay();
            }
         }
         if (rumor.isActive() && NpcGossipRules.shouldExpire(currentDay, rumor.createdDay(), expiryDays, newest)) {
            data.replaceRumor(rumor.withExpired(true));
         }
         NpcRumor current = data.getRumor(rumor.id());
         if (current != null && current.expired() && currentDay - current.createdDay() > expiryDays + 30L) {
            removeRumors.add(current.id());
         }
      }
      for (Long id : removeRumors) {
         data.removeRumor(id);
      }
      long minLearnDay = currentDay - 30L;
      for (NpcMind mind : data.getMinds().values()) {
         mind.removeKnowledgeOlderThan(minLearnDay, rumorId -> {
            NpcRumor rumor = data.getRumor(rumorId);
            return rumor == null || rumor.expired() || rumor.supersededBy() != 0L;
         });
      }
   }
}
