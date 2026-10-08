package org.millenaire.building;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.block.ImportTableBlock;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public final class BuildingImporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int IMPORT_ALL_SPACING = 4;
   private static final BlockState WHITE_WOOL = Blocks.WHITE_WOOL.defaultBlockState();
   private static final BlockState ORANGE_WOOL = Blocks.ORANGE_WOOL.defaultBlockState();
   private static final BlockState YELLOW_WOOL = Blocks.YELLOW_WOOL.defaultBlockState();
   public static final String SCRATCH_CATEGORY = "extra";
   private static final int CLEAR_ABOVE = 20;

   private BuildingImporter() {
   }

   public static void importLevelFromCulture(
      ServerLevel level,
      ImportTableBlockEntity be,
      ServerPlayer player,
      String cultureKey,
      String buildingId,
      String variant,
      int upgradeLevel,
      String parentBuildingId,
      String parentVariant
   ) {
      Optional<ImportTablePlanResolver.LevelView> levelOpt = ImportTablePlanResolver.resolveLevel(cultureKey, buildingId, variant, upgradeLevel);
      if (levelOpt.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Invalid building ID: " + buildingId));
         } else if (ModCultures.getBuildingPlanSet(planSetId) == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Building plan set not found: " + planSetId));
         } else {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Variant '" + variant + "' level " + upgradeLevel + " not found for " + buildingId));
         }
      } else {
         ImportTablePlanResolver.LevelView levelView = levelOpt.get();
         boolean realIsSub = readIsSubBuildingFlag(cultureKey, buildingId);
         int resolvedTrigger = -1;
         if (realIsSub) {
            Optional<BuildingPlanSet.ParentRef> refOpt = readParentBuildingRef(cultureKey, buildingId);
            if (refOpt.isPresent()) {
               BuildingPlanSet.ParentRef ref = refOpt.get();
               if (!parentRefResolves(cultureKey, ref)) {
                  player.sendSystemMessage(
                     Component.literal(
                        "§c[ImportTable] '"
                           + buildingId
                           + "' declares parent '"
                           + ref.id()
                           + "/"
                           + ref.variant()
                           + "' level "
                           + ref.level()
                           + " which does not resolve — aborting import (fix its parent_building reference)"
                     )
                  );
                  return;
               }

               parentBuildingId = ref.id();
               parentVariant = ref.variant();
               resolvedTrigger = ref.level();
            } else {
               if (parentBuildingId == null || parentBuildingId.isEmpty()) {
                  player.sendSystemMessage(
                     Component.literal("§c[ImportTable] '" + buildingId + "' is a sub-building — select it under a parent so a base can be placed")
                  );
                  return;
               }

               Optional<Integer> triggerOpt = resolveTriggerLevel(level, cultureKey, parentBuildingId, parentVariant, buildingId);
               if (triggerOpt.isEmpty()) {
                  player.sendSystemMessage(
                     Component.literal(
                        "§c[ImportTable] Parent '"
                           + parentBuildingId
                           + (parentVariant != null && !parentVariant.isEmpty() ? "/" + parentVariant : "")
                           + "' does not reference sub-building '"
                           + buildingId
                           + "'"
                     )
                  );
                  return;
               }

               resolvedTrigger = triggerOpt.get();
            }
         }

         ResourceLocation cultureRl = ResourceLocation.tryParse(cultureKey);
         if (cultureRl == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Invalid culture key: " + cultureKey));
         } else {
            Optional<StructureTemplate> templateOpt = ImportBuildingResolver.resolveBuilding(cultureKey, buildingId)
               .flatMap(rb -> rb.nbt(variant, upgradeLevel))
               .map(res -> TemplateLoader.loadStructureFromResource(res, level));
            if (templateOpt.isEmpty()) {
               player.sendSystemMessage(Component.literal("§c[ImportTable] Template not found: " + levelView.nbtPath()));
            } else {
               boolean isFreshLoad = !buildingId.equals(be.getBuildingId()) || !variant.equals(be.getVariant()) || !cultureKey.equals(be.getCultureKey());
               be.setBuildingId(buildingId);
               be.setVariant(variant);
               be.setCultureKey(cultureKey);
               be.setUpgradeLevel(upgradeLevel);
               be.setWidth(levelView.width());
               be.setLength(levelView.depth());
               be.setHeight(levelView.height());
               be.setStartingLevel(levelView.groundLevel());
               if (realIsSub) {
                  be.setParentBuildingId(parentBuildingId);
                  be.setParentVariant(parentVariant == null ? "" : parentVariant);
                  be.setParentTriggerLevel(resolvedTrigger);
               } else {
                  be.clearParentContext();
               }

               if (isFreshLoad) {
                  be.setOrientation(levelView.buildingOrientation());
               }

               be.setImportedFromCulture(true);
               clearPlotVolume(level, be);
               placePreviousLevels(level, be, upgradeLevel, player);
               Rotation rotation = Rotation.NONE;
               boolean processMockBlocks = !be.isImportMockBlocks();
               if (!be.hasParentContext() && upgradeLevel == 0) {
                  placeTemplateWithFixes(level, be, templateOpt.get());
               } else {
                  BlockPos origin = BuildingExporter.computeScanOrigin(be);
                  BuildingPlacer.placeUpgradeFromTemplate(level, templateOpt.get(), origin, rotation, processMockBlocks);
               }

               HearthLightingUtil.lightHearthsInArea(level, BuildingExporter.computeScanOrigin(be), BuildingExporter.computeScanSize(be));
               be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
               be.setDirty(false);
               be.markImportSettlePending();
               placeConstructionBorder(level, be);
               placeImportLabel(level, be, labelFor(buildingId, variant, upgradeLevel));
               player.sendSystemMessage(Component.literal("§a[ImportTable] Imported " + buildingId + " " + variant + " level " + upgradeLevel));
            }
         }
      }
   }

   public static void importLevelFromExports(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String buildingId, String variant, int upgradeLevel
   ) {
      importLevelFromExports(level, be, player, "", "", buildingId, variant, upgradeLevel);
   }

   public static void importLevelFromExports(
      ServerLevel level,
      ImportTableBlockEntity be,
      ServerPlayer player,
      String cultureKey,
      String culturePath,
      String buildingId,
      String variant,
      int upgradeLevel
   ) {
      Optional<Path> nbtPathOpt = BuildingExporter.findExportedNbt(level, emptyToNull(culturePath), buildingId, variant, upgradeLevel);
      String nbtFile = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
      if (nbtPathOpt.isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] NBT file not found: " + nbtFile));
      } else {
         Path nbtPath = nbtPathOpt.get();
         Path exportDir = nbtPath.getParent();
         if (readExportIsSubBuildingFlag(exportDir, buildingId)) {
            player.sendSystemMessage(
               Component.literal("§c[ImportTable] '" + buildingId + "' is a sub-building — edit it via its culture's parent grouping, not the exports tab")
            );
         } else {
            StructureTemplate template = new StructureTemplate();

            try (InputStream is = Files.newInputStream(nbtPath)) {
               CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
               HearthTemplateSanitizer.sanitize(nbt, null);
               template.load(level.holderLookup(Registries.BLOCK), nbt);
            } catch (IOException e) {
               LOGGER.error("Failed to load NBT: {}", nbtPath, e);
               player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to load NBT: " + e.getMessage()));
               return;
            }

            String newCultureKey = cultureKey == null ? "" : cultureKey;
            boolean isFreshLoad = !buildingId.equals(be.getBuildingId()) || !variant.equals(be.getVariant()) || !newCultureKey.equals(be.getCultureKey());
            loadDimensionsFromJson(exportDir, buildingId, variant, upgradeLevel, be, isFreshLoad);
            be.setBuildingId(buildingId);
            be.setVariant(variant);
            be.setUpgradeLevel(upgradeLevel);
            be.setCultureKey(newCultureKey);
            if (isFreshLoad) {
               be.setImportedFromCulture(false);
               be.clearParentContext();
            }

            clearPlotVolume(level, be);
            placePreviousLevels(level, be, upgradeLevel, player);
            if (upgradeLevel == 0) {
               placeTemplateWithFixes(level, be, template);
            } else {
               Rotation rotation = Rotation.NONE;
               boolean processMockBlocks = !be.isImportMockBlocks();
               BlockPos origin = BuildingExporter.computeScanOrigin(be);
               BuildingPlacer.placeUpgradeFromTemplate(level, template, origin, rotation, processMockBlocks);
            }

            HearthLightingUtil.lightHearthsInArea(level, BuildingExporter.computeScanOrigin(be), BuildingExporter.computeScanSize(be));
            be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
            be.setDirty(false);
            be.markImportSettlePending();
            placeConstructionBorder(level, be);
            placeImportLabel(level, be, labelFor(buildingId, variant, upgradeLevel));
            player.sendSystemMessage(Component.literal("§a[ImportTable] Imported " + buildingId + " " + variant + " level " + upgradeLevel + " from exports"));
         }
      }
   }

   public static void importAllFromCulture(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String cultureKey, String buildingId, String variant
   ) {
      BuildingImporter.ImportAabb aabb = importAllVariantsFromCulture(level, be, player, cultureKey, buildingId);
      if (aabb != null) {
         player.sendSystemMessage(Component.literal("§a[ImportTable] Imported all plans of " + buildingId));
         announceFromExports(player, cultureKey, buildingId);
      }
   }

   public static void announceFromExports(ServerPlayer player, String cultureKey, String buildingId) {
      if (ImportTablePlanResolver.isServedFromExports(cultureKey, buildingId)) {
         ImportTablePlanResolver.formatFromExportsMessage("ImportTable", List.of(buildingId))
            .ifPresent(msg -> player.sendSystemMessage(Component.literal(msg)));
      }
   }

   @Nullable
   public static BuildingImporter.ImportAabb importAllVariantsFromCulture(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String cultureKey, String buildingId
   ) {
      Optional<ImportTablePlanResolver.PlanView> planOpt = ImportTablePlanResolver.resolvePlan(cultureKey, buildingId);
      if (planOpt.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId == null) {
            return null;
         }

         if (ModCultures.getBuildingPlanSet(planSetId) == null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Building plan set not found: " + planSetId));
         } else {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Could not read on-disk JSON for " + buildingId));
         }

         return null;
      } else {
         ImportTablePlanResolver.PlanView planView = planOpt.get();
         be.setIsMainTable(true);
         BlockPos mainPos = be.getBlockPos();
         Direction facing = (Direction)level.getBlockState(mainPos).getValue(ImportTableBlock.FACING);
         int globalMinX = Integer.MAX_VALUE;
         int globalMaxX = Integer.MIN_VALUE;
         int globalMinZ = Integer.MAX_VALUE;
         int globalMaxZ = Integer.MIN_VALUE;
         int imported = 0;
         int nextVariantMinX = Integer.MIN_VALUE;

         for (String variantKey : new ArrayList<>(planView.variants().keySet())) {
            ImportTablePlanResolver.VariantView variantView = planView.variants().get(variantKey);
            if (variantView != null && !variantView.levels().isEmpty()) {
               List<Integer> sortedLevels = variantView.levels().keySet().stream().sorted().toList();
               int variantMinX = Integer.MAX_VALUE;
               int variantMaxX = Integer.MIN_VALUE;
               int nextLevelMinZ = Integer.MIN_VALUE;
               boolean variantAnchored = false;
               int importedInVariant = 0;

               for (int levelNum : sortedLevels) {
                  ImportTableBlockEntity targetBe;
                  if (imported == 0) {
                     targetBe = be;
                  } else {
                     int desiredMinX;
                     int desiredMinZ;
                     if (!variantAnchored) {
                        desiredMinX = nextVariantMinX;
                        desiredMinZ = mainPos.getZ();
                     } else {
                        desiredMinX = variantMinX;
                        desiredMinZ = nextLevelMinZ;
                     }

                     BlockPos tablePos = tablePosForBuildingAabb(desiredMinX, desiredMinZ, mainPos.getY());
                     level.setBlock(
                        tablePos, (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get()).defaultBlockState().setValue(ImportTableBlock.FACING, facing), 2
                     );
                     if (!(level.getBlockEntity(tablePos) instanceof ImportTableBlockEntity childBe)) {
                        LOGGER.warn("Failed to create child ImportTable at {}", tablePos);
                        continue;
                     }

                     be.copySettingsTo(childBe);
                     targetBe = childBe;
                  }

                  importLevelFromCulture(level, targetBe, player, cultureKey, buildingId, variantKey, levelNum, "", "");
                  int actualOrient = normaliseOrientation(targetBe.getOrientation());
                  int actualW = targetBe.getWidth();
                  int actualL = targetBe.getLength();
                  int[] aabb = buildingAabb(targetBe.getBlockPos(), actualOrient, actualW, actualL);
                  int aMinX = aabb[0];
                  int aMinZ = aabb[1];
                  int aMaxX = aabb[2];
                  int aMaxZ = aabb[3];
                  if (!variantAnchored) {
                     variantMinX = aMinX;
                     variantMaxX = aMaxX;
                     variantAnchored = true;
                  } else {
                     variantMinX = Math.min(variantMinX, aMinX);
                     variantMaxX = Math.max(variantMaxX, aMaxX);
                  }

                  nextLevelMinZ = aMaxZ + 1 + 4;
                  globalMinX = Math.min(globalMinX, aMinX);
                  globalMinZ = Math.min(globalMinZ, aMinZ);
                  globalMaxX = Math.max(globalMaxX, aMaxX);
                  globalMaxZ = Math.max(globalMaxZ, aMaxZ);
                  imported++;
                  importedInVariant++;
               }

               if (importedInVariant > 0) {
                  nextVariantMinX = variantMaxX + 1 + 4;
               }
            }
         }

         return imported == 0 ? null : new BuildingImporter.ImportAabb(globalMinX, globalMinZ, globalMaxX, globalMaxZ);
      }
   }

   private static int normaliseOrientation(int orient) {
      return (orient % 4 + 4) % 4;
   }

   public static int[] buildingAabb(BlockPos tablePos, int orient, int w, int l) {
      int tx = tablePos.getX();
      int tz = tablePos.getZ();
      return new int[]{tx + 1, tz + 1, tx + w, tz + l};
   }

   public static BlockPos tablePosForBuildingAabb(int worldMinX, int worldMinZ, int y) {
      return new BlockPos(worldMinX - 1, y, worldMinZ - 1);
   }

   @Deprecated
   public static BlockPos tablePosForBuildingAabb(int worldMinX, int worldMinZ, int y, int orient, int w, int l) {
      return tablePosForBuildingAabb(worldMinX, worldMinZ, y);
   }

   public static void importAllFromExports(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String buildingId, String variant) {
      importAllFromExports(level, be, player, "", "", buildingId, variant);
   }

   public static void importAllFromExports(
      ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, String cultureKey, String culturePath, String buildingId, String variant
   ) {
      String scope = emptyToNull(culturePath);
      if (BuildingExporter.findExportedNbt(level, scope, buildingId, variant, 0).isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] No levels found for " + buildingId + " " + variant));
      } else {
         Path exportJson = BuildingExporter.findExportedJson(level, scope, buildingId).orElse(null);
         if (exportJson != null && readExportIsSubBuildingFlag(exportJson.getParent(), buildingId)) {
            player.sendSystemMessage(
               Component.literal("§c[ImportTable] '" + buildingId + "' is a sub-building — edit it via its culture's parent grouping, not the exports tab")
            );
         } else {
            int maxLevel = 0;

            while (BuildingExporter.findExportedNbt(level, scope, buildingId, variant, maxLevel + 1).isPresent()) {
               maxLevel++;
            }

            be.setIsMainTable(true);
            be.setBuildingId(buildingId);
            be.setVariant(variant);
            int totalLevels = maxLevel + 1;
            importAllLevels(
               level,
               be,
               player,
               totalLevels,
               (lvl, childBe) -> importLevelFromExports(level, childBe, player, cultureKey, culturePath, buildingId, variant, lvl)
            );
            player.sendSystemMessage(
               Component.literal("§a[ImportTable] Imported all " + totalLevels + " levels of " + buildingId + " " + variant + " from exports")
            );
         }
      }
   }

   public static void reimport(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (!be.hasPlan()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] No plan loaded on this table"));
      } else {
         String cultureKey = be.getCultureKey();
         if (!cultureKey.isEmpty() && !cultureKey.startsWith("millenaire-custom")) {
            importLevelFromCulture(
               level, be, player, cultureKey, be.getBuildingId(), be.getVariant(), be.getUpgradeLevel(), be.getParentBuildingId(), be.getParentVariant()
            );
         } else {
            importLevelFromExports(level, be, player, be.getBuildingId(), be.getVariant(), be.getUpgradeLevel());
         }
      }
   }

   public static void reimportAll(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      ImportTableBlockEntity mainTable = be.resolveMainTable(level);
      mainTable.propagatePlanIdentity(level);
      reimport(level, mainTable, player);
      List<ImportTableBlockEntity> children = mainTable.findChildTables(level);

      for (ImportTableBlockEntity child : children) {
         reimport(level, child, player);
      }

      player.sendSystemMessage(Component.literal("§a[ImportTable] Re-imported all " + (1 + children.size()) + " levels"));
      announceFromExports(player, mainTable.getCultureKey(), mainTable.getBuildingId());
   }

   public static void reimportDependentLevels(ServerLevel level, ImportTableBlockEntity exported, ServerPlayer player) {
      ImportTableBlockEntity main = exported.resolveMainTable(level);
      if (main.isMainTable()) {
         String variant = exported.getVariant();
         int exportedLevel = exported.getUpgradeLevel();
         List<Integer> skippedDirty = new ArrayList<>();

         for (ImportTableBlockEntity table : main.findChildTables(level)) {
            if (table != exported && variant.equals(table.getVariant()) && table.getUpgradeLevel() > exportedLevel) {
               if (table.hasUnsavedChanges(level)) {
                  skippedDirty.add(table.getUpgradeLevel());
               } else {
                  reimport(level, table, player);
               }
            }
         }

         if (!skippedDirty.isEmpty()) {
            skippedDirty.sort(Integer::compareTo);
            StringBuilder levels = new StringBuilder();

            for (int i = 0; i < skippedDirty.size(); i++) {
               if (i > 0) {
                  levels.append(", ");
               }

               levels.append(skippedDirty.get(i));
            }

            player.sendSystemMessage(
               Component.literal("§e[ImportTable] Did not refresh level(s) " + levels + " (unsaved edits) — export them before Re-Import All")
            );
         }
      }
   }

   static String sanitizeBuildingName(String input) {
      return input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_\\-]", "");
   }

   public static void createNewBuilding(
      ServerLevel level,
      ImportTableBlockEntity be,
      ServerPlayer player,
      int length,
      int width,
      int startingLevel,
      int height,
      boolean clearGround,
      int numberOfUpgrades,
      boolean preBuilt,
      String buildingName
   ) {
      Path exportDir = BuildingExporter.getExportDir(level, "", "extra");

      try {
         Files.createDirectories(exportDir);
      } catch (IOException e) {
         LOGGER.error("Failed to create export dir", e);
         return;
      }

      if (preBuilt && numberOfUpgrades > 0) {
         int spacing = length + 4;
         BlockPos mainPos = be.getBlockPos();

         for (int lvl = 1; lvl <= numberOfUpgrades; lvl++) {
            BlockPos childPos = mainPos.offset(0, 0, spacing * lvl);
            if (level.getBlockEntity(childPos) != null) {
               player.sendSystemMessage(
                  Component.literal(
                     "§c[ImportTable] Level "
                        + lvl
                        + " table position "
                        + childPos.toShortString()
                        + " is obstructed (block entity present) — clear the row and retry. Expected spacing: "
                        + spacing
                        + " blocks (Z+)."
                  )
               );
               return;
            }
         }
      }

      String sanitized = sanitizeBuildingName(buildingName);
      String buildingId;
      if (sanitized.isEmpty()) {
         int nextN = 0;

         while (BuildingExporter.findExportedJson(level, "export" + nextN).isPresent()) {
            nextN++;
         }

         buildingId = "export" + nextN;
      } else if (BuildingExporter.findExportedJson(level, sanitized).isEmpty()) {
         buildingId = sanitized;
      } else {
         int suffix = 2;

         while (BuildingExporter.findExportedJson(level, sanitized + "_" + suffix).isPresent()) {
            suffix++;
         }

         buildingId = sanitized + "_" + suffix;
      }

      String nativeName = buildingName != null && !buildingName.isBlank() ? buildingName.trim() : buildingId;
      be.setBuildingId(buildingId);
      be.setVariant("a");
      be.setUpgradeLevel(0);
      be.setLength(length);
      be.setWidth(width);
      be.setStartingLevel(startingLevel);
      be.setHeight(height);
      be.setClearGround(clearGround);
      be.setCultureKey("");
      be.setOrientation(1);
      be.setImportedFromCulture(false);
      JsonObject root = new JsonObject();
      root.addProperty("culture", "millenaire:custom");
      root.addProperty("building_id", buildingId);
      root.addProperty("category", "extra");
      root.addProperty("native_name", nativeName);
      JsonArray variants = new JsonArray();
      JsonObject variantObj = new JsonObject();
      variantObj.addProperty("variant", "a");
      variantObj.addProperty("building_orientation", be.getOrientation());
      JsonArray levels = new JsonArray();
      JsonObject levelObj = new JsonObject();
      levelObj.addProperty("level", 0);
      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", width);
      footprint.addProperty("height", height);
      footprint.addProperty("depth", length);
      levelObj.add("footprint", footprint);
      levelObj.addProperty("ground_level", startingLevel);
      levels.add(levelObj);
      variantObj.add("levels", levels);
      variants.add(variantObj);
      root.add("variants", variants);
      if (clearGround) {
         clearBuildingGround(level, be);
      }

      BlockPos scanOrigin = BuildingExporter.computeScanOrigin(be);
      if (preBuilt) {
         int detectedH = BuildingExporter.detectBuildingHeight(level, scanOrigin, be, 0);
         be.setHeight(detectedH);
         footprint.addProperty("height", detectedH);
      }

      Vec3i scanSize = BuildingExporter.computeScanSize(be);
      StructureTemplate template = new StructureTemplate();
      template.fillFromWorld(level, scanOrigin, scanSize, false, Blocks.AIR);
      CompoundTag nbt = template.save(new CompoundTag());

      try (ExportTransaction tx = ExportTransaction.begin(
            BuildingExporter.getExportedRoot(level), BuildingExporter.culturePathComponent(""), buildingId, exportDir
         )) {
         tx.stageNbt(buildingId + "_a_0.nbt", nbt);
         tx.stageMarker(buildingId + ".json", new GsonBuilder().setPrettyPrinting().create().toJson(root));
         tx.commit();
      } catch (IOException | RuntimeException e) {
         LOGGER.error("Failed to create building {}", buildingId, e);
         player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to create building: " + e.getMessage()));
         return;
      }

      be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
      be.setDirty(false);
      placeConstructionBorder(level, be);
      if (preBuilt && numberOfUpgrades > 0) {
         be.setIsMainTable(true);
         placeImportLabel(level, be, labelFor(buildingId, "a", 0));
         int spacing = length + 4;
         BlockPos mainPos = be.getBlockPos();
         BlockState mainFacing = level.getBlockState(mainPos);
         int captured = 0;

         for (int lvl = 1; lvl <= numberOfUpgrades; lvl++) {
            BlockPos childPos = mainPos.offset(0, 0, spacing * lvl);
            level.setBlock(
               childPos,
               (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get())
                  .defaultBlockState()
                  .setValue(ImportTableBlock.FACING, (Direction)mainFacing.getValue(ImportTableBlock.FACING)),
               2
            );
            ImportTableBlockEntity childBe = (ImportTableBlockEntity)level.getBlockEntity(childPos);
            if (childBe == null) {
               LOGGER.warn("[ImportTable] Failed to create child table at {} for level {}", childPos, lvl);
               player.sendSystemMessage(
                  Component.literal(
                     "§c[ImportTable] Failed to place child table for level "
                        + lvl
                        + " at "
                        + childPos.toShortString()
                        + " — stopping at "
                        + captured
                        + " captured level(s)."
                  )
               );
               break;
            }

            be.copySettingsTo(childBe);
            childBe.setBuildingId(buildingId);
            childBe.setVariant("a");
            childBe.setUpgradeLevel(lvl);
            childBe.setCultureKey("");
            childBe.setImportedFromCulture(false);
            if (!BuildingExporter.captureAndWriteLevel(level, childBe, exportDir, player)) {
               break;
            }

            placeConstructionBorder(level, childBe);
            placeImportLabel(level, childBe, labelFor(buildingId, "a", lvl));
            captured++;
         }

         BuildingExporter.refreshOverlay();
         player.sendSystemMessage(
            Component.literal(
               "§a[ImportTable] Created pre-built building: " + buildingId + " — " + (captured + 1) + " level(s) captured (" + width + "x" + length + ")"
            )
         );
      } else {
         BuildingExporter.exportAdditionalLevels(level, be, player, numberOfUpgrades);
         player.sendSystemMessage(Component.literal("§a[ImportTable] Created new building: " + buildingId + " (" + width + "x" + length + ")"));
      }
   }

   @Nullable
   public static String createSubBuilding(
      ServerLevel level,
      ImportTableBlockEntity be,
      ServerPlayer player,
      String parentCulture,
      String parentId,
      String parentVariant,
      int triggerLevel,
      String subName
   ) {
      if (parentCulture == null
         || parentCulture.isEmpty()
         || parentId == null
         || parentId.isEmpty()
         || parentVariant == null
         || parentVariant.isEmpty()
         || subName == null
         || subName.isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] create-sub requires culture, parent, variant and a sub name"));
         return null;
      }

      if (triggerLevel < 0) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] trigger level must be >= 0"));
         return null;
      }

      if (readIsSubBuildingFlag(parentCulture, parentId)) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] '" + parentId + "' is itself a sub-building — cannot parent another sub"));
         return null;
      }

      Optional<ImportTablePlanResolver.LevelView> parentLevelOpt = ImportTablePlanResolver.resolveLevel(parentCulture, parentId, parentVariant, triggerLevel);
      if (parentLevelOpt.isEmpty()) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] Parent '" + parentId + "/" + parentVariant + "' level " + triggerLevel + " not found"));
         return null;
      }

      ImportTablePlanResolver.LevelView parentLevel = parentLevelOpt.get();
      String subId = parentId + "_" + parentVariant + "_" + subName;
      if (ImportTableIdentifierValidator.isValidIdentifier(subId) && subId.equals(subId.toLowerCase(Locale.ROOT))) {
         ResourceLocation subPlanId = ResourceLocation.tryParse(parentCulture + "/" + subId);
         Path subExportJson = BuildingExporter.getExportDir(level, parentCulture, BuildingExporter.categoryFor(parentCulture, parentId))
            .resolve(subId + ".json");
         if ((subPlanId == null || ModCultures.getBuildingPlanSet(subPlanId) == null) && !Files.exists(subExportJson)) {
            be.setBuildingId(subId);
            be.setVariant("a");
            be.setCultureKey(parentCulture);
            be.setUpgradeLevel(0);
            be.setWidth(parentLevel.width());
            be.setLength(parentLevel.depth());
            be.setHeight(parentLevel.height());
            be.setStartingLevel(parentLevel.groundLevel());
            be.setOrientation(parentLevel.buildingOrientation());
            be.setParentBuildingId(parentId);
            be.setParentVariant(parentVariant);
            be.setParentTriggerLevel(triggerLevel);
            be.setImportMockBlocks(true);
            be.setImportedFromCulture(true);
            clearPlotVolume(level, be);
            placePreviousLevels(level, be, 0, player);
            HearthLightingUtil.lightHearthsInArea(level, BuildingExporter.computeScanOrigin(be), BuildingExporter.computeScanSize(be));
            be.captureSavedState(BuildingExporter.computeBlocksHash(level, be));
            be.setDirty(false);
            be.markImportSettlePending();
            placeConstructionBorder(level, be);
            placeImportLabel(level, be, labelFor(subId, "a", 0));
            player.sendSystemMessage(
               Component.literal(
                  "§a[ImportTable] Created sub-building '"
                     + subId
                     + "' on "
                     + parentId
                     + "/"
                     + parentVariant
                     + " at level "
                     + triggerLevel
                     + " — build on top, then Export"
               )
            );
            return subId;
         } else {
            player.sendSystemMessage(Component.literal("§c[ImportTable] '" + subId + "' already exists — pick another name"));
            return null;
         }
      } else {
         player.sendSystemMessage(Component.literal("§c[ImportTable] Invalid sub id '" + subId + "' — use lowercase [a-z0-9_-]"));
         return null;
      }
   }

   static List<BuildingImporter.BaseLayer> computeBaseLayers(ImportTableBlockEntity be) {
      return computeBaseLayers(
         be.getBuildingId(),
         be.getVariant(),
         be.getUpgradeLevel(),
         be.hasParentContext() ? be.getParentBuildingId() : "",
         be.getParentVariant(),
         be.getParentTriggerLevel()
      );
   }

   static List<BuildingImporter.BaseLayer> computeExportBaseLayers(ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      boolean isRealSub = readIsSubBuildingFlag(cultureKey, buildingId) || be.hasParentContext();
      return !isRealSub
         ? computeBaseLayers(buildingId, be.getVariant(), be.getUpgradeLevel(), "", be.getParentVariant(), be.getParentTriggerLevel())
         : computeExportBaseLayersForSub(be, cultureKey, buildingId);
   }

   static boolean exportParentDeclared(ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      boolean isRealSub = readIsSubBuildingFlag(cultureKey, buildingId) || be.hasParentContext();
      if (!isRealSub) {
         return false;
      }

      Optional<BuildingPlanSet.ParentRef> refOpt = readParentBuildingRef(cultureKey, buildingId);
      String parentId = refOpt.map(BuildingPlanSet.ParentRef::id).orElse(be.hasParentContext() ? be.getParentBuildingId() : "");
      return parentId != null && !parentId.isEmpty();
   }

   private static List<BuildingImporter.BaseLayer> computeExportBaseLayersForSub(ImportTableBlockEntity be, String cultureKey, String buildingId) {
      Optional<BuildingPlanSet.ParentRef> refOpt = readParentBuildingRef(cultureKey, buildingId);
      String parentId = refOpt.map(BuildingPlanSet.ParentRef::id).orElse(be.hasParentContext() ? be.getParentBuildingId() : "");
      String parentVariant = refOpt.map(BuildingPlanSet.ParentRef::variant).orElse(be.getParentVariant());
      int triggerLevel = refOpt.map(BuildingPlanSet.ParentRef::level).orElse(be.getParentTriggerLevel());
      return computeBaseLayers(buildingId, be.getVariant(), be.getUpgradeLevel(), parentId, parentVariant, triggerLevel);
   }

   static List<BuildingImporter.BaseLayer> computeBaseLayers(
      String selfId, String selfVariant, int selfLevel, String parentId, String parentVariant, int triggerLevel
   ) {
      List<BuildingImporter.BaseLayer> layers = new ArrayList<>();
      boolean hasParent = parentId != null && !parentId.isEmpty();
      if (hasParent) {
         for (int lvl = 0; lvl <= triggerLevel; lvl++) {
            layers.add(new BuildingImporter.BaseLayer(parentId, parentVariant, lvl));
         }
      }

      for (int lvl = 0; lvl < selfLevel; lvl++) {
         layers.add(new BuildingImporter.BaseLayer(selfId, selfVariant, lvl));
      }

      return layers;
   }

   public static Optional<Integer> resolveTriggerLevel(ServerLevel level, String cultureKey, String parentId, String parentVariant, String subId) {
      JsonObject parentRoot = readBuildingJson(cultureKey, parentId).orElse(null);
      return parentRoot == null ? Optional.empty() : resolveTriggerLevelFromJson(parentRoot, parentId, parentVariant, subId);
   }

   public static List<BuildingImporter.SubRef> listReferencedSubs(String cultureKey, String parentId) {
      JsonObject root = readBuildingJson(cultureKey, parentId).orElse(null);
      if (root == null) {
         return List.of();
      }

      LinkedHashMap<String, String> byId = new LinkedHashMap<>();
      if (root.has("starting_sub_buildings")) {
         for (JsonElement e : root.getAsJsonArray("starting_sub_buildings")) {
            if (e.isJsonPrimitive()) {
               byId.putIfAbsent(e.getAsString(), "");
            }
         }
      }

      if (root.has("variants")) {
         for (JsonElement ve : root.getAsJsonArray("variants")) {
            JsonObject v = ve.getAsJsonObject();
            if (v.has("variant") && v.has("levels")) {
               String variant = v.get("variant").getAsString();

               for (JsonElement le : v.getAsJsonArray("levels")) {
                  JsonArray subs = le.getAsJsonObject().has("sub_buildings") ? le.getAsJsonObject().getAsJsonArray("sub_buildings") : null;
                  if (subs != null) {
                     for (JsonElement se : subs) {
                        if (se.isJsonPrimitive()) {
                           byId.putIfAbsent(se.getAsString(), variant);
                        }
                     }
                  }
               }
            }
         }
      }

      return byId.entrySet()
         .stream()
         .sorted(Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
         .map(e -> new BuildingImporter.SubRef(e.getKey(), e.getValue()))
         .toList();
   }

   static Optional<Integer> resolveTriggerLevelFromJson(JsonObject parentRoot, String parentId, String parentVariant, String subId) {
      if (jsonArrayContains(parentRoot.has("starting_sub_buildings") ? parentRoot.getAsJsonArray("starting_sub_buildings") : null, subId)) {
         return Optional.of(0);
      }

      JsonArray variants = parentRoot.has("variants") ? parentRoot.getAsJsonArray("variants") : null;
      if (variants == null) {
         return Optional.empty();
      }

      Integer found = null;

      for (JsonElement ve : variants) {
         JsonObject v = ve.getAsJsonObject();
         if (v.has("variant") && parentVariant.equals(v.get("variant").getAsString())) {
            JsonArray levels = v.has("levels") ? v.getAsJsonArray("levels") : null;
            if (levels != null) {
               for (JsonElement le : levels) {
                  JsonObject l = le.getAsJsonObject();
                  if (l.has("level")) {
                     JsonArray subs = l.has("sub_buildings") ? l.getAsJsonArray("sub_buildings") : null;
                     if (jsonArrayContains(subs, subId)) {
                        int lvl = l.get("level").getAsInt();
                        if (found == null) {
                           found = lvl;
                        } else {
                           LOGGER.warn(
                              "ImportTable: sub '{}' referenced at multiple levels of {} variant {} (at least {} and {}); using lowest ({})",
                              new Object[]{subId, parentId, parentVariant, found, lvl, Math.min(found, lvl)}
                           );
                           found = Math.min(found, lvl);
                        }
                     }
                  }
               }
            }
            break;
         }
      }

      return Optional.ofNullable(found);
   }

   private static boolean jsonArrayContains(@Nullable JsonArray array, String value) {
      if (array == null) {
         return false;
      }

      for (JsonElement e : array) {
         if (e.isJsonPrimitive() && value.equals(e.getAsString())) {
            return true;
         }
      }

      return false;
   }

   public static boolean readIsSubBuildingFlag(String cultureKey, String buildingId) {
      return readBuildingJson(cultureKey, buildingId).map(root -> root.has("is_sub_building") && root.get("is_sub_building").getAsBoolean()).orElse(false);
   }

   public static Optional<BuildingPlanSet.ParentRef> readParentBuildingRef(String cultureKey, String buildingId) {
      return readBuildingJson(cultureKey, buildingId).map(ImportTablePlanResolver::parseParentRef);
   }

   static boolean parentRefResolves(String cultureKey, BuildingPlanSet.ParentRef ref) {
      return ref.level() < 0 ? false : ImportTablePlanResolver.resolveLevel(cultureKey, ref.id(), ref.variant(), ref.level()).isPresent();
   }

   @Nullable
   private static String emptyToNull(@Nullable String s) {
      return s != null && !s.isEmpty() ? s : null;
   }

   private static boolean readExportIsSubBuildingFlag(Path exportDir, String buildingId) {
      if (exportDir == null) {
         return false;
      }

      Path jsonPath = exportDir.resolve(buildingId + ".json");
      if (!Files.exists(jsonPath)) {
         return false;
      }

      try {
         JsonObject root = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();
         return root.has("is_sub_building") && root.get("is_sub_building").getAsBoolean();
      } catch (Exception e) {
         LOGGER.warn("ImportTable: failed to read export JSON {}: {}", jsonPath, e.getMessage());
         return false;
      }
   }

   static Optional<JsonObject> readBuildingJson(String cultureKey, String buildingId) {
      return ImportBuildingResolver.resolveBuilding(cultureKey, buildingId).map(ImportBuildingResolver.ResolvedBuilding::effectiveJson);
   }

   static Optional<BuildingImporter.ResolvedPreviousLevel> resolvePreviousLevel(
      ServerLevel level, String cultureKey, String buildingId, String variant, int lvl, int fallbackGroundLevel, @Nullable Path exportJsonPath
   ) {
      boolean hasCulture = cultureKey != null && !cultureKey.isEmpty();
      ImportTablePlanResolver.VariantView variantView = hasCulture
         ? ImportTablePlanResolver.resolveVariant(cultureKey, buildingId, variant).orElse(null)
         : null;
      if (hasCulture) {
         if (variantView != null && variantView.levels().containsKey(lvl)) {
            ImportTablePlanResolver.LevelView lv = variantView.levels().get(lvl);
            StructureTemplate tmpl = ImportBuildingResolver.resolveBuilding(cultureKey, buildingId)
               .flatMap(rb -> rb.nbt(variant, lvl))
               .map(res -> TemplateLoader.loadStructureFromResource(res, level))
               .orElse(null);
            return tmpl == null ? Optional.empty() : Optional.of(new BuildingImporter.ResolvedPreviousLevel(tmpl, lv.groundLevel()));
         } else {
            return Optional.empty();
         }
      } else {
         StructureTemplate tmpl = TemplateLoader.resolve(level, cultureKey, buildingId, variant, lvl).orElse(null);
         if (tmpl == null) {
            return Optional.empty();
         }

         int gl = readGroundLevelFromJsonPath(exportJsonPath, variant, lvl, fallbackGroundLevel);
         return Optional.of(new BuildingImporter.ResolvedPreviousLevel(tmpl, gl));
      }
   }

   private static void placePreviousLevels(ServerLevel level, ImportTableBlockEntity be, int upgradeLevel, ServerPlayer player) {
      List<BuildingImporter.BaseLayer> layers = computeBaseLayers(be);
      if (!layers.isEmpty()) {
         String cultureKey = be.getCultureKey();
         boolean processMockBlocks = !be.isImportMockBlocks();
         Rotation rotation = Rotation.NONE;
         BlockPos scanOrigin = BuildingExporter.computeScanOrigin(be);
         int currentGroundLevel = be.getStartingLevel();
         int baseY = be.getBlockPos().getY();

         for (int i = 0; i < layers.size(); i++) {
            BuildingImporter.BaseLayer layer = layers.get(i);
            Path exportJsonPath = BuildingExporter.findExportedJson(level, layer.buildingId()).orElse(null);
            BuildingImporter.ResolvedPreviousLevel resolved = resolvePreviousLevel(
                  level, cultureKey, layer.buildingId(), layer.variant(), layer.level(), currentGroundLevel, exportJsonPath
               )
               .orElse(null);
            if (resolved == null) {
               if (i == 0) {
                  player.sendSystemMessage(
                     Component.literal(
                        "§e[ImportTable] Warning: base level template not found ("
                           + layer.buildingId()
                           + " "
                           + layer.variant()
                           + " "
                           + layer.level()
                           + "), skipping previous levels"
                     )
                  );
                  return;
               }

               player.sendSystemMessage(
                  Component.literal(
                     "§e[ImportTable] Warning: base level template not found ("
                        + layer.buildingId()
                        + " "
                        + layer.variant()
                        + " "
                        + layer.level()
                        + "), skipping"
                  )
               );
            } else {
               BlockPos originLvl = new BlockPos(scanOrigin.getX(), baseY + resolved.groundLevel(), scanOrigin.getZ());
               if (i == 0) {
                  BuildingPlacer.placeFromTemplate(level, resolved.template(), originLvl, rotation, processMockBlocks);
               } else {
                  BuildingPlacer.placeUpgradeFromTemplate(level, resolved.template(), originLvl, rotation, processMockBlocks);
               }
            }
         }
      }
   }

   private static int readGroundLevelFromJsonPath(@Nullable Path jsonPath, String variant, int upgradeLevel, int fallback) {
      if (jsonPath != null && Files.exists(jsonPath)) {
         try {
            String content = Files.readString(jsonPath);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : null;
            if (variants == null) {
               return fallback;
            }

            for (JsonElement ve : variants) {
               JsonObject v = ve.getAsJsonObject();
               if (variant.equals(v.get("variant").getAsString())) {
                  Integer variantGroundLevel = v.has("ground_level") ? v.get("ground_level").getAsInt() : null;
                  JsonArray levels = v.has("levels") ? v.getAsJsonArray("levels") : null;
                  if (levels != null) {
                     for (JsonElement le : levels) {
                        JsonObject l = le.getAsJsonObject();
                        if (l.get("level").getAsInt() == upgradeLevel) {
                           if (l.has("ground_level")) {
                              return l.get("ground_level").getAsInt();
                           }
                           break;
                        }
                     }
                  }

                  return variantGroundLevel != null ? variantGroundLevel : fallback;
               }
            }
         } catch (Exception e) {
            LOGGER.warn("Failed to read ground_level from export JSON: {}", jsonPath, e);
         }

         return fallback;
      } else {
         return fallback;
      }
   }

   private static void placeTemplateWithFixes(ServerLevel level, ImportTableBlockEntity be, StructureTemplate template) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      Rotation rotation = Rotation.NONE;
      boolean processMockBlocks = !be.isImportMockBlocks();
      BuildingPlacer.placeFromTemplate(level, template, origin, rotation, processMockBlocks);
   }

   private static void clearPlotVolume(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      BlockPos tablePos = be.getBlockPos();
      Vec3i size = BuildingExporter.computeScanSize(be);
      int w = size.getX();
      int l = size.getZ();
      int h = size.getY();
      int clearH = h + 20;

      for (int x = 0; x < w; x++) {
         for (int z = 0; z < l; z++) {
            BlockPos foundationPos = origin.offset(x, -1, z);
            if (!foundationPos.equals(tablePos)) {
               level.setBlock(foundationPos, Blocks.STONE.defaultBlockState(), 2);
            }

            for (int y = 0; y < clearH; y++) {
               BlockPos pos = origin.offset(x, y, z);
               if (!pos.equals(tablePos)) {
                  level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
               }
            }
         }
      }
   }

   public static void placeConstructionBorder(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      Vec3i size = BuildingExporter.computeScanSize(be);
      int w = size.getX();
      int l = size.getZ();
      int orientation = be.getOrientation();
      BlockPos tablePos = be.getBlockPos();
      int groundY = tablePos.getY() - 1;
      int minX = origin.getX() - 1;
      int maxX = origin.getX() + w;
      int minZ = origin.getZ() - 1;
      int maxZ = origin.getZ() + l;
      BlockState neutralWool = be.isDirty() ? YELLOW_WOOL : WHITE_WOOL;
      BlockState northWool = orientation == 0 ? ORANGE_WOOL : neutralWool;

      for (int x = minX; x <= maxX; x++) {
         placeBorderBlock(level, new BlockPos(x, groundY, minZ), northWool, tablePos);
      }

      BlockState southWool = orientation == 2 ? ORANGE_WOOL : neutralWool;

      for (int x = minX; x <= maxX; x++) {
         placeBorderBlock(level, new BlockPos(x, groundY, maxZ), southWool, tablePos);
      }

      BlockState westWool = orientation == 3 ? ORANGE_WOOL : neutralWool;

      for (int z = minZ; z <= maxZ; z++) {
         placeBorderBlock(level, new BlockPos(minX, groundY, z), westWool, tablePos);
      }

      BlockState eastWool = orientation == 1 ? ORANGE_WOOL : neutralWool;

      for (int z = minZ; z <= maxZ; z++) {
         placeBorderBlock(level, new BlockPos(maxX, groundY, z), eastWool, tablePos);
      }

      int margin = 2;
      int clearMinX = minX - margin;
      int clearMaxX = maxX + margin;
      int clearMinZ = minZ - margin;
      int clearMaxZ = maxZ + margin;
      int clearHeight = size.getY() + 20;

      for (int x = clearMinX; x <= clearMaxX; x++) {
         for (int z = clearMinZ; z <= clearMaxZ; z++) {
            boolean insidePlot = x >= origin.getX() && x < origin.getX() + w && z >= origin.getZ() && z < origin.getZ() + l;
            if (!insidePlot) {
               for (int y = 1; y <= clearHeight; y++) {
                  BlockPos pos = new BlockPos(x, groundY + y, z);
                  if (!pos.equals(tablePos)) {
                     level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                  }
               }
            }
         }
      }
   }

   private static void placeBorderBlock(ServerLevel level, BlockPos pos, BlockState wool, BlockPos tablePos) {
      if (!pos.equals(tablePos)) {
         level.setBlock(pos, wool, 2);
      }
   }

   private static void clearBuildingGround(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = BuildingExporter.computeScanOrigin(be);
      int w = be.getWidth();
      int l = be.getLength();
      int h = be.getHeight();
      int surfaceY = be.getBlockPos().getY() - 1;
      int originY = origin.getY();

      for (int x = 0; x < w; x++) {
         for (int z = 0; z < l; z++) {
            int worldX = origin.getX() + x;
            int worldZ = origin.getZ() + z;

            for (int y = originY; y <= surfaceY; y++) {
               level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.DIRT.defaultBlockState(), 2);
            }

            for (int y = surfaceY + 1; y < originY + h; y++) {
               level.setBlock(new BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState(), 2);
            }
         }
      }
   }

   private static void importAllLevels(
      ServerLevel level, ImportTableBlockEntity mainBe, ServerPlayer player, int totalLevels, BuildingImporter.LevelImporter importer
   ) {
      mainBe.setUpgradeLevel(0);
      importer.importLevel(0, mainBe);
      int spacing = tableZExtent(mainBe) + 4;

      for (int lvl = 1; lvl < totalLevels; lvl++) {
         BlockPos childPos = mainBe.getBlockPos().offset(0, 0, spacing * lvl);
         level.setBlock(
            childPos,
            (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get())
               .defaultBlockState()
               .setValue(ImportTableBlock.FACING, (Direction)level.getBlockState(mainBe.getBlockPos()).getValue(ImportTableBlock.FACING)),
            2
         );
         ImportTableBlockEntity childBe = (ImportTableBlockEntity)level.getBlockEntity(childPos);
         if (childBe == null) {
            LOGGER.warn("Failed to create child ImportTable at {}", childPos);
         } else {
            mainBe.copySettingsTo(childBe);
            childBe.setUpgradeLevel(lvl);
            importer.importLevel(lvl, childBe);
         }
      }
   }

   private static int tableZExtent(ImportTableBlockEntity be) {
      return be.getLength();
   }

   private static String labelFor(String buildingId, String variant, int upgradeLevel) {
      return variant != null && !variant.isBlank() && !"a".equals(variant) ? buildingId + "_" + variant + "_" + upgradeLevel : buildingId + "_" + upgradeLevel;
   }

   private static void placeImportLabel(ServerLevel level, ImportTableBlockEntity be, String label) {
      BlockPos labelPos = be.getBlockPos().offset(0, 0, -1);
      BlockState state = (BlockState)Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8);
      level.setBlock(labelPos, state, 2);
      if (level.getBlockEntity(labelPos) instanceof SignBlockEntity sign) {
         SignText text = new SignText().setMessage(0, Component.literal(label)).setColor(DyeColor.BLACK);
         sign.setText(text, true);
         sign.setText(text, false);
         sign.setChanged();
         level.sendBlockUpdated(labelPos, state, state, 3);
      }
   }

   private static void loadDimensionsFromJson(
      Path exportDir, String buildingId, String variant, int upgradeLevel, ImportTableBlockEntity be, boolean isFreshLoad
   ) {
      Path jsonPath = exportDir.resolve(buildingId + ".json");
      if (Files.exists(jsonPath)) {
         try {
            String content = Files.readString(jsonPath);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            applyJsonOverrides(root, variant, upgradeLevel, be, isFreshLoad);
         } catch (Exception e) {
            LOGGER.warn("Failed to read export JSON: {}", jsonPath, e);
         }
      }
   }

   private static void applyJsonOverrides(JsonObject root, String variant, int upgradeLevel, ImportTableBlockEntity be, boolean isFreshLoad) {
      JsonArray variants = root.getAsJsonArray("variants");
      if (variants != null) {
         Integer planSetOrientation = root.has("building_orientation") ? root.get("building_orientation").getAsInt() : null;

         for (JsonElement ve : variants) {
            JsonObject v = ve.getAsJsonObject();
            if (variant.equals(v.get("variant").getAsString())) {
               Integer variantGroundLevel = v.has("ground_level") ? v.get("ground_level").getAsInt() : null;
               Integer resolvedOrientation = v.has("building_orientation") ? v.get("building_orientation").getAsInt() : planSetOrientation;
               if (resolvedOrientation != null && isFreshLoad) {
                  be.setOrientation(resolvedOrientation & 3);
               }

               JsonArray levels = v.getAsJsonArray("levels");
               if (levels != null) {
                  for (JsonElement le : levels) {
                     JsonObject l = le.getAsJsonObject();
                     if (l.get("level").getAsInt() == upgradeLevel) {
                        if (l.has("footprint")) {
                           JsonObject fp = l.getAsJsonObject("footprint");
                           be.setWidth(fp.get("width").getAsInt());
                           be.setHeight(fp.get("height").getAsInt());
                           be.setLength(fp.get("depth").getAsInt());
                        }

                        if (l.has("ground_level")) {
                           be.setStartingLevel(l.get("ground_level").getAsInt());
                        } else if (variantGroundLevel != null) {
                           be.setStartingLevel(variantGroundLevel);
                        } else {
                           be.setStartingLevel(0);
                        }

                        return;
                     }
                  }
               }
            }
         }
      }
   }

   record BaseLayer(String buildingId, String variant, int level) {
   }

   public record ImportAabb(int minX, int minZ, int maxX, int maxZ) {
      public int xSpan() {
         return this.maxX - this.minX + 1;
      }

      public int zSpan() {
         return this.maxZ - this.minZ + 1;
      }
   }

   @FunctionalInterface
   private interface LevelImporter {
      void importLevel(int var1, ImportTableBlockEntity var2);
   }

   record ResolvedPreviousLevel(StructureTemplate template, int groundLevel) {
   }

   public record SubRef(String subId, String parentVariant) {
   }
}
