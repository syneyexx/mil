package org.millenaire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ScreenEvent.Opening;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.building.ClearMargins;
import org.millenaire.network.BuildingProjectListPayload;
import org.millenaire.network.BuildingProjectRequestPayload;
import org.millenaire.network.PlanPreviewPayload;
import org.millenaire.network.PlanPreviewRequestPayload;
import org.millenaire.world.PlacedLocation;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class AdjustModeController {
   private static boolean active;
   private static String villageUuid = "";
   private static String planSetId = "";
   private static int width;
   private static int depth;
   private static int buildingOrientation;
   private static int centerX;
   private static int centerY;
   private static int centerZ;
   private static int radius;
   private static List<BuildingProjectListPayload.Box> occupied = List.of();
   private static ClearMargins planMargins = ClearMargins.defaults();
   private static int rotation;
   private static int variantCount = 1;
   private static int variantIndex = -1;
   private static int requestedVariantIndex = -1;
   private static long nextVariantRequestTime;
   private static long previewPendingUntil;
   private static AdjustModeController.PlacementPhase placementPhase = AdjustModeController.PlacementPhase.FOLLOWING;
   private static int frozenCenterX2;
   private static int frozenCenterZ2;
   private static int frozenY;
   private static int fixedOrientation = -1;
   private static String actionbarSig;
   private static String transientEchoKey;
   private static Object[] transientEchoArgs;
   private static long transientEchoUntil;
   private static PlanPreviewPayload preview;
   private static long suppressClicksUntil;
   private static final int PLACEMENT_STANDOFF = 3;
   private static final double AIM_RANGE = 128.0;
   private static final int GHOST_GROUND_DROP_MAX = 8;

   private AdjustModeController() {
   }

   public static void arm(BuildingProjectListPayload ctx, BuildingProjectListPayload.BuildingEntry entry) {
      if (DesignateModeController.isActive()) {
         DesignateModeController.disarm();
      }

      villageUuid = ctx.villageUuid();
      planSetId = entry.planSetId();
      width = Math.max(1, entry.width());
      depth = Math.max(1, entry.depth());
      buildingOrientation = entry.buildingOrientation();
      centerX = ctx.villageCenter().getX();
      centerY = ctx.villageCenter().getY();
      centerZ = ctx.villageCenter().getZ();
      radius = ctx.villageRadius();
      occupied = ctx.occupied();
      planMargins = new ClearMargins(entry.marginLengthBefore(), entry.marginLengthAfter(), entry.marginWidthBefore(), entry.marginWidthAfter());
      variantCount = Math.max(1, entry.variantCount());
      nextVariantRequestTime = 0L;
      previewPendingUntil = 0L;
      placementPhase = AdjustModeController.PlacementPhase.FOLLOWING;
      frozenCenterX2 = 0;
      frozenCenterZ2 = 0;
      frozenY = 0;
      actionbarSig = null;
      transientEchoKey = null;
      transientEchoArgs = null;
      transientEchoUntil = 0L;
      fixedOrientation = entry.fixedOrientation() != null ? entry.fixedOrientation() : -1;
      if (fixedOrientation >= 0) {
         rotation = Math.floorMod(fixedOrientation - buildingOrientation, 4);
      } else {
         int dir = directionToCenter(ctx.clickedPos().getX(), ctx.clickedPos().getZ());
         rotation = Math.floorMod(dir - buildingOrientation, 4);
      }

      active = true;
      if (preview != null && preview.planSetId().equals(planSetId)) {
         variantIndex = preview.variantIndex();
         requestedVariantIndex = preview.variantIndex();
         showVariantActionbar();
      } else {
         preview = null;
         variantIndex = -1;
         requestedVariantIndex = -1;
         sendPreviewRequest(-1);
      }

      sendControlsRecap();
   }

   private static void sendControlsRecap() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         boolean fixed = fixedOrientation >= 0;
         mc.player
            .displayClientMessage(
               Component.translatable(fixed ? "gui.millenaire.building_project.help_aiming_fixed" : "gui.millenaire.building_project.help_aiming")
                  .withStyle(ChatFormatting.GRAY),
               false
            );
         mc.player
            .displayClientMessage(
               Component.translatable(fixed ? "gui.millenaire.building_project.help_placed_fixed" : "gui.millenaire.building_project.help_placed")
                  .withStyle(ChatFormatting.GRAY),
               false
            );
         mc.player.displayClientMessage(Component.translatable("gui.millenaire.building_project.help_cancel").withStyle(ChatFormatting.GRAY), false);
      }
   }

   public static void onPreviewReceived(PlanPreviewPayload payload) {
      if (active && payload.planSetId().equals(planSetId)) {
         preview = payload;
         variantIndex = payload.variantIndex();
         if (requestedVariantIndex == -1 || payload.variantIndex() == requestedVariantIndex) {
            previewPendingUntil = 0L;
         }

         showVariantActionbar();
      }
   }

   private static void showVariantActionbar() {
      Minecraft mc = Minecraft.getInstance();
      if (variantCount > 1 && variantIndex >= 0 && mc.level != null) {
         setTransientEcho("gui.millenaire.building_project.variant", mc.level.getGameTime(), variantIndex + 1, variantCount);
      }
   }

   private static void setTransientEcho(String key, long now, Object... args) {
      transientEchoKey = key;
      transientEchoArgs = args;
      transientEchoUntil = now + 60L;
   }

   private static void sendPreviewRequest(int variantIdx) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         previewPendingUntil = mc.level.getGameTime() + 40L;
      }

      PacketDistributor.sendToServer(new PlanPreviewRequestPayload(villageUuid, planSetId, variantIdx), new CustomPacketPayload[0]);
   }

   public static void disarm() {
      active = false;
      occupied = List.of();
      placementPhase = AdjustModeController.PlacementPhase.FOLLOWING;
      frozenCenterX2 = 0;
      frozenCenterZ2 = 0;
      frozenY = 0;
      fixedOrientation = -1;
      transientEchoKey = null;
      transientEchoArgs = null;
      transientEchoUntil = 0L;
      Minecraft mcHint = Minecraft.getInstance();
      if (actionbarSig != null && mcHint.player != null) {
         mcHint.player.displayClientMessage(Component.empty(), true);
      }

      actionbarSig = null;
      Minecraft mc = Minecraft.getInstance();
      mc.options.keyAttack.setDown(false);
      mc.options.keyUse.setDown(false);
      if (mc.level != null) {
         suppressClicksUntil = mc.level.getGameTime() + 5L;
      }
   }

   public static boolean isActive() {
      return active;
   }

   @SubscribeEvent
   public static void onScroll(MouseScrollingEvent event) {
      if (active) {
         event.setCanceled(true);
         double dy = event.getScrollDeltaY();
         if (dy != 0.0) {
            int step = dy > 0.0 ? 1 : -1;
            Minecraft mc = Minecraft.getInstance();
            if (Screen.hasAltDown()) {
               if (variantCount > 1 && mc.level != null && mc.level.getGameTime() >= nextVariantRequestTime) {
                  nextVariantRequestTime = mc.level.getGameTime() + 5L;
                  int base = requestedVariantIndex >= 0 ? requestedVariantIndex : (variantIndex >= 0 ? variantIndex : 0);
                  requestedVariantIndex = Math.floorMod(base + step, variantCount);
                  sendPreviewRequest(requestedVariantIndex);
               }
            } else if (Screen.hasControlDown() && placementPhase == AdjustModeController.PlacementPhase.PLACED) {
               nudge(step);
            } else if (fixedOrientation >= 0) {
               setTransientEcho("gui.millenaire.building_project.rotate_locked", mc.level != null ? mc.level.getGameTime() : 0L);
            } else {
               rotation = Math.floorMod(rotation + step, 4);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onInteractionKey(InteractionKeyMappingTriggered event) {
      if (active) {
         if (event.isAttack()) {
            event.setCanceled(true);
            handleAttack();
         } else if (event.isUseItem()) {
            disarm();
            event.setCanceled(true);
         }
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.level.getGameTime() < suppressClicksUntil && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
         }
      }
   }

   private static void handleAttack() {
      if (placementPhase == AdjustModeController.PlacementPhase.FOLLOWING) {
         AdjustModeController.Footprint fp = followingFootprint();
         if (fp != null) {
            freezeFootprint(fp);
            placementPhase = AdjustModeController.PlacementPhase.PLACED;
            actionbarSig = null;
         }

         latchClick();
      } else {
         switch (classifyPlacedClick()) {
            case CONFIRM:
               if (!confirm()) {
                  latchClick();
               }
               break;
            case REPOSITION:
               AdjustModeController.Footprint fp = followingFootprint();
               if (fp != null) {
                  freezeFootprint(fp);
               }

               latchClick();
               break;
            case NONE:
               latchClick();
         }
      }
   }

   private static void freezeFootprint(AdjustModeController.Footprint fp) {
      frozenCenterX2 = GhostPlacementMath.centerHalf(fp.nwCorner().getX(), fp.extentX());
      frozenCenterZ2 = GhostPlacementMath.centerHalf(fp.nwCorner().getZ(), fp.extentZ());
      frozenY = fp.nwCorner().getY();
   }

   private static void latchClick() {
      Minecraft mc = Minecraft.getInstance();
      mc.options.keyAttack.setDown(false);
      if (mc.level != null) {
         suppressClicksUntil = mc.level.getGameTime() + 5L;
      }
   }

   private static AdjustModeController.PlacedClick classifyPlacedClick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         Vec3 eye = mc.player.getEyePosition();
         Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(128.0));
         AABB ghost = placedGhostAABB();
         if (ghost == null || !ghost.contains(eye) && !ghost.clip(eye, end).isPresent()) {
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, mc.player));
            return hit.getType() == Type.BLOCK ? AdjustModeController.PlacedClick.REPOSITION : AdjustModeController.PlacedClick.NONE;
         } else {
            return AdjustModeController.PlacedClick.CONFIRM;
         }
      } else {
         return AdjustModeController.PlacedClick.NONE;
      }
   }

   private static AABB placedGhostAABB() {
      AdjustModeController.Footprint fp = currentFootprint();
      if (fp == null) {
         return null;
      }

      int minX = fp.nwCorner().getX();
      int minZ = fp.nwCorner().getZ();
      int maxX = minX + fp.extentX();
      int maxZ = minZ + fp.extentZ();
      double baseY = fp.nwCorner().getY() + 1.0;
      return new AABB(minX, baseY, minZ, maxX, baseY + ghostHeight(fp), maxZ);
   }

   private static int ghostHeight(AdjustModeController.Footprint fp) {
      PlanPreviewPayload pv = fp.preview();
      if (pv != null && !pv.blocks().isEmpty()) {
         int maxRelY = 0;

         for (PlanPreviewPayload.Entry e : pv.blocks()) {
            maxRelY = Math.max(maxRelY, e.relativePos().getY());
         }

         return Math.max(4, 1 + maxRelY + pv.groundLevel());
      } else {
         return 4;
      }
   }

   private static void nudge(int step) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         Vec3 v = mc.player.getViewVector(1.0F);
         if (Math.abs(v.x) >= Math.abs(v.z)) {
            int delta = step * (v.x >= 0.0 ? 1 : -1);
            frozenCenterX2 = GhostPlacementMath.nudgeHalf(frozenCenterX2, delta);
         } else {
            int delta = step * (v.z >= 0.0 ? 1 : -1);
            frozenCenterZ2 = GhostPlacementMath.nudgeHalf(frozenCenterZ2, delta);
         }

         reprojectFrozenY();
      }
   }

   private static void reprojectFrozenY() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         int cx = Math.floorDiv(frozenCenterX2, 2);
         int cz = Math.floorDiv(frozenCenterZ2, 2);

         for (int d = 0; d <= 8; d++) {
            Integer y = groundAt(mc.level, cx, frozenY - d, cz);
            if (y != null) {
               frozenY = y;
               return;
            }

            if (d > 0) {
               y = groundAt(mc.level, cx, frozenY + d, cz);
               if (y != null) {
                  frozenY = y;
                  return;
               }
            }
         }
      }
   }

   private static Integer groundAt(Level level, int x, int y, int z) {
      BlockPos p = new BlockPos(x, y, z);
      boolean solid = !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
      BlockPos above = p.above();
      boolean airAbove = level.getBlockState(above).getCollisionShape(level, above).isEmpty();
      return solid && airAbove ? y : null;
   }

   @SubscribeEvent
   public static void onLoggingOut(LoggingOut event) {
      active = false;
      occupied = List.of();
      suppressClicksUntil = 0L;
      preview = null;
      variantIndex = -1;
      requestedVariantIndex = -1;
      variantCount = 1;
      nextVariantRequestTime = 0L;
      previewPendingUntil = 0L;
      placementPhase = AdjustModeController.PlacementPhase.FOLLOWING;
      frozenCenterX2 = 0;
      frozenCenterZ2 = 0;
      frozenY = 0;
      fixedOrientation = -1;
      actionbarSig = null;
      transientEchoKey = null;
      transientEchoArgs = null;
      transientEchoUntil = 0L;
      GhostBuildingRenderer.clear();
   }

   @SubscribeEvent
   public static void onClone(Clone event) {
      if (active) {
         disarm();
      }
   }

   @SubscribeEvent
   public static void onScreenOpening(Opening event) {
      if (active) {
         if (event.getScreen() instanceof PauseScreen) {
            event.setCanceled(true);
            disarm();
         }
      }
   }

   private static boolean confirm() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level.getGameTime() < previewPendingUntil) {
         return false;
      }

      AdjustModeController.Footprint fp = currentFootprint();
      if (fp == null) {
         return false;
      }

      PacketDistributor.sendToServer(new BuildingProjectRequestPayload(villageUuid, planSetId, fp.nwCorner(), rotation), new CustomPacketPayload[0]);
      preview = null;
      variantIndex = -1;
      requestedVariantIndex = -1;
      disarm();
      return true;
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (active) {
         if (event.getStage() == Stage.AFTER_TRANSLUCENT_BLOCKS) {
            AdjustModeController.Footprint fp = currentFootprint();
            if (fp == null) {
               updateActionbar(null);
            } else {
               int minX = fp.nwCorner().getX();
               int minZ = fp.nwCorner().getZ();
               int maxX = minX + fp.extentX();
               int maxZ = minZ + fp.extentZ();
               double y = fp.nwCorner().getY() + 1.0;
               ClearMargins m = planMargins.forRotation(Rotation.values()[rotation]);
               int expMinX = minX - m.lengthBefore();
               int expMinZ = minZ - m.widthBefore();
               int expMaxX = maxX + m.lengthAfter();
               int expMaxZ = maxZ + m.widthAfter();
               boolean clashBuilt = false;
               boolean clashPlanned = false;
               boolean[] overlapping = new boolean[occupied.size()];

               for (int i = 0; i < occupied.size(); i++) {
                  BuildingProjectListPayload.Box b = occupied.get(i);
                  if (overlapsFootprint(b, expMinX, expMinZ, expMaxX, expMaxZ)) {
                     overlapping[i] = true;
                     if (b.planned()) {
                        clashPlanned = true;
                     } else {
                        clashBuilt = true;
                     }
                  }
               }

               boolean inRadius = withinRadius(expMinX, expMinZ, expMaxX, expMaxZ);
               boolean valid = inRadius && !clashBuilt && !clashPlanned;
               String predictionKey = predictionHintKey(inRadius, clashBuilt, clashPlanned);
               updateActionbar(predictionKey);
               PoseStack pose = event.getPoseStack();
               Vec3 cam = event.getCamera().getPosition();
               BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
               VertexConsumer vcTop = buffers.getBuffer(GhostRenderTypes.LINES_NO_DEPTH);
               pose.pushPose();
               pose.translate(-cam.x, -cam.y, -cam.z);
               AABB ghost = new AABB(minX, y, minZ, maxX, y + 1.0, maxZ);
               if (valid) {
                  LevelRenderer.renderLineBox(pose, vcTop, ghost, 0.2F, 0.9F, 0.2F, 0.9F);
               } else {
                  LevelRenderer.renderLineBox(pose, vcTop, ghost, 0.9F, 0.2F, 0.2F, 0.9F);
               }

               renderFarEdge(pose, vcTop, minX, minZ, maxX, maxZ, y);
               VertexConsumer vc = buffers.getBuffer(RenderType.lines());
               AABB bound = new AABB(centerX - radius, centerY - 1.0, centerZ - radius, centerX + radius + 1.0, centerY, centerZ + radius + 1.0);
               LevelRenderer.renderLineBox(pose, vc, bound, 0.3F, 0.5F, 0.95F, 0.6F);

               for (int i = 0; i < occupied.size(); i++) {
                  BuildingProjectListPayload.Box b = occupied.get(i);
                  double by = b.surfaceY() + 1.0;
                  AABB box = new AABB(b.minX(), by, b.minZ(), b.maxX(), by + 1.0, b.maxZ());
                  if (overlapping[i]) {
                     LevelRenderer.renderLineBox(pose, vc, box, 1.0F, 0.15F, 0.15F, 1.0F);
                  } else if (b.planned()) {
                     LevelRenderer.renderLineBox(pose, vc, box, 1.0F, 0.6F, 0.15F, 0.55F);
                  } else {
                     LevelRenderer.renderLineBox(pose, vc, box, 0.75F, 0.75F, 0.75F, 0.35F);
                  }
               }

               if (fp.preview() != null) {
                  Rotation rot = Rotation.values()[rotation];
                  BlockPos pivotXZ = PlacedLocation.pivotForNwCorner(minX, minZ, fp.planWidth(), fp.planDepth(), rot);
                  BlockPos pivot = new BlockPos(pivotXZ.getX(), fp.nwCorner().getY() + 1 + fp.preview().groundLevel(), pivotXZ.getZ());
                  GhostBuildingRenderer.render(pose, buffers, fp.preview(), pivot, rot, valid);
               }

               pose.popPose();
               buffers.endBatch(RenderType.lines());
               buffers.endBatch(GhostRenderTypes.LINES_NO_DEPTH);
               buffers.endBatch(RenderType.translucent());
            }
         }
      }
   }

   private static void renderFarEdge(PoseStack pose, VertexConsumer vc, int minX, int minZ, int maxX, int maxZ, double y) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         double px = mc.player.getX();
         double pz = mc.player.getZ();
         double midX = (minX + maxX) / 2.0;
         double midZ = (minZ + maxZ) / 2.0;
         double dNorth = dist2(px, pz, midX, minZ);
         double dSouth = dist2(px, pz, midX, maxZ);
         double dWest = dist2(px, pz, minX, midZ);
         double dEast = dist2(px, pz, maxX, midZ);
         double far = Math.max(Math.max(dNorth, dSouth), Math.max(dWest, dEast));
         AABB edge;
         if (far == dNorth) {
            edge = new AABB(minX, y, minZ, maxX, y + 1.0, minZ);
         } else if (far == dSouth) {
            edge = new AABB(minX, y, maxZ, maxX, y + 1.0, maxZ);
         } else if (far == dWest) {
            edge = new AABB(minX, y, minZ, minX, y + 1.0, maxZ);
         } else {
            edge = new AABB(maxX, y, minZ, maxX, y + 1.0, maxZ);
         }

         LevelRenderer.renderLineBox(pose, vc, edge, 1.0F, 0.85F, 0.1F, 1.0F);
      }
   }

   private static double dist2(double x0, double z0, double x1, double z1) {
      double dx = x0 - x1;
      double dz = z0 - z1;
      return dx * dx + dz * dz;
   }

   private static String predictionHintKey(boolean inRadius, boolean clashBuilt, boolean clashPlanned) {
      if (!inRadius) {
         return "gui.millenaire.building_project.hint_outside_radius";
      } else if (clashBuilt) {
         return "gui.millenaire.building_project.hint_clash_building";
      } else {
         return clashPlanned ? "gui.millenaire.building_project.hint_clash_project" : null;
      }
   }

   private static void updateActionbar(String predictionKey) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         long now = mc.level != null ? mc.level.getGameTime() : 0L;
         Component msg;
         String sig;
         if (predictionKey != null) {
            msg = Component.translatable(predictionKey);
            sig = "pred:" + predictionKey;
         } else if (mc.level != null && now < previewPendingUntil) {
            msg = Component.translatable("gui.millenaire.building_project.preview_pending");
            sig = "pending";
         } else if (transientEchoKey != null && now < transientEchoUntil) {
            msg = "gui.millenaire.building_project.rotate_locked".equals(transientEchoKey)
               ? Component.translatable(transientEchoKey).withStyle(ChatFormatting.GRAY)
               : Component.translatable(transientEchoKey, transientEchoArgs);
            sig = "echo:" + transientEchoKey + ":" + Arrays.toString(transientEchoArgs);
         } else {
            String stateKey = stateHintKey();
            msg = stateKey.endsWith("_fixed") ? Component.translatable(stateKey).withStyle(ChatFormatting.GRAY) : Component.translatable(stateKey);
            sig = "state:" + stateKey;
         }

         if (!Objects.equals(sig, actionbarSig)) {
            actionbarSig = sig;
            mc.player.displayClientMessage(msg, true);
         }
      }
   }

   private static String stateHintKey() {
      if (placementPhase == AdjustModeController.PlacementPhase.PLACED) {
         return fixedOrientation >= 0 ? "gui.millenaire.building_project.hint_placed_fixed" : "gui.millenaire.building_project.hint_placed";
      } else {
         return fixedOrientation >= 0 ? "gui.millenaire.building_project.hint_following_fixed" : "gui.millenaire.building_project.hint_following";
      }
   }

   private static boolean withinRadius(int minX, int minZ, int maxX, int maxZ) {
      return !outsideRadius(minX, minZ) && !outsideRadius(maxX, minZ) && !outsideRadius(minX, maxZ) && !outsideRadius(maxX, maxZ);
   }

   private static boolean overlapsFootprint(BuildingProjectListPayload.Box b, int minX, int minZ, int maxX, int maxZ) {
      return maxX > b.minX() && minX < b.maxX() && maxZ > b.minZ() && minZ < b.maxZ();
   }

   private static boolean outsideRadius(int x, int z) {
      long dx = x - centerX;
      long dz = z - centerZ;
      return dx * dx + dz * dz > (long)radius * radius;
   }

   private static AdjustModeController.Footprint currentFootprint() {
      return placementPhase == AdjustModeController.PlacementPhase.PLACED ? placedFootprint() : followingFootprint();
   }

   private static int planWidthInEffect(PlanPreviewPayload pv) {
      return pv != null ? Math.max(1, pv.width()) : width;
   }

   private static int planDepthInEffect(PlanPreviewPayload pv) {
      return pv != null ? Math.max(1, pv.depth()) : depth;
   }

   private static AdjustModeController.Footprint followingFootprint() {
      BlockPos aimed = currentAnchor();
      Minecraft mc = Minecraft.getInstance();
      if (aimed != null && mc.player != null) {
         PlanPreviewPayload pv = preview != null && preview.planSetId().equals(planSetId) ? preview : null;
         int planWidth = planWidthInEffect(pv);
         int planDepth = planDepthInEffect(pv);
         int extentX = GhostPlacementMath.extentX(planWidth, planDepth, rotation);
         int extentZ = GhostPlacementMath.extentZ(planWidth, planDepth, rotation);
         Direction facing = mc.player.getDirection();
         BlockPos anchor = aimed.relative(facing, 3);

         BlockPos nw = switch (facing) {
            case SOUTH -> anchor.offset(-extentX / 2, 0, 0);
            case NORTH -> anchor.offset(-extentX / 2, 0, -(extentZ - 1));
            case EAST -> anchor.offset(0, 0, -extentZ / 2);
            default -> anchor.offset(-(extentX - 1), 0, -extentZ / 2);
         };
         return new AdjustModeController.Footprint(nw, pv, planWidth, planDepth, extentX, extentZ);
      } else {
         return null;
      }
   }

   private static AdjustModeController.Footprint placedFootprint() {
      PlanPreviewPayload pv = preview != null && preview.planSetId().equals(planSetId) ? preview : null;
      int planWidth = planWidthInEffect(pv);
      int planDepth = planDepthInEffect(pv);
      int extentX = GhostPlacementMath.extentX(planWidth, planDepth, rotation);
      int extentZ = GhostPlacementMath.extentZ(planWidth, planDepth, rotation);
      int nwX = GhostPlacementMath.nwFromCenter(frozenCenterX2, extentX);
      int nwZ = GhostPlacementMath.nwFromCenter(frozenCenterZ2, extentZ);
      return new AdjustModeController.Footprint(new BlockPos(nwX, frozenY, nwZ), pv, planWidth, planDepth, extentX, extentZ);
   }

   private static BlockPos currentAnchor() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         Vec3 eye = mc.player.getEyePosition();
         Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(128.0));
         BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, mc.player));
         if (hit.getType() != Type.BLOCK) {
            return null;
         }

         BlockPos pos = hit.getBlockPos();
         if (mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
            BlockPos ground = pos;

            for (int i = 0; i < 8; i++) {
               ground = ground.below();
               if (!mc.level.getBlockState(ground).getCollisionShape(mc.level, ground).isEmpty()) {
                  return ground;
               }
            }
         }

         return pos;
      } else {
         return null;
      }
   }

   private static int directionToCenter(int fromX, int fromZ) {
      int dx = centerX - fromX;
      int dz = centerZ - fromZ;
      if (Math.abs(dx) >= Math.abs(dz)) {
         return dx >= 0 ? 1 : 3;
      } else {
         return dz >= 0 ? 2 : 0;
      }
   }

   private record Footprint(BlockPos nwCorner, PlanPreviewPayload preview, int planWidth, int planDepth, int extentX, int extentZ) {
   }

   private enum PlacedClick {
      CONFIRM,
      REPOSITION,
      NONE;
   }

   private enum PlacementPhase {
      FOLLOWING,
      PLACED;
   }
}
