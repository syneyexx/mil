package org.millenaire.faction.crime.hunter;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.crime.CultureBountyManager;
import org.millenaire.faction.crime.CultureBountyRecord;
import org.millenaire.faction.crime.CultureBountySavedData;
import org.slf4j.Logger;

/**
 * Idempotent bounty settlement against the existing culture bounty ledger.
 * Does not mint currency — reduces/clears the authoritative bounty score once per capture claim.
 */
public final class BountyHunterSettlementService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private BountyHunterSettlementService() {
   }

   /**
    * @return true if this call performed a new settlement; false if already settled or invalid.
    */
   public static boolean settleOnce(
      ServerLevel level,
      UUID captureId,
      UUID targetPlayerId,
      ResourceLocation cultureId,
      UUID contractId
   ) {
      if (level == null || captureId == null || targetPlayerId == null || cultureId == null) {
         return false;
      }
      BountyHunterContractSavedData data = BountyHunterContractSavedData.get(level);
      if (data.hasSettledClaim(captureId)) {
         return false;
      }

      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      CultureBountyRecord record = manager.get(targetPlayerId, cultureId);
      if (record == null || record.bountyScore() <= 0) {
         data.markSettledClaim(captureId, targetPlayerId);
         markContractSettled(data, targetPlayerId, captureId);
         return false;
      }

      int reduction = settlementReduction();
      if (reduction <= 0) {
         // Clear to below wanted threshold by default when configured as full settle.
         reduction = record.bountyScore();
      }
      manager.reduceBounty(level, targetPlayerId, cultureId, reduction);
      data.markSettledClaim(captureId, targetPlayerId);
      markContractSettled(data, targetPlayerId, captureId);

      LOGGER.info(
         "[Millenaire] Bounty hunter settled claim capture={} player={} culture={} reduced={}",
         captureId,
         targetPlayerId,
         cultureId,
         reduction
      );
      return true;
   }

   private static void markContractSettled(BountyHunterContractSavedData data, UUID targetPlayerId, UUID captureId) {
      BountyHunterContract contract = data.getForTarget(targetPlayerId);
      if (contract != null) {
         contract.setSettled(true);
         contract.setSettlementId(captureId);
         contract.setPhase(BountyHunterPhase.BOUNTY_SETTLED);
         data.setDirty();
      }
   }

   private static int settlementReduction() {
      try {
         return MillenaireServerConfig.SERVER.bountyHunterSettlementReduction.getAsInt();
      } catch (IllegalStateException e) {
         return 0;
      }
   }
}
