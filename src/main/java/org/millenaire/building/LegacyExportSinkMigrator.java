package org.millenaire.building;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.slf4j.Logger;

public final class LegacyExportSinkMigrator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private LegacyExportSinkMigrator() {
   }

   public static LegacyExportSinkMigrator.Result migrate(Path gameDir, String buildingId) {
      if (!ImportTableIdentifierValidator.isValidIdentifier(buildingId)) {
         return LegacyExportSinkMigrator.Result.NOT_PRESENT;
      }

      Path legacyDir = BuildingExporter.getLegacyExportDir(gameDir);
      Path legacyJson = legacyDir.resolve(buildingId + ".json");
      if (!Files.isRegularFile(legacyJson)) {
         return LegacyExportSinkMigrator.Result.NOT_PRESENT;
      }

      JsonObject root = BuildingExporter.readJsonFile(legacyJson);
      if (root == null) {
         return LegacyExportSinkMigrator.Result.FAILED;
      }

      String cultureKey = root.has("culture") && root.get("culture").isJsonPrimitive() ? root.get("culture").getAsString() : "";
      if (!cultureKey.isEmpty() && ImportTableIdentifierValidator.isValidCultureKey(cultureKey)) {
         String culturePath = BuildingExporter.culturePathComponent(cultureKey);
         String category = BuildingExporter.categoryFor(cultureKey, buildingId);
         Path exportedRoot = BuildingExporter.getExportedRoot(gameDir);
         Path targetDir = BuildingExporter.computeExportDir(gameDir, cultureKey, category);
         List<String> members = new ArrayList<>();
         Map<String, List<Integer>> plan = ImportBuildingResolver.variantLevels(root);

         for (Entry<String, List<Integer>> e : plan.entrySet()) {
            for (int lvl : e.getValue()) {
               members.add(buildingId + "_" + e.getKey() + "_" + lvl + ".nbt");
               members.add(buildingId + "_" + e.getKey() + "_" + lvl + "_special_points.json");
            }
         }

         members.addAll(fragmentsOf(legacyDir, buildingId));

         try (ExportTransaction tx = ExportTransaction.begin(exportedRoot, culturePath, buildingId, targetDir)) {
            for (String member : members) {
               Path source = legacyDir.resolve(member);
               if (Files.isRegularFile(source) && !Files.isRegularFile(targetDir.resolve(member))) {
                  tx.stageCopy(member, source);
               }
            }

            if (!Files.isRegularFile(targetDir.resolve(buildingId + ".json"))) {
               tx.stageMarker(buildingId + ".json", Files.readString(legacyJson));
            }

            tx.commit();
         } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to migrate legacy export '{}' out of {}: {}", new Object[]{buildingId, legacyDir, e.getMessage()});
            return LegacyExportSinkMigrator.Result.FAILED;
         }

         deleteLegacyCopies(legacyDir, buildingId, members);
         LOGGER.info("Migrated legacy export '{}' from {} to {}", new Object[]{buildingId, legacyDir, targetDir});
         return LegacyExportSinkMigrator.Result.MIGRATED;
      } else {
         LOGGER.warn(
            "Legacy export '{}' declares no usable culture — left in {} where reads still find it. Add a \"culture\" field to have it migrated.",
            buildingId,
            legacyDir
         );
         return LegacyExportSinkMigrator.Result.NOT_PRESENT;
      }
   }

   static void deleteLegacyCopies(Path legacyDir, String buildingId, List<String> members) {
      List<String> all = new ArrayList<>(members);
      all.add(buildingId + ".json");

      for (String member : all) {
         try {
            Files.deleteIfExists(legacyDir.resolve(member));
         } catch (IOException e) {
            LOGGER.warn("Could not remove migrated legacy export file {}: {}", legacyDir.resolve(member), e.getMessage());
         }
      }
   }

   private static List<String> fragmentsOf(Path dir, String buildingId) {
      String prefix = buildingId + ".";

      try (Stream<Path> files = Files.list(dir)) {
         return files.filter(x$0 -> Files.isRegularFile(x$0))
            .map(p -> p.getFileName().toString())
            .filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
            .toList();
      } catch (IOException e) {
         LOGGER.warn("Could not list legacy export fragments in {}: {}", dir, e.getMessage());
         return List.of();
      }
   }

   public enum Result {
      NOT_PRESENT,
      MIGRATED,
      FAILED;
   }
}
