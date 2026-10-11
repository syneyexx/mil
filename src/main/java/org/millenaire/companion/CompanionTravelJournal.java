package org.millenaire.companion;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class CompanionTravelJournal {
   private CompanionTravelJournal() {
   }

   public static void update(
      CompanionBond bond, ServerLevel level, ServerPlayer player, MillVillager companion, @Nullable Village village, CompanionBondSavedData data
   ) {
      String dim = level.dimension().location().toString();
      String biome = level.getBiome(companion.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
      String villageName = village != null ? village.getVillageName() : null;

      if (!dim.equals(bond.lastDimension())) {
         bond.addTravelLine("entered dimension " + dim + " with player");
         bond.setLastDimension(dim);
         data.markChanged();
      }
      if (!biome.equals(bond.lastBiome())) {
         bond.addTravelLine("traveled into biome " + biome);
         bond.setLastBiome(biome);
         data.markChanged();
      }
      if (villageName != null && !villageName.equals(bond.lastVillageName())) {
         bond.addTravelLine("visited village " + villageName);
         bond.setLastVillageName(villageName);
         data.markChanged();
      }
   }
}
