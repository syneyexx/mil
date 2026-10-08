package org.millenaire.catalog.client;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.millenaire.Millenaire;
import org.millenaire.catalog.CatalogJob;
import org.slf4j.Logger;

public final class CatalogWorldBootstrap {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String WORLD_NAME = "catalog";
   private static boolean requested = false;

   private CatalogWorldBootstrap() {
   }

   public static void ensureWorld(Minecraft mc) {
      if (!requested) {
         requested = true;
         Millenaire.setNaturalSpawnSuppressed(true);

         boolean exists;
         try {
            exists = mc.getLevelSource().levelExists("catalog");
         } catch (Exception e) {
            LOGGER.error("Could not check catalog world existence: {}", e.toString());
            return;
         }

         if (exists) {
            LOGGER.info("Loading existing catalog world");
            mc.createWorldOpenFlows().openWorld("catalog", () -> requested = false);
         } else {
            LOGGER.info("Creating catalog world");
            LevelSettings settings = new LevelSettings(
               "catalog", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT
            );
            WorldOptions options = new WorldOptions(jobSeed(), false, false);
            mc.createWorldOpenFlows().createFreshLevel("catalog", settings, options, CatalogWorldBootstrap::voidDimensions, new TitleScreen());
         }
      }
   }

   private static WorldDimensions voidDimensions(RegistryAccess registryAccess) {
      HolderGetter<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
      FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.empty(), biomes.getOrThrow(Biomes.THE_VOID), List.of());
      WorldDimensions normal = ((WorldPreset)registryAccess.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value())
         .createWorldDimensions();
      return normal.replaceOverworldGenerator(registryAccess, new FlatLevelSource(flat));
   }

   private static long jobSeed() {
      try {
         return CatalogJob.fromJson(Files.readString(CatalogClientEvents.jobFile())).seed();
      } catch (Exception e) {
         LOGGER.warn("Could not read seed from job, using default: {}", e.toString());
         return 80085L;
      }
   }
}
