package org.millenaire.activity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;

public final class ActivityNavigationService {
   private static final ActivityNavigationService INSTANCE = new ActivityNavigationService();
   private final Map<String, Long> failedRouteCache = new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
         return size() > 256;
      }
   };
   private long navNanosThisTick;

   private ActivityNavigationService() {}

   public static ActivityNavigationService get() {
      return INSTANCE;
   }

   public void resetTick() {
      this.navNanosThisTick = 0L;
   }

   public long navNanosThisTick() {
      return this.navNanosThisTick;
   }

   public boolean requestNavigate(
      ServerLevel level,
      ActivityAdmissionController admission,
      MillVillager villager,
      BlockPos dest,
      double speed,
      ActivityOperation op
   ) {
      if (villager == null || dest == null) {
         return false;
      }
      if (!level.isPositionEntityTicking(dest) && !level.hasChunkAt(dest)) {
         op.setDeferReason(ActivityReasonCode.CHUNKS_UNAVAILABLE);
         return false;
      }
      String key = villager.getUUID() + "->" + (dest.getX() >> 3) + "," + (dest.getZ() >> 3);
      Long failUntil = this.failedRouteCache.get(key);
      if (failUntil != null && level.getGameTime() < failUntil) {
         op.setDeferReason(ActivityReasonCode.PATH_FAILED);
         return false;
      }
      if (!admission.tryNavRequest()) {
         return false;
      }
      long start = System.nanoTime();
      // Cap segment length
      BlockPos from = villager.blockPosition();
      BlockPos localDest = dest;
      int maxSeg = MillenaireServerConfig.SERVER.regionalActivityMaxNavSegment.getAsInt();
      if (from.distManhattan(dest) > maxSeg) {
         double dx = dest.getX() - from.getX();
         double dz = dest.getZ() - from.getZ();
         double len = Math.sqrt(dx * dx + dz * dz);
         localDest = new BlockPos(from.getX() + (int) (dx / len * maxSeg), from.getY(), from.getZ() + (int) (dz / len * maxSeg));
      }
      villager.getNavManager().navigateTo(villager, localDest, speed);
      this.navNanosThisTick += System.nanoTime() - start;
      boolean hasPath = villager.getNavigation().getPath() != null && !villager.getNavigation().isDone();
      if (!hasPath) {
         op.setConsecutivePathFails(op.consecutivePathFails() + 1);
         op.setLastNavFailGameTime(level.getGameTime());
         if (op.consecutivePathFails() >= 3) {
            this.failedRouteCache.put(key, level.getGameTime() + 200L);
            op.setDeferReason(ActivityReasonCode.PATH_FAILED);
         }
         return false;
      }
      op.setConsecutivePathFails(0);
      return true;
   }

   public void clearRuntime() {
      this.failedRouteCache.clear();
      this.navNanosThisTick = 0L;
   }
}
