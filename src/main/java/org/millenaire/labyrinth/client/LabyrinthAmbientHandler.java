package org.millenaire.labyrinth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthAtmosphere;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;

/**
 * Client ambient particles and sounds inside the Labyrinth.
 * Cold palette: soul / white ash / chain clicks. Classic: warmer ash.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID, value = Dist.CLIENT)
public final class LabyrinthAmbientHandler {
   private LabyrinthAmbientHandler() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null) {
         return;
      }
      if (!minecraft.level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         return;
      }
      if (minecraft.isPaused()) {
         return;
      }

      RandomSource random = minecraft.level.getRandom();
      boolean cold = isCold();

      if (random.nextInt(cold ? 28 : 40) == 0) {
         double x = player.getX() + (random.nextDouble() - 0.5) * 10.0;
         double y = player.getY() + random.nextDouble() * 2.5;
         double z = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
         if (cold) {
            minecraft.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, 0.005, 0.0);
         } else {
            minecraft.level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, 0.01, 0.0);
         }
      }
      if (random.nextInt(cold ? 90 : 120) == 0) {
         minecraft.level.addParticle(
            ParticleTypes.SOUL,
            player.getX() + (random.nextDouble() - 0.5) * 8.0,
            player.getY() + 0.2,
            player.getZ() + (random.nextDouble() - 0.5) * 8.0,
            0.0,
            0.02,
            0.0
         );
      }
      if (random.nextInt(220) == 0) {
         minecraft.level.addParticle(
            ParticleTypes.DRIPPING_WATER,
            player.getX() + (random.nextDouble() - 0.5) * 6.0,
            player.getY() + 2.5,
            player.getZ() + (random.nextDouble() - 0.5) * 6.0,
            0.0,
            0.0,
            0.0
         );
      }

      int huntNearby = countHunting(minecraft, player);
      int chainChance = huntNearby > 0 ? 80 : 320;
      if (random.nextInt(chainChance) == 0) {
         float volume = 0.25F + Math.min(0.45F, huntNearby * 0.08F);
         minecraft.level.playLocalSound(
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.CHAIN_FALL,
            SoundSource.AMBIENT,
            volume,
            0.55F + random.nextFloat() * 0.35F,
            false
         );
      }
      if (random.nextInt(cold ? 360 : 400) == 0) {
         minecraft.level.playLocalSound(
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.AMBIENT_CAVE.value(),
            SoundSource.AMBIENT,
            0.3F,
            cold ? 0.55F + random.nextFloat() * 0.25F : 0.7F + random.nextFloat() * 0.3F,
            false
         );
      }
      if (huntNearby > 0 && random.nextInt(100) == 0) {
         // Rapid bone clicks stand in for "chatter" without franchise audio.
         minecraft.level.playLocalSound(
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.SKELETON_AMBIENT,
            SoundSource.HOSTILE,
            0.35F,
            1.6F + random.nextFloat() * 0.4F,
            false
         );
      }
   }

   private static int countHunting(Minecraft minecraft, LocalPlayer player) {
      if (minecraft.level == null) {
         return 0;
      }
      AABB box = player.getBoundingBox().inflate(24.0);
      return minecraft.level.getEntitiesOfClass(
         GebondeneEntity.class,
         box,
         e -> e.getAttitude() == GebondeneAttitude.JACHT || e.getAttitude() == GebondeneAttitude.GEALARMEERD
      ).size();
   }

   private static boolean isCold() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthAtmosphere.get() != LabyrinthAtmosphere.CLASSIC;
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
