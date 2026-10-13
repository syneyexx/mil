package org.millenaire.world.road;

public final class RouteSegment {
   public static final byte GROUND = 0;
   public static final byte BRIDGE = 1;
   public static final byte TUNNEL = 2;
   public static final byte STAIRS = 3;

   private RouteSegment() {
   }

   public static String name(byte kind) {
      return switch (kind) {
         case BRIDGE -> "BRIDGE";
         case TUNNEL -> "TUNNEL";
         case STAIRS -> "STAIRS";
         default -> "GROUND";
      };
   }
}
