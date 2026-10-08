package org.millenaire.culture;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.millenaire.content.ContentFs;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.content.SourceElection;
import org.slf4j.Logger;

final class CultureContentScanner {
   private static final Logger LOGGER = LogUtils.getLogger();

   private CultureContentScanner() {
   }

   static void scan(String contentType, String culture) {
      Set<String> disabledIds = CultureLoader.resolveDisabledIds(culture, contentType);
      if (!"villager_type".equals(contentType) && !"village_type".equals(contentType) && !"building_plan".equals(contentType)) {
         LOGGER.error("scan: unknown contentType '{}' for culture {}", contentType, culture);
      } else {
         scanAndLoadFromContentFs(contentType, culture, disabledIds);
      }
   }

   private static void scanAndLoadFromContentFs(String contentType, String culture, Set<String> disabledIds) {
      String pluralType = CultureLoader.pluralTypeName(contentType);
      if (pluralType != null) {
         ContentFs cultureFs = CustomContentIndex.current().forCulture(culture);
         boolean flatLayout = "villages".equals(pluralType);
         int maxDepth = flatLayout ? 0 : 1;
         List<Resource> entries = cultureFs.walk(pluralType, maxDepth).collect(Collectors.toList());
         String typeRootPrefix = ("cultures/" + culture + "/" + pluralType + "/").toLowerCase(Locale.ROOT);
         Set<String> crossSourceShadowedPaths = buildCrossSourceShadowSet(contentType, entries, typeRootPrefix, flatLayout);
         Map<String, List<Resource>> fragmentsByBase = collectVariantFragments(contentType, entries, disabledIds);
         Set<String> dispatchedBases = new HashSet<>();

         for (Resource res : entries) {
            String relPathFull = res.relPath();
            if (!crossSourceShadowedPaths.contains(relPathFull) && relPathFull.endsWith(".json")) {
               String fileName = relPathFull.substring(relPathFull.lastIndexOf(47) + 1);
               if (!fileName.startsWith("_")
                  && (!"building_plan".equals(contentType) || !fileName.endsWith("_special_points.json"))
                  && !isVariantFragment(contentType, fileName)
                  && relPathFull.startsWith(typeRootPrefix)) {
                  String relInsideType = relPathFull.substring(typeRootPrefix.length());
                  int sep = relInsideType.indexOf(47);
                  String residualId;
                  if (flatLayout) {
                     if (sep >= 0) {
                        LOGGER.warn(
                           "Ignoring {} in sub-directory: {} (villages are flat under cultures/<c>/villages/). Move the file up one level.",
                           contentType,
                           relPathFull
                        );
                        continue;
                     }

                     residualId = relInsideType.substring(0, relInsideType.length() - 5);
                  } else {
                     if (sep < 0 || relInsideType.indexOf(47, sep + 1) >= 0) {
                        LOGGER.warn(
                           "Ignoring {} at unexpected depth: {} (expected cultures/<c>/{}/<category>/<id>.json).",
                           new Object[]{contentType, relPathFull, pluralType}
                        );
                        continue;
                     }

                     String leaf = relInsideType.substring(sep + 1);
                     residualId = leaf.substring(0, leaf.length() - 5);
                  }

                  if (!residualId.isEmpty()) {
                     if (disabledIds.contains(residualId)) {
                        LOGGER.debug("Skipping {} '{}' (listed in _disabled.json)", contentType, residualId);
                     } else {
                        String contentId = culture + "/" + residualId;
                        String relForCulture = pluralType + "/" + relInsideType;
                        String pathCategory = !flatLayout && sep > 0 ? relInsideType.substring(0, sep) : null;
                        String baseKey = relPathFull.substring(0, relPathFull.lastIndexOf(47)) + "/" + residualId.toLowerCase(Locale.ROOT);

                        try {
                           dispatch(
                              contentType, contentId, cultureFs, relForCulture, residualId, pathCategory, fragmentsByBase.getOrDefault(baseKey, List.of())
                           );
                           dispatchedBases.add(baseKey);
                        } catch (CrossFolderConflictException ex) {
                           throw ex;
                        } catch (Exception ex) {
                           LOGGER.error("Error loading {} from ContentFs: {}", new Object[]{relPathFull, ex.getMessage(), ex});
                        }
                     }
                  }
               }
            }
         }

         warnOrphanFragments(fragmentsByBase, dispatchedBases);
      }
   }

   private static Map<String, List<Resource>> collectVariantFragments(String contentType, List<Resource> entries, Set<String> disabledIds) {
      Map<String, List<Resource>> byBase = new LinkedHashMap<>();

      for (Resource res : entries) {
         String relPathFull = res.relPath();
         if (relPathFull.endsWith(".json")) {
            int slash = relPathFull.lastIndexOf(47);
            if (slash >= 0) {
               String fileName = relPathFull.substring(slash + 1);
               if (!fileName.startsWith("_") && isVariantFragment(contentType, fileName)) {
                  String stem = fileName.substring(0, fileName.length() - ".json".length());
                  if (disabledIds.contains(stem)) {
                     LOGGER.debug("Skipping variant fragment '{}' (listed in _disabled.json)", stem);
                  } else {
                     String baseId = stem.substring(0, stem.indexOf(46)).toLowerCase(Locale.ROOT);
                     String dir = relPathFull.substring(0, slash);
                     byBase.computeIfAbsent(dir + "/" + baseId, k -> new ArrayList<>()).add(res);
                  }
               }
            }
         }
      }

      return byBase;
   }

   private static boolean isVariantFragment(String contentType, String fileName) {
      if (!"building_plan".equals(contentType)) {
         return false;
      }

      if (!fileName.endsWith(".json")) {
         return false;
      }

      String stem = fileName.substring(0, fileName.length() - ".json".length());
      int dot = stem.indexOf(46);
      return dot > 0 && dot < stem.length() - 1;
   }

   private static void warnOrphanFragments(Map<String, List<Resource>> fragmentsByBase, Set<String> dispatchedBases) {
      for (Entry<String, List<Resource>> entry : fragmentsByBase.entrySet()) {
         if (!dispatchedBases.contains(entry.getKey())) {
            String baseId = entry.getKey().substring(entry.getKey().lastIndexOf(47) + 1);

            for (Resource fragment : entry.getValue()) {
               LOGGER.warn(
                  "[Millenaire] Variant fragment '{}' targets building '{}', which is not defined in that same folder (missing, disabled, failed to load, or shipped under another category) — the fragment was NOT applied. A fragment must sit in the very directory of its base plan set.",
                  fragment.relPath(),
                  baseId
               );
            }
         }
      }
   }

   private static Set<String> buildCrossSourceShadowSet(String contentType, List<Resource> entries, String typeRootPrefix, boolean flatLayout) {
      Map<String, List<Resource>> byResidualId = new LinkedHashMap<>();

      for (Resource res : entries) {
         String relPathFull = res.relPath();
         if (relPathFull.endsWith(".json")) {
            String fileName = relPathFull.substring(relPathFull.lastIndexOf(47) + 1);
            if (!fileName.startsWith("_")
               && (!"building_plan".equals(contentType) || !fileName.endsWith("_special_points.json"))
               && !isVariantFragment(contentType, fileName)
               && relPathFull.startsWith(typeRootPrefix)) {
               String relInsideType = relPathFull.substring(typeRootPrefix.length());
               int sep = relInsideType.indexOf(47);
               String residualId;
               if (flatLayout) {
                  if (sep >= 0) {
                     continue;
                  }

                  residualId = relInsideType.substring(0, relInsideType.length() - 5);
               } else {
                  if (sep < 0 || relInsideType.indexOf(47, sep + 1) >= 0) {
                     continue;
                  }

                  String leaf = relInsideType.substring(sep + 1);
                  residualId = leaf.substring(0, leaf.length() - 5);
               }

               if (!residualId.isEmpty()) {
                  byResidualId.computeIfAbsent(residualId, k -> new ArrayList<>()).add(res);
               }
            }
         }
      }

      Set<String> shadowed = new HashSet<>();

      for (Entry<String, List<Resource>> e : byResidualId.entrySet()) {
         List<Resource> list = e.getValue();
         if (list.size() >= 2) {
            int winnerIdx = SourceElection.crossSourceWinnerIndexOf(list);
            if (winnerIdx != -1) {
               Resource winner = list.get(winnerIdx);

               for (int i = 0; i < list.size(); i++) {
                  if (i != winnerIdx) {
                     Resource shadowedEntry = list.get(i);
                     shadowed.add(shadowedEntry.relPath());
                     LOGGER.warn(
                        "[Millenaire] {} '{}' at '{}' ({}) shadowed by '{}' at '{}' ({}) (cross-source category override — higher-priority source wins)",
                        new Object[]{
                           contentType,
                           e.getKey(),
                           shadowedEntry.relPath(),
                           shadowedEntry.source().displayName(),
                           winner.relPath(),
                           winner.source().displayName(),
                           winner.kind()
                        }
                     );
                  }
               }
            }
         }
      }

      return shadowed;
   }

   private static void dispatch(
      String contentType, String contentId, ContentFs cultureFs, String relPath, String residualId, String pathCategory, List<Resource> variantFragments
   ) {
      switch (contentType) {
         case "building_plan":
            BuildingPlanSetLoader.loadFromContentFs(contentId, cultureFs, relPath, pathCategory, variantFragments);
            break;
         case "villager_type":
            VillagerTypeLoader.loadFromContentFs(contentId, cultureFs, relPath);
            break;
         case "village_type":
            VillageTypeLoader.loadFromContentFs(contentId, cultureFs, relPath, residualId);
            break;
         default:
            LOGGER.warn("dispatch: unknown contentType '{}'", contentType);
      }
   }
}
