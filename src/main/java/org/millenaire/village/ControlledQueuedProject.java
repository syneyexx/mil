package org.millenaire.village;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.world.PlacedLocation;

public record ControlledQueuedProject(ResourceLocation planSetId, String variant, int level, PlacedLocation plannedLocation) {
   public boolean matchesEcho(String planSetIdStr, int x, int y, int z) {
      if (!this.planSetId.toString().equals(planSetIdStr)) {
         return false;
      }

      if (this.plannedLocation == null) {
         return false;
      }

      BlockPos pos = this.plannedLocation.position();
      return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
   }
}
