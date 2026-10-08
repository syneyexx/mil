package org.millenaire.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.village.Village;

public interface VillagerNavDriver {
   void navigateTo(MillVillager var1, BlockPos var2, double var3);

   default void navigateToCombatTarget(MillVillager villager, BlockPos dest, double walkSpeed) {
      this.navigateTo(villager, dest, walkSpeed);
   }

   default boolean isDestinationCombatTarget() {
      return false;
   }

   void tick(MillVillager var1, @Nullable Village var2);

   boolean isArrived(MillVillager var1, double var2);

   boolean isArrivedHorizontal(MillVillager var1, double var2);

   boolean isArrivedSameFloor(MillVillager var1, double var2);

   boolean isAbandoned();

   void stop(MillVillager var1);

   @Nullable
   BlockPos getDestination();

   VillagerNavDriver.NavDiagnostics getDiagnostics();

   record NavDiagnostics(int localStuck, int longDistanceStuck, int teleportCount, @Nullable String waypointState) {
   }
}
