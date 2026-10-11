package org.millenaire.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public final class FireplaceSmokeHandler {
   private static final double RENDER_DIST_SQ = 2304.0; // 48 blocks (was 64)
   private static final double EVICT_DIST_SQ = 9216.0; // 96 blocks
   private static final int EVICT_CHECK_INTERVAL = 20;
   private static final long SMOKE_START_TIME = 9000L;
   private static final long SMOKE_END_TIME = 16000L;
   /** Hard cap of campfire particles spawned per eligible client tick. */
   private static final int MAX_PARTICLES_PER_TICK = 8;
   private static final Map<UUID, FireplaceSmokeHandler.FireplaceVillageData> knownPositions = new HashMap<>();
   private static ResourceKey<Level> lastDimension = null;

   private FireplaceSmokeHandler() {
   }

   public static void updatePositions(UUID villageId, BlockPos center, List<BlockPos> positions) {
      if (positions.isEmpty()) {
         knownPositions.remove(villageId);
      } else {
         knownPositions.put(villageId, new FireplaceSmokeHandler.FireplaceVillageData(center, positions));
      }
   }

   public static void clearAll() {
      knownPositions.clear();
      lastDimension = null;
   }

   public static void tick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Level level = player.level();
         ResourceKey<Level> currentDimension = level.dimension();
         if (lastDimension != null && !lastDimension.equals(currentDimension)) {
            knownPositions.clear();
         }

         lastDimension = currentDimension;
         long gameTime = level.getGameTime();
         if (gameTime % 2L == 0L) {
            long dayTime = level.getDayTime() % 24000L;
            if (dayTime >= 9000L && dayTime <= 16000L) {
               if (gameTime % 20L == 0L) {
                  evictDistant(player);
               }

               RandomSource random = level.getRandom();
               int spawned = 0;
               int subsamplePhase = (int)Math.floorMod(gameTime / 2L, 4L);

               for (FireplaceSmokeHandler.FireplaceVillageData data : knownPositions.values()) {
                  List<BlockPos> positions = data.positions();
                  for (int i = 0; i < positions.size(); i++) {
                     if (spawned >= MAX_PARTICLES_PER_TICK) {
                        return;
                     }
                     // Rotate which fireplaces emit so dense villages still smoke over time.
                     if (Math.floorMod(i, 4) != subsamplePhase) {
                        continue;
                     }
                     BlockPos pos = positions.get(i);
                     double distSq = pos.distToCenterSqr(player.position());
                     if (distSq > RENDER_DIST_SQ || !level.isLoaded(pos)) {
                        continue;
                     }
                     // Farther fireplaces emit less often (every other eligible tick).
                     if (distSq > 1024.0 && (gameTime & 4L) != 0L) {
                        continue;
                     }
                     level.addAlwaysVisibleParticle(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        true,
                        pos.getX() + 0.5 + random.nextGaussian() * 0.15,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5 + random.nextGaussian() * 0.15,
                        0.0,
                        0.07,
                        0.0
                     );
                     spawned++;
                  }
               }
            }
         }
      }
   }

   private static void evictDistant(LocalPlayer player) {
      knownPositions.entrySet().removeIf(entry -> {
         BlockPos center = entry.getValue().center();
         return center.distToCenterSqr(player.position()) > EVICT_DIST_SQ;
      });
   }

   public record FireplaceVillageData(BlockPos center, List<BlockPos> positions) {
      public FireplaceVillageData(BlockPos center, List<BlockPos> positions) {
         this.center = center;
         this.positions = Collections.unmodifiableList(positions);
      }
   }
}
