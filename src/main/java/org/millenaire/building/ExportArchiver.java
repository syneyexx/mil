package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class ExportArchiver {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

   private ExportArchiver() {
   }

   public static ExportArchiver.Result forget(ServerLevel level, String cultureKey, String buildingId) {
      ImportBuildingResolver.ResolvedBuilding resolved = ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).orElse(null);
      if (resolved != null && resolved.fromExports()) {
         Path gameDir = level.getServer().getServerDirectory();
         ExportArchiver.Result result = archive(
            gameDir,
            BuildingExporter.culturePathComponent(cultureKey),
            resolved.category(),
            buildingId,
            ImportBuildingResolver.variantLevels(resolved.unionPlan())
         );
         if (result == ExportArchiver.Result.ARCHIVED) {
            BuildingExporter.refreshOverlay();
         }

         return result;
      } else {
         return ExportArchiver.Result.NOT_AN_EXPORT;
      }
   }

   static ExportArchiver.Result archive(Path gameDir, String culturePath, String category, String buildingId, Map<String, List<Integer>> plan) {
      Path exportDir = BuildingExporter.getExportedRoot(gameDir).resolve("cultures").resolve(culturePath).resolve("buildings").resolve(category);
      String jsonName = buildingId + ".json";
      if (!Files.isRegularFile(exportDir.resolve(jsonName))) {
         return ExportArchiver.Result.NOT_AN_EXPORT;
      }

      Path trashDir = freshTrashDir(gameDir).resolve("cultures").resolve(culturePath).resolve("buildings").resolve(category);

      try {
         Files.createDirectories(trashDir);
         move(exportDir.resolve(jsonName), trashDir.resolve(jsonName));

         for (String member : familyOf(exportDir, buildingId, plan)) {
            move(exportDir.resolve(member), trashDir.resolve(member));
         }
      } catch (IOException e) {
         LOGGER.error("Failed to archive export {}/{} into {}", new Object[]{culturePath, buildingId, trashDir, e});
         return ExportArchiver.Result.FAILED;
      }

      Path legacyDir = BuildingExporter.getLegacyExportDir(gameDir);

      try {
         Path legacyJson = legacyDir.resolve(jsonName);
         if (Files.isRegularFile(legacyJson)) {
            move(legacyJson, trashDir.resolve("legacy-" + jsonName));

            for (String member : familyOf(legacyDir, buildingId, plan)) {
               move(legacyDir.resolve(member), trashDir.resolve("legacy-" + member));
            }
         }
      } catch (IOException e) {
         LOGGER.error(
            "Archived the canonical export of {} into {}, but its legacy copy in {} could not be cleared — it will be migrated back on the next selection. Remove it by hand.",
            new Object[]{buildingId, trashDir, legacyDir, e}
         );
         return ExportArchiver.Result.FAILED;
      }

      LOGGER.info("Archived export {}/{} to {}", new Object[]{culturePath, buildingId, trashDir});
      return ExportArchiver.Result.ARCHIVED;
   }

   private static Path freshTrashDir(Path gameDir) {
      Path root = BuildingExporter.getExportedRoot(gameDir).resolve(".trash");
      String stamp = LocalDateTime.now().format(STAMP);
      Path candidate = root.resolve(stamp);

      for (int n = 2; Files.exists(candidate); n++) {
         candidate = root.resolve(stamp + "-" + n);
      }

      return candidate;
   }

   private static List<String> familyOf(Path dir, String buildingId, Map<String, List<Integer>> plan) {
      Set<String> members = new LinkedHashSet<>();

      for (Entry<String, List<Integer>> e : plan.entrySet()) {
         for (int lvl : e.getValue()) {
            String stem = buildingId + "_" + e.getKey() + "_" + lvl;
            members.add(stem + ".nbt");
            members.add(stem + "_special_points.json");
         }
      }

      members.addAll(fragmentsOf(dir, buildingId));
      List<String> present = new ArrayList<>(members.size());

      for (String member : members) {
         if (Files.isRegularFile(dir.resolve(member))) {
            present.add(member);
         }
      }

      return present;
   }

   private static List<String> fragmentsOf(Path dir, String buildingId) {
      if (!Files.isDirectory(dir)) {
         return List.of();
      }

      String prefix = buildingId + ".";

      try (Stream<Path> files = Files.list(dir)) {
         return files.filter(x$0 -> Files.isRegularFile(x$0))
            .map(p -> p.getFileName().toString())
            .filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
            .toList();
      } catch (IOException e) {
         LOGGER.warn("Could not list fragments of {} in {}: {}", new Object[]{buildingId, dir, e.getMessage()});
         return List.of();
      }
   }

   private static void move(Path source, Path target) throws IOException {
      if (Files.exists(source)) {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public enum Result {
      ARCHIVED,
      NOT_AN_EXPORT,
      FAILED;
   }
}
