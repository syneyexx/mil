package org.millenaire.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;

/**
 * Reports overworld biomes coverage for Millenaire natural spawn (tags + climate fallback).
 */
public final class BiomeCoverageReporter {
   private static final List<String> TRACKED_NAMESPACES = List.of(
      "biomesoplenty",
      "biomeswevegone",
      "natures_spirit",
      "minecraft"
   );

   private BiomeCoverageReporter() {
   }

   public record Report(
      int scanned,
      int tagMatched,
      int fallbackMatched,
      int excluded,
      int uncovered,
      List<String> uncoveredBiomeIds,
      List<String> fallbackBiomeIds
   ) {
   }

   public static Report scan(ServerLevel level) {
      WorldgenContentSnapshot snapshot = WorldgenContentSnapshot.current();
      List<String> uncovered = new ArrayList<>();
      List<String> fallbackOnly = new ArrayList<>();
      int scanned = 0;
      int tagMatched = 0;
      int fallbackMatched = 0;
      int excluded = 0;
      for (Holder.Reference<Biome> holder : level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
         ResourceLocation id = holder.key().location();
         if (!TRACKED_NAMESPACES.contains(id.getNamespace())) {
            continue;
         }
         scanned++;
         if (ClimateBiomeFallback.isExcluded(holder) || !ClimateBiomeFallback.isSurfaceEligible(holder)) {
            excluded++;
            continue;
         }
         if (snapshot == null) {
            uncovered.add(id.toString());
            continue;
         }
         WorldgenContentSnapshot.BiomeMatchResult villages = snapshot.matchingVillagesForBiome(holder);
         WorldgenContentSnapshot.BiomeMatchResult lones = snapshot.matchingLoneBuildingsForBiome(holder);
         boolean any = !villages.isEmpty() || !lones.isEmpty();
         boolean usedFallback = villages.usedFallback() || lones.usedFallback();
         if (!any) {
            uncovered.add(id.toString());
         } else if (usedFallback) {
            fallbackMatched++;
            fallbackOnly.add(id.toString());
         } else {
            tagMatched++;
         }
      }
      uncovered.sort(Comparator.naturalOrder());
      fallbackOnly.sort(Comparator.naturalOrder());
      return new Report(
         scanned,
         tagMatched,
         fallbackMatched,
         excluded,
         uncovered.size(),
         List.copyOf(uncovered),
         List.copyOf(fallbackOnly)
      );
   }

   public static List<String> format(Report report, int limit) {
      List<String> lines = new ArrayList<>();
      lines.add(
         String.format(
            Locale.ROOT,
            "Biome coverage: scanned=%d tag=%d fallback=%d excluded=%d uncovered=%d",
            report.scanned(),
            report.tagMatched(),
            report.fallbackMatched(),
            report.excluded(),
            report.uncovered()
         )
      );
      int n = Math.min(limit, report.uncoveredBiomeIds().size());
      if (n > 0) {
         lines.add("Uncovered surface biomes:");
         for (int i = 0; i < n; i++) {
            lines.add("  - " + report.uncoveredBiomeIds().get(i));
         }
         if (report.uncoveredBiomeIds().size() > n) {
            lines.add("  ... +" + (report.uncoveredBiomeIds().size() - n) + " more");
         }
      }
      int f = Math.min(10, report.fallbackBiomeIds().size());
      if (f > 0) {
         lines.add("Sample fallback-only biomes:");
         for (int i = 0; i < f; i++) {
            lines.add("  ~ " + report.fallbackBiomeIds().get(i));
         }
      }
      return lines;
   }
}
