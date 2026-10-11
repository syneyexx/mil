package org.millenaire.companion.capability;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.millenaire.companion.CompanionIntent;
import org.millenaire.companion.CompanionWorldActor;
import org.millenaire.entity.MillVillager;

public final class WaystonesCompanionCapability implements CompanionCapability {
   private static final ResourceLocation WAYSTONE = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");

   @Override
   public String id() {
      return "waystones";
   }

   @Override
   public boolean isAvailable() {
      return ModList.get().isLoaded("waystones");
   }

   @Override
   public List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      BlockPos near = findNearbyWaystone(level, companion.blockPosition(), 8);
      if (near != null) {
         return List.of("cap.waystones.nearby=" + near.toShortString());
      }
      return List.of("cap.waystones=loaded");
   }

   @Override
   @Nullable
   public String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      if (!"USE_WAYSTONE".equals(intent.type())) {
         return null;
      }
      BlockPos near = findNearbyWaystone(level, companion.blockPosition(), 10);
      if (near == null) {
         return "USE_WAYSTONE failed: no waystone nearby";
      }
      companion.getNavManager().navigateTo(companion, near, 0.5);
      return CompanionWorldActor.useBlock(level, companion, near);
   }

   @Nullable
   private static BlockPos findNearbyWaystone(ServerLevel level, BlockPos origin, int radius) {
      var blockOpt = BuiltInRegistries.BLOCK.getOptional(WAYSTONE);
      if (blockOpt.isEmpty()) {
         return null;
      }
      var block = blockOpt.get();
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 4, radius))) {
         BlockState state = level.getBlockState(pos);
         if (state.is(block)) {
            return pos.immutable();
         }
      }
      return null;
   }
}
