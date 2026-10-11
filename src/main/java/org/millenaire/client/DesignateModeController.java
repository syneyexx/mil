package org.millenaire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.network.BuildingProjectListPayload;
import org.millenaire.network.DesignateBuildingRequestPayload;
import org.millenaire.village.DesignatedBuildingService;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class DesignateModeController {
   private static boolean active;
   private static String villageUuid = "";
   private static BuildingPurpose purpose = BuildingPurpose.HOUSE;
   private static int centerX;
   private static int centerY;
   private static int centerZ;
   private static int radius;
   private static List<BuildingProjectListPayload.Box> occupied = List.of();
   private static DesignateModeController.Phase phase = DesignateModeController.Phase.CORNER_A;
   private static BlockPos cornerA;
   private static BlockPos cornerB;
   private static int previewHeight = DesignatedBuildingService.MIN_HEIGHT;
   private static String actionbarSig;
   private static final double AIM_RANGE = 128.0;

   private DesignateModeController() {
   }

   public static void arm(BuildingProjectListPayload ctx, BuildingPurpose selectedPurpose) {
      if (AdjustModeController.isActive()) {
         AdjustModeController.disarm();
      }

      villageUuid = ctx.villageUuid();
      purpose = selectedPurpose;
      centerX = ctx.villageCenter().getX();
      centerY = ctx.villageCenter().getY();
      centerZ = ctx.villageCenter().getZ();
      radius = ctx.villageRadius();
      occupied = ctx.occupied();
      phase = DesignateModeController.Phase.CORNER_A;
      cornerA = null;
      cornerB = null;
      previewHeight = DesignatedBuildingService.minHeightFor(purpose);
      actionbarSig = null;
      active = true;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.displayClientMessage(Component.translatable("gui.millenaire.designate.help_corner_a").withStyle(ChatFormatting.GRAY), false);
         mc.player.displayClientMessage(Component.translatable("gui.millenaire.designate.help_cancel").withStyle(ChatFormatting.GRAY), false);
      }
   }

   public static boolean isActive() {
      return active;
   }

   public static void disarm() {
      active = false;
      villageUuid = "";
      cornerA = null;
      cornerB = null;
      phase = DesignateModeController.Phase.CORNER_A;
      actionbarSig = null;
   }

   @SubscribeEvent
   public static void onLogout(LoggingOut event) {
      if (active) {
         disarm();
      }
   }

   @SubscribeEvent
   public static void onClone(Clone event) {
      if (active) {
         disarm();
      }
   }

   @SubscribeEvent
   public static void onScreenOpening(Opening event) {
      if (active && event.getScreen() instanceof PauseScreen) {
         event.setCanceled(true);
         disarm();
      }
   }

   @SubscribeEvent
   public static void onInteraction(InteractionKeyMappingTriggered event) {
      if (!active) {
         return;
      }

      if (event.isUseItem()) {
         event.setCanceled(true);
         event.setSwingHand(false);
         disarm();
         return;
      }

      if (!event.isAttack()) {
         return;
      }

      event.setCanceled(true);
      event.setSwingHand(false);
      BlockPos aimed = currentAnchor();
      if (aimed == null) {
         return;
      }

      if (phase == DesignateModeController.Phase.CORNER_A) {
         cornerA = aimed.immutable();
         phase = DesignateModeController.Phase.CORNER_B;
         actionbarSig = null;
         return;
      }

      if (phase == DesignateModeController.Phase.CORNER_B) {
         cornerB = aimed.immutable();
         previewHeight = estimateHeightClient(cornerA, cornerB);
         phase = DesignateModeController.Phase.CONFIRM;
         actionbarSig = null;
         return;
      }

      if (phase == DesignateModeController.Phase.CONFIRM) {
         if (cornerA == null || cornerB == null) {
            disarm();
            return;
         }

         BlockPos min = new BlockPos(
            Math.min(cornerA.getX(), cornerB.getX()), Math.min(cornerA.getY(), cornerB.getY()), Math.min(cornerA.getZ(), cornerB.getZ())
         );
         BlockPos max = new BlockPos(
            Math.max(cornerA.getX(), cornerB.getX()), Math.max(cornerA.getY(), cornerB.getY()), Math.max(cornerA.getZ(), cornerB.getZ())
         );
         int width = max.getX() - min.getX() + 1;
         int depth = max.getZ() - min.getZ() + 1;
         boolean sizeOk = width >= DesignatedBuildingService.MIN_WIDTH
            && depth >= DesignatedBuildingService.MIN_DEPTH
            && previewHeight >= DesignatedBuildingService.minHeightFor(purpose)
            && width <= DesignatedBuildingService.MAX_WIDTH
            && depth <= DesignatedBuildingService.MAX_DEPTH;
         boolean inRadius = withinRadius(min.getX(), min.getZ(), max.getX(), max.getZ());
         boolean clash = overlapsOccupied(min.getX(), min.getZ(), max.getX() + 1, max.getZ() + 1);
         if (!sizeOk || !inRadius || clash) {
            return;
         }

         PacketDistributor.sendToServer(
            new DesignateBuildingRequestPayload(villageUuid, purpose.id(), cornerA, cornerB), new CustomPacketPayload[0]
         );
         disarm();
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (!active || event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) {
         return;
      }

      PoseStack pose = event.getPoseStack();
      Vec3 cam = event.getCamera().getPosition();
      BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      VertexConsumer vc = buffers.getBuffer(GhostRenderTypes.LINES_NO_DEPTH);
      pose.pushPose();
      pose.translate(-cam.x, -cam.y, -cam.z);

      AABB villageBound = new AABB(centerX - radius, centerY - 1.0, centerZ - radius, centerX + radius + 1.0, centerY, centerZ + radius + 1.0);
      LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), villageBound, 0.3F, 0.5F, 0.95F, 0.6F);

      for (BuildingProjectListPayload.Box b : occupied) {
         double by = b.surfaceY() + 1.0;
         AABB box = new AABB(b.minX(), by, b.minZ(), b.maxX(), by + 1.0, b.maxZ());
         LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.75F, 0.75F, 0.75F, 0.35F);
      }

      BlockPos a = cornerA;
      BlockPos b = phase == DesignateModeController.Phase.CORNER_A ? currentAnchor() : cornerB != null ? cornerB : currentAnchor();
      if (a != null && b != null) {
         BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
         BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
         int width = max.getX() - min.getX() + 1;
         int depth = max.getZ() - min.getZ() + 1;
         int minH = DesignatedBuildingService.minHeightFor(purpose);
         int height = phase == DesignateModeController.Phase.CONFIRM ? previewHeight : Math.max(minH, estimateHeightClient(a, b));
         boolean sizeOk = width >= DesignatedBuildingService.MIN_WIDTH
            && depth >= DesignatedBuildingService.MIN_DEPTH
            && height >= minH
            && width <= DesignatedBuildingService.MAX_WIDTH
            && depth <= DesignatedBuildingService.MAX_DEPTH;
         boolean inRadius = withinRadius(min.getX(), min.getZ(), max.getX(), max.getZ());
         boolean clash = overlapsOccupied(min.getX(), min.getZ(), max.getX() + 1, max.getZ() + 1);
         boolean valid = sizeOk && inRadius && !clash;
         AABB ghost = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, min.getY() + height, max.getZ() + 1);
         if (valid) {
            LevelRenderer.renderLineBox(pose, vc, ghost, 0.2F, 0.9F, 0.2F, 0.9F);
         } else {
            LevelRenderer.renderLineBox(pose, vc, ghost, 0.9F, 0.2F, 0.2F, 0.9F);
         }

         updateActionbar(valid, sizeOk, inRadius, clash, width, depth, height);
      } else {
         updateActionbarSimple();
      }

      pose.popPose();
      buffers.endBatch(RenderType.lines());
      buffers.endBatch(GhostRenderTypes.LINES_NO_DEPTH);
   }

   private static void updateActionbarSimple() {
      String key = phase == DesignateModeController.Phase.CORNER_A
         ? "gui.millenaire.designate.hint_corner_a"
         : "gui.millenaire.designate.hint_corner_b";
      pushActionbar(key, Component.translatable(key));
   }

   private static void updateActionbar(boolean valid, boolean sizeOk, boolean inRadius, boolean clash, int width, int depth, int height) {
      if (phase == DesignateModeController.Phase.CONFIRM) {
         if (valid) {
            pushActionbar(
               "confirm:" + width + "x" + depth + "x" + height,
               Component.translatable(
                  "gui.millenaire.designate.hint_confirm",
                  width,
                  depth,
                  height,
                  Component.translatable("gui.millenaire.designate.purpose." + purpose.id())
               )
            );
         } else if (!sizeOk) {
            pushActionbar("badsize", Component.translatable("gui.millenaire.designate.hint_bad_size"));
         } else if (!inRadius) {
            pushActionbar("radius", Component.translatable("gui.millenaire.designate.hint_outside_radius"));
         } else {
            pushActionbar("clash", Component.translatable("gui.millenaire.designate.hint_clash"));
         }
         return;
      }

      updateActionbarSimple();
   }

   private static void pushActionbar(String sig, Component msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !Objects.equals(sig, actionbarSig)) {
         actionbarSig = sig;
         mc.player.displayClientMessage(msg, true);
      }
   }

   private static int estimateHeightClient(BlockPos a, BlockPos b) {
      Minecraft mc = Minecraft.getInstance();
      int minH = DesignatedBuildingService.minHeightFor(purpose);
      if (mc.level == null) {
         return minH;
      }

      BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
      BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
      int width = max.getX() - min.getX() + 1;
      int depth = max.getZ() - min.getZ() + 1;
      int maxPossible = Math.min(DesignatedBuildingService.MAX_HEIGHT, mc.level.getMaxBuildHeight() - min.getY());
      int height = 0;

      for (int dy = 0; dy < maxPossible; dy++) {
         boolean found = false;
         for (int dx = 0; dx < width && !found; dx++) {
            for (int dz = 0; dz < depth && !found; dz++) {
               if (!mc.level.getBlockState(min.offset(dx, dy, dz)).isAir()) {
                  found = true;
               }
            }
         }

         if (found) {
            height = dy + 1;
         } else if (dy >= minH) {
            break;
         }
      }

      return Math.max(height, minH);
   }

   private static boolean withinRadius(int minX, int minZ, int maxX, int maxZ) {
      return !outsideRadius(minX, minZ) && !outsideRadius(maxX, minZ) && !outsideRadius(minX, maxZ) && !outsideRadius(maxX, maxZ);
   }

   private static boolean outsideRadius(int x, int z) {
      long dx = x - centerX;
      long dz = z - centerZ;
      return dx * dx + dz * dz > (long)radius * radius;
   }

   private static boolean overlapsOccupied(int minX, int minZ, int maxX, int maxZ) {
      for (BuildingProjectListPayload.Box b : occupied) {
         if (maxX > b.minX() && minX < b.maxX() && maxZ > b.minZ() && minZ < b.maxZ()) {
            return true;
         }
      }

      return false;
   }

   private static BlockPos currentAnchor() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return null;
      }

      Vec3 eye = mc.player.getEyePosition();
      Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(AIM_RANGE));
      BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, mc.player));
      return hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
   }

   private enum Phase {
      CORNER_A,
      CORNER_B,
      CONFIRM
   }
}
