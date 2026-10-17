package org.millenaire.labyrinth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Client presence bed: zone crossfade, chase proximity, De Ruit nadering.
 * Uses {@link LabyrinthSounds} placeholders (resource-pack replaceable).
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID, value = Dist.CLIENT)
public final class LabyrinthAmbientHandler {
   private static LabyrinthKeys.Zone lastZone = null;
   private static int zoneTicks;
   private static int droneCooldown;
   private static int metalCooldown;
   private static int whisperCooldown;
   private static int ruitCooldown;

   private LabyrinthAmbientHandler() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null) {
         lastZone = null;
         return;
      }
      if (!minecraft.level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         lastZone = null;
         return;
      }
      if (minecraft.isPaused()) {
         return;
      }

      RandomSource random = minecraft.level.getRandom();
      boolean cold = isCold();
      int[] cell = LabyrinthMaze.globalCellFromBlock(player.blockPosition().getX(), player.blockPosition().getZ());
      LabyrinthKeys.Zone zone = LabyrinthKeys.zoneAt(cell[0], cell[1]);
      if (zone != lastZone) {
         lastZone = zone;
         zoneTicks = 0;
         // Soft crossfade cue on zone enter.
         play(minecraft, player, LabyrinthSounds.DRONE.get(), 0.2F, pitchForZone(zone, random));
      }
      zoneTicks++;

      tickParticles(minecraft, player, random, cold, zone);
      tickZoneBed(minecraft, player, random, zone);
      tickChaseProximity(minecraft, player, random);
      tickRuitPresence(minecraft, player, random, zone, cell);
   }

   private static void tickParticles(
      Minecraft minecraft,
      LocalPlayer player,
      RandomSource random,
      boolean cold,
      LabyrinthKeys.Zone zone
   ) {
      int ashChance = switch (zone) {
         case ARRIVAL -> cold ? 22 : 36;
         case BLEED -> cold ? 18 : 30;
         case APPROACH, CORE -> cold ? 40 : 55;
         default -> cold ? 28 : 40;
      };
      if (random.nextInt(ashChance) == 0) {
         double x = player.getX() + (random.nextDouble() - 0.5) * 10.0;
         double y = player.getY() + random.nextDouble() * 2.5;
         double z = player.getZ() + (random.nextDouble() - 0.5) * 10.0;
         if (cold) {
            minecraft.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, 0.005, 0.0);
         } else {
            minecraft.level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, 0.01, 0.0);
         }
      }
      if (zone == LabyrinthKeys.Zone.BLEED && random.nextInt(70) == 0) {
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
      if (zone != LabyrinthKeys.Zone.APPROACH && zone != LabyrinthKeys.Zone.CORE && random.nextInt(220) == 0) {
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
   }

   private static void tickZoneBed(Minecraft minecraft, LocalPlayer player, RandomSource random, LabyrinthKeys.Zone zone) {
      if (droneCooldown > 0) {
         droneCooldown--;
      }
      if (metalCooldown > 0) {
         metalCooldown--;
      }
      if (whisperCooldown > 0) {
         whisperCooldown--;
      }

      int droneEvery = switch (zone) {
         case ARRIVAL -> 160;
         case BLEED -> 110;
         case ORDE -> 140;
         case APPROACH -> 200;
         case CORE -> 240;
      };
      if (droneCooldown <= 0 && (zoneTicks < 40 || random.nextInt(droneEvery) == 0)) {
         droneCooldown = droneEvery / 2 + random.nextInt(40);
         float vol = zone == LabyrinthKeys.Zone.APPROACH ? 0.12F : 0.28F;
         play(minecraft, player, LabyrinthSounds.DRONE.get(), vol, pitchForZone(zone, random));
      }

      if (zone == LabyrinthKeys.Zone.BLEED || zone == LabyrinthKeys.Zone.ORDE) {
         if (metalCooldown <= 0 && random.nextInt(90) == 0) {
            metalCooldown = 60 + random.nextInt(80);
            play(minecraft, player, LabyrinthSounds.METAL.get(), 0.22F, 0.55F + random.nextFloat() * 0.3F);
         }
      }

      if (zone == LabyrinthKeys.Zone.BLEED && whisperCooldown <= 0 && random.nextInt(100) == 0) {
         whisperCooldown = 80 + random.nextInt(100);
         play(minecraft, player, LabyrinthSounds.WHISPER.get(), 0.18F, 0.7F + random.nextFloat() * 0.4F);
      }

      // Legacy cave underlay (sparse) so headphones still feel wrong ≤10s in bleed.
      if (zone == LabyrinthKeys.Zone.BLEED && random.nextInt(280) == 0) {
         minecraft.level.playLocalSound(
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT,
            0.25F, 0.5F + random.nextFloat() * 0.2F, false
         );
      }
   }

   private static void tickChaseProximity(Minecraft minecraft, LocalPlayer player, RandomSource random) {
      int huntNearby = countHunting(minecraft, player);
      if (huntNearby <= 0) {
         return;
      }
      float proximity = Mth.clamp(huntNearby / 4.0F, 0.15F, 1.0F);
      int chainChance = Math.max(40, (int) (200 / (1.0F + proximity * 3.0F)));
      if (random.nextInt(chainChance) == 0) {
         play(minecraft, player, LabyrinthSounds.METAL.get(), 0.2F + proximity * 0.35F, 0.5F + random.nextFloat() * 0.3F);
      }
      if (random.nextInt(Math.max(50, 120 - huntNearby * 15)) == 0) {
         minecraft.level.playLocalSound(
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE,
            0.25F + proximity * 0.2F, 1.5F + random.nextFloat() * 0.4F, false
         );
      }
   }

   private static void tickRuitPresence(
      Minecraft minecraft,
      LocalPlayer player,
      RandomSource random,
      LabyrinthKeys.Zone zone,
      int[] cell
   ) {
      if (ruitCooldown > 0) {
         ruitCooldown--;
         return;
      }
      double dx = player.getX() - (LabyrinthKeys.audienceCenterBlockX() + 0.5);
      double dz = player.getZ() - (LabyrinthKeys.audienceCenterBlockZ() + 0.5);
      double dist = Math.sqrt(dx * dx + dz * dz);
      boolean nearAudience = LabyrinthKeys.inAudienceFootprint(cell[0], cell[1])
         || zone == LabyrinthKeys.Zone.APPROACH
         || zone == LabyrinthKeys.Zone.CORE;
      if (!nearAudience && dist > 96.0) {
         return;
      }
      // Louder / more frequent as distance shrinks (Ruit nadering curve).
      double closeness = Mth.clamp(1.0 - dist / 120.0, 0.0, 1.0);
      int chance = Math.max(40, (int) (180 - closeness * 140));
      if (random.nextInt(chance) == 0) {
         ruitCooldown = 50 + random.nextInt(40);
         float vol = (float) (0.12 + closeness * 0.45);
         float pitch = (float) (0.4 + closeness * 0.35);
         play(minecraft, player, LabyrinthSounds.RUIT_DRONE.get(), vol, pitch);
         if (closeness > 0.55 && random.nextBoolean()) {
            minecraft.level.addParticle(
               ParticleTypes.END_ROD,
               player.getX() + (random.nextDouble() - 0.5) * 4.0,
               player.getY() + 1.5 + random.nextDouble(),
               player.getZ() + (random.nextDouble() - 0.5) * 4.0,
               0.0, 0.01, 0.0
            );
         }
      }
   }

   private static void play(Minecraft minecraft, LocalPlayer player, SoundEvent sound, float volume, float pitch) {
      minecraft.level.playLocalSound(
         player.getX(), player.getY(), player.getZ(),
         sound, SoundSource.AMBIENT, volume, pitch, false
      );
   }

   private static float pitchForZone(LabyrinthKeys.Zone zone, RandomSource random) {
      float base = switch (zone) {
         case ARRIVAL -> 0.55F;
         case BLEED -> 0.45F;
         case ORDE -> 0.6F;
         case APPROACH -> 0.4F;
         case CORE -> 0.35F;
      };
      return base + random.nextFloat() * 0.15F;
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
