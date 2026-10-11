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
import org.millenaire.entity.AntichristPriestEntity;
import org.millenaire.entity.InfernalCultistEntity;
import org.millenaire.entity.ModEntities;
import org.slf4j.Logger;

/**
 * Dev-only automated visual preview for Antichrist Priest and Infernal Cultist.
 * Activate with: -Dmillenaire.cultPreview=true
 */
@OnlyIn(Dist.CLIENT)
public final class InfernalCultPreviewSession {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WORLD = "cult_preview";
   private static final String PROP = "millenaire.cultPreview";

   private enum Phase {
      BOOT,
      WAIT_WORLD,
      STAGE,
      ORBIT_PRIEST,
      ORBIT_CULTIST,
      DONE
   }

   private static volatile Phase phase = Phase.BOOT;
   private static boolean worldRequested;
   private static boolean stageRequested;
   private static int settleTicks;
   private static int orbitIndex;
   private static int orbitTicks;
   private static volatile UUID priestId;
   private static volatile UUID cultistId;
   private static String pendingCapture;
   private static boolean captureDoneForAngle;
   private static boolean darkPass;

   private InfernalCultPreviewSession() {
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
         LOGGER.error("[CultPreview] tick failed", e);
      }
   }

   private static void tickInner(Minecraft mc) {
      switch (phase) {
         case BOOT -> {
            ensureWorld(mc);
            phase = Phase.WAIT_WORLD;
            LOGGER.info("[CultPreview] Boot → waiting for world");
         }
         case WAIT_WORLD -> {
            if (mc.level != null && mc.player != null && mc.hasSingleplayerServer()) {
               settleTicks++;
               if (settleTicks > 80) {
                  LOGGER.info("[CultPreview] World ready → staging");
                  phase = Phase.STAGE;
                  settleTicks = 0;
               }
            }
         }
         case STAGE -> {
            if (priestId != null && cultistId != null && findPriest(mc) != null && findCultist(mc) != null) {
               LocalPlayer player = mc.player;
               if (player != null) {
                  player.getAbilities().flying = true;
                  player.getAbilities().mayfly = true;
                  player.onUpdateAbilities();
               }
               phase = Phase.ORBIT_PRIEST;
               orbitIndex = 0;
               orbitTicks = 0;
               captureDoneForAngle = false;
               darkPass = false;
               LOGGER.info("[CultPreview] Orbit priest start");
               return;
            }
            if (stageRequested) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[CultPreview] Staging timed out");
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
         case ORBIT_PRIEST -> orbitEntity(mc, true);
         case ORBIT_CULTIST -> orbitEntity(mc, false);
         case DONE -> {
            if (pendingCapture == null && captureDoneForAngle) {
               LOGGER.info("[CultPreview] Captures complete — exiting");
               finish(mc, 0);
            } else if (orbitTicks++ > 80) {
               finish(mc, pendingCapture == null ? 0 : 1);
            }
         }
      }
   }

   private static void orbitEntity(Minecraft mc, boolean priestPass) {
      AntichristPriestEntity priest = findPriest(mc);
      InfernalCultistEntity cultist = findCultist(mc);
      LocalPlayer player = mc.player;
      if (player == null || (priestPass ? priest == null : cultist == null)) {
         settleTicks++;
         if (settleTicks > 200) {
            LOGGER.error("[CultPreview] Lost subject/player");
            finish(mc, 1);
         }
         return;
      }
      double subjectX = priestPass ? priest.getX() : cultist.getX();
      double subjectY = priestPass ? priest.getY() : cultist.getY();
      double subjectZ = priestPass ? priest.getZ() : cultist.getZ();
      double heightBias = priestPass ? 2.6 : 1.7;
      double radius = priestPass ? 6.2 : 4.6;
      poseCamera(player, subjectX, subjectY, subjectZ, heightBias, radius, orbitIndex);
      orbitTicks++;
      if (orbitTicks == 18) {
         String prefix = priestPass ? "priest_" : "cultist_";
         String suffix = darkPass ? "_dark" : "";
         pendingCapture = prefix + angleName(orbitIndex) + suffix;
         captureDoneForAngle = false;
      }
      if (orbitTicks >= 40 && captureDoneForAngle) {
         orbitIndex++;
         orbitTicks = 0;
         captureDoneForAngle = false;
         if (orbitIndex >= 6) {
            if (!darkPass) {
               darkPass = true;
               orbitIndex = 0;
               MinecraftServer server = mc.getSingleplayerServer();
               if (server != null) {
                  server.execute(() -> {
                     server.overworld().setDayTime(18000L);
                     for (int x = -8; x <= 8; x++) {
                        for (int z = -8; z <= 8; z++) {
                           server.overworld().setBlock(new BlockPos(x, 70, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
                        }
                     }
                  });
               }
               LOGGER.info("[CultPreview] Dark pass for {}", priestPass ? "priest" : "cultist");
               return;
            }
            darkPass = false;
            orbitIndex = 0;
            if (priestPass) {
               if (priest != null) {
                  priest.handleEntityEvent((byte)5);
               }
               pendingCapture = "priest_cast_pose";
               phase = Phase.ORBIT_CULTIST;
               LOGGER.info("[CultPreview] Switching to cultist orbit");
            } else {
               if (cultist != null) {
                  cultist.handleEntityEvent((byte)4);
               }
               pendingCapture = "cultist_attack_pose";
               phase = Phase.DONE;
               LOGGER.info("[CultPreview] Final cultist capture queued");
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
         Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("cult_preview");
         Files.createDirectories(dir);
         Path out = dir.resolve(name + ".png");
         NativeImage image = Screenshot.takeScreenshot(target);
         image.writeToFile(out);
         image.close();
         captureDoneForAngle = true;
         LOGGER.info("[CultPreview] Wrote {}", out.toAbsolutePath());
      } catch (Exception e) {
         LOGGER.error("[CultPreview] Screenshot failed for {}", name, e);
         captureDoneForAngle = true;
      }
   }

   private static void finish(Minecraft mc, int code) {
      phase = Phase.DONE;
      mc.execute(() -> Runtime.getRuntime().halt(code));
   }

   private static AntichristPriestEntity findPriest(Minecraft mc) {
      if (mc.level == null || priestId == null) {
         return null;
      }
      List<AntichristPriestEntity> found = mc.level.getEntitiesOfClass(
         AntichristPriestEntity.class, new AABB(-48, 0, -48, 48, 128, 48), e -> e.getUUID().equals(priestId)
      );
      return found.isEmpty() ? null : found.getFirst();
   }

   private static InfernalCultistEntity findCultist(Minecraft mc) {
      if (mc.level == null || cultistId == null) {
         return null;
      }
      List<InfernalCultistEntity> found = mc.level.getEntitiesOfClass(
         InfernalCultistEntity.class, new AABB(-48, 0, -48, 48, 128, 48), e -> e.getUUID().equals(cultistId)
      );
      return found.isEmpty() ? null : found.getFirst();
   }

   private static void ensureWorld(Minecraft mc) {
      if (worldRequested) {
         return;
      }
      worldRequested = true;
      String worldName = WORLD + "_" + System.currentTimeMillis();
      LOGGER.info("[CultPreview] Creating world {}", worldName);
      LevelSettings settings = new LevelSettings(
         worldName, GameType.CREATIVE, false, Difficulty.HARD, true, new GameRules(), WorldDataConfiguration.DEFAULT
      );
      WorldOptions options = new WorldOptions(42L, false, false);
      mc.createWorldOpenFlows().createFreshLevel(worldName, settings, options, InfernalCultPreviewSession::voidDimensions, new TitleScreen());
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
         for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
               level.setBlock(origin.offset(x, -1, z), Blocks.BLACKSTONE.defaultBlockState(), 3);
               for (int y = 0; y < 5; y++) {
                  level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
         for (int y = 0; y < 8; y++) {
            for (int x = -8; x <= 8; x++) {
               level.setBlock(origin.offset(x, y, -8), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
               level.setBlock(origin.offset(x, y, 8), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
            for (int z = -8; z <= 8; z++) {
               level.setBlock(origin.offset(-8, y, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
               level.setBlock(origin.offset(8, y, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
         }
         level.setDayTime(13000L);

         EntityType<AntichristPriestEntity> priestType = ModEntities.ANTICHRIST_PRIEST.get();
         AntichristPriestEntity priest = priestType.create(level);
         if (priest == null) {
            LOGGER.error("[CultPreview] Failed to create Antichrist Priest");
            return;
         }
         priest.moveTo(-1.5, 64.0, 0.5, 200.0F, 0.0F);
         level.addFreshEntity(priest);
         priestId = priest.getUUID();

         EntityType<InfernalCultistEntity> cultistType = ModEntities.INFERNAL_CULTIST.get();
         InfernalCultistEntity cultist = cultistType.create(level);
         if (cultist == null) {
            LOGGER.error("[CultPreview] Failed to create Infernal Cultist");
            return;
         }
         cultist.moveTo(2.0, 64.0, 0.5, 160.0F, 0.0F);
         cultist.setVariant(InfernalCultistEntity.VARIANT_ZEALOT);
         level.addFreshEntity(cultist);
         cultistId = cultist.getUUID();
         LOGGER.info("[CultPreview] Staged priest={} cultist={}", priestId, cultistId);
      } catch (Exception e) {
         LOGGER.error("[CultPreview] stageWorld failed", e);
      }
   }

   private static void poseCamera(LocalPlayer player, double sx, double sy, double sz, double heightBias, double radius, int index) {
      double angle = index * (Math.PI / 3.0);
      double x = sx + Math.sin(angle) * radius;
      double z = sz + Math.cos(angle) * radius;
      double y = sy + (index == 4 ? heightBias + 3.2 : index == 5 ? 0.35 : heightBias);
      float yaw = (float)Math.toDegrees(Math.atan2(sx - x, sz - z));
      float pitch = index == 4 ? 48.0F : index == 5 ? -38.0F : -8.0F;
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
