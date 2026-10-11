package org.millenaire.faction.crime.hunter;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.BountyHunterEntity;
import org.millenaire.faction.crime.PlayerArrestSavedData;
import org.slf4j.Logger;

/**
 * Completes restraint → custody → jail transfer → bounty settlement.
 * Preserves player inventory (unlike guard soft-death ArrestService confiscation).
 */
public final class BountyHunterCaptureService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private BountyHunterCaptureService() {
   }

   public static boolean tryCompleteArrest(ServerLevel level, BountyHunterEntity hunter, ServerPlayer player) {
      if (level == null || hunter == null || player == null) {
         return false;
      }
      if (!BountyEligibilityService.isEligibleForArrest(level, player)) {
         return false;
      }
      UUID contractTarget = hunter.getTargetPlayerId();
      if (contractTarget == null || !contractTarget.equals(player.getUUID())) {
         return false;
      }
      ResourceLocation cultureId = hunter.getAssignedCultureId();
      if (cultureId == null || !BountyEligibilityService.isStillValidTarget(level, player, cultureId)) {
         return false;
      }

      Optional<BountyHunterJailResolver.JailDestination> jailOpt =
         BountyHunterJailResolver.resolve(level, player, cultureId);
      if (jailOpt.isEmpty()) {
         player.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.no_jail"));
         LOGGER.warn("[Millenaire] Bounty hunter capture aborted — no jail for culture {}", cultureId);
         return false;
      }

      BountyHunterJailResolver.JailDestination jail = jailOpt.get();
      UUID captureId = UUID.randomUUID();
      UUID contractId = hunter.getContractId() != null ? hunter.getContractId() : captureId;
      long now = level.getGameTime();
      long releaseAt = now + jailSentenceTicks();

      // Transport sequence
      hunter.setHunterPhase(BountyHunterPhase.TRANSPORTING);
      player.displayClientMessage(Component.translatable("millenaire.bounty_hunter.arrested"), true);
      player.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.arrested"));
      level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);

      hunter.setHunterPhase(BountyHunterPhase.JAIL_TRANSFER);
      player.teleportTo(jail.cellPos().getX() + 0.5, jail.cellPos().getY() + 0.1, jail.cellPos().getZ() + 0.5);
      player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * 0.35F));
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false, true));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false, true));

      BountyHunterCustodySavedData.get(level)
         .put(
            new BountyHunterCustodySavedData.CustodyRecord(
               player.getUUID(),
               captureId,
               contractId,
               cultureId,
               hunter.getUUID(),
               now,
               releaseAt,
               jail.cellPos(),
               jail.releasePos(),
               jail.village().getId() != null ? jail.village().getId().uuid() : null,
               false
            )
         );

      // Mirror into legacy arrest record for compatibility with any consumers.
      PlayerArrestSavedData.get(level)
         .put(
            new PlayerArrestSavedData.ArrestRecord(
               player.getUUID(),
               cultureId,
               jail.village().getId() != null ? jail.village().getId().uuid() : player.getUUID(),
               releaseAt,
               null,
               jail.cellPos()
            )
         );

      boolean settled = BountyHunterSettlementService.settleOnce(level, captureId, player.getUUID(), cultureId, contractId);
      if (settled) {
         Culture culture = ModCultures.getCulture(cultureId);
         String name = culture != null && culture.displayName() != null ? culture.displayName() : cultureId.getPath();
         player.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.bounty_settled", name));
      }

      BountyHunterContractSavedData contracts = BountyHunterContractSavedData.get(level);
      BountyHunterContract contract = contracts.getForTarget(player.getUUID());
      if (contract != null) {
         contract.setPhase(BountyHunterPhase.COMPLETE);
         contract.setHunterEntityUuid(null);
         contracts.setDirty();
      }

      hunter.setHunterPhase(BountyHunterPhase.COMPLETE);
      hunter.setTarget(null);
      hunter.discardAfterCapture();

      LOGGER.info(
         "[Millenaire] Bounty hunter arrested player={} culture={} jail={} settled={}",
         player.getGameProfile().getName(),
         cultureId,
         jail.cellPos(),
         settled
      );
      return true;
   }

   public static long jailSentenceTicks() {
      try {
         return Math.max(20L, MillenaireServerConfig.SERVER.bountyHunterJailSentenceSeconds.getAsInt() * 20L);
      } catch (IllegalStateException e) {
         return 90L * 20L;
      }
   }
}
