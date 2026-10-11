package org.millenaire.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.io.IOException;
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
import org.millenaire.entity.ModEntities;
import org.millenaire.entity.NetherWraithEntity;
import org.slf4j.Logger;

/**
 * Dev-only automated visual preview for the Nether Wraith.
 * Activate with: -Dmillenaire.wraithPreview=true
 */
@OnlyIn(Dist.CLIENT)
public final class WraithPreviewSession {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WORLD = "wraith_preview";
   private static final String PROP = "millenaire.wraithPreview";

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
   private static volatile UUID wraithId;
   private static String pendingCapture;
   private static boolean captureDoneForAngle;

   private WraithPreviewSession() {
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
         LOGGER.error("[WraithPreview] tick failed", e);
      }
   }

   private static void tickInner(Minecraft mc) {
      switch (phase) {
         case BOOT -> {
            ensureWorld(mc);
            phase = Phase.WAIT_WORLD;
            LOGGER.info("[WraithPreview] Boot → waiting for world");
         }
         case WAIT_WORLD -> {
            if (mc.level != null && mc.player != null && mc.hasSingleplayerServer()) {
               settleTicks++;
               if (settleTicks > 80) {
                  LOGGER.info("[WraithPreview] World ready → staging");
                  phase = Phase.STAGE;
                  settleTicks = 0;
               }
            }
         }
         case STAGE -> {
            if (wraithId != null && findWraith(mc) != null) {
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
               LOGGER.info("[WraithPreview] Orbit start");
               return;
            }
            if (stageRequested) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[WraithPreview] Staging timed out");
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
            NetherWraithEntity wraith = findWraith(mc);
            LocalPlayer player = mc.player;
            if (wraith == null || player == null) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[WraithPreview] Lost wraith/player");
                  finish(mc, 1);
               }
               return;
            }
            poseCamera(player, wraith, orbitIndex);
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
                  pendingCapture = "attack_pose";
                  wraith.handleEntityEvent((byte)4);
                  // one more settle then exit from render callback
                  orbitTicks = 0;
                  phase = Phase.DONE;
                  LOGGER.info("[WraithPreview] Final attack capture queued");
               }
            }
         }
         case DONE -> {
            if (pendingCapture == null && captureDoneForAngle) {
               LOGGER.info("[WraithPreview] Captures complete — exiting");
               finish(mc, 0);
            } else if (orbitTicks++ > 80) {
               // force exit even if last capture missed
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
         Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("wraith_preview");
         Files.createDirectories(dir);
         Path out = dir.resolve(name + ".png");
         NativeImage image = Screenshot.takeScreenshot(target);
         image.writeToFile(out);
         image.close();
         captureDoneForAngle = true;
         LOGGER.info("[WraithPreview] Wrote {}", out.toAbsolutePath());
      } catch (Exception e) {
         LOGGER.error("[WraithPreview] Screenshot failed for {}", name, e);
         captureDoneForAngle = true;
      }
   }

   private static void finish(Minecraft mc, int code) {
      phase = Phase.DONE;
      mc.execute(() -> Runtime.getRuntime().halt(code));
   }

   private static NetherWraithEntity findWraith(Minecraft mc) {
      if (mc.level == null) {
         return null;
      }
      if (wraithId != null) {
         var e = mc.level.getEntitiesOfClass(
            NetherWraithEntity.class, new AABB(-32, 0, -32, 32, 128, 32), ent -> ent.getUUID().equals(wraithId)
         );
         if (!e.isEmpty()) {
            return e.getFirst();
         }
      }
      List<NetherWraithEntity> all = mc.level.getEntitiesOfClass(NetherWraithEntity.class, new AABB(-32, 0, -32, 32, 128, 32));
      return all.isEmpty() ? null : all.getFirst();
   }

   private static void ensureWorld(Minecraft mc) {
      if (worldRequested) {
         return;
      }
      worldRequested = true;
      String worldName = WORLD + "_" + System.currentTimeMillis();
      LOGGER.info("[WraithPreview] Creating world {}", worldName);
      // HARD — Peaceful despawns Monster subclasses (including Nether Wraith)
      LevelSettings settings = new LevelSettings(
         worldName, GameType.CREATIVE, false, Difficulty.HARD, true, new GameRules(), WorldDataConfiguration.DEFAULT
      );
      WorldOptions options = new WorldOptions(42L, false, false);
      mc.createWorldOpenFlows().createFreshLevel(worldName, settings, options, WraithPreviewSession::voidDimensions, new TitleScreen());
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
         for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
               level.setBlock(origin.offset(x, -1, z), Blocks.BLACKSTONE.defaultBlockState(), 3);
               for (int y = 0; y < 4; y++) {
                  level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
         for (int y = 0; y < 7; y++) {
            for (int x = -7; x <= 7; x++) {
               level.setBlock(origin.offset(x, y, -7), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
               level.setBlock(origin.offset(x, y, 7), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
            for (int z = -7; z <= 7; z++) {
               level.setBlock(origin.offset(-7, y, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
               level.setBlock(origin.offset(7, y, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            }
         }
         level.setDayTime(18000L);
         EntityType<NetherWraithEntity> type = ModEntities.NETHER_WRAITH.get();
         NetherWraithEntity entity = type.create(level);
         if (entity == null) {
            LOGGER.error("[WraithPreview] Failed to create Nether Wraith");
            return;
         }
         entity.moveTo(0.5, 64.0, 0.5, 210.0F, 0.0F);
         level.addFreshEntity(entity);
         wraithId = entity.getUUID();
         LOGGER.info("[WraithPreview] Staged wraith id={}", wraithId);
      } catch (Exception e) {
         LOGGER.error("[WraithPreview] stageWorld failed", e);
      }
   }

   private static void poseCamera(LocalPlayer player, NetherWraithEntity wraith, int index) {
      double angle = index * (Math.PI / 3.0);
      double radius = 5.8;
      double x = wraith.getX() + Math.sin(angle) * radius;
      double z = wraith.getZ() + Math.cos(angle) * radius;
      double y = wraith.getY() + (index == 4 ? 5.8 : index == 5 ? 0.4 : 2.4);
      float yaw = (float)(Math.toDegrees(Math.atan2(wraith.getX() - x, wraith.getZ() - z)));
      float pitch = index == 4 ? 50.0F : index == 5 ? -40.0F : -10.0F;
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
