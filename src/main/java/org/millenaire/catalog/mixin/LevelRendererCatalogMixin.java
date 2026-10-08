package org.millenaire.catalog.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.millenaire.catalog.client.CatalogRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererCatalogMixin {
   @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
   private void millenaire$catalogTransparentSky(CallbackInfo ci) {
      if (CatalogRenderState.skySuppressed) {
         RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
         RenderSystem.clear(16384, Minecraft.ON_OSX);
         ci.cancel();
      }
   }
}
