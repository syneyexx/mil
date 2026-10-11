package org.millenaire.faction.crime.hunter;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.BountyHunterEntity;
import org.millenaire.entity.ModEntities;
import org.millenaire.faction.crime.CultureBountyRecord;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Event-driven dispatch: creates contracts for eligible bounties and spawns hunters
 * outside the player's immediate view after a delay.
 */
public final class BountyHunterDispatchManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int TICK_INTERVAL = 40;

   private BountyHunterDispatchManager() {
   }

   public static void tick(ServerLevel overworld) {
      if (overworld == null || overworld.dimension() != Level.OVERWORLD) {
         return;
      }
      if (!BountyEligibilityService.isHunterSystemEnabled()) {
         cancelAllActive(overworld);
         return;
      }
      if (overworld.getDifficulty() == Difficulty.PEACEFUL && !allowPeaceful()) {
         cancelAllActive(overworld);
         return;
      }
      if (overworld.getGameTime() % TICK_INTERVAL != 0L) {
         return;
      }

      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(overworld);
      long now = overworld.getGameTime();

      // Ensure contracts exist for online eligible players.
      for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
         if (!(player.level() instanceof ServerLevel playerLevel)) {
            continue;
         }
         if (playerLevel.dimension() != Level.OVERWORLD || !huntInOverworld()) {
            continue;
         }
         Optional<CultureBountyRecord> bounty = BountyEligibilityService.selectPrimaryBounty(overworld, player.getUUID());
         if (bounty.isEmpty()) {
            abandonIfPresent(overworld, player.getUUID());
            continue;
         }
         if (BountyEligibilityService.isAlreadyInCustody(overworld, player.getUUID())) {
            continue;
         }
         ensureContract(overworld, player, bounty.get(), now);
      }

      // Advance contracts.
      for (BountyHunterContract contract : new ArrayList<>(data.all())) {
         processContract(overworld, contract, now);
      }
   }

   private static void ensureContract(ServerLevel level, ServerPlayer player, CultureBountyRecord bounty, long now) {
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      BountyHunterContract existing = data.getForTarget(player.getUUID());
      String key = BountyEligibilityService.bountyKey(player.getUUID(), bounty.cultureId());
      if (existing != null) {
         if (existing.settled()) {
            return;
         }
         // Retarget culture if primary changed and no active hunter yet.
         if (!existing.bountyKey().equals(key) && existing.hunterEntityUuid() == null) {
            existing.setPhase(BountyHunterPhase.DISPATCH_PENDING);
            existing.setDispatchAtGameTime(now + dispatchDelayTicks());
            // Replace with new culture binding via fresh contract.
            BountyHunterContract refreshed = new BountyHunterContract(
               UUID.randomUUID(), player.getUUID(), bounty.cultureId(), key, now, now + dispatchDelayTicks()
            );
            refreshed.setNextRedispatchGameTime(existing.nextRedispatchGameTime());
            data.put(refreshed);
         }
         return;
      }
      long dispatchAt = now + dispatchDelayTicks();
      BountyHunterContract contract = new BountyHunterContract(
         UUID.randomUUID(), player.getUUID(), bounty.cultureId(), key, now, dispatchAt
      );
      data.put(contract);
      LOGGER.info(
         "[Millenaire] Bounty hunter contract created for {} culture={} dispatchIn={}t",
         player.getGameProfile().getName(),
         bounty.cultureId(),
         dispatchDelayTicks()
      );
   }

   private static void processContract(ServerLevel level, BountyHunterContract contract, long now) {
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      ServerPlayer player = level.getServer().getPlayerList().getPlayer(contract.targetPlayerId());

      if (!BountyEligibilityService.hasActiveEnforceableBounty(level, contract.targetPlayerId())) {
         abandonContract(level, contract);
         return;
      }
      if (contract.settled() || contract.phase() == BountyHunterPhase.COMPLETE || contract.phase() == BountyHunterPhase.BOUNTY_SETTLED) {
         despawnHunter(level, contract);
         data.remove(contract.targetPlayerId());
         return;
      }

      // Update last-known tracking when player is online in overworld.
      if (player != null && player.level().dimension() == Level.OVERWORLD) {
         contract.setLastKnownPos(player.blockPosition());
         contract.setLastKnownDimension(Level.OVERWORLD.location());
         contract.setLastSightingGameTime(now);
         data.setDirty();
      }

      // Validate live hunter entity assignment.
      if (contract.hunterEntityUuid() != null) {
         Entity entity = findHunter(level, contract.hunterEntityUuid());
         if (entity instanceof BountyHunterEntity hunter && hunter.isAlive()) {
            if (player == null || player.level().dimension() != Level.OVERWORLD) {
               hunter.enterLogicalTracking();
            }
            return;
         }
         // Hunter lost/defeated.
         contract.setHunterEntityUuid(null);
         contract.incrementFailureCount();
         contract.setPhase(BountyHunterPhase.DISPATCH_PENDING);
         contract.setNextRedispatchGameTime(now + replacementCooldownTicks());
         contract.setDispatchAtGameTime(now + replacementCooldownTicks());
         data.setDirty();
         LOGGER.info("[Millenaire] Bounty hunter lost for {}; redispatch at +{}t", contract.targetPlayerId(), replacementCooldownTicks());
         return;
      }

      if (contract.phase() == BountyHunterPhase.DISPATCH_PENDING) {
         if (now < contract.dispatchAtGameTime() || now < contract.nextRedispatchGameTime()) {
            return;
         }
         if (player == null || player.level().dimension() != Level.OVERWORLD) {
            return;
         }
         if (BountyEligibilityService.isAlreadyInCustody(level, player.getUUID())) {
            return;
         }
         if (countHuntersFor(level, player.getUUID()) >= maxHuntersPerPlayer()) {
            return;
         }
         BlockPos spawn = selectSpawnPos(level, player, contract.cultureId());
         if (spawn == null) {
            contract.setDispatchAtGameTime(now + 200L);
            data.setDirty();
            return;
         }
         BountyHunterEntity hunter = ModEntities.BOUNTY_HUNTER.get().create(level);
         if (hunter == null) {
            return;
         }
         hunter.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
         hunter.assignContract(contract.contractId(), player.getUUID(), contract.cultureId());
         hunter.setHunterPhase(BountyHunterPhase.TRAVELLING);
         hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.TRIGGERED, null);
         level.addFreshEntity(hunter);

         contract.setHunterEntityUuid(hunter.getUUID());
         contract.setPhase(BountyHunterPhase.TRAVELLING);
         data.setDirty();
         LOGGER.info(
            "[Millenaire] Dispatched bounty hunter at {} for {}",
            spawn,
            player.getGameProfile().getName()
         );
      }
   }

   private static void abandonIfPresent(ServerLevel level, UUID playerId) {
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      BountyHunterContract contract = data.getForTarget(playerId);
      if (contract != null) {
         abandonContract(level, contract);
      }
   }

   private static void abandonContract(ServerLevel level, BountyHunterContract contract) {
      despawnHunter(level, contract);
      BountyHunterContractSavedData.get(level).remove(contract.targetPlayerId());
   }

   private static void cancelAllActive(ServerLevel level) {
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      for (BountyHunterContract contract : new ArrayList<>(data.all())) {
         abandonContract(level, contract);
      }
   }

   private static void despawnHunter(ServerLevel level, BountyHunterContract contract) {
      if (contract.hunterEntityUuid() == null) {
         return;
      }
      Entity entity = findHunter(level, contract.hunterEntityUuid());
      if (entity instanceof BountyHunterEntity hunter) {
         hunter.setHunterPhase(BountyHunterPhase.DESPAWNING);
         hunter.discard();
      }
      contract.setHunterEntityUuid(null);
   }

   @Nullable
   private static Entity findHunter(ServerLevel level, UUID uuid) {
      return level.getEntity(uuid);
   }

   private static int countHuntersFor(ServerLevel level, UUID targetId) {
      int count = 0;
      for (ServerPlayer ignored : level.getServer().getPlayerList().getPlayers()) {
         // bounded: use contract map instead of entity scan
      }
      BountyHunterContract c = BountyHunterContractSavedData.get(level).getForTarget(targetId);
      if (c != null && c.hunterEntityUuid() != null) {
         count++;
      }
      return count;
   }

   @Nullable
   private static BlockPos selectSpawnPos(ServerLevel level, ServerPlayer player, ResourceLocation cultureId) {
      // Prefer a village of the issuing culture with a loaded center.
      List<Village> cultureVillages = new ArrayList<>();
      List<Village> anyVillages = new ArrayList<>();
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (village == null || village.getCenter() == null) {
            continue;
         }
         if (!level.isLoaded(village.getCenter())) {
            continue;
         }
         double distSq = player.distanceToSqr(Vec3.atCenterOf(village.getCenter()));
         if (distSq < 48.0 * 48.0 || distSq > 160.0 * 160.0) {
            continue;
         }
         if (cultureId != null && cultureId.equals(village.getCultureId())) {
            cultureVillages.add(village);
         } else {
            anyVillages.add(village);
         }
      }
      for (Village village : cultureVillages) {
         BlockPos pos = findStandableNear(level, village.getCenter(), player);
         if (pos != null) {
            return pos;
         }
      }
      for (Village village : anyVillages) {
         BlockPos pos = findStandableNear(level, village.getCenter(), player);
         if (pos != null) {
            return pos;
         }
      }

      // Fallback: ring 48–96 blocks from player, prefer outside view.
      var random = level.random;
      for (int attempt = 0; attempt < 24; attempt++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = 48.0 + random.nextDouble() * 48.0;
         int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
         int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
         BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
         if (!level.isLoaded(surface)) {
            continue;
         }
         if (!BountyHunterJailResolver.isSafeStandPos(level, surface)) {
            continue;
         }
         if (isInPlayerView(player, surface) && attempt < 16) {
            continue;
         }
         return surface;
      }
      return null;
   }

   @Nullable
   private static BlockPos findStandableNear(ServerLevel level, BlockPos center, ServerPlayer player) {
      var random = level.random;
      for (int i = 0; i < 12; i++) {
         int x = center.getX() + random.nextInt(17) - 8;
         int z = center.getZ() + random.nextInt(17) - 8;
         BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
         if (BountyHunterJailResolver.isSafeStandPos(level, surface) && !isInPlayerView(player, surface)) {
            return surface;
         }
      }
      return BountyHunterJailResolver.isSafeStandPos(level, center) ? center : null;
   }

   private static boolean isInPlayerView(ServerPlayer player, BlockPos pos) {
      Vec3 look = player.getLookAngle().normalize();
      Vec3 to = Vec3.atCenterOf(pos).subtract(player.position()).normalize();
      return look.dot(to) > 0.55 && player.distanceToSqr(Vec3.atCenterOf(pos)) < 96.0 * 96.0;
   }

   public static void onHunterDefeated(ServerLevel level, BountyHunterEntity hunter) {
      if (level == null || hunter == null || hunter.getTargetPlayerId() == null) {
         return;
      }
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      BountyHunterContract contract = data.getForTarget(hunter.getTargetPlayerId());
      if (contract == null) {
         return;
      }
      long now = level.getGameTime();
      contract.setHunterEntityUuid(null);
      contract.incrementFailureCount();
      contract.setPhase(BountyHunterPhase.DISPATCH_PENDING);
      contract.setNextRedispatchGameTime(now + replacementCooldownTicks());
      contract.setDispatchAtGameTime(now + replacementCooldownTicks());
      data.setDirty();
   }

   private static long dispatchDelayTicks() {
      try {
         return Math.max(20L, MillenaireServerConfig.SERVER.bountyHunterDispatchDelaySeconds.getAsInt() * 20L);
      } catch (IllegalStateException e) {
         return 45L * 20L;
      }
   }

   private static long replacementCooldownTicks() {
      try {
         return Math.max(200L, MillenaireServerConfig.SERVER.bountyHunterReplacementCooldownTicks.getAsInt());
      } catch (IllegalStateException e) {
         return 24000L;
      }
   }

   private static int maxHuntersPerPlayer() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.bountyHunterMaxPerPlayer.getAsInt());
      } catch (IllegalStateException e) {
         return 1;
      }
   }

   private static boolean allowPeaceful() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.bountyHunterAllowPeaceful.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   private static boolean huntInOverworld() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.bountyHunterHuntInOverworld.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
