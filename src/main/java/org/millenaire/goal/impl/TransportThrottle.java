package org.millenaire.goal.impl;

import java.util.UUID;
import org.millenaire.goal.PerVillagerThrottle;

final class TransportThrottle {
   private final PerVillagerThrottle startDelay;
   private final PerVillagerThrottle failedTransferDelay;

   TransportThrottle(int delayTicks) {
      this.startDelay = new PerVillagerThrottle(delayTicks);
      this.failedTransferDelay = new PerVillagerThrottle(delayTicks);
   }

   boolean allowStart(UUID villagerUuid, long currentTick, int count, int threshold) {
      if (this.failedTransferDelay.isThrottled(villagerUuid, currentTick)) {
         return false;
      } else {
         return count > threshold ? true : !this.startDelay.isThrottled(villagerUuid, currentTick);
      }
   }

   void recordStart(UUID villagerUuid, long currentTick) {
      this.startDelay.record(villagerUuid, currentTick);
   }

   void recordFailedTransfer(UUID villagerUuid, long currentTick) {
      this.failedTransferDelay.record(villagerUuid, currentTick);
   }
}
