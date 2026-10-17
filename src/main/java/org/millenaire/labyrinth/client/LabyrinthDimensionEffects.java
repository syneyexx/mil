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
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthAtmosphere;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Fog and dimension effects inside the Labyrinth.
 * Default {@link LabyrinthAtmosphere#COLD}: tight blue-grey liminal fog.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID, value = Dist.CLIENT)
public final class LabyrinthDimensionEffects {
   private static final ResourceLocation EFFECTS_ID =
      ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "labyrinth");

   /** Cold blue-grey (Revival-like). */
   private static final float COLD_R = 0.06F;
   private static final float COLD_G = 0.10F;
   private static final float COLD_B = 0.16F;

   /** Classic crimson-slate. */
   private static final float CLASSIC_R = 0.18F;
   private static final float CLASSIC_G = 0.05F;
   private static final float CLASSIC_B = 0.08F;

   private LabyrinthDimensionEffects() {
   }

   @SubscribeEvent
   public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
      event.register(EFFECTS_ID, new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true) {
         @Override
         public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            float[] rgb = palette();
            return new Vec3(rgb[0], rgb[1], rgb[2]);
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
      event.setNearPlaneDistance(1.2F);
      event.setFarPlaneDistance(isCold() ? 18.0F : 22.0F);
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
      if (!isInLabyrinth()) {
         return;
      }
      float[] rgb = palette();
      event.setRed(rgb[0]);
      event.setGreen(rgb[1]);
      event.setBlue(rgb[2]);
   }

   private static float[] palette() {
      if (isCold()) {
         return new float[] {COLD_R, COLD_G, COLD_B};
      }
      return new float[] {CLASSIC_R, CLASSIC_G, CLASSIC_B};
   }

   private static boolean isCold() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthAtmosphere.get() != LabyrinthAtmosphere.CLASSIC;
      } catch (IllegalStateException ignored) {
         return true;
      }
   }

   private static boolean isInLabyrinth() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.player == null) {
         return false;
      }
      return minecraft.level.dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL);
   }
}
