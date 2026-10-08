package org.millenaire.encyclopedia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class EncyclopediaWriter {
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   private EncyclopediaWriter() {
   }

   public static void write(
      Path outDir,
      EncyclopediaIndex index,
      Map<String, List<ExportLine>> structures,
      Map<String, Map<String, Map<String, String>>> textByLocale,
      Map<String, Map<String, String>> langByLocale,
      Map<String, VillageData> villageData,
      Map<String, BuildingCostData> buildingCost
   ) {
      try {
         Files.createDirectories(outDir);
         writeJson(outDir.resolve("index.json"), index);
         Path structureDir = outDir.resolve("generated").resolve("structure");
         Files.createDirectories(structureDir);

         for (Entry<String, List<ExportLine>> structureEntry : structures.entrySet()) {
            Travelbook book = new Travelbook(List.of(new ExportPage(structureEntry.getValue())));
            writeJson(structureDir.resolve(structureEntry.getKey() + ".json"), book);
         }

         Path textDir = outDir.resolve("generated").resolve("text");
         Files.createDirectories(textDir);

         for (Entry<String, Map<String, Map<String, String>>> localeEntry : textByLocale.entrySet()) {
            writeJson(textDir.resolve(localeEntry.getKey() + ".json"), localeEntry.getValue());
         }

         Path langDir = outDir.resolve("generated").resolve("lang");
         Files.createDirectories(langDir);

         for (Entry<String, Map<String, String>> langEntry : langByLocale.entrySet()) {
            writeJson(langDir.resolve(langEntry.getKey() + ".json"), langEntry.getValue());
         }

         Path villagesDir = outDir.resolve("generated").resolve("villages");
         Files.createDirectories(villagesDir);

         for (Entry<String, VillageData> villageEntry : villageData.entrySet()) {
            writeJson(villagesDir.resolve(villageEntry.getKey() + ".json"), villageEntry.getValue());
         }

         Path buildingCostDir = outDir.resolve("generated").resolve("buildingcost");
         Files.createDirectories(buildingCostDir);

         for (Entry<String, BuildingCostData> costEntry : buildingCost.entrySet()) {
            writeJson(buildingCostDir.resolve(costEntry.getKey() + ".json"), costEntry.getValue());
         }
      } catch (IOException e) {
         throw new UncheckedIOException("Failed to write encyclopedia export to " + outDir, e);
      }
   }

   private static void writeJson(Path path, Object value) throws IOException {
      try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
         GSON.toJson(value, writer);
      }
   }
}
