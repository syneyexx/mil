package org.millenaire.companion.capability;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.companion.CompanionWorldActor;
import org.millenaire.entity.MillVillager;

public final class AdAstraCompanionCapability implements CompanionCapability {
   @Override
   public String id() {
      return "ad_astra";
   }

   @Override
   public boolean isAvailable() {
      return ModList.get().isLoaded("ad_astra");
   }

   @Override
   public List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      String dim = level.dimension().location().toString();
      boolean spaceish = dim.contains("ad_astra") || dim.contains("glacio") || dim.contains("mars") || dim.contains("moon");
      BlockPos rocket = findNearbyAdAstra(level, companion.blockPosition(), 10);
      return List.of(
         "cap.ad_astra=loaded",
         "cap.ad_astra.spaceDim=" + spaceish,
         "cap.ad_astra.nearbyRocketOrMachine=" + (rocket != null ? rocket.toShortString() : "none")
      );
   }

   @Override
   @Nullable
   public String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      if (!"AD_ASTRA_INTERACT".equals(intent.type())) {
         return null;
      }
      BlockPos target = findNearbyAdAstra(level, companion.blockPosition(), 12);
      if (target == null) {
         return "AD_ASTRA_INTERACT failed: no Ad Astra block nearby";
      }
      companion.getNavManager().navigateTo(companion, target, 0.5);
      return CompanionWorldActor.useBlock(level, companion, target);
   }

   @Nullable
   private static BlockPos findNearbyAdAstra(ServerLevel level, BlockPos origin, int radius) {
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 6, radius))) {
         BlockState state = level.getBlockState(pos);
         var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
         if (key != null && "ad_astra".equals(key.getNamespace())) {
            String path = key.getPath();
            if (path.contains("rocket") || path.contains("launch") || path.contains("fuel") || path.contains("oxygen") || path.contains("compressor")) {
               return pos.immutable();
            }
         }
      }
      return null;
   }
}
