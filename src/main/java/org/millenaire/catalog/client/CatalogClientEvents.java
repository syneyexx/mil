package org.millenaire.catalog.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.millenaire.catalog.CatalogJob;
import org.millenaire.catalog.CatalogSeedScout;
import org.millenaire.catalog.CatalogSubjectEnumerator;
import org.millenaire.catalog.ShotSpec;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class CatalogClientEvents {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String JOB_FILENAME = "screenshot-job.json";
   private static CatalogScreenshotSession session;
   private static boolean jobConsumed = false;

   private CatalogClientEvents() {
   }

   public static Path jobFile() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshot-job.json");
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (Files.exists(jobFile())) {
         if (mc.level == null) {
            CatalogWorldBootstrap.ensureWorld(mc);
         } else if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && !server.isDedicatedServer()) {
               if (session != null) {
                  if (!session.isFinished()) {
                     session.tick(mc);
                  }
               } else if (!jobConsumed && mc.player != null) {
                  jobConsumed = true;
                  startSession(mc, server);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (session != null && !session.isFinished()) {
         if (event.getStage() == Stage.AFTER_WEATHER) {
            session.onRenderFrame(Minecraft.getInstance().getMainRenderTarget());
         }
      }
   }

   private static void startSession(Minecraft mc, MinecraftServer server) {
      try {
         CatalogJob job = CatalogJob.fromJson(Files.readString(jobFile()));
         if (job.isScout()) {
            startScout(mc, server, job);
            return;
         }

         Path root = mc.gameDirectory.toPath().resolve("screenshots");
         List<ShotSpec> shots = CatalogSubjectEnumerator.enumerate(ModCultures.getAllVillagerTypes(), ModCultures.getAllBuildingPlanSets(), job, root);
         session = new CatalogScreenshotSession(mc, server, root);
         session.start(shots);
      } catch (IOException e) {
         LOGGER.error("Failed to start catalog session: {}", e.toString());
      }
   }

   private static void startScout(Minecraft mc, MinecraftServer server, CatalogJob job) {
      Path reportDir = mc.gameDirectory.toPath().resolve("seed-reports");
      LOGGER.info("Catalog seed scout starting for seed {}", job.seed());
      server.execute(() -> {
         try {
            CatalogSeedScout.run(server.overworld(), job.seed(), reportDir);
         } catch (Exception e) {
            LOGGER.error("Seed scout failed for seed {}: {}", job.seed(), e.toString());
         } finally {
            try {
               Files.deleteIfExists(jobFile());
            } catch (IOException var11) {
            }

            LOGGER.info("Catalog seed scout done for seed {}", job.seed());
            Runtime.getRuntime().halt(0);
         }
      });
   }
}
