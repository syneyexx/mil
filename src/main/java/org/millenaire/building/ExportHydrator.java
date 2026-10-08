package org.millenaire.building;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class ExportHydrator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private ExportHydrator() {
   }

   public static ExportHydrator.Outcome select(ServerLevel level, String cultureKey, String buildingId) {
      Path gameDir = level.getServer().getServerDirectory();
      boolean migrated = LegacyExportSinkMigrator.migrate(gameDir, buildingId) == LegacyExportSinkMigrator.Result.MIGRATED;
      if (migrated) {
         BuildingExporter.refreshOverlay();
      }

      ImportBuildingResolver.ResolvedBuilding resolved = ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).orElse(null);
      if (resolved == null) {
         return new ExportHydrator.Outcome(null, 0, migrated);
      }

      if (!resolved.fromExports()) {
         return new ExportHydrator.Outcome(resolved, 0, migrated);
      }

      int hydrated = hydrate(level, gameDir, resolved);
      if (hydrated == 0) {
         return new ExportHydrator.Outcome(resolved, 0, migrated);
      }

      BuildingExporter.refreshOverlay();
      ImportBuildingResolver.ResolvedBuilding after = ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).orElse(resolved);
      return new ExportHydrator.Outcome(after, hydrated, migrated);
   }

   private static int hydrate(ServerLevel level, Path gameDir, ImportBuildingResolver.ResolvedBuilding resolved) {
      String culturePath = BuildingExporter.culturePathComponent(resolved.cultureKey());
      Optional<Path> exportDirOpt = BuildingExporter.findExportedDir(gameDir, culturePath, resolved.buildingId());
      if (exportDirOpt.isEmpty()) {
         LOGGER.warn("Hydration skipped for {}/{}: export directory vanished", resolved.cultureKey(), resolved.buildingId());
         return 0;
      }

      Path exportDir = exportDirOpt.get();
      if (!isIncomplete(exportDir, resolved)) {
         return 0;
      }

      try (ExportTransaction tx = ExportTransaction.begin(BuildingExporter.getExportedRoot(level), culturePath, resolved.buildingId(), exportDir)) {
         int staged = BuildingExporter.stageCompletePlanSet(level, tx, resolved);
         if (staged == 0) {
            tx.abort();
            return 0;
         } else {
            JsonObject root = resolved.effectiveJson().deepCopy();
            BuildingExporter.applyPlanToJson(root, resolved.buildingId(), "", Integer.MIN_VALUE, resolved.unionPlan(), tx::willExist);
            tx.stageMarker(resolved.buildingId() + ".json", GSON.toJson(root));
            tx.commit();
            LOGGER.info("Hydrated export {}/{}: {} level(s) materialised", new Object[]{resolved.cultureKey(), resolved.buildingId(), staged});
            return staged;
         }
      } catch (IOException | RuntimeException e) {
         LOGGER.error("Hydration failed for {}/{} — export left untouched", new Object[]{resolved.cultureKey(), resolved.buildingId(), e});
         return 0;
      }
   }

   private static boolean isIncomplete(Path exportDir, ImportBuildingResolver.ResolvedBuilding resolved) {
      for (Entry<String, List<Integer>> e : ImportBuildingResolver.variantLevels(resolved.unionPlan()).entrySet()) {
         for (int lvl : e.getValue()) {
            Path nbt = exportDir.resolve(resolved.buildingId() + "_" + e.getKey() + "_" + lvl + ".nbt");
            if (!Files.isRegularFile(nbt)) {
               return true;
            }
         }
      }

      return false;
   }

   public record Outcome(@Nullable ImportBuildingResolver.ResolvedBuilding resolved, int hydratedLevels, boolean migratedLegacy) {
   }
}
