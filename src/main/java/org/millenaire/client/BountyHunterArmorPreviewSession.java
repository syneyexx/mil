package org.millenaire.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
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
import org.millenaire.item.ModItems;
import org.slf4j.Logger;

/**
 * Dev-only visual QA for wearable Bounty Hunter armor on an armor stand.
 * Activate with: -Dmillenaire.bountyArmorPreview=true
 */
@OnlyIn(Dist.CLIENT)
public final class BountyHunterArmorPreviewSession {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WORLD = "bounty_armor_preview";
   private static final String PROP = "millenaire.bountyArmorPreview";

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
   private static volatile UUID standId;
   private static String pendingCapture;
   private static boolean captureDoneForAngle;

   private BountyHunterArmorPreviewSession() {
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
         LOGGER.error("[BountyArmorPreview] tick failed", e);
      }
   }

   private static void tickInner(Minecraft mc) {
      switch (phase) {
         case BOOT -> {
            ensureWorld(mc);
            phase = Phase.WAIT_WORLD;
            LOGGER.info("[BountyArmorPreview] Boot → waiting for world");
         }
         case WAIT_WORLD -> {
            if (mc.level != null && mc.player != null && mc.hasSingleplayerServer()) {
               settleTicks++;
               if (settleTicks > 80) {
                  phase = Phase.STAGE;
                  settleTicks = 0;
                  LOGGER.info("[BountyArmorPreview] World ready → staging");
               }
            }
         }
         case STAGE -> {
            if (standId != null && findStand(mc) != null) {
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
               LOGGER.info("[BountyArmorPreview] Orbit start");
               return;
            }
            if (stageRequested) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[BountyArmorPreview] Staging timed out");
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
            ArmorStand stand = findStand(mc);
            LocalPlayer player = mc.player;
            if (stand == null || player == null) {
               settleTicks++;
               if (settleTicks > 200) {
                  LOGGER.error("[BountyArmorPreview] Lost stand/player");
                  finish(mc, 1);
               }
               return;
            }
            poseCamera(player, stand, orbitIndex);
            orbitTicks++;
            if (orbitTicks == 24) {
               pendingCapture = angleName(orbitIndex);
               captureDoneForAngle = false;
            }
            if (orbitTicks >= 50 && captureDoneForAngle) {
               orbitIndex++;
               orbitTicks = 0;
               captureDoneForAngle = false;
               if (orbitIndex >= 6) {
                  phase = Phase.DONE;
               }
            }
         }
         case DONE -> {
            LOGGER.info("[BountyArmorPreview] Captures complete — exiting");
            finish(mc, 0);
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
         Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("bounty_armor_preview");
         Files.createDirectories(dir);
         Path out = dir.resolve(name + ".png");
         NativeImage image = Screenshot.takeScreenshot(target);
         image.writeToFile(out);
         image.close();
         captureDoneForAngle = true;
         LOGGER.info("[BountyArmorPreview] Wrote {}", out.toAbsolutePath());
      } catch (Exception e) {
         LOGGER.error("[BountyArmorPreview] Screenshot failed for {}", name, e);
         captureDoneForAngle = true;
      }
   }

   private static void finish(Minecraft mc, int code) {
      phase = Phase.DONE;
      mc.execute(() -> Runtime.getRuntime().halt(code));
   }

   private static ArmorStand findStand(Minecraft mc) {
      if (mc.level == null) {
         return null;
      }
      if (standId != null) {
         List<ArmorStand> found = mc.level.getEntitiesOfClass(
            ArmorStand.class, new AABB(-32, 0, -32, 32, 128, 32), ent -> ent.getUUID().equals(standId)
         );
         if (!found.isEmpty()) {
            return found.getFirst();
         }
      }
      List<ArmorStand> all = mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(-32, 0, -32, 32, 128, 32));
      return all.isEmpty() ? null : all.getFirst();
   }

   private static void ensureWorld(Minecraft mc) {
      if (worldRequested) {
         return;
      }
      worldRequested = true;
      String worldName = WORLD + "_" + System.currentTimeMillis();
      LOGGER.info("[BountyArmorPreview] Creating world {}", worldName);
      LevelSettings settings = new LevelSettings(
         worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT
      );
      WorldOptions options = new WorldOptions(42L, false, false);
      mc.createWorldOpenFlows().createFreshLevel(worldName, settings, options, BountyHunterArmorPreviewSession::voidDimensions, new TitleScreen());
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
         for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
               level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
               level.setBlock(origin.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            }
         }
         for (int y = 0; y < 5; y++) {
            for (int x = -6; x <= 6; x++) {
               level.setBlock(origin.offset(x, y, -6), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
            }
         }
         level.setDayTime(6000L);
         ArmorStand stand = new ArmorStand(level, 0.5, 64.0, 0.5);
         stand.setNoBasePlate(true);
         stand.setShowArms(true);
         stand.setYRot(210.0F);
         stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.BOUNTY_HUNTER_HELMET.get()));
         stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.BOUNTY_HUNTER_CHESTPLATE.get()));
         stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.BOUNTY_HUNTER_LEGGINGS.get()));
         stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.BOUNTY_HUNTER_BOOTS.get()));
         level.addFreshEntity(stand);
         standId = stand.getUUID();
         if (!server.getPlayerList().getPlayers().isEmpty()) {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.teleportTo(level, 4.5, 66.0, 4.5, Set.of(), 210.0F, 10.0F);
         }
         LOGGER.info("[BountyArmorPreview] Staged armor stand id={}", standId);
      } catch (Exception e) {
         LOGGER.error("[BountyArmorPreview] stageWorld failed", e);
      }
   }

   private static void poseCamera(LocalPlayer player, ArmorStand stand, int index) {
      // Fixed named angles aimed at stand center torso.
      double yawDeg = switch (index) {
         case 0 -> 0.0; // front
         case 1 -> 40.0; // 3/4 front
         case 2 -> 90.0; // right
         case 3 -> 180.0; // back
         case 4 -> 220.0; // 3/4 back
         default -> 270.0; // left
      };
      double radius = 4.0;
      double rad = Math.toRadians(yawDeg);
      double x = stand.getX() - Math.sin(rad) * radius;
      double z = stand.getZ() - Math.cos(rad) * radius;
      double y = stand.getY() + 1.45;
      float yaw = (float)(Math.toDegrees(Math.atan2(stand.getX() - x, stand.getZ() - z)));
      float pitch = -8.0F;
      player.getAbilities().flying = true;
      player.moveTo(x, y, z, yaw, pitch);
      player.setYRot(yaw);
      player.setXRot(pitch);
      player.yRotO = yaw;
      player.xRotO = pitch;
      player.setDeltaMovement(0, 0, 0);
   }

   private static String angleName(int index) {
      return switch (index) {
         case 0 -> "front";
         case 1 -> "three_quarter_front";
         case 2 -> "right";
         case 3 -> "back";
         case 4 -> "three_quarter_back";
         default -> "left";
      };
   }
}
