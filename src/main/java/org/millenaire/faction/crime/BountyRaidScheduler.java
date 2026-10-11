package org.millenaire.faction.crime;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.civilization.settlement.SovereignDiplomacyBridge;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionIds;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Schedules bounty raid waves against player sovereign settlements and escalates to war.
 */
public final class BountyRaidScheduler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long WAVE_SPACING_TICKS = 12000L; // half day
   private static final int WAR_AFTER_SUCCESSFUL_RAIDS = 2;

   private BountyRaidScheduler() {
   }

   public static int computeWaveCount(ServerLevel level, ServerPlayer player, ResourceLocation cultureId, int bountyScore) {
      Village target = findPlayerSovereignVillage(level, player.getUUID());
      int stock = VillageStockScorer.score(level, target);
      int stockWaves = 1 + Math.min(4, stock / Math.max(1, stockPerWave()));
      int bountyWaves = 1 + Math.min(3, bountyScore / 150);
      return Math.min(5, Math.max(stockWaves, bountyWaves));
   }

   private static int stockPerWave() {
      try {
         return MillenaireServerConfig.SERVER.cultureBountyStockPerWave.getAsInt();
      } catch (IllegalStateException e) {
         return 256;
      }
   }

   private static int wantedRaidThreshold() {
      try {
         return MillenaireServerConfig.SERVER.cultureBountyRaidThreshold.getAsInt();
      } catch (IllegalStateException e) {
         return WantedTier.WANTED.minScore();
      }
   }

   private static int warScoreThreshold() {
      try {
         return MillenaireServerConfig.SERVER.cultureBountyWarThreshold.getAsInt();
      } catch (IllegalStateException e) {
         return WantedTier.PUBLIC_ENEMY.minScore();
      }
   }

   public static void tick(ServerLevel level) {
      if (!CultureBountyManager.isEnabled() || level == null) {
         return;
      }
      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      manager.tickDecay(level);
      long now = level.getGameTime();
      for (CultureBountyRecord record : new ArrayList<>(manager.getAllRecords())) {
         if (record.bountyScore() < wantedRaidThreshold()) {
            continue;
         }
         if (record.pendingWaves() <= 0) {
            maybeDeclareWar(level, record);
            continue;
         }
         if (record.nextWaveGameTime() > now) {
            continue;
         }
         boolean launched = tryLaunchWave(level, record);
         if (launched) {
            record.setPendingWaves(record.pendingWaves() - 1);
            record.setNextWaveGameTime(now + WAVE_SPACING_TICKS);
            CultureBountySavedData.get(level).setDirty();
            maybeDeclareWar(level, record);
         } else {
            // Retry later if no attacker/target available.
            record.setNextWaveGameTime(now + 2400L);
            CultureBountySavedData.get(level).setDirty();
         }
      }
   }

   private static boolean tryLaunchWave(ServerLevel level, CultureBountyRecord record) {
      Village target = findPlayerSovereignVillage(level, record.playerId());
      if (target == null || target.isUnderAttack()) {
         return false;
      }
      Village attacker = selectAttackerVillage(level, record.cultureId(), target);
      if (attacker == null || attacker.getRaidTarget() != null) {
         return false;
      }

      // Force hostile village relation so classic raid path accepts the fight.
      attacker.adjustRelationSymmetric(level, target.getId(), -100, true);

      RaidManager.planRaid(attacker, target, level);
      SovereignDiplomacyBridge.onBountyRaidPlanned(level, attacker, target, record);

      ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.playerId());
      if (player != null) {
         player.sendSystemMessage(
            Component.translatable(
               "millenaire.crime.bounty_raid_wave",
               Component.literal(attacker.getVillageName() != null ? attacker.getVillageName() : attacker.getId().uuid().toString()),
               Component.literal(record.cultureId().getPath()),
               record.pendingWaves()
            )
         );
      }
      LOGGER.info(
         "[Millenaire] Bounty raid wave: {} -> {} culture={} remainingWaves={}",
         attacker.getVillageName(),
         target.getVillageName(),
         record.cultureId(),
         record.pendingWaves() - 1
      );
      return true;
   }

   public static void onBountyRaidEnded(ServerLevel level, Village attacker, Village target, boolean attackersWon) {
      if (!CultureBountyManager.isEnabled() || !attackersWon || target == null || attacker == null) {
         return;
      }
      if (!PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, target)) {
         return;
      }
      UUID owner = target.getOwnerUUID();
      ResourceLocation cultureId = attacker.getCultureId();
      if (owner == null || cultureId == null) {
         return;
      }
      CultureBountyRecord record = CultureBountySavedData.get(level).getManager().get(owner, cultureId);
      if (record == null || record.bountyScore() < wantedRaidThreshold()) {
         return;
      }
      record.incrementSuccessfulBountyRaids();
      CultureBountySavedData.get(level).setDirty();
      maybeDeclareWar(level, record);
   }

   private static void maybeDeclareWar(ServerLevel level, CultureBountyRecord record) {
      if (record.warDeclared()) {
         return;
      }
      boolean byScore = record.bountyScore() >= warScoreThreshold();
      boolean byRaids = record.successfulBountyRaids() >= WAR_AFTER_SUCCESSFUL_RAIDS;
      if (!byScore && !byRaids) {
         return;
      }
      Faction playerFaction = FactionSavedData.get(level).getFactionManager().findSovereignFaction(record.playerId());
      if (playerFaction == null || playerFaction.getType() != FactionType.PLAYER) {
         return;
      }
      FactionId cultureFactionId = FactionIds.defaultCultureFaction(record.cultureId());
      Faction cultureFaction = FactionSavedData.get(level).getFactionManager().getFaction(cultureFactionId);
      if (cultureFaction == null) {
         cultureFaction = FactionSavedData.get(level).getFactionManager().getOrCreateDefaultCultureFaction(record.cultureId(), level.getGameTime());
      }
      if (cultureFaction == null) {
         return;
      }
      if (WarSavedData.get(level).getWarManager().areAtWar(cultureFaction.getId(), playerFaction.getId())) {
         record.setWarDeclared(true);
         CultureBountySavedData.get(level).setDirty();
         return;
      }
      // Peace treaty blocks war until broken.
      if (DiplomacySavedData.get(level).getDiplomaticManager().isPeaceful(cultureFaction.getId(), playerFaction.getId())) {
         return;
      }
      var war = WarSavedData.get(level)
         .getWarManager()
         .declareWar(level, cultureFaction.getId(), playerFaction.getId(), WarGoalType.MILITARY_SUPERIORITY);
      if (war != null) {
         record.setWarDeclared(true);
         CultureBountySavedData.get(level).setDirty();
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.playerId());
         if (player != null) {
            player.sendSystemMessage(
               Component.translatable("millenaire.crime.war_declared", Component.literal(record.cultureId().getPath()))
            );
         }
         LOGGER.info(
            "[Millenaire] Culture bounty war: {} → playerFaction {} (score={} raids={})",
            record.cultureId(),
            playerFaction.getId(),
            record.bountyScore(),
            record.successfulBountyRaids()
         );
      }
   }

   @Nullable
   private static Village findPlayerSovereignVillage(ServerLevel level, UUID playerId) {
      Faction faction = FactionSavedData.get(level).getFactionManager().findSovereignFaction(playerId);
      if (faction == null) {
         return null;
      }
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
            && playerId.equals(village.getOwnerUUID())) {
            return village;
         }
      }
      return null;
   }

   @Nullable
   private static Village selectAttackerVillage(ServerLevel level, ResourceLocation cultureId, Village target) {
      List<Village> candidates = new ArrayList<>();
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (village.getId().equals(target.getId())) {
            continue;
         }
         if (cultureId == null || !cultureId.equals(village.getCultureId())) {
            continue;
         }
         if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
            continue;
         }
         VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
         if (vt != null && vt.loneBuilding()) {
            continue;
         }
         if (village.getRaidTarget() != null || village.isUnderAttack()) {
            continue;
         }
         if (village.getVillageRaidingStrength() <= 0) {
            continue;
         }
         candidates.add(village);
      }
      if (candidates.isEmpty()) {
         return null;
      }
      candidates.sort(Comparator
         .comparingDouble((Village v) -> v.getCenter().distSqr(target.getCenter()))
         .thenComparing(v -> v.getId().uuid()));
      // Prefer nearest with decent strength; among close ones pick strongest.
      Village best = candidates.get(0);
      double bestDist = best.getCenter().distSqr(target.getCenter());
      for (Village v : candidates) {
         double dist = v.getCenter().distSqr(target.getCenter());
         if (dist <= bestDist * 2.0 && v.getVillageRaidingStrength() > best.getVillageRaidingStrength()) {
            best = v;
            bestDist = dist;
         }
      }
      return best;
   }
}
