package org.millenaire.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.HemelwachterEntity;
import org.millenaire.entity.ModEntities;
import org.slf4j.Logger;

/**
 * Dev-only automated visual preview for the Hemelwachter.
 * Activate with: -Dmillenaire.hemelwachterPreview=true
 */
@OnlyIn(Dist.CLIENT)
public final class HemelwachterPreviewSession {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WORLD = "hemelwachter_preview";
   private static final String PROP = "millenaire.hemelwachterPreview";

   private enum Phase {
      BOOT,
      WAIT_WORLD,
      STAGE,
      ORBIT,
      DONE
   }

   private static volatile Phase phase = Phase.BOOT;
   private static boolean worldRequested;
   private static boolean stageRequested;
   private static int settleTicks;
   private static int orbitIndex;
   private static int orbitTicks;
   private static volatile UUID bossId;
   private static String pendingCapture;
   private static boolean captureDoneForAngle;

   private HemelwachterPreviewSession() {
   }

   public static boolean enabled() {
      return Boolean.getBoolean(PROP);
   }

   public static void tick(Minecraft mc) {
      if (!enabled()) {
         return;
      }
      try {
         tickInner(mc);
      } catch (Exception e) {
         LOGGER.error("[HemelwachterPreview] tick failed", e);
      }
   }

   private static void tickInner(Minecraft mc) {
      switch (phase) {
         case BOOT -> {
            ensureWorld(mc);
            phase = Phase.WAIT_WORLD;
            LOGGER.info("[HemelwachterPreview] Boot → waiting for world");
         }
         case WAIT_WORLD -> {
            if (mc.level != null && mc.player != null && mc.hasSingleplayerServer()) {
               settleTicks++;
               if (settleTicks > 80) {
                  LOGGER.info("[HemelwachterPreview] World ready → staging");
                  phase = Phase.STAGE;
                  settleTicks = 0;
               }
            }
         }
         case STAGE -> {
            if (bossId != null && findBoss(mc) != null) {
               LocalPlayer player = mc.player;
               if (player != null) {
                  player.getAbilities().flying = true;
                  player.getAbilities().mayfly = true;
                  player.onUpdateAbilities();
               }
               phase = Phase.ORBIT;
               orbitIndex = 0;
               orbitTicks = 0;
               captureDoneForAngle = false;
               LOGGER.info("[HemelwachterPreview] Orbit start");
               return;
            }
            if (stageRequested) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[HemelwachterPreview] Staging timed out");
                  finish(mc, 1);
               }
               return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) {
               return;
            }
            stageRequested = true;
            settleTicks = 0;
            server.execute(() -> stageWorld(server));
         }
         case ORBIT -> {
            HemelwachterEntity boss = findBoss(mc);
            LocalPlayer player = mc.player;
            if (boss == null || player == null) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[HemelwachterPreview] Lost Hemelwachter/player");
                  finish(mc, 1);
               }
               return;
            }
            poseCamera(player, boss, orbitIndex);
            orbitTicks++;
            if (orbitTicks == 20) {
               pendingCapture = angleName(orbitIndex);
               captureDoneForAngle = false;
            }
            if (orbitTicks >= 45 && captureDoneForAngle) {
               orbitIndex++;
               orbitTicks = 0;
               captureDoneForAngle = false;
               if (orbitIndex >= 6) {
                  boss.previewForceProvoked();
                  pendingCapture = "provoked_front";
                  orbitTicks = 0;
                  phase = Phase.DONE;
                  LOGGER.info("[HemelwachterPreview] Provoked capture queued");
               }
            }
         }
         case DONE -> {
            if (pendingCapture == null && captureDoneForAngle) {
               LOGGER.info("[HemelwachterPreview] Captures complete — exiting");
               finish(mc, 0);
            } else if (orbitTicks++ > 80) {
               finish(mc, pendingCapture == null ? 0 : 1);
            }
         }
      }
   }

   public static void onRenderFrame(RenderTarget target) {
      if (!enabled() || pendingCapture == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String name = pendingCapture;
      pendingCapture = null;
      try {
         Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("hemelwachter_preview");
         Files.createDirectories(dir);
         Path out = dir.resolve(name + ".png");
         NativeImage image = Screenshot.takeScreenshot(target);
         image.writeToFile(out);
         image.close();
         captureDoneForAngle = true;
         LOGGER.info("[HemelwachterPreview] Wrote {}", out.toAbsolutePath());
      } catch (Exception e) {
         LOGGER.error("[HemelwachterPreview] Screenshot failed for {}", name, e);
         captureDoneForAngle = true;
      }
   }

   private static void finish(Minecraft mc, int code) {
      phase = Phase.DONE;
      mc.execute(() -> Runtime.getRuntime().halt(code));
   }

   private static HemelwachterEntity findBoss(Minecraft mc) {
      if (mc.level == null) {
         return null;
      }
      AABB box = new AABB(-48, 0, -48, 48, 160, 48);
      if (bossId != null) {
         var e = mc.level.getEntitiesOfClass(HemelwachterEntity.class, box, ent -> ent.getUUID().equals(bossId));
         if (!e.isEmpty()) {
            return e.getFirst();
         }
      }
      List<HemelwachterEntity> all = mc.level.getEntitiesOfClass(HemelwachterEntity.class, box);
      return all.isEmpty() ? null : all.getFirst();
   }

   private static void ensureWorld(Minecraft mc) {
      if (worldRequested) {
         return;
      }
      worldRequested = true;
      String worldName = WORLD + "_" + System.currentTimeMillis();
      LOGGER.info("[HemelwachterPreview] Creating world {}", worldName);
      LevelSettings settings = new LevelSettings(
         worldName, GameType.CREATIVE, false, Difficulty.HARD, true, new GameRules(), WorldDataConfiguration.DEFAULT
      );
      WorldOptions options = new WorldOptions(42L, false, false);
      mc.createWorldOpenFlows().createFreshLevel(worldName, settings, options, HemelwachterPreviewSession::voidDimensions, new TitleScreen());
   }

   private static WorldDimensions voidDimensions(RegistryAccess registryAccess) {
      HolderGetter<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
      FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.empty(), biomes.getOrThrow(Biomes.THE_VOID), List.of());
      WorldDimensions normal = ((WorldPreset)registryAccess.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value())
         .createWorldDimensions();
      return normal.replaceOverworldGenerator(registryAccess, new FlatLevelSource(flat));
   }

   private static void stageWorld(MinecraftServer server) {
      try {
         ServerLevel level = server.overworld();
         BlockPos origin = new BlockPos(0, 64, 0);
         for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
               level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
               for (int y = 0; y < 8; y++) {
                  level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
         level.setDayTime(18000L);
         EntityType<HemelwachterEntity> type = ModEntities.HEMELWACHTER.get();
         HemelwachterEntity entity = type.create(level);
         if (entity == null) {
            LOGGER.error("[HemelwachterPreview] Failed to create Hemelwachter");
            return;
         }
         entity.moveTo(0.5, 72.0, 0.5, 210.0F, 0.0F);
         entity.beginArrival(72.0);
         level.addFreshEntity(entity);
         bossId = entity.getUUID();
         LOGGER.info("[HemelwachterPreview] Staged Hemelwachter id={}", bossId);
      } catch (Exception e) {
         LOGGER.error("[HemelwachterPreview] stageWorld failed", e);
      }
   }

   private static void poseCamera(LocalPlayer player, HemelwachterEntity boss, int index) {
      double angle = index * (Math.PI / 3.0);
      double radius = 18.0;
      double x = boss.getX() + Math.sin(angle) * radius;
      double z = boss.getZ() + Math.cos(angle) * radius;
      double y = boss.getY() + (index == 4 ? 14.0 : index == 5 ? -2.0 : 6.0);
      float yaw = (float)(Math.toDegrees(Math.atan2(boss.getX() - x, boss.getZ() - z)));
      float pitch = index == 4 ? 50.0F : index == 5 ? -40.0F : -8.0F;
      player.getAbilities().flying = true;
      player.moveTo(x, y, z, yaw, pitch);
      player.setDeltaMovement(0, 0, 0);
   }

   private static String angleName(int index) {
      return switch (index) {
         case 0 -> "front";
         case 1 -> "front_right";
         case 2 -> "side_right";
         case 3 -> "back";
         case 4 -> "above";
         case 5 -> "below";
         default -> "angle_" + index;
      };
   }
}
