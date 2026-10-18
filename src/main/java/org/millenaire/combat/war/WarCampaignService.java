package org.millenaire.combat.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.combat.raid.RaidSpawnLocator;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageTreasuryService;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Server-thread war campaign orchestration: declare/start, schedule waves, spawn via RaidManager /
 * budgeted clone materialize, defeat on player death, heavy loot + treasury steal.
 */
public final class WarCampaignService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int COMMIT_BUDGET = 4;
   private static final int MAX_CLONE_SPAWNS_PER_TICK = 2;
   private static boolean waveSpawnedThisTick;

   private WarCampaignService() {
   }

   public static void onServerStarted(ServerLevel level) {
      WarPlanner.get().onServerStarted(1);
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      data.bumpSession();
      WarBuildingDamageService.clearQueue();
      LOGGER.info("[War] Session {} started", data.worldSessionId());
   }

   public static void onServerStopping() {
      WarPlanner.get().shutdown();
      WarBuildingDamageService.clearQueue();
   }

   public static void tick(ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return;
      }
      waveSpawnedThisTick = false;
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      long gameTime = level.getGameTime();

      List<WarPlanResult> results = WarPlanner.get().drainResults(COMMIT_BUDGET);
      for (WarPlanResult result : results) {
         if (applyResult(level, data, result)) {
            TickPerfCounters.WAR_COMMITS.incrementAndGet();
         }
      }

      WarBuildingDamageService.tick(level);

      int cloneBudget = MAX_CLONE_SPAWNS_PER_TICK;
      for (WarCampaign campaign : data.openCampaigns()) {
         if (campaign.phase() == WarCampaign.Phase.WAVE_FIGHT && campaign.frontVillageId() != null) {
            Village front = findVillage(level, campaign.frontVillageId());
            if (front != null && front.isUnderAttack()) {
               cloneBudget -= materializeClonesBudgeted(front, level, gameTime, cloneBudget);
            }
         }
         if (gameTime % 10L == Math.floorMod(campaign.id().hashCode(), 10L)) {
            checkWaveBattle(level, data, campaign);
         }
         advancePhases(level, data, campaign, gameTime);
      }

      int submits = 0;
      int candidates = 0;
      for (UUID campaignId : WarPlanner.get().pollDirtyCampaigns(8)) {
         if (candidates >= 8 || submits >= 2) {
            break;
         }
         candidates++;
         WarCampaign campaign = data.getCampaign(campaignId);
         if (campaign == null || !campaign.isOpen()) {
            WarPlanner.get().clearDirty(campaignId);
            continue;
         }
         if (WarPlanner.get().canSubmit(campaignId)) {
            WarPlanner.get().submit(buildSnapshot(level, data, campaign));
            submits++;
         }
      }
   }

   public static void onPlayerDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.warPlayerDeathIsDefeat.getAsBoolean()) {
         return;
      }
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      for (WarCampaign campaign : data.openCampaigns()) {
         if (campaign.phase() != WarCampaign.Phase.WAVE_FIGHT || !campaign.playerWar()) {
            continue;
         }
         Village front = campaign.frontVillageId() != null ? findVillage(level, campaign.frontVillageId()) : null;
         if (front == null || !front.isPlayerControlled() || !player.getUUID().equals(front.getOwnerUUID())) {
            continue;
         }
         if (!isNearFront(player.blockPosition(), front)) {
            continue;
         }
         LOGGER.info("[War] Player death counts as defeat for campaign {}", campaign.id());
         resolveDefeat(level, data, campaign, true);
         break;
      }
   }

   /** Start or reuse a campaign for a culture pair (player or AI war). */
   public static WarCampaign declareOrGet(ServerLevel level, ResourceLocation sideA, ResourceLocation sideB, boolean playerWar) {
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      WarCampaign existing = data.findOpenBetween(sideA, sideB);
      if (existing != null) {
         return existing;
      }
      int waves = MillenaireServerConfig.SERVER.warWavesPerCampaign.getAsInt();
      WarCampaign campaign = new WarCampaign(UUID.randomUUID(), sideA, sideB, waves, playerWar, level.getGameTime());
      campaign.setPhase(WarCampaign.Phase.WARNING);
      campaign.setNextWaveGameTime(level.getGameTime() + 200L);
      if (!data.putCampaign(campaign)) {
         return null;
      }
      pickInitialFront(level, campaign);
      WarPlanner.get().markDirty(campaign.id());
      broadcastNearCultures(
         level,
         sideA,
         sideB,
         Component.translatable("message.millenaire.war.campaign_declared", sideA.getPath(), sideB.getPath(), waves)
      );
      LOGGER.info("[War] Campaign {} declared {} vs {} (playerWar={})", campaign.id(), sideA, sideB, playerWar);
      VillageSavedData.get(level).setDirty();
      return campaign;
   }

   /** Culture-war raid scheduling: start/advance a campaign wave instead of a lone RaidManager plan when enabled. */
   public static boolean tryScheduleCampaignWave(ServerLevel level, Village attacker, Village target) {
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return false;
      }
      if (attacker == null || target == null || waveSpawnedThisTick) {
         return false;
      }
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      WarCampaign campaign = data.findOpenBetween(attacker.getCultureId(), target.getCultureId());
      if (campaign == null) {
         campaign = declareOrGet(level, attacker.getCultureId(), target.getCultureId(), false);
      }
      if (campaign == null || !campaign.isOpen()) {
         return false;
      }
      if (campaign.phase() == WarCampaign.Phase.WAVE_FIGHT) {
         return true;
      }
      if (level.getGameTime() < campaign.nextWaveGameTime() && campaign.phase() != WarCampaign.Phase.WARNING) {
         WarPlanner.get().markDirty(campaign.id());
         return true;
      }
      return startWave(level, data, campaign, attacker, target);
   }

   public static boolean startWave(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign, Village attacker, Village target) {
      if (waveSpawnedThisTick || attacker == null || target == null) {
         return false;
      }
      if (target.isUnderAttack() || attacker.getRaidTarget() != null && attacker.getRaidStart() > 0L) {
         return false;
      }
      campaign.setAttackerVillageId(attacker.getId().uuid());
      campaign.setDefenderVillageId(target.getId().uuid());
      campaign.setFrontVillageId(target.getId().uuid());
      campaign.setWaveIndex(campaign.waveIndex() + 1);
      UUID waveId = UUID.randomUUID();
      WarWaveInstance wave = new WarWaveInstance(
         waveId, campaign.id(), attacker.getId().uuid(), target.getId().uuid(), campaign.waveIndex(), level.getGameTime()
      );
      data.putWave(wave);
      campaign.setActiveWaveId(waveId);
      campaign.setPhase(WarCampaign.Phase.WAVE_FIGHT);
      campaign.setSabotageTargets(pickSabotageTargets(target));
      data.setDirty();

      if (campaign.playerWar() && attacker.isPlayerControlled() && attacker.getOwnerUUID() != null) {
         ServerPlayer owner = level.getServer().getPlayerList().getPlayer(attacker.getOwnerUUID());
         if (owner != null) {
            WarMusterService.musterForOffense(level, owner, attacker, campaign);
         }
      }
      RaidManager.planRaid(attacker, target, level);
      RaidManager.startRaidForced(attacker, target, level);
      waveSpawnedThisTick = true;

      broadcastNear(
         level,
         target.getCenter(),
         Component.translatable(
            "message.millenaire.war.wave_started",
            campaign.waveIndex(),
            campaign.wavesTotal(),
            nameOf(attacker),
            nameOf(target)
         )
      );
      WarPlanner.get().markDirty(campaign.id());
      LOGGER.info("[War] Wave {}/{} started: {} -> {}", campaign.waveIndex(), campaign.wavesTotal(), nameOf(attacker), nameOf(target));
      return true;
   }

   private static void advancePhases(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign, long gameTime) {
      switch (campaign.phase()) {
         case WARNING -> {
            if (gameTime >= campaign.nextWaveGameTime()) {
               campaign.setPhase(WarCampaign.Phase.ACTIVE);
               data.setDirty();
               WarPlanner.get().markDirty(campaign.id());
            }
         }
         case ACTIVE, ARMISTICE -> {
            if (campaign.waveIndex() >= campaign.wavesTotal()) {
               endCampaign(level, data, campaign);
               return;
            }
            if (gameTime >= campaign.nextWaveGameTime() && !waveSpawnedThisTick) {
               Village attacker = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
               Village defender = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
               if (attacker == null || defender == null) {
                  pickInitialFront(level, campaign);
                  attacker = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
                  defender = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
               }
               if (attacker != null && defender != null) {
                  startWave(level, data, campaign, attacker, defender);
               } else {
                  WarPlanner.get().markDirty(campaign.id());
               }
            }
         }
         case WAVE_FIGHT -> {
            // Resolved via checkWaveBattle / raid end / player death.
         }
         case ENDED -> {
         }
      }
   }

   private static void checkWaveBattle(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign) {
      if (campaign.phase() != WarCampaign.Phase.WAVE_FIGHT) {
         return;
      }
      Village defender = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
      Village attacker = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
      if (defender == null) {
         resolveVictory(level, data, campaign, true);
         return;
      }
      // Wave ends when the underlying raid clears (no under-attack / no raid target).
      if (!defender.isUnderAttack() && (attacker == null || attacker.getRaidTarget() == null || attacker.getRaidStart() == 0L)) {
         // Infer winner from last raid history if present; default defender held.
         boolean attackersWon = inferAttackersWon(attacker, defender);
         if (attackersWon) {
            resolveVictory(level, data, campaign, false);
         } else {
            resolveDefeat(level, data, campaign, false);
         }
      }
   }

   private static boolean inferAttackersWon(@Nullable Village attacker, Village defender) {
      if (attacker == null) {
         return false;
      }
      List<String> performed = attacker.getRaidsPerformed();
      if (performed == null || performed.isEmpty()) {
         return false;
      }
      String last = performed.get(performed.size() - 1);
      return last.startsWith("success;");
   }

   private static void resolveVictory(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign, boolean clearOnly) {
      WarWaveInstance wave = campaign.activeWaveId() != null ? data.getWave(campaign.activeWaveId()) : null;
      if (wave != null && !wave.resolved()) {
         wave.resolve(true);
      }
      campaign.addScoreA(10);
      applyHeavyLoot(level, campaign, true);
      finishWave(level, data, campaign);
      if (!clearOnly) {
         broadcastNearCultures(
            level,
            campaign.sideA(),
            campaign.sideB(),
            Component.translatable("message.millenaire.war.wave_victory", campaign.waveIndex(), campaign.wavesTotal())
         );
      }
   }

   private static void resolveDefeat(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign, boolean playerDeath) {
      // Player death at the front means the attackers win that wave.
      boolean attackersWon = playerDeath;
      WarWaveInstance wave = campaign.activeWaveId() != null ? data.getWave(campaign.activeWaveId()) : null;
      if (wave != null && !wave.resolved()) {
         wave.resolve(attackersWon);
      }
      if (attackersWon) {
         campaign.addScoreA(10);
      } else {
         campaign.addScoreB(10);
      }
      applyHeavyLoot(level, campaign, attackersWon);
      Village defender = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
      Village attacker = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
      if (defender != null && attacker != null && playerDeath) {
         RaidManager.abortRaidForAttacker(attacker, level);
         defender.setUnderAttack(false);
      }
      finishWave(level, data, campaign);
      broadcastNearCultures(
         level,
         campaign.sideA(),
         campaign.sideB(),
         Component.translatable(
            playerDeath ? "message.millenaire.war.player_defeat" : "message.millenaire.war.wave_defeat",
            campaign.waveIndex(),
            campaign.wavesTotal()
         )
      );
   }

   private static void finishWave(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign) {
      if (campaign.activeWaveId() != null) {
         data.removeWave(campaign.activeWaveId());
         campaign.setActiveWaveId(null);
      }
      campaign.sabotageTargets().clear();
      releaseMusterForCampaign(level, campaign);
      long interval = MillenaireServerConfig.SERVER.warWaveIntervalTicks.getAsInt();
      campaign.setNextWaveGameTime(level.getGameTime() + interval);
      if (campaign.waveIndex() >= campaign.wavesTotal()) {
         endCampaign(level, data, campaign);
      } else {
         campaign.setPhase(WarCampaign.Phase.ARMISTICE);
         data.setDirty();
         WarPlanner.get().markDirty(campaign.id());
      }
   }

   private static void endCampaign(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign) {
      campaign.setPhase(WarCampaign.Phase.ENDED);
      campaign.setActiveWaveId(null);
      releaseMusterForCampaign(level, campaign);
      data.clearWavesForCampaign(campaign.id());
      data.setDirty();
      broadcastNearCultures(
         level,
         campaign.sideA(),
         campaign.sideB(),
         Component.translatable(
            "message.millenaire.war.campaign_ended",
            campaign.sideA().getPath(),
            campaign.sideB().getPath(),
            campaign.warScoreA(),
            campaign.warScoreB()
         )
      );
      LOGGER.info("[War] Campaign {} ended scores {}-{}", campaign.id(), campaign.warScoreA(), campaign.warScoreB());
   }

   private static void applyHeavyLoot(ServerLevel level, WarCampaign campaign, boolean attackersWon) {
      Village winner;
      Village loser;
      if (attackersWon) {
         winner = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
         loser = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
      } else {
         winner = campaign.defenderVillageId() != null ? findVillage(level, campaign.defenderVillageId()) : null;
         loser = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
         // On defender hold, still punish the front if player-death defeat path already ran loot against defender.
         if (loser == null) {
            loser = campaign.frontVillageId() != null ? findVillage(level, campaign.frontVillageId()) : null;
            winner = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : winner;
         }
      }
      // Player-death / attacker win: steal from front (defender).
      Village stealFrom = campaign.frontVillageId() != null ? findVillage(level, campaign.frontVillageId()) : loser;
      Village stealTo = attackersWon
         ? (campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : winner)
         : winner;
      if (stealFrom == null) {
         return;
      }
      int balance = VillageTreasuryService.balance(level, stealFrom);
      int steal = Math.min(balance, Math.max(50, balance / 2));
      if (steal > 0 && VillageTreasuryService.withdraw(level, stealFrom, steal, VillageTreasuryService.REASON_WAR_STOLEN)) {
         if (stealTo != null) {
            VillageTreasuryService.deposit(level, stealTo, steal, VillageTreasuryService.REASON_WAR_LOOT);
         }
      }
      // Extra chest loot via RaidManager path when attacker still has a raid context is already applied;
      // for war defeat we also bump war damage on a few buildings.
      if (MillenaireServerConfig.SERVER.warBuildingDamageEnabled.getAsBoolean()) {
         int n = 0;
         for (BuildingInstance b : stealFrom.getBuildings()) {
            if (n >= 3) {
               break;
            }
            BuildingInstance th = stealFrom.getTownhall();
            if (th != null && th.getId().equals(b.getId())) {
               continue;
            }
            WarBuildingDamageService.requestDamage(stealFrom, b, campaign.id(), campaign.activeWaveId(), 8);
            n++;
         }
      }
   }

   private static boolean applyResult(ServerLevel level, WarCampaignSavedData data, WarPlanResult result) {
      if (result == null || result.kind() == WarPlanResult.Kind.NONE || result.kind() == WarPlanResult.Kind.FAILED) {
         return false;
      }
      if (result.worldSessionId() != data.worldSessionId()) {
         return false;
      }
      WarCampaign campaign = data.getCampaign(result.campaignId());
      if (campaign == null || !campaign.isOpen()) {
         return false;
      }
      switch (result.kind()) {
         case PICK_FRONT -> {
            if (result.frontVillageId() != null) {
               campaign.setFrontVillageId(result.frontVillageId());
            }
            if (result.attackerVillageId() != null) {
               campaign.setAttackerVillageId(result.attackerVillageId());
            }
            if (result.defenderVillageId() != null) {
               campaign.setDefenderVillageId(result.defenderVillageId());
            }
            if (result.nextWaveGameTime() > 0L) {
               campaign.setNextWaveGameTime(result.nextWaveGameTime());
            }
            if (campaign.phase() == WarCampaign.Phase.WARNING) {
               campaign.setPhase(WarCampaign.Phase.ACTIVE);
            }
            data.setDirty();
            return true;
         }
         case SCHEDULE_WAVE -> {
            if (!result.sabotageTargets().isEmpty()) {
               campaign.setSabotageTargets(result.sabotageTargets());
            }
            Village attacker = result.attackerVillageId() != null ? findVillage(level, result.attackerVillageId()) : null;
            Village defender = result.defenderVillageId() != null ? findVillage(level, result.defenderVillageId()) : null;
            if (attacker != null && defender != null && !waveSpawnedThisTick) {
               return startWave(level, data, campaign, attacker, defender);
            }
            if (result.nextWaveGameTime() > 0L) {
               campaign.setNextWaveGameTime(result.nextWaveGameTime());
               data.setDirty();
            }
            return true;
         }
         case SET_SABOTAGE -> {
            campaign.setSabotageTargets(result.sabotageTargets());
            data.setDirty();
            return true;
         }
         case END_CAMPAIGN -> {
            endCampaign(level, data, campaign);
            return true;
         }
         default -> {
            return false;
         }
      }
   }

   private static WarSnapshot buildSnapshot(ServerLevel level, WarCampaignSavedData data, WarCampaign campaign) {
      List<UUID> fronts = new ArrayList<>();
      List<UUID> sabotage = new ArrayList<>();
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding()) {
            continue;
         }
         if (campaign.sideA().equals(v.getCultureId()) || campaign.sideB().equals(v.getCultureId())) {
            fronts.add(v.getId().uuid());
         }
      }
      Village front = campaign.frontVillageId() != null ? findVillage(level, campaign.frontVillageId()) : null;
      int atk = 0;
      int def = 0;
      if (front != null) {
         def = front.getVillageDefendingStrength();
         for (BuildingInstance b : front.getBuildings()) {
            BuildingInstance th = front.getTownhall();
            if (th != null && th.getId().equals(b.getId())) {
               continue;
            }
            if (b.isOperational()) {
               sabotage.add(b.getId().uuid());
               if (sabotage.size() >= 8) {
                  break;
               }
            }
         }
      }
      Village attacker = campaign.attackerVillageId() != null ? findVillage(level, campaign.attackerVillageId()) : null;
      if (attacker != null) {
         atk = attacker.getVillageRaidingStrength();
      }
      return new WarSnapshot(
         campaign.id(),
         campaign.sideA(),
         campaign.sideB(),
         campaign.phase(),
         campaign.frontVillageId(),
         campaign.attackerVillageId(),
         campaign.defenderVillageId(),
         campaign.waveIndex(),
         campaign.wavesTotal(),
         campaign.warScoreA(),
         campaign.warScoreB(),
         campaign.nextWaveGameTime(),
         level.getGameTime(),
         data.worldSessionId(),
         atk,
         def,
         fronts,
         sabotage,
         MillenaireServerConfig.SERVER.warWaveIntervalTicks.getAsInt(),
         MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean(),
         MillenaireServerConfig.SERVER.warBuildingDamageEnabled.getAsBoolean()
      );
   }

   private static void pickInitialFront(ServerLevel level, WarCampaign campaign) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<Village> sideA = new ArrayList<>();
      List<Village> sideB = new ArrayList<>();
      for (Village v : vm.getAllVillages()) {
         if (v.isLoneBuilding()) {
            continue;
         }
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if (campaign.sideA().equals(v.getCultureId())) {
            if (vt != null && vt.carriesRaid()) {
               sideA.add(v);
            } else {
               sideA.add(v);
            }
         } else if (campaign.sideB().equals(v.getCultureId())) {
            sideB.add(v);
         }
      }
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Village attacker = sideA.isEmpty() ? null : sideA.get(rng.nextInt(sideA.size()));
      Village defender = sideB.isEmpty() ? null : sideB.get(rng.nextInt(sideB.size()));
      // Prefer raiding culture as attacker when possible.
      if (attacker != null) {
         VillageType vt = ModCultures.getVillageType(attacker.getVillageTypeId());
         if (vt == null || !vt.carriesRaid()) {
            Village swap = defender;
            Village altAtk = null;
            for (Village v : sideB) {
               VillageType t = ModCultures.getVillageType(v.getVillageTypeId());
               if (t != null && t.carriesRaid()) {
                  altAtk = v;
                  break;
               }
            }
            if (altAtk != null && !sideA.isEmpty()) {
               attacker = altAtk;
               defender = sideA.get(rng.nextInt(sideA.size()));
            } else if (swap != null) {
               // keep as-is
            }
         }
      }
      if (attacker != null) {
         campaign.setAttackerVillageId(attacker.getId().uuid());
      }
      if (defender != null) {
         campaign.setDefenderVillageId(defender.getId().uuid());
         campaign.setFrontVillageId(defender.getId().uuid());
      }
   }

   private static List<UUID> pickSabotageTargets(Village target) {
      List<UUID> out = new ArrayList<>();
      BuildingInstance th = target.getTownhall();
      for (BuildingInstance b : target.getBuildings()) {
         if (!b.isOperational()) {
            continue;
         }
         if (th != null && th.getId().equals(b.getId())) {
            continue;
         }
         out.add(b.getId().uuid());
         if (out.size() >= 4) {
            break;
         }
      }
      return out;
   }

   /** Budgeted clone materialization (≤2/tick). Mirrors RaidManager materialize path. */
   static int materializeClonesBudgeted(Village target, ServerLevel level, long now, int budget) {
      if (budget <= 0) {
         return 0;
      }
      int spawned = 0;
      List<UUID> cloneUuids = new ArrayList<>();
      for (VillagerRecord r : target.getVillagerRecords().values()) {
         if (r.isRaidingVillage()) {
            cloneUuids.add(r.getUuid());
         }
      }
      for (UUID uuid : cloneUuids) {
         if (spawned >= budget) {
            break;
         }
         VillagerRecord clone = target.getVillagerRecords().get(uuid);
         if (clone == null || level.getEntity(uuid) instanceof MillVillager || clone.isKilled()) {
            continue;
         }
         if (now <= clone.getRaiderSpawn() + 500L) {
            continue;
         }
         VillageId originalId = clone.getOriginalVillageId();
         BlockPos attackerCenter = target.getCenter();
         if (originalId != null) {
            Village attacker = VillageSavedData.get(level).getVillageManager().getVillage(originalId);
            if (attacker != null) {
               attackerCenter = attacker.getCenter();
            }
         }
         BlockPos spawnPos = RaidSpawnLocator.findSpawnPoint(level, target, attackerCenter);
         if (!level.isPositionEntityTicking(spawnPos)) {
            BlockPos fallback = RaidManager.resolveDefendingPos(target);
            if (!level.isPositionEntityTicking(fallback)) {
               continue;
            }
            spawnPos = fallback;
         }
         target.removeVillagerRecord(uuid);
         MillVillager entity = VillagerSpawnFactory.spawnInVillage(level, target, clone.getVillagerTypeId(), spawnPos, null, clone);
         if (entity == null) {
            clone.setUuid(uuid);
            target.addVillager(clone);
         } else {
            target.markDirty();
            spawned++;
         }
      }
      return spawned;
   }

   @Nullable
   public static BuildingInstance resolveSabotageTarget(Village village, WarCampaign campaign) {
      if (village == null || campaign == null || campaign.sabotageTargets().isEmpty()) {
         return null;
      }
      for (UUID id : campaign.sabotageTargets()) {
         BuildingInstance b = village.getBuilding(new BuildingId(id));
         if (b != null && b.isOperational()) {
            return b;
         }
      }
      return null;
   }

   @Nullable
   public static WarCampaign activeCampaignForVillage(ServerLevel level, Village village) {
      if (village == null) {
         return null;
      }
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      WarCampaign byFront = data.findOpenForFront(village.getId().uuid());
      if (byFront != null) {
         return byFront;
      }
      return data.findOpenInvolving(village.getCultureId());
   }

   /** Open campaigns contribute to shared admission load (not stacked as free extra capacity). */
   public static int countActiveCampaignLoad(ServerLevel level) {
      WarCampaignSavedData data = WarCampaignSavedData.get(level);
      int n = 0;
      for (WarCampaign c : data.openCampaigns()) {
         if (c.isOpen()) {
            n++;
         }
      }
      return n;
   }

   private static boolean isNearFront(BlockPos playerPos, Village front) {
      long r = 64L;
      return playerPos.distSqr(front.getCenter()) <= r * r;
   }

   private static void releaseMusterForCampaign(ServerLevel level, WarCampaign campaign) {
      if (campaign.attackerVillageId() == null) {
         return;
      }
      Village attacker = findVillage(level, campaign.attackerVillageId());
      if (attacker != null && attacker.getOwnerUUID() != null) {
         WarMusterService.releaseMuster(level, attacker, attacker.getOwnerUUID());
      }
   }

   @Nullable
   private static Village findVillage(ServerLevel level, UUID id) {
      return VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(id));
   }

   private static String nameOf(Village v) {
      return v.getVillageName() != null ? v.getVillageName() : v.getVillageTypeId().getPath();
   }

   private static void broadcastNear(ServerLevel level, BlockPos center, Component msg) {
      for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
         if (p.level() == level && p.blockPosition().distSqr(center) <= 128.0 * 128.0) {
            p.sendSystemMessage(msg);
         }
      }
   }

   private static void broadcastNearCultures(ServerLevel level, ResourceLocation a, ResourceLocation b, Component msg) {
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      for (Village v : vm.getAllVillages()) {
         if (!v.isPlayerControlled() || v.getOwnerUUID() == null) {
            continue;
         }
         if (!a.equals(v.getCultureId()) && !b.equals(v.getCultureId())) {
            continue;
         }
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(v.getOwnerUUID());
         if (p != null) {
            p.sendSystemMessage(msg);
         }
      }
   }
}
