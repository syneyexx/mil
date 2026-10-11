package org.millenaire.worldboss.spawn;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

@FunctionalInterface
public interface WorldBossSpawnStrategy {
   Optional<BlockPos> findSpawn(ServerLevel level, ServerPlayer anchor, RandomSource random);

   default boolean canConsider(ServerLevel level, ServerPlayer anchor) {
      return true;
   }
}
