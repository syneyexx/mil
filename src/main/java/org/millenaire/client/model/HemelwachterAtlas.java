package org.millenaire.client.model;

/**
 * Pixel UV regions from hemelwachter_uv_regions.json (texture 1254×1254).
 * Values are top-left texOffs for CubeListBuilder; boxes should stay within region bounds.
 */
public final class HemelwachterAtlas {
   public static final int TEX_W = 1254;
   public static final int TEX_H = 1254;

   public static final int EYE_NEUTRAL_U = 48;
   public static final int EYE_NEUTRAL_V = 58;
   public static final int EYE_PROVOKED_U = 347;
   public static final int EYE_PROVOKED_V = 58;
   public static final int CORE_SIDE_U = 648;
   public static final int CORE_SIDE_V = 58;
   public static final int CORE_REAR_U = 947;
   public static final int CORE_REAR_V = 58;
   public static final int FEATHER_FRONT_U = 48;
   public static final int FEATHER_FRONT_V = 351;
   public static final int FEATHER_BACK_U = 347;
   public static final int FEATHER_BACK_V = 351;
   public static final int FEATHER_EDGE_U = 648;
   public static final int FEATHER_EDGE_V = 351;
   public static final int WING_ROOT_U = 947;
   public static final int WING_ROOT_V = 351;
   public static final int RING_FRONT_U = 48;
   public static final int RING_FRONT_V = 649;
   public static final int RING_UNDER_U = 347;
   public static final int RING_UNDER_V = 649;
   public static final int RUNE_CYAN_U = 648;
   public static final int RUNE_CYAN_V = 649;
   public static final int RUNE_AMBER_U = 947;
   public static final int RUNE_AMBER_V = 649;
   public static final int WING_TRIM_U = 48;
   public static final int WING_TRIM_V = 922;
   public static final int HALO_CYAN_U = 347;
   public static final int HALO_CYAN_V = 922;
   public static final int IVORY_U = 648;
   public static final int IVORY_V = 922;
   public static final int GOLD_U = 947;
   public static final int GOLD_V = 922;

   private HemelwachterAtlas() {
   }
}
