package org.millenaire.client;

public final class GhostPlacementMath {
   private GhostPlacementMath() {
   }

   public static int extentX(int planWidth, int planDepth, int rotation) {
      return Math.floorMod(rotation, 2) == 0 ? planWidth : planDepth;
   }

   public static int extentZ(int planWidth, int planDepth, int rotation) {
      return Math.floorMod(rotation, 2) == 0 ? planDepth : planWidth;
   }

   public static int centerHalf(int nw, int extent) {
      return 2 * nw + extent;
   }

   public static int nwFromCenter(int center2, int extent) {
      return Math.floorDiv(center2 - extent, 2);
   }

   public static int nudgeHalf(int center2, int deltaBlocks) {
      return center2 + 2 * deltaBlocks;
   }
}
