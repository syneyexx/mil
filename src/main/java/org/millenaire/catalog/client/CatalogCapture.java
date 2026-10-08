package org.millenaire.catalog.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;

public final class CatalogCapture {
   private static final Logger LOGGER = LogUtils.getLogger();

   private CatalogCapture() {
   }

   public static boolean capture(RenderTarget target, Path out) {
      try {
         if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
         }

         NativeImage image = Screenshot.takeScreenshot(target);

         try {
            image.writeToFile(out);
         } catch (Throwable var6) {
            if (image != null) {
               try {
                  image.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (image != null) {
            image.close();
         }

         return true;
      } catch (IOException e) {
         LOGGER.error("Catalog capture failed for {}: {}", out, e.toString());
         return false;
      }
   }

   public static CatalogCapture.TransparentCapture captureTransparent(RenderTarget target, Path out) {
      try {
         if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
         }

         NativeImage image = new NativeImage(target.width, target.height, false);

         CatalogCapture.TransparentCapture var5;
         try {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            double ratio = missingTextureRatio(image);
            image.writeToFile(out);
            var5 = new CatalogCapture.TransparentCapture(true, ratio);
         } catch (Throwable var7) {
            try {
               image.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }

            throw var7;
         }

         image.close();
         return var5;
      } catch (IOException e) {
         LOGGER.error("Catalog transparent capture failed for {}: {}", out, e.toString());
         return new CatalogCapture.TransparentCapture(false, 0.0);
      }
   }

   static double missingTextureRatio(NativeImage image) {
      int opaque = 0;
      int magenta = 0;

      for (int y = 0; y < image.getHeight(); y++) {
         for (int x = 0; x < image.getWidth(); x++) {
            int p = image.getPixelRGBA(x, y);
            int a = p >> 24 & 0xFF;
            if (a > 16) {
               opaque++;
               int b = p >> 16 & 0xFF;
               int g = p >> 8 & 0xFF;
               int r = p & 0xFF;
               if (r > 180 && g < 80 && b > 180) {
                  magenta++;
               }
            }
         }
      }

      return opaque == 0 ? 0.0 : (double)magenta / opaque;
   }

   public record TransparentCapture(boolean ok, double missingTextureRatio) {
   }
}
