package org.millenaire.catalog;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class CatalogSeedScout {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SITE_RADIUS = 48;
   private static final BlockPos SEARCH_ORIGIN = new BlockPos(0, 96, 0);
   private static final double NOT_FOUND_PENALTY = 1000000.0;

   private CatalogSeedScout() {
   }

   public static void run(ServerLevel level, long seed, Path reportDir) throws IOException {
      List<CatalogSeedScout.CultureSite> sites = new ArrayList<>();

      for (ResourceLocation culture : ModCultures.getAllCultures().keySet()) {
         BlockPos biome = CatalogBiomeLocator.findCultureAnchor(level, culture, SEARCH_ORIGIN);
         double biomeDist = horizontalDistance(biome);
         BlockPos site = CatalogBiomeLocator.findValidatedSite(level, biome, 48, CatalogBiomeLocator.biomeMatcherFor(culture));
         boolean found = site != null;
         BlockPos at = found ? site : biome;
         sites.add(new CatalogSeedScout.CultureSite(culture.toString(), found, at.getX(), at.getZ(), biomeDist, found ? horizontalDistance(site) : -1.0));
         LOGGER.info("Scout seed {} culture {}: {} at {},{}", new Object[]{seed, culture, found ? "VALID" : "no valid site", at.getX(), at.getZ()});
      }

      int foundCount = (int)sites.stream().filter(CatalogSeedScout.CultureSite::found).count();
      double score = 0.0;

      for (CatalogSeedScout.CultureSite s : sites) {
         score += s.found() ? s.siteDistance() : 1000000.0;
      }

      Files.createDirectories(reportDir);
      writeReport(reportDir, seed, sites, foundCount, score);
      appendSummary(reportDir, seed, foundCount, sites.size(), score);
   }

   private static double horizontalDistance(@Nullable BlockPos pos) {
      return pos == null ? -1.0 : Math.sqrt((double)pos.getX() * pos.getX() + (double)pos.getZ() * pos.getZ());
   }

   private static void writeReport(Path reportDir, long seed, List<CatalogSeedScout.CultureSite> sites, int foundCount, double score) throws IOException {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("seed", seed);
      root.put("foundCount", foundCount);
      root.put("totalCultures", sites.size());
      root.put("score", score);
      root.put("sites", sites);
      Path out = reportDir.resolve("seed-" + seed + ".json");
      Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root));
   }

   private static void appendSummary(Path reportDir, long seed, int foundCount, int total, double score) throws IOException {
      String line = seed + "\t" + foundCount + "\t" + total + "\t" + Math.round(score) + "\n";
      Files.writeString(reportDir.resolve("summary.tsv"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
   }

   private record CultureSite(String culture, boolean found, int x, int z, double biomeDistance, double siteDistance) {
   }
}
