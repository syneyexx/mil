package org.millenaire.labyrinth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Sparse client ambient particles/sounds inside the Labyrinth.
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
      if (random.nextInt(40) == 0) {
         double x = player.getX() + (random.nextDouble() - 0.5) * 10.0;
         double y = player.getY() + random.nextDouble() * 2.5;
         double z = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
         minecraft.level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, 0.01, 0.0);
      }
      if (random.nextInt(120) == 0) {
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
      if (random.nextInt(400) == 0) {
         minecraft.level.playLocalSound(
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.AMBIENT_CAVE.value(),
            SoundSource.AMBIENT,
            0.35F,
            0.7F + random.nextFloat() * 0.3F,
            false
         );
      }
   }
}
