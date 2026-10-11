package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Shared Overworld night-only natural spawn gate for infernal cult hostiles.
 * Natural daytime spawns (including dark caves during day) are rejected.
 */
public final class InfernalNightSpawnRules {
   private InfernalNightSpawnRules() {
   }

   public static boolean canNaturalSpawnAtNight(
      EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      if (level.getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      }
      if (isForcedSpawn(spawnType)) {
         return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
      }
      return isOverworldNight(level) && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
   }

   public static boolean isForcedSpawn(MobSpawnType spawnType) {
      return spawnType == MobSpawnType.SPAWNER
         || spawnType == MobSpawnType.SPAWN_EGG
         || spawnType == MobSpawnType.COMMAND;
   }

   public static boolean isOverworldNight(ServerLevelAccessor level) {
      return level.getLevel().isNight();
   }

   /** Pure decision helper for unit tests — mirrors natural-spawn night gate without world access. */
   public static boolean allowsNaturalSpawn(boolean peaceful, boolean forcedSpawn, boolean isNight) {
      if (peaceful) {
         return false;
      }
      if (forcedSpawn) {
         return true;
      }
      return isNight;
   }
}
