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

public final class CreateCompanionCapability implements CompanionCapability {
   @Override
   public String id() {
      return "create";
   }

   @Override
   public boolean isAvailable() {
      return ModList.get().isLoaded("create");
   }

   @Override
   public List<String> sensorLines(ServerLevel level, ServerPlayer player, MillVillager companion) {
      BlockPos machine = findNearbyCreate(level, companion.blockPosition(), 8);
      return List.of(
         "cap.create=loaded",
         "cap.create.nearby=" + (machine != null ? machine.toShortString() : "none")
      );
   }

   @Override
   @Nullable
   public String tryExecute(ServerLevel level, ServerPlayer player, MillVillager companion, CompanionIntent intent) {
      if (!"CREATE_INTERACT".equals(intent.type())) {
         return null;
      }
      BlockPos target = findNearbyCreate(level, companion.blockPosition(), 10);
      if (target == null) {
         return "CREATE_INTERACT failed: no Create block nearby";
      }
      companion.getNavManager().navigateTo(companion, target, 0.5);
      return CompanionWorldActor.useBlock(level, companion, target);
   }

   @Nullable
   private static BlockPos findNearbyCreate(ServerLevel level, BlockPos origin, int radius) {
      for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 4, radius))) {
         BlockState state = level.getBlockState(pos);
         var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
         if (key != null && "create".equals(key.getNamespace())) {
            return pos.immutable();
         }
      }
      return null;
   }
}
