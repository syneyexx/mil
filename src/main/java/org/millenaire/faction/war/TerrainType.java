package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Coarse terrain categories for centralized combat modifiers.
 */
public enum TerrainType {
   PLAINS,
   FOREST,
   HILLS,
   MOUNTAINS,
   VILLAGE,
   DEFENSIVE_STRUCTURE;

   @Nullable
   public static TerrainType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TerrainType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public static TerrainType classify(ServerLevel level, BlockPos pos, boolean nearSettlement, boolean nearDefenses) {
      if (nearDefenses) {
         return DEFENSIVE_STRUCTURE;
      }

      if (nearSettlement) {
         return VILLAGE;
      }

      if (level == null || pos == null) {
         return PLAINS;
      }

      int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
      int localRelief = Math.abs(surfaceY - pos.getY());
      if (level.getBiome(pos).is(BiomeTags.IS_MOUNTAIN) || localRelief > 24) {
         return MOUNTAINS;
      }

      if (localRelief > 10) {
         return HILLS;
      }

      Biome biome = level.getBiome(pos).value();
      var biomeKey = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(biome);
      String path = biomeKey == null ? "" : biomeKey.getPath();
      if (path.contains("forest") || path.contains("taiga") || path.contains("jungle")) {
         return FOREST;
      }

      return PLAINS;
   }

   /** Attacker combat multiplier in permille (1000 = neutral). */
   public int attackerModifierPermille() {
      return switch (this) {
         case PLAINS -> 1050;
         case FOREST -> 950;
         case HILLS -> 900;
         case MOUNTAINS -> 850;
         case VILLAGE -> 900;
         case DEFENSIVE_STRUCTURE -> 800;
      };
   }

   /** Defender combat multiplier in permille (1000 = neutral). */
   public int defenderModifierPermille() {
      return switch (this) {
         case PLAINS -> 1000;
         case FOREST -> 1050;
         case HILLS -> 1100;
         case MOUNTAINS -> 1150;
         case VILLAGE -> 1120;
         case DEFENSIVE_STRUCTURE -> 1250;
      };
   }
}
