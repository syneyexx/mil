package org.millenaire.faction.crime.hunter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.combat.CombatHelper;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.crime.CultureBountyManager;
import org.millenaire.faction.crime.CultureBountyRecord;
import org.millenaire.faction.crime.CultureBountySavedData;
import org.millenaire.faction.crime.PlayerArrestSavedData;
import org.millenaire.faction.crime.WantedTier;

/**
 * Server-authoritative bounty eligibility API for bounty hunters.
 * Uses the existing culture bounty ledger — does not create a parallel bounty system.
 */
public final class BountyEligibilityService {
   private BountyEligibilityService() {
   }

   public static boolean isHunterSystemEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.bountyHunterEnabled.get())
            && CultureBountyManager.isEnabled();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static WantedTier minDispatchTier() {
      try {
         return WantedTier.tryParse(MillenaireServerConfig.SERVER.bountyHunterMinTier.get());
      } catch (IllegalStateException e) {
         return WantedTier.WANTED;
      }
   }

   public static boolean hasActiveEnforceableBounty(ServerLevel level, UUID playerId) {
      return selectPrimaryBounty(level, playerId).isPresent();
   }

   public static boolean isEligibleForArrest(ServerLevel level, ServerPlayer player) {
      if (!isHunterSystemEnabled() || player == null || level == null) {
         return false;
      }
      if (!CombatHelper.isPlayerTargetable(player)) {
         return false;
      }
      if (isAlreadyInCustody(level, player.getUUID())) {
         return false;
      }
      return hasActiveEnforceableBounty(level, player.getUUID());
   }

   public static boolean isAlreadyInCustody(ServerLevel level, UUID playerId) {
      if (level == null || playerId == null) {
         return false;
      }
      PlayerArrestSavedData.ArrestRecord arrest = PlayerArrestSavedData.get(level).get(playerId);
      if (arrest != null && arrest.arrestedUntilGameTime() > level.getGameTime()) {
         return true;
      }
      BountyHunterCustodySavedData.CustodyRecord custody = BountyHunterCustodySavedData.get(level).get(playerId);
      return custody != null && custody.releaseAtGameTime() > level.getGameTime();
   }

   /**
    * Primary bounty = highest score among culture records at/above dispatch tier.
    */
   public static Optional<CultureBountyRecord> selectPrimaryBounty(ServerLevel level, UUID playerId) {
      if (level == null || playerId == null || !isHunterSystemEnabled()) {
         return Optional.empty();
      }
      WantedTier min = minDispatchTier();
      List<CultureBountyRecord> records = CultureBountySavedData.get(level).getManager().getRecordsForPlayer(playerId);
      return records.stream()
         .filter(r -> r.tier().isAtLeast(min))
         .max(Comparator.comparingInt(CultureBountyRecord::bountyScore).thenComparing(r -> r.cultureId().toString()));
   }

   public static String bountyKey(UUID playerId, ResourceLocation cultureId) {
      return playerId + "|" + cultureId;
   }

   @Nullable
   public static CultureBountyRecord getBounty(ServerLevel level, UUID playerId, ResourceLocation cultureId) {
      if (level == null || playerId == null || cultureId == null) {
         return null;
      }
      CultureBountyRecord record = CultureBountySavedData.get(level).getManager().get(playerId, cultureId);
      if (record == null || !record.tier().isAtLeast(minDispatchTier())) {
         return null;
      }
      return record;
   }

   public static boolean isStillValidTarget(ServerLevel level, ServerPlayer player, ResourceLocation cultureId) {
      if (!isEligibleForArrest(level, player)) {
         return false;
      }
      return getBounty(level, player.getUUID(), cultureId) != null;
   }
}
