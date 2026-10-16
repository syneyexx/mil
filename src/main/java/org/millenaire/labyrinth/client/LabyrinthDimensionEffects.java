package org.millenaire.labyrinth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Dark crimson/slate fog and dimension effects inside the Labyrinth.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID, value = Dist.CLIENT)
public final class LabyrinthDimensionEffects {
   private static final ResourceLocation EFFECTS_ID =
      ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "labyrinth");

   /** Crimson-slate fog colour. */
   private static final float FOG_R = 0.18F;
   private static final float FOG_G = 0.05F;
   private static final float FOG_B = 0.08F;

   private LabyrinthDimensionEffects() {
   }

   @SubscribeEvent
   public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
      event.register(EFFECTS_ID, new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true) {
         @Override
         public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return new Vec3(FOG_R, FOG_G, FOG_B);
         }

         @Override
         public boolean isFoggyAt(int x, int y) {
            return true;
         }
      });
   }

   @SubscribeEvent
   public static void onRenderFog(ViewportEvent.RenderFog event) {
      if (!isInLabyrinth()) {
         return;
      }
      // Tight corridors: close fog sells claustrophobia without hiding melee.
      event.setNearPlaneDistance(1.5F);
      event.setFarPlaneDistance(22.0F);
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
      if (!isInLabyrinth()) {
         return;
      }
      event.setRed(FOG_R);
      event.setGreen(FOG_G);
      event.setBlue(FOG_B);
   }

   private static boolean isInLabyrinth() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.player == null) {
         return false;
      }
      return minecraft.level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL);
   }
}
