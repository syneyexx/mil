package org.millenaire.catalog.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.phys.Vec3;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.catalog.CameraPose;
import org.millenaire.catalog.CatalogBuildingStager;
import org.millenaire.catalog.CatalogCameraFraming;
import org.millenaire.catalog.CatalogVillagerStager;
import org.millenaire.catalog.ShotSpec;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.slf4j.Logger;

public final class CatalogScreenshotSession {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SETTLE_FRAMES = 4;
   private static final int WATCHDOG_TICKS = 2400;
   private static final double POS_EPSILON = 0.6;
   private static final BlockPos VILLAGER_ANCHOR = new BlockPos(0, 250, 0);
   private static final BlockPos BUILDING_SEARCH_ORIGIN = new BlockPos(0, 96, 0);
   private static final int BUILDING_STRIDE = 320;
   private static final int RENDER_DISTANCE_CHUNKS = 16;
   private static final int MIN_ABOVE_FLOOR_BLOCKS = 5;
   private static final int FORCE_RADIUS_CHUNKS = 4;
   private static final int CLIENT_CHUNK_RADIUS = 3;
   private static final int MAX_SOCLE_BLOCKS = 2;
   private static final int MAX_SOCLE_BLOCKS_BELOW_SURFACE = 3;
   private final Minecraft mc;
   private final MinecraftServer server;
   private final Path screenshotRoot;
   private final Deque<ShotSpec> queue = new ArrayDeque<>();
   private final List<CatalogScreenshotSession.IndexEntry> captured = new ArrayList<>();
   private final List<String> misses = new ArrayList<>();
   private final List<String> skipped = new ArrayList<>();
   private final List<ShotSpec> missedShots = new ArrayList<>();
   private boolean retriedMisses = false;
   private CatalogScreenshotSession.State state = CatalogScreenshotSession.State.IDLE;
   private int ticksInState = 0;
   private volatile int framesSincePose = 0;
   private boolean sceneInitialized = false;
   private int originalFov;
   private boolean originalHideGui;
   private CloudStatus originalClouds;
   private int originalRenderDistance;
   private boolean originalPauseOnLostFocus;
   private int variantSpotIndex = 0;
   private ShotSpec currentShot;
   private CameraPose currentPose;
   private int currentVillagerId = -1;
   private BlockPos currentBuildingAnchor;
   private BlockPos currentBuildingOrigin;
   private int currentBuildingWidth;
   private int currentBuildingHeight;
   private int currentBuildingDepth;
   private final Set<Long> forcedChunks = new HashSet<>();
   private final Map<String, CatalogScreenshotSession.BuildingFrame> buildingFrames = new HashMap<>();
   private final Map<String, BlockPos> variantSpots = new HashMap<>();
   private final AtomicBoolean serverDone = new AtomicBoolean(false);
   private final AtomicReference<Object> serverResult = new AtomicReference<>();
   private volatile boolean captureRequested = false;
   private volatile Path captureOutput;
   private volatile boolean captureFinished = false;
   private volatile boolean captureOk = false;
   private volatile double captureMissingTextureRatio;
   private volatile int lastCaptureWidth;
   private volatile int lastCaptureHeight;
   private static final double MISSING_TEXTURE_REJECT_RATIO = 0.05;

   public CatalogScreenshotSession(Minecraft mc, MinecraftServer server, Path screenshotRoot) {
      this.mc = mc;
      this.server = server;
      this.screenshotRoot = screenshotRoot;
   }

   public void start(List<ShotSpec> shots) {
      this.queue.addAll(shots);
      LOGGER.info("Catalog session starting: {} shots", this.queue.size());
      this.transition(CatalogScreenshotSession.State.NEXT);
   }

   public boolean isFinished() {
      return this.state == CatalogScreenshotSession.State.FINISHED;
   }

   public void tick(Minecraft mc) {
      this.ticksInState++;
      if (this.state == CatalogScreenshotSession.State.IDLE
         || this.state == CatalogScreenshotSession.State.DONE
         || this.state == CatalogScreenshotSession.State.FINISHED
         || this.ticksInState <= 2400) {
         switch (this.state) {
            case STAGING:
               this.stepStaging();
               break;
            case AWAIT_SERVER:
               this.stepAwaitServer();
               break;
            case AWAIT_CLIENT_SYNC:
               this.stepAwaitClientSync();
               break;
            case SETTLE_FRAMES:
               this.stepSettleFrames();
               break;
            case CAPTURE:
               this.stepCapture();
               break;
            case AWAIT_CAPTURE:
               this.stepAwaitCapture();
               break;
            case CLEANUP:
               this.stepCleanup();
               break;
            case NEXT:
               this.stepNext();
               break;
            case DONE:
               this.stepDone();
         }
      } else if (this.state == CatalogScreenshotSession.State.SETTLE_FRAMES && this.currentShot instanceof ShotSpec.BuildingShot) {
         LOGGER.warn("Catalog settle watchdog for {} — capturing best-effort frame", describe(this.currentShot));
         this.transition(CatalogScreenshotSession.State.CAPTURE);
      } else {
         this.recordMiss("watchdog timeout in " + this.state);
         this.transition(CatalogScreenshotSession.State.CLEANUP);
      }
   }

   private void stepNext() {
      if (!this.sceneInitialized) {
         this.initScene();
         this.sceneInitialized = true;
      }

      if (this.queue.isEmpty()) {
         if (this.retriedMisses || this.missedShots.isEmpty()) {
            this.transition(CatalogScreenshotSession.State.DONE);
            return;
         }

         LOGGER.info("Catalog retry pass: re-staging {} missed shot(s)", this.missedShots.size());
         this.queue.addAll(this.missedShots);
         this.missedShots.clear();
         this.misses.clear();
         this.retriedMisses = true;
      }

      this.currentShot = this.queue.pollFirst();
      this.transition(CatalogScreenshotSession.State.STAGING);
   }

   private void stepStaging() {
      this.serverDone.set(false);
      this.serverResult.set(null);
      ShotSpec shot = this.currentShot;
      if (shot instanceof ShotSpec.VillagerShot villager) {
         this.submitServer(() -> {
            CatalogVillagerStager.discardPrevious();
            MillVillager v = CatalogVillagerStager.spawn(this.serverLevel(), villager.villagerType(), VILLAGER_ANCHOR);
            this.serverResult.set(v);
         });
      } else if (shot instanceof ShotSpec.BuildingShot building) {
         this.submitServer(
            () -> {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.planSet());
               BlockPos anchor = this.buildingAnchor(planSet, building);
               int clearHeight = this.maxFootprint(planSet, building)[1];
               CatalogBuildingStager.PlacedBuilding placed = planSet == null
                  ? null
                  : CatalogBuildingStager.placeInVoid(this.serverLevel(), planSet, building.variant(), building.level(), anchor, clearHeight);
               if (placed != null) {
                  this.forceChunksAround(placed.origin().offset(placed.width() / 2, placed.height() / 2, placed.depth() / 2));
               }

               this.serverResult.set(placed);
            }
         );
      }

      this.transition(CatalogScreenshotSession.State.AWAIT_SERVER);
   }

   private void stepAwaitServer() {
      if (this.serverDone.get()) {
         Object result = this.serverResult.get();
         if (result == null) {
            this.recordMiss("staging returned null");
            this.transition(CatalogScreenshotSession.State.CLEANUP);
         } else {
            if (result instanceof MillVillager villager) {
               this.currentPose = CatalogCameraFraming.forVillager(villager.position());
               this.currentVillagerId = villager.getId();
            } else if (result instanceof CatalogBuildingStager.PlacedBuilding placed) {
               if (placed.aboveFloorBlocks() < 5 && !isAgricultural(this.currentShot)) {
                  this.recordSkip(placed.aboveFloorBlocks() + " blocks above floor");
                  this.transition(CatalogScreenshotSession.State.CLEANUP);
                  return;
               }

               this.currentPose = this.buildingPose((ShotSpec.BuildingShot)this.currentShot, placed);
               this.currentBuildingAnchor = placed.origin().offset(placed.width() / 2, placed.height() / 2, placed.depth() / 2);
               this.currentBuildingOrigin = placed.origin();
               this.currentBuildingWidth = placed.width();
               this.currentBuildingHeight = placed.height();
               this.currentBuildingDepth = placed.depth();
            }

            CameraPose pose = this.currentPose;
            this.mc.options.fov().set((int)pose.fov());
            this.submitServer(() -> this.teleportCamera(pose));
            this.transition(CatalogScreenshotSession.State.AWAIT_CLIENT_SYNC);
         }
      }
   }

   private void stepAwaitClientSync() {
      if (this.mc.player != null && this.mc.level != null && this.currentPose != null) {
         if (!(this.mc.player.getEyePosition().distanceTo(this.currentPose.pos()) > 0.6)) {
            if (this.currentShot instanceof ShotSpec.VillagerShot) {
               if (!(this.mc.level.getEntity(this.currentVillagerId) instanceof MillVillager clientVillager)) {
                  return;
               }

               clientVillager.setGuiPreviewMode(true);
            }

            if (!(this.currentShot instanceof ShotSpec.BuildingShot) || this.clientChunksReady(this.currentBuildingAnchor)) {
               this.framesSincePose = 0;
               this.transition(CatalogScreenshotSession.State.SETTLE_FRAMES);
            }
         }
      }
   }

   private void stepSettleFrames() {
      if (this.framesSincePose >= 4) {
         if (!(this.currentShot instanceof ShotSpec.BuildingShot) || this.buildingRenderReady()) {
            this.transition(CatalogScreenshotSession.State.CAPTURE);
         }
      }
   }

   private boolean buildingRenderReady() {
      LevelRenderer lr = this.mc.levelRenderer;
      if (this.currentBuildingOrigin != null && lr.hasRenderedAllSections()) {
         BlockPos o = this.currentBuildingOrigin;

         for (int dx = 0; dx <= this.currentBuildingWidth; dx += 16) {
            for (int dy = 0; dy <= this.currentBuildingHeight; dy += 16) {
               for (int dz = 0; dz <= this.currentBuildingDepth; dz += 16) {
                  BlockPos p = o.offset(
                     Math.min(dx, this.currentBuildingWidth), Math.min(dy, this.currentBuildingHeight), Math.min(dz, this.currentBuildingDepth)
                  );
                  if (!lr.isSectionCompiled(p)) {
                     return false;
                  }
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private void stepCapture() {
      this.captureFinished = false;
      this.captureOk = false;
      this.captureOutput = this.currentShot.output();
      this.captureRequested = true;
      this.transition(CatalogScreenshotSession.State.AWAIT_CAPTURE);
   }

   private void stepAwaitCapture() {
      if (this.captureFinished) {
         if (!this.captureOk) {
            this.recordMiss("capture write failed");
         } else if (this.captureMissingTextureRatio >= 0.05) {
            this.recordMiss(String.format("missing-texture render (%.0f%% magenta)", this.captureMissingTextureRatio * 100.0));
         } else {
            this.recordCapture(this.currentShot, this.lastCaptureWidth, this.lastCaptureHeight);
         }

         this.transition(CatalogScreenshotSession.State.CLEANUP);
      }
   }

   private void stepCleanup() {
      if (this.currentShot instanceof ShotSpec.VillagerShot) {
         this.submitServer(CatalogVillagerStager::discardPrevious);
      }

      this.transition(CatalogScreenshotSession.State.NEXT);
   }

   private void stepDone() {
      this.writeIndex();
      this.deleteJobFile();
      LOGGER.info("Catalog session done: {} captured, {} skipped, {} missed", new Object[]{this.captured.size(), this.skipped.size(), this.misses.size()});
      this.transition(CatalogScreenshotSession.State.FINISHED);
      Runtime.getRuntime().halt(0);
   }

   public void onRenderFrame(RenderTarget target) {
      this.framesSincePose++;
      if (this.captureRequested) {
         this.captureRequested = false;
         this.lastCaptureWidth = target.width;
         this.lastCaptureHeight = target.height;
         CatalogCapture.TransparentCapture result = CatalogCapture.captureTransparent(target, this.captureOutput);
         this.captureOk = result.ok();
         this.captureMissingTextureRatio = result.missingTextureRatio();
         this.captureFinished = true;
      }
   }

   private void submitServer(Runnable action) {
      this.server.execute(() -> {
         try {
            action.run();
         } catch (RuntimeException e) {
            LOGGER.error("Catalog server action failed", e);
         } finally {
            this.serverDone.set(true);
         }
      });
   }

   private void initScene() {
      this.server.execute(() -> {
         ServerLevel level = this.serverLevel();
         GameRules rules = this.server.getGameRules();
         ((BooleanValue)rules.getRule(GameRules.RULE_DAYLIGHT)).set(false, this.server);
         ((BooleanValue)rules.getRule(GameRules.RULE_WEATHER_CYCLE)).set(false, this.server);
         level.setDayTime(6000L);
         level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);
         ServerPlayer player = this.serverPlayer();
         if (player != null) {
            player.setGameMode(GameType.SPECTATOR);
         }
      });
      this.originalFov = (Integer)this.mc.options.fov().get();
      this.originalHideGui = this.mc.options.hideGui;
      this.originalClouds = (CloudStatus)this.mc.options.cloudStatus().get();
      this.originalRenderDistance = (Integer)this.mc.options.renderDistance().get();
      this.originalPauseOnLostFocus = this.mc.options.pauseOnLostFocus;
      this.mc.options.hideGui = true;
      this.mc.options.cloudStatus().set(CloudStatus.OFF);
      this.mc.options.pauseOnLostFocus = false;
      this.mc.options.renderDistance().set(16);
      this.mc.levelRenderer.allChanged();
      CatalogRenderState.skySuppressed = true;
   }

   private void teleportCamera(CameraPose pose) {
      ServerPlayer player = this.serverPlayer();
      if (player != null) {
         double feetY = pose.pos().y - player.getEyeHeight();
         player.connection.teleport(pose.pos().x, feetY, pose.pos().z, pose.yRot(), pose.xRot());
      }
   }

   private BlockPos buildingAnchor(BuildingPlanSet planSet, ShotSpec.BuildingShot shot) {
      String key = shot.planSet() + "|" + shot.variant();
      return this.variantSpots.computeIfAbsent(key, k -> {
         BlockPos cell = spiralCell(BUILDING_SEARCH_ORIGIN, this.variantSpotIndex++, 320);
         return new BlockPos(cell.getX(), 128, cell.getZ());
      });
   }

   private static BlockPos spiralCell(BlockPos base, int index, int stride) {
      int x = 0;
      int z = 0;
      int dx = 1;
      int dz = 0;
      int segLen = 1;
      int segDone = 0;
      int turns = 0;

      for (int i = 0; i < index; i++) {
         x += dx;
         z += dz;
         if (++segDone == segLen) {
            segDone = 0;
            int t = dx;
            dx = -dz;
            dz = t;
            if (++turns % 2 == 0) {
               segLen++;
            }
         }
      }

      return base.offset(x * stride, 0, z * stride);
   }

   private int[] maxFootprint(BuildingPlanSet planSet, ShotSpec.BuildingShot shot) {
      if (planSet == null) {
         return new int[]{16, 16, 16};
      }

      BuildingPlanSet.LevelDef def = planSet.getLevel(shot.variant(), shot.maxLevel());
      BuildingPlan plan = def == null ? null : ModCultures.getBuildingPlan(def.planId());
      return plan == null ? new int[]{16, 16, 16} : new int[]{plan.width(), plan.height(), plan.depth()};
   }

   private static boolean isAgricultural(ShotSpec shot) {
      if (!(shot instanceof ShotSpec.BuildingShot building)) {
         return false;
      } else {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.planSet());
         return planSet != null && planSet.tags().contains("agricultural");
      }
   }

   private void forceChunksAround(BlockPos anchor) {
      ServerLevel level = this.serverLevel();

      for (long key : this.forcedChunks) {
         ChunkPos cp = new ChunkPos(key);
         level.setChunkForced(cp.x, cp.z, false);
      }

      this.forcedChunks.clear();
      ChunkPos center = new ChunkPos(anchor);

      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            int cx = center.x + dx;
            int cz = center.z + dz;
            level.setChunkForced(cx, cz, true);
            this.forcedChunks.add(ChunkPos.asLong(cx, cz));
         }
      }
   }

   private boolean clientChunksReady(BlockPos center) {
      if (this.mc.level != null && center != null) {
         ChunkPos c = new ChunkPos(center);

         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               if (!this.mc.level.getChunkSource().hasChunk(c.x + dx, c.z + dz)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private CameraPose buildingPose(ShotSpec.BuildingShot shot, CatalogBuildingStager.PlacedBuilding placed) {
      String key = shot.planSet() + "|" + shot.variant();
      Vec3 nominal = Vec3.atLowerCornerOf(placed.nominalOrigin());
      if (shot.isMaxLevel()) {
         CameraPose pose = this.frameWithFootprint(placed);
         this.buildingFrames.put(key, new CatalogScreenshotSession.BuildingFrame(pose.pos().subtract(nominal), pose.yRot(), pose.xRot(), pose.fov()));
         return pose;
      } else {
         CatalogScreenshotSession.BuildingFrame frame = this.buildingFrames.get(key);
         return frame == null ? this.frameWithFootprint(placed) : new CameraPose(nominal.add(frame.camOffset()), frame.yaw(), frame.pitch(), frame.fov());
      }
   }

   private CameraPose frameWithFootprint(CatalogBuildingStager.PlacedBuilding placed) {
      int structureTopY = placed.origin().getY() + placed.height();
      int padBottomY = placed.origin().getY() - Math.min(2, Math.max(0, placed.origin().getY() - placed.groundBottomY()));
      int frameBottomY = Math.max(padBottomY, placed.baseY() - 3);
      Vec3 boxOrigin = new Vec3(placed.origin().getX(), frameBottomY, placed.origin().getZ());
      float yaw = CatalogCameraFraming.buildingYaw(placed.doorOrientation());
      return CatalogCameraFraming.forBuilding(boxOrigin, placed.width(), structureTopY - frameBottomY, placed.depth(), this.viewportAspect(), yaw);
   }

   private double viewportAspect() {
      int w = this.mc.getWindow().getWidth();
      int h = this.mc.getWindow().getHeight();
      return h <= 0 ? 1.7777777777777777 : (double)w / h;
   }

   private ServerLevel serverLevel() {
      return this.server.overworld();
   }

   private ServerPlayer serverPlayer() {
      List<ServerPlayer> players = this.server.getPlayerList().getPlayers();
      return players.isEmpty() ? null : players.get(0);
   }

   private void recordCapture(ShotSpec shot, int width, int height) {
      String file = this.screenshotRoot.relativize(shot.output()).toString().replace('\\', '/');
      if (shot instanceof ShotSpec.VillagerShot v) {
         this.captured.add(new CatalogScreenshotSession.IndexEntry("villager", v.villagerType().toString(), null, null, file, width, height));
      } else if (shot instanceof ShotSpec.BuildingShot b) {
         this.captured.add(new CatalogScreenshotSession.IndexEntry("building", b.planSet().toString(), b.variant(), b.level(), file, width, height));
      }
   }

   private void recordMiss(String reason) {
      String id = describe(this.currentShot);
      this.misses.add(id + " — " + reason);
      if (!this.retriedMisses && this.currentShot != null) {
         this.missedShots.add(this.currentShot);
      }

      LOGGER.warn("Catalog miss: {} — {}", id, reason);
   }

   private void recordSkip(String reason) {
      String id = describe(this.currentShot);
      this.skipped.add(id + " — " + reason);
      LOGGER.info("Catalog skip: {} — {}", id, reason);
   }

   private static String describe(ShotSpec shot) {
      if (shot instanceof ShotSpec.VillagerShot v) {
         return "villager " + v.villagerType();
      } else {
         return shot instanceof ShotSpec.BuildingShot b ? "building " + b.planSet() + " " + b.variant() + " L" + b.level() : "<none>";
      }
   }

   private void writeIndex() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("captured", this.captured);
      root.put("misses", this.misses);
      root.put("skipped", this.skipped);
      Path indexPath = this.screenshotRoot.resolve("millenaire").resolve("index.json");

      try {
         Files.createDirectories(indexPath.getParent());
         Files.writeString(indexPath, new GsonBuilder().setPrettyPrinting().create().toJson(root));
      } catch (IOException e) {
         LOGGER.error("Failed to write catalog index {}: {}", indexPath, e.toString());
      }

      this.mc.options.fov().set(this.originalFov);
      this.mc.options.hideGui = this.originalHideGui;
      if (this.originalClouds != null) {
         this.mc.options.cloudStatus().set(this.originalClouds);
      }

      this.mc.options.renderDistance().set(this.originalRenderDistance);
      this.mc.options.pauseOnLostFocus = this.originalPauseOnLostFocus;
      CatalogRenderState.skySuppressed = false;
   }

   private void deleteJobFile() {
      try {
         Files.deleteIfExists(CatalogClientEvents.jobFile());
      } catch (IOException e) {
         LOGGER.warn("Could not delete job file: {}", e.toString());
      }
   }

   private void transition(CatalogScreenshotSession.State next) {
      this.state = next;
      this.ticksInState = 0;
   }

   private record BuildingFrame(Vec3 camOffset, float yaw, float pitch, float fov) {
   }

   private record IndexEntry(String kind, String id, String variant, Integer level, String file, int width, int height) {
   }

   private enum State {
      IDLE,
      STAGING,
      AWAIT_SERVER,
      AWAIT_CLIENT_SYNC,
      SETTLE_FRAMES,
      CAPTURE,
      AWAIT_CAPTURE,
      CLEANUP,
      NEXT,
      DONE,
      FINISHED;
   }
}
