package org.millenaire.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.millenaire.compat.xaero.XaeroCompatBootstrap;

@EventBusSubscriber(modid = "millenaire", value = Dist.CLIENT)
public final class ClientGameEvents {
   private ClientGameEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (WraithPreviewSession.enabled()) {
         WraithPreviewSession.tick(mc);
      }
      if (InfernalCultPreviewSession.enabled()) {
         InfernalCultPreviewSession.tick(mc);
      }
      if (BountyHunterArmorPreviewSession.enabled()) {
         BountyHunterArmorPreviewSession.tick(mc);
      }
      if (mc.player == null) {
         FireplaceSmokeHandler.clearAll();
      } else {
         FireplaceSmokeHandler.tick();
         XaeroCompatBootstrap.syncFromCacheIfNeeded();
      }
   }

   @SubscribeEvent
   public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      XaeroCompatBootstrap.onClientLogout();
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_WEATHER) {
         return;
      }
      if (WraithPreviewSession.enabled()) {
         WraithPreviewSession.onRenderFrame(Minecraft.getInstance().getMainRenderTarget());
      }
      if (InfernalCultPreviewSession.enabled()) {
         InfernalCultPreviewSession.onRenderFrame(Minecraft.getInstance().getMainRenderTarget());
      }
      if (BountyHunterArmorPreviewSession.enabled()) {
         BountyHunterArmorPreviewSession.onRenderFrame(Minecraft.getInstance().getMainRenderTarget());
      }
   }
}
