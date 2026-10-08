package org.millenaire.building;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.mock.MockBlock;
import org.millenaire.content.BuiltInCultures;
import org.millenaire.content.CustomContentIndex;
import org.millenaire.content.Resource;
import org.millenaire.culture.BrickColourTheme;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class BuildingExporter {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String PRESERVE_GROUND_BLOCK = "millenaire:mock_marker[type=preserve_ground]";
   private static final Set<String> GROUND_BLOCKS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:sand");
   private static final String SNOW_BLOCK_PREFIX = "minecraft:snow";

   private BuildingExporter() {
   }

   public static void exportLevel(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (!rejectIfBeIdentifiersUnsafe(player, be)) {
         Path exportDir = getExportDirFor(level, be);
         ensureDir(exportDir);
         String buildingId = be.getBuildingId();
         String variant = be.getVariant();
         int upgradeLevel = be.getUpgradeLevel();
         BuildingExporter.CaptureResult capture = captureLevelCore(level, be, exportDir, player);
         if (capture.success()) {
            int copiedLevels = capture.copiedLevels();
            String nbtFileName = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
            refreshOverlay();
            maybePatchParentForCreatedSub(level, be, player);
            player.sendSystemMessage(
               Component.literal(
                  "§a[ImportTable] Exported "
                     + buildingId
                     + " "
                     + variant
                     + " level "
                     + upgradeLevel
                     + " to "
                     + nbtFileName
                     + " (height: "
                     + capture.detectedHeight()
                     + (copiedLevels > 0 ? ", copied " + copiedLevels + " linked upgrade(s)" : "")
                     + ")"
               )
            );
            be.captureSavedState(computeBlocksHash(level, be));
            be.setDirty(false);
            BuildingImporter.reimportDependentLevels(level, be, player);
         }
      }
   }

   public static void exportNewLevel(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (!rejectIfBeIdentifiersUnsafe(player, be)) {
         be.setUpgradeLevel(be.getUpgradeLevel() + 1);
         exportLevel(level, be, player);
      }
   }

   private static int cloneLevel(Path exportedRoot, Path exportDir, ImportTableBlockEntity be, ServerPlayer player) {
      String buildingId = be.getBuildingId();
      String variant = be.getVariant();
      int currentMax = be.getUpgradeLevel();
      String sourceFile = buildingId + "_" + variant + "_" + currentMax + ".nbt";
      int nextLevel = currentMax + 1;
      String destFile = buildingId + "_" + variant + "_" + nextLevel + ".nbt";
      Path sourcePath = exportDir.resolve(sourceFile);
      if (!Files.exists(sourcePath)) {
         player.sendSystemMessage(Component.literal("§c[ImportTable] Source NBT not found: " + sourceFile));
         return -1;
      }

      be.setUpgradeLevel(nextLevel);

      try (ExportTransaction tx = ExportTransaction.begin(exportedRoot, culturePathComponent(be.getCultureKey()), buildingId, exportDir)) {
         tx.stageCopy(destFile, sourcePath);
         JsonObject root = readOrCreateExportJson(exportDir, be);
         updateLevelEntry(root, be);
         tx.stageMarker(buildingId + ".json", GSON.toJson(root));
         tx.commit();
         return nextLevel;
      } catch (IOException | RuntimeException e) {
         be.setUpgradeLevel(currentMax);
         LOGGER.error("Failed to create new level {} in {}", new Object[]{destFile, exportDir, e});
         player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to create new level: " + e.getMessage()));
         return -1;
      }
   }

   public static void exportAdditionalLevels(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player, int numberOfUpgrades) {
      if (numberOfUpgrades > 0) {
         Path exportDir = getExportDirFor(level, be);
         ensureDir(exportDir);
         int created = 0;

         for (int i = 0; i < numberOfUpgrades; i++) {
            int nextLevel = cloneLevel(getExportedRoot(level), exportDir, be, player);
            if (nextLevel < 0) {
               break;
            }

            created++;
         }

         if (created > 0) {
            refreshOverlay();
            player.sendSystemMessage(
               Component.literal("§a[ImportTable] Created " + created + " upgrade level(s) for " + be.getBuildingId() + " " + be.getVariant())
            );
         }
      }
   }

   private static void postProcessNbt(CompoundTag nbt, ImportTableBlockEntity be) {
      if (nbt.contains("palette", 9)) {
         if (nbt.contains("blocks", 9)) {
            ListTag palette = nbt.getList("palette", 10);
            ListTag blocks = nbt.getList("blocks", 10);
            Map<Integer, String> paletteIndexToName = new HashMap<>();

            for (int i = 0; i < palette.size(); i++) {
               CompoundTag entry = palette.getCompound(i);
               paletteIndexToName.put(i, entry.getString("Name"));
            }

            if (!be.isExportSnow()) {
               removeSnowBlocks(blocks, paletteIndexToName);
            }

            if (be.isConvertToPreserveGround()) {
               convertToPreserveGround(palette, blocks, paletteIndexToName);
            }
         }
      }
   }

   private static boolean isSnowLayer(String name) {
      String baseName = name.contains("[") ? name.substring(0, name.indexOf(91)) : name;
      return "minecraft:snow".equals(baseName);
   }

   private static void removeSnowBlocks(ListTag blocks, Map<Integer, String> paletteIndexToName) {
      Set<Integer> snowIndices = new HashSet<>();

      for (Entry<Integer, String> entry : paletteIndexToName.entrySet()) {
         if (isSnowLayer(entry.getValue())) {
            snowIndices.add(entry.getKey());
         }
      }

      if (!snowIndices.isEmpty()) {
         for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag block = blocks.getCompound(i);
            int state = block.getInt("state");
            if (snowIndices.contains(state)) {
               blocks.remove(i);
            }
         }
      }
   }

   private static void convertToPreserveGround(ListTag palette, ListTag blocks, Map<Integer, String> paletteIndexToName) {
      Set<Integer> groundIndices = new HashSet<>();

      for (Entry<Integer, String> entry : paletteIndexToName.entrySet()) {
         String name = entry.getValue();
         String baseName = name.contains("[") ? name.substring(0, name.indexOf(91)) : name;
         if (GROUND_BLOCKS.contains(baseName)) {
            groundIndices.add(entry.getKey());
         }
      }

      if (!groundIndices.isEmpty()) {
         int preserveIndex = -1;

         for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if ("millenaire:mock_marker".equals(entry.getString("Name"))) {
               CompoundTag props = entry.getCompound("Properties");
               if ("preserve_ground".equals(props.getString("type"))) {
                  preserveIndex = i;
                  break;
               }
            }
         }

         if (preserveIndex == -1) {
            CompoundTag preserveEntry = new CompoundTag();
            preserveEntry.putString("Name", "millenaire:mock_marker");
            CompoundTag properties = new CompoundTag();
            properties.putString("type", "preserve_ground");
            preserveEntry.put("Properties", properties);
            palette.add(preserveEntry);
            preserveIndex = palette.size() - 1;
         }

         for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            int state = block.getInt("state");
            if (groundIndices.contains(state)) {
               ListTag pos = block.getList("pos", 3);
               if (pos.size() >= 3 && pos.getInt(1) == 0) {
                  block.putInt("state", preserveIndex);
               }
            }
         }
      }
   }

   static BuildingExporter.DiffOutcome classifyDiffPosition(@Nullable BlockState baseState, @Nullable BlockState capturedState) {
      boolean baseSolid = baseState != null && !baseState.isAir();
      boolean capturedSolid = capturedState != null && !capturedState.isAir();
      if (baseSolid) {
         if (!capturedSolid) {
            return BuildingExporter.DiffOutcome.DELETE;
         } else {
            return baseState.equals(capturedState) ? BuildingExporter.DiffOutcome.INHERIT : BuildingExporter.DiffOutcome.OVERRIDE;
         }
      } else {
         return capturedSolid ? BuildingExporter.DiffOutcome.OWN : BuildingExporter.DiffOutcome.NONE;
      }
   }

   static boolean isUnderground(BlockPos relPos, int groundLevel) {
      return relPos.getY() + groundLevel < 0;
   }

   static boolean isAlreadyExcavatedAtSpawn(BlockPos relPos, int currentGroundLevel, int levelZeroGroundLevel) {
      int deltaY = currentGroundLevel + relPos.getY();
      return deltaY > levelZeroGroundLevel;
   }

   private static void stripPreviousLevelBlocks(CompoundTag nbt, ImportTableBlockEntity be, ServerLevel level) {
      List<BuildingImporter.BaseLayer> baseLayers = BuildingImporter.computeExportBaseLayers(be);
      if (baseLayers.isEmpty() && BuildingImporter.exportParentDeclared(be)) {
         LOGGER.warn(
            "ImportTable: sub '{}/{}' level {} declared a parent but produced an empty diff base — §3.6 semantics lost, exporting a full raw copy (check its parent_building reference resolves)",
            new Object[]{be.getBuildingId(), be.getVariant(), be.getUpgradeLevel()}
         );
      } else {
         String cultureKey = be.getCultureKey();
         boolean importMockBlocks = be.isImportMockBlocks();
         StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
         int baseY = be.getBlockPos().getY();
         BlockPos scanOrigin = computeScanOrigin(be);
         int currentGroundLevel = be.getStartingLevel();
         Map<BlockPos, BlockState> consolidated = new HashMap<>();
         Integer ownLevelZeroGroundLevel = null;

         for (int i = 0; i < baseLayers.size(); i++) {
            BuildingImporter.BaseLayer layer = baseLayers.get(i);
            Path exportJsonPath = findExportedJson(level, layer.buildingId()).orElse(null);
            BuildingImporter.ResolvedPreviousLevel resolved = BuildingImporter.resolvePreviousLevel(
                  level, cultureKey, layer.buildingId(), layer.variant(), layer.level(), currentGroundLevel, exportJsonPath
               )
               .orElse(null);
            if (resolved != null) {
               if (layer.level() == 0 && layer.buildingId().equals(be.getBuildingId()) && layer.variant().equals(be.getVariant())) {
                  ownLevelZeroGroundLevel = resolved.groundLevel();
               }

               int yOffset = baseY + resolved.groundLevel() - scanOrigin.getY();
               boolean isUpgradeLayer = i > 0;
               extractBlocksFromTemplateRelative(resolved.template(), settings, yOffset, level, consolidated, isUpgradeLayer);
            }
         }

         if (consolidated.isEmpty() && BuildingImporter.exportParentDeclared(be)) {
            LOGGER.warn(
               "ImportTable: sub '{}/{}' level {} declared a parent but the consolidated base is empty — §3.6 semantics lost, exporting a full raw copy (parent templates missing?)",
               new Object[]{be.getBuildingId(), be.getVariant(), be.getUpgradeLevel()}
            );
         } else {
            if (!importMockBlocks) {
               Map<BlockPos, BlockState> converted = new HashMap<>();

               for (Entry<BlockPos, BlockState> entry : consolidated.entrySet()) {
                  BlockState state = entry.getValue();
                  if (state.getBlock() instanceof MockBlock mockBlock) {
                     BlockState replacement = mockBlock.getReplacementState(state);
                     converted.put(entry.getKey(), replacement != null ? replacement : Blocks.AIR.defaultBlockState());
                  } else {
                     converted.put(entry.getKey(), state);
                  }
               }

               consolidated = converted;
            }

            ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
            if (paletteTag != null) {
               ListTag blocks = nbt.getList("blocks", 10);
               List<BlockState> palette = new ArrayList<>();

               for (int i = 0; i < paletteTag.size(); i++) {
                  CompoundTag stateTag = paletteTag.getCompound(i);
                  BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
                  palette.add(state);
               }

               Map<BlockPos, Integer> exportedPositions = new HashMap<>();

               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  ListTag pos = block.getList("pos", 3);
                  BlockPos relPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                  exportedPositions.put(relPos, i);
               }

               Set<Integer> toRemove = new HashSet<>();

               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  ListTag pos = block.getList("pos", 3);
                  BlockPos relPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                  int stateIndex = block.getInt("state");
                  if (stateIndex >= 0 && stateIndex < palette.size()) {
                     BlockState exportedState = palette.get(stateIndex);
                     BlockState consolidatedState = consolidated.get(relPos);
                     BuildingExporter.DiffOutcome outcome = classifyDiffPosition(consolidatedState, exportedState);
                     if (outcome == BuildingExporter.DiffOutcome.INHERIT) {
                        toRemove.add(i);
                     } else if (outcome == BuildingExporter.DiffOutcome.NONE) {
                        boolean keepAsUndergroundDig;
                        if (!isUnderground(relPos, currentGroundLevel)) {
                           keepAsUndergroundDig = false;
                        } else if (baseLayers.isEmpty()) {
                           keepAsUndergroundDig = false;
                        } else if (ownLevelZeroGroundLevel != null) {
                           keepAsUndergroundDig = !isAlreadyExcavatedAtSpawn(relPos, currentGroundLevel, ownLevelZeroGroundLevel);
                        } else {
                           keepAsUndergroundDig = true;
                        }

                        if (!keepAsUndergroundDig) {
                           toRemove.add(i);
                        }
                     }
                  }
               }

               List<Integer> sortedRemove = new ArrayList<>(toRemove);
               sortedRemove.sort(Comparator.reverseOrder());

               for (int idx : sortedRemove) {
                  blocks.remove(idx);
               }

               int airIndex = -1;

               for (int i = 0; i < palette.size(); i++) {
                  if (palette.get(i).isAir()) {
                     airIndex = i;
                     break;
                  }
               }

               if (airIndex == -1) {
                  CompoundTag airEntry = new CompoundTag();
                  airEntry.putString("Name", "minecraft:air");
                  paletteTag.add(airEntry);
                  airIndex = paletteTag.size() - 1;
               }

               for (Entry<BlockPos, BlockState> entry : consolidated.entrySet()) {
                  BlockPos relPos = entry.getKey();
                  BlockState baseState = entry.getValue();
                  if (!baseState.isAir()) {
                     Integer existingIdx = exportedPositions.get(relPos);
                     if ((existingIdx == null || !toRemove.contains(existingIdx)) && existingIdx == null) {
                        CompoundTag airBlock = new CompoundTag();
                        ListTag posTag = new ListTag();
                        posTag.add(IntTag.valueOf(relPos.getX()));
                        posTag.add(IntTag.valueOf(relPos.getY()));
                        posTag.add(IntTag.valueOf(relPos.getZ()));
                        airBlock.put("pos", posTag);
                        airBlock.putInt("state", airIndex);
                        blocks.add(airBlock);
                     }
                  }
               }
            }
         }
      }
   }

   static void compactPalette(CompoundTag nbt) {
      if (nbt.contains("palette", 9)) {
         if (nbt.contains("blocks", 9)) {
            ListTag palette = nbt.getList("palette", 10);
            ListTag blocks = nbt.getList("blocks", 10);
            int[] refCount = new int[palette.size()];

            for (int i = 0; i < blocks.size(); i++) {
               CompoundTag block = blocks.getCompound(i);
               int state = block.getInt("state");
               if (state >= 0 && state < refCount.length) {
                  refCount[state]++;
               }
            }

            int[] remap = new int[palette.size()];
            ListTag newPalette = new ListTag();

            for (int i = 0; i < palette.size(); i++) {
               if (refCount[i] > 0) {
                  remap[i] = newPalette.size();
                  newPalette.add(palette.getCompound(i));
               } else {
                  remap[i] = -1;
               }
            }

            if (newPalette.size() != palette.size()) {
               for (int i = 0; i < blocks.size(); i++) {
                  CompoundTag block = blocks.getCompound(i);
                  int oldState = block.getInt("state");
                  if (oldState >= 0 && oldState < remap.length && remap[oldState] >= 0) {
                     block.putInt("state", remap[oldState]);
                  }
               }

               nbt.put("palette", newPalette);
            }
         }
      }
   }

   private static JsonObject readOrCreateExportJson(Path exportDir, ImportTableBlockEntity be) {
      Path jsonPath = exportDir.resolve(be.getBuildingId() + ".json");
      if (Files.exists(jsonPath)) {
         JsonObject existing = readJsonFile(jsonPath);
         if (existing != null) {
            return existing;
         }

         LOGGER.warn("Unreadable export JSON, rebuilding from scratch: {}", jsonPath);
      }

      return createNewBuildingJson(be);
   }

   private static int stageMissingPlanLevels(
      ServerLevel level, ImportTableBlockEntity be, ExportTransaction tx, @Nullable ImportBuildingResolver.ResolvedBuilding resolved
   ) {
      if (resolved == null) {
         return 0;
      }

      String buildingId = be.getBuildingId();
      String currentVariant = be.getVariant();
      int currentLevel = be.getUpgradeLevel();
      int copied = 0;

      for (Entry<String, List<Integer>> entry : ImportBuildingResolver.variantLevels(resolved.unionPlan()).entrySet()) {
         String variant = entry.getKey();

         for (int lvl : entry.getValue()) {
            if (!variant.equals(currentVariant) || lvl != currentLevel) {
               String nbtFileName = buildingId + "_" + variant + "_" + lvl + ".nbt";
               if (!tx.willExist(nbtFileName)) {
                  seedInto(tx, level, resolved, variant, lvl, nbtFileName);
                  copied++;
               }
            }
         }
      }

      return copied;
   }

   static BuildingExporter.SubRefPatchResult patchParentJsonWithSubRef(JsonObject parentRoot, String parentVariant, int triggerLevel, String subId) {
      if (triggerLevel <= 0) {
         JsonArray arr = parentRoot.has("starting_sub_buildings") ? parentRoot.getAsJsonArray("starting_sub_buildings") : null;
         if (arr == null) {
            arr = new JsonArray();
            parentRoot.add("starting_sub_buildings", arr);
         }

         if (jsonArrayContainsString(arr, subId)) {
            return BuildingExporter.SubRefPatchResult.ALREADY_PRESENT;
         }

         arr.add(subId);
         return BuildingExporter.SubRefPatchResult.ADDED;
      } else {
         JsonArray variants = parentRoot.has("variants") ? parentRoot.getAsJsonArray("variants") : null;
         if (variants == null) {
            return BuildingExporter.SubRefPatchResult.LEVEL_NOT_FOUND;
         }

         for (JsonElement ve : variants) {
            JsonObject v = ve.getAsJsonObject();
            if (v.has("variant") && parentVariant.equals(v.get("variant").getAsString())) {
               JsonArray levels = v.has("levels") ? v.getAsJsonArray("levels") : null;
               if (levels == null) {
                  return BuildingExporter.SubRefPatchResult.LEVEL_NOT_FOUND;
               }

               for (JsonElement le : levels) {
                  JsonObject l = le.getAsJsonObject();
                  if (l.has("level") && l.get("level").getAsInt() == triggerLevel) {
                     JsonArray subs = l.has("sub_buildings") ? l.getAsJsonArray("sub_buildings") : null;
                     if (subs == null) {
                        subs = new JsonArray();
                        l.add("sub_buildings", subs);
                     }

                     if (jsonArrayContainsString(subs, subId)) {
                        return BuildingExporter.SubRefPatchResult.ALREADY_PRESENT;
                     }

                     subs.add(subId);
                     return BuildingExporter.SubRefPatchResult.ADDED;
                  }
               }

               return BuildingExporter.SubRefPatchResult.LEVEL_NOT_FOUND;
            }
         }

         return BuildingExporter.SubRefPatchResult.LEVEL_NOT_FOUND;
      }
   }

   public static BuildingExporter.SubRefPatchResult addSubBuildingRef(
      ServerLevel level, String parentCulture, String parentId, String parentVariant, int triggerLevel, String subId
   ) {
      if (!ImportTableIdentifierValidator.isValidIdentifier(parentId)) {
         LOGGER.warn("ImportTable: refusing to patch parent '{}' — path-unsafe id", parentId);
         return BuildingExporter.SubRefPatchResult.PARENT_NOT_FOUND;
      }

      Path gameDir = level.getServer().getServerDirectory();
      if (LegacyExportSinkMigrator.migrate(gameDir, parentId) == LegacyExportSinkMigrator.Result.MIGRATED) {
         refreshOverlay();
      }

      ImportBuildingResolver.ResolvedBuilding parent = ImportBuildingResolver.resolveBuilding(parentCulture, parentId).orElse(null);
      if (parent == null) {
         LOGGER.warn("ImportTable: cannot patch parent '{}' for sub '{}' — parent JSON not found", parentId, subId);
         return BuildingExporter.SubRefPatchResult.PARENT_NOT_FOUND;
      }

      JsonObject parentRoot = parent.effectiveJson().deepCopy();
      BuildingExporter.SubRefPatchResult result = patchParentJsonWithSubRef(parentRoot, parentVariant, triggerLevel, subId);
      if (result != BuildingExporter.SubRefPatchResult.ADDED) {
         return result;
      }

      String culturePath = culturePathComponent(parentCulture);
      Path exportDir = findExportedDir(gameDir, culturePath, parentId).orElseGet(() -> computeExportDir(gameDir, parentCulture, parent.category()));

      try (ExportTransaction tx = ExportTransaction.begin(getExportedRoot(level), culturePath, parentId, exportDir)) {
         stageCompletePlanSet(level, tx, parent);
         applyPlanToJson(parentRoot, parentId, "", Integer.MIN_VALUE, parent.unionPlan(), tx::willExist);
         tx.stageMarker(parentId + ".json", GSON.toJson(parentRoot));
         tx.commit();
      } catch (IOException | RuntimeException e) {
         LOGGER.error("Failed to publish patched parent '{}' into {}", new Object[]{parentId, exportDir, e});
         return BuildingExporter.SubRefPatchResult.PARENT_NOT_FOUND;
      }

      refreshOverlay();
      return BuildingExporter.SubRefPatchResult.ADDED;
   }

   static int stageCompletePlanSet(ServerLevel level, ExportTransaction tx, ImportBuildingResolver.ResolvedBuilding resolved) {
      int staged = 0;

      for (Entry<String, List<Integer>> entry : ImportBuildingResolver.variantLevels(resolved.unionPlan()).entrySet()) {
         for (int lvl : entry.getValue()) {
            String fileName = resolved.buildingId() + "_" + entry.getKey() + "_" + lvl + ".nbt";
            if (!tx.willExist(fileName)) {
               seedInto(tx, level, resolved, entry.getKey(), lvl, fileName);
               staged++;
            }
         }
      }

      return staged;
   }

   private static void seedInto(
      ExportTransaction tx, ServerLevel level, ImportBuildingResolver.ResolvedBuilding resolved, String variant, int lvl, String fileName
   ) {
      Resource source = resolved.seedNbt(variant, lvl).orElse(null);
      if (source == null) {
         throw new BuildingExporter.IncompletePlanSetException("no stratum carries " + fileName + ", which the plan set declares", null);
      }

      try {
         StructureTemplate template = TemplateLoader.loadStructureFromResource(source, level);
         tx.stageNbt(fileName, template.save(new CompoundTag()));
      } catch (IOException | RuntimeException e) {
         throw new BuildingExporter.IncompletePlanSetException("could not stage " + fileName, e);
      }
   }

   private static boolean jsonArrayContainsString(JsonArray arr, String value) {
      for (JsonElement e : arr) {
         if (e.isJsonPrimitive() && value.equals(e.getAsString())) {
            return true;
         }
      }

      return false;
   }

   private static void maybePatchParentForCreatedSub(ServerLevel level, ImportTableBlockEntity be, ServerPlayer player) {
      if (be.hasParentContext()) {
         String parentCulture = be.getCultureKey();
         String parentId = be.getParentBuildingId();
         String parentVariant = be.getParentVariant();
         String subId = be.getBuildingId();
         if (parentCulture != null && !parentCulture.isEmpty() && parentId != null && !parentId.isEmpty()) {
            if (!BuildingImporter.resolveTriggerLevel(level, parentCulture, parentId, parentVariant, subId).isPresent()) {
               int triggerLevel = be.getParentTriggerLevel();
               BuildingExporter.SubRefPatchResult result = addSubBuildingRef(level, parentCulture, parentId, parentVariant, triggerLevel, subId);
               switch (result) {
                  case ADDED:
                     player.sendSystemMessage(
                        Component.literal(
                           "§a[ImportTable] Wired sub '"
                              + subId
                              + "' into parent "
                              + parentId
                              + (triggerLevel <= 0 ? "" : "/" + parentVariant)
                              + " at trigger level "
                              + triggerLevel
                        )
                     );
                  case ALREADY_PRESENT:
                  default:
                     break;
                  case LEVEL_NOT_FOUND:
                     player.sendSystemMessage(
                        Component.literal(
                           "§c[ImportTable] Could not wire sub '" + subId + "' — parent " + parentId + "/" + parentVariant + " has no level " + triggerLevel
                        )
                     );
                     break;
                  case PARENT_NOT_FOUND:
                     player.sendSystemMessage(
                        Component.literal("§c[ImportTable] Could not wire sub '" + subId + "' — parent '" + parentId + "' JSON not found")
                     );
               }
            }
         }
      }
   }

   static void applyPlanToJson(JsonObject root, String buildingId, String currentVariant, int currentLevel, JsonObject plan, Predicate<String> nbtWillExist) {
      JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : new JsonArray();
      root.add("variants", variants);

      for (JsonElement ve : plan.has("variants") && plan.get("variants").isJsonArray() ? plan.getAsJsonArray("variants") : new JsonArray()) {
         if (ve.isJsonObject()) {
            JsonObject planVariant = ve.getAsJsonObject();
            if (planVariant.has("variant")) {
               String variantKey = planVariant.get("variant").getAsString();
               JsonObject variantObj = findVariant(variants, variantKey);
               if (variantObj == null) {
                  variantObj = planVariant.deepCopy();
                  variantObj.add("levels", new JsonArray());
                  variants.add(variantObj);
               } else {
                  fillMissingFields(variantObj, planVariant, "variant", "levels");
               }

               JsonArray levelArray = variantObj.getAsJsonArray("levels");

               for (JsonElement le : planVariant.has("levels") && planVariant.get("levels").isJsonArray()
                  ? planVariant.getAsJsonArray("levels")
                  : new JsonArray()) {
                  if (le.isJsonObject()) {
                     JsonObject planLevel = le.getAsJsonObject();
                     if (planLevel.has("level")) {
                        int lvl = planLevel.get("level").getAsInt();
                        if (nbtWillExist.test(buildingId + "_" + variantKey + "_" + lvl + ".nbt")) {
                           boolean isCurrent = variantKey.equals(currentVariant) && lvl == currentLevel;
                           JsonObject levelObj = findLevel(levelArray, lvl);
                           if (levelObj == null) {
                              levelObj = isCurrent ? new JsonObject() : planLevel.deepCopy();
                              levelObj.addProperty("level", lvl);
                              levelArray.add(levelObj);
                           } else if (!isCurrent) {
                              fillMissingFields(levelObj, planLevel, "level");
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void fillMissingFields(JsonObject target, JsonObject source, String... skip) {
      List<String> skipped = List.of(skip);

      for (Entry<String, JsonElement> e : source.entrySet()) {
         if (!skipped.contains(e.getKey()) && !target.has(e.getKey())) {
            target.add(e.getKey(), e.getValue().deepCopy());
         }
      }
   }

   private static BuildingPlanSet getPlanSet(String cultureKey, String buildingId) {
      if (cultureKey != null && !cultureKey.isEmpty() && buildingId != null && !buildingId.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         return planSetId != null ? ModCultures.getBuildingPlanSet(planSetId) : null;
      } else {
         return null;
      }
   }

   @Nullable
   private static JsonObject findVariant(JsonArray variants, String variant) {
      for (JsonElement elem : variants) {
         JsonObject obj = elem.getAsJsonObject();
         if (obj.has("variant") && variant.equals(obj.get("variant").getAsString())) {
            return obj;
         }
      }

      return null;
   }

   @Nullable
   private static JsonObject findLevel(JsonArray levels, int level) {
      for (JsonElement elem : levels) {
         JsonObject obj = elem.getAsJsonObject();
         if (obj.has("level") && obj.get("level").getAsInt() == level) {
            return obj;
         }
      }

      return null;
   }

   private static JsonObject createNewBuildingJson(ImportTableBlockEntity be) {
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      if (!cultureKey.isEmpty() && !buildingId.isEmpty()) {
         ResourceLocation planSetId = ResourceLocation.tryParse(cultureKey + "/" + buildingId);
         if (planSetId != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
            if (planSet != null) {
               return serializeFromExistingPlanSet(planSet);
            }
         }
      }

      JsonObject root = new JsonObject();
      root.addProperty("culture", cultureKey.isEmpty() ? "millenaire:custom" : cultureKey);
      root.addProperty("building_id", buildingId);
      boolean createdSub = be.hasParentContext() && !be.getParentBuildingId().isEmpty();
      root.addProperty("category", createdSub ? categoryFor(cultureKey, be.getParentBuildingId()) : "extra");
      root.addProperty("native_name", buildingId);
      root.addProperty("converter_skip", true);
      if (createdSub) {
         root.addProperty("is_sub_building", true);
         root.addProperty("max_count", 0);
         addParentBuildingObject(root, be.getParentBuildingId(), be.getParentVariant(), be.getParentTriggerLevel());
      }

      root.add("variants", new JsonArray());
      return root;
   }

   private static void addParentBuildingObject(JsonObject root, String parentId, String parentVariant, int parentLevel) {
      if (parentId != null && !parentId.isEmpty() && parentVariant != null && !parentVariant.isEmpty()) {
         JsonObject parent = new JsonObject();
         parent.addProperty("id", parentId);
         parent.addProperty("variant", parentVariant);
         parent.addProperty("level", parentLevel);
         root.add("parent_building", parent);
      }
   }

   static JsonObject serializeFromExistingPlanSet(BuildingPlanSet planSet) {
      JsonObject root = new JsonObject();
      root.addProperty("culture", planSet.culture().toString());
      root.addProperty("building_id", planSet.buildingId());
      if (!"houses".equals(planSet.category())) {
         root.addProperty("category", planSet.category());
      }

      root.addProperty("native_name", planSet.nativeName());
      root.addProperty("converter_skip", true);
      if (planSet.maxCount() != 1) {
         root.addProperty("max_count", planSet.maxCount());
      }

      if (planSet.isSubBuilding()) {
         root.addProperty("is_sub_building", true);
      }

      if (planSet.isTownHall()) {
         root.addProperty("is_town_hall", true);
      }

      if (planSet.isWallSegment()) {
         root.addProperty("is_wall_segment", true);
      }

      if (planSet.isBorderBuilding()) {
         root.addProperty("is_border_building", true);
      }

      if (planSet.isGift()) {
         root.addProperty("is_gift", true);
      }

      if (planSet.extraWallConstructionSlots() != 0) {
         root.addProperty("extra_wall_construction_slots", planSet.extraWallConstructionSlots());
      }

      if (planSet.fixedOrientation() != null) {
         root.addProperty("fixed_orientation", cardinalName(planSet.fixedOrientation()));
      }

      addTagDistanceArray(root, "far_from_tags", planSet.farFromTags());
      addTagDistanceArray(root, "close_to_tags", planSet.closeToTags());
      if (planSet.parentBuilding() != null) {
         addParentBuildingObject(root, planSet.parentBuilding().id(), planSet.parentBuilding().variant(), planSet.parentBuilding().level());
      }

      if (planSet.minDistance() != 0.0) {
         root.addProperty("min_distance", planSet.minDistance());
      }

      if (planSet.maxDistance() != 1.0) {
         root.addProperty("max_distance", planSet.maxDistance());
      }

      if (!planSet.maleResidents().isEmpty()) {
         root.add("male", toStringArray(planSet.maleResidents()));
      }

      if (!planSet.femaleResidents().isEmpty()) {
         root.add("female", toStringArray(planSet.femaleResidents()));
      }

      if (!planSet.visitors().isEmpty()) {
         root.add("visitors", toStringArray(planSet.visitors()));
      }

      root.addProperty("priority_move_in", planSet.priorityMoveIn());
      if (!planSet.tags().isEmpty()) {
         root.add("tags", toStringArray(planSet.tags()));
      }

      if (!"clear_and_flatten".equals(planSet.terrainPolicy())) {
         root.addProperty("terrain_policy", planSet.terrainPolicy());
      }

      if (!"bottom_up".equals(planSet.constructionOrder())) {
         root.addProperty("construction_order", planSet.constructionOrder());
      }

      if (planSet.icon() != null) {
         root.addProperty("icon", planSet.icon());
      }

      ClearMargins margins = planSet.clearMargins();
      if (margins.lengthBefore() != 5 || margins.lengthAfter() != 5 || margins.widthBefore() != 5 || margins.widthAfter() != 5) {
         root.addProperty("area_to_clear_length_before", margins.lengthBefore());
         root.addProperty("area_to_clear_length_after", margins.lengthAfter());
         root.addProperty("area_to_clear_width_before", margins.widthBefore());
         root.addProperty("area_to_clear_width_after", margins.widthAfter());
      }

      if (planSet.price() > 0) {
         root.addProperty("price", planSet.price());
      }

      if (planSet.reputation() > 0) {
         root.addProperty("reputation", planSet.reputation());
      }

      BuildingPlan firstPlan = findFirstPlan(planSet);
      if (firstPlan != null && firstPlan.shopId() != null) {
         root.addProperty("shop", firstPlan.shopId());
      }

      Map<String, Integer> variantOrientations = collectVariantOrientations(planSet);
      if (!planSet.startingSubBuildings().isEmpty()) {
         root.add("starting_sub_buildings", toStringArray(planSet.startingSubBuildings()));
      }

      if (!planSet.startingGoods().isEmpty()) {
         JsonArray sgArray = new JsonArray();

         for (BuildingPlanSet.StartingGood sg : planSet.startingGoods()) {
            JsonObject sgObj = new JsonObject();
            sgObj.addProperty("item", sg.item());
            if (sg.probability() != 1.0) {
               sgObj.addProperty("probability", sg.probability());
            }

            if (sg.fixedNumber() != 0) {
               sgObj.addProperty("fixed", sg.fixedNumber());
            }

            if (sg.randomNumber() != 0) {
               sgObj.addProperty("random", sg.randomNumber());
            }

            sgArray.add(sgObj);
         }

         root.add("starting_goods", sgArray);
      }

      if (!planSet.randomBrickColours().isEmpty()) {
         JsonObject rbcObj = new JsonObject();

         for (Entry<DyeColor, List<BrickColourTheme.WeightedColor>> entry : planSet.randomBrickColours().entrySet()) {
            JsonArray colors = new JsonArray();

            for (BrickColourTheme.WeightedColor wc : entry.getValue()) {
               JsonObject wcObj = new JsonObject();
               wcObj.addProperty("color", wc.color().getName());
               wcObj.addProperty("weight", wc.weight());
               colors.add(wcObj);
            }

            rbcObj.add(entry.getKey().getName(), colors);
         }

         root.add("random_brick_colours", rbcObj);
      }

      if (planSet.travelBookCategory() != null) {
         root.addProperty("travel_book_category", planSet.travelBookCategory());
      }

      if (!planSet.travelBookDisplay()) {
         root.addProperty("travel_book_display", false);
      }

      JsonArray variantsArray = new JsonArray();

      for (Entry<String, List<BuildingPlanSet.LevelDef>> variantEntry : planSet.variants().entrySet()) {
         JsonObject variantObj = new JsonObject();
         variantObj.addProperty("variant", variantEntry.getKey());
         Integer variantOrientation = variantOrientations.get(variantEntry.getKey());
         variantObj.addProperty("building_orientation", variantOrientation != null ? variantOrientation : 1);
         List<BuildingPlanSet.LevelDef> levelDefs = variantEntry.getValue();
         boolean groundLevelConstant = true;
         boolean tagsConstant = true;
         int firstGround = levelDefs.isEmpty() ? 0 : levelDefs.get(0).groundLevel();
         List<String> firstTags = planTagsFor(levelDefs.isEmpty() ? null : levelDefs.get(0));

         for (int i = 1; i < levelDefs.size(); i++) {
            if (levelDefs.get(i).groundLevel() != firstGround) {
               groundLevelConstant = false;
            }

            if (!planTagsFor(levelDefs.get(i)).equals(firstTags)) {
               tagsConstant = false;
            }
         }

         if (groundLevelConstant && !levelDefs.isEmpty() && firstGround != 0) {
            variantObj.addProperty("ground_level", firstGround);
         }

         if (tagsConstant && !firstTags.isEmpty()) {
            variantObj.add("tags", toStringArray(firstTags));
         }

         JsonArray levelsArray = new JsonArray();

         for (BuildingPlanSet.LevelDef ld : levelDefs) {
            levelsArray.add(serializeLevelDef(ld, groundLevelConstant, tagsConstant));
         }

         variantObj.add("levels", levelsArray);
         variantsArray.add(variantObj);
      }

      root.add("variants", variantsArray);
      return root;
   }

   private static List<String> planTagsFor(@Nullable BuildingPlanSet.LevelDef ld) {
      if (ld == null) {
         return List.of();
      }

      BuildingPlan plan = ModCultures.getBuildingPlan(ld.planId());
      return plan != null ? plan.tags() : List.of();
   }

   private static JsonObject serializeLevelDef(BuildingPlanSet.LevelDef ld, boolean groundLevelConstant, boolean tagsConstant) {
      JsonObject levelObj = new JsonObject();
      levelObj.addProperty("level", ld.level());
      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", ld.width());
      footprint.addProperty("height", ld.height());
      footprint.addProperty("depth", ld.depth());
      levelObj.add("footprint", footprint);
      if (!groundLevelConstant) {
         levelObj.addProperty("ground_level", ld.groundLevel());
      }

      if (!tagsConstant) {
         List<String> planTags = planTagsFor(ld);
         if (!planTags.isEmpty()) {
            levelObj.add("tags", toStringArray(planTags));
         }
      }

      if (ld.priority() != 100) {
         levelObj.addProperty("priority", ld.priority());
      }

      if (ld.nativeName() != null) {
         levelObj.addProperty("native_name", ld.nativeName());
      }

      if (!ld.requiredTags().isEmpty()) {
         levelObj.add("required_tags", toStringArray(ld.requiredTags()));
      }

      if (!ld.forbiddenTagsInVillage().isEmpty()) {
         levelObj.add("forbidden_tags_in_village", toStringArray(ld.forbiddenTagsInVillage()));
      }

      if (!ld.requiredVillageTags().isEmpty()) {
         levelObj.add("required_village_tags", toStringArray(ld.requiredVillageTags()));
      }

      if (!ld.parentTags().isEmpty()) {
         levelObj.add("parent_tags", toStringArray(ld.parentTags()));
      }

      if (!ld.requiredParentTags().isEmpty()) {
         levelObj.add("required_parent_tags", toStringArray(ld.requiredParentTags()));
      }

      if (!ld.clearTags().isEmpty()) {
         levelObj.add("clear_tags", toStringArray(ld.clearTags()));
      }

      if (!ld.villageTags().isEmpty()) {
         levelObj.add("village_tags", toStringArray(ld.villageTags()));
      }

      if (!ld.subBuildings().isEmpty()) {
         levelObj.add("sub_buildings", toStringArray(ld.subBuildings()));
      }

      if (ld.signOrder() != null) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < ld.signOrder().length; i++) {
            if (i > 0) {
               sb.append(",");
            }

            sb.append(ld.signOrder()[i]);
         }

         levelObj.addProperty("signs", sb.toString());
      }

      if (ld.pathLevel() != 0) {
         levelObj.addProperty("path_level", ld.pathLevel());
      }

      if (ld.rebuildPath()) {
         levelObj.addProperty("rebuild_path", true);
      }

      if (ld.pathWidth() != 2) {
         levelObj.addProperty("path_width", ld.pathWidth());
      }

      if (ld.noPathsToBuilding()) {
         levelObj.addProperty("no_paths_to_building", true);
      }

      if (ld.maleResidents() != null) {
         levelObj.add("male", toStringArray(ld.maleResidents()));
      }

      if (ld.femaleResidents() != null) {
         levelObj.add("female", toStringArray(ld.femaleResidents()));
      }

      if (ld.irrigation() != 0) {
         levelObj.addProperty("irrigation", ld.irrigation());
      }

      if (!ld.abstractedProduction().isEmpty()) {
         JsonArray apArray = new JsonArray();

         for (Entry<String, Integer> apEntry : ld.abstractedProduction().entrySet()) {
            apArray.add(apEntry.getKey() + "," + apEntry.getValue());
         }

         levelObj.add("abstracted_production", apArray);
      }

      return levelObj;
   }

   @Nullable
   private static BuildingPlan findFirstPlan(BuildingPlanSet planSet) {
      for (List<BuildingPlanSet.LevelDef> levels : planSet.variants().values()) {
         if (!levels.isEmpty()) {
            return ModCultures.getBuildingPlan(levels.get(0).planId());
         }
      }

      return null;
   }

   static Map<String, Integer> collectVariantOrientations(BuildingPlanSet planSet) {
      Map<String, Integer> result = new LinkedHashMap<>();

      for (Entry<String, List<BuildingPlanSet.LevelDef>> entry : planSet.variants().entrySet()) {
         List<BuildingPlanSet.LevelDef> levels = entry.getValue();
         if (levels != null && !levels.isEmpty()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(levels.get(0).planId());
            if (plan != null) {
               result.put(entry.getKey(), plan.buildingOrientation());
            }
         }
      }

      return result;
   }

   private static void addTagDistanceArray(JsonObject root, String field, Map<String, Integer> tags) {
      if (tags != null && !tags.isEmpty()) {
         JsonArray array = new JsonArray();

         for (Entry<String, Integer> entry : tags.entrySet()) {
            array.add(entry.getKey() + "," + entry.getValue());
         }

         root.add(field, array);
      }
   }

   private static String cardinalName(int ordinal) {
      return switch (ordinal & 3) {
         case 1 -> "east";
         case 2 -> "south";
         case 3 -> "west";
         default -> "north";
      };
   }

   private static JsonArray toStringArray(List<String> list) {
      JsonArray array = new JsonArray();

      for (String s : list) {
         array.add(s);
      }

      return array;
   }

   static void updateLevelEntry(JsonObject root, ImportTableBlockEntity be) {
      JsonArray variants = root.has("variants") ? root.getAsJsonArray("variants") : new JsonArray();
      JsonObject variantObj = null;

      for (JsonElement elem : variants) {
         JsonObject v = elem.getAsJsonObject();
         if (be.getVariant().equals(v.get("variant").getAsString())) {
            variantObj = v;
            break;
         }
      }

      if (variantObj == null) {
         variantObj = new JsonObject();
         variantObj.addProperty("variant", be.getVariant());
         variantObj.add("levels", new JsonArray());
         variants.add(variantObj);
      }

      JsonArray levels = variantObj.has("levels") ? variantObj.getAsJsonArray("levels") : new JsonArray();
      JsonObject levelObj = null;

      for (JsonElement elem : levels) {
         JsonObject l = elem.getAsJsonObject();
         if (l.get("level").getAsInt() == be.getUpgradeLevel()) {
            levelObj = l;
            break;
         }
      }

      if (levelObj == null) {
         levelObj = new JsonObject();
         levelObj.addProperty("level", be.getUpgradeLevel());
         levels.add(levelObj);
      }

      JsonObject footprint = new JsonObject();
      footprint.addProperty("width", be.getWidth());
      footprint.addProperty("height", be.getHeight());
      footprint.addProperty("depth", be.getLength());
      levelObj.add("footprint", footprint);
      int levelGroundLevel = be.getStartingLevel();
      int variantGroundLevel = variantObj.has("ground_level") ? variantObj.get("ground_level").getAsInt() : 0;
      if (levelGroundLevel != variantGroundLevel) {
         levelObj.addProperty("ground_level", levelGroundLevel);
      } else {
         levelObj.remove("ground_level");
      }

      applyVariantOrientation(variantObj, be.getOrientation());
      variantObj.add("levels", levels);
      root.add("variants", variants);
   }

   static void applyVariantOrientation(JsonObject variantObj, int tableOrientation) {
      variantObj.addProperty("building_orientation", tableOrientation);
   }

   public static BlockPos computeScanOrigin(ImportTableBlockEntity be) {
      BlockPos tablePos = be.getBlockPos();
      int startingLevel = be.getStartingLevel();
      return tablePos.offset(1, startingLevel, 1);
   }

   static int detectBuildingHeight(ServerLevel level, BlockPos origin, ImportTableBlockEntity be, int upgradeLevel) {
      int scanX = be.getWidth();
      int scanZ = be.getLength();
      int minHeight = be.getHeight();
      int maxY = level.getMaxBuildHeight();
      int maxPossibleHeight = maxY - origin.getY();
      int height = 0;

      for (int dy = 0; dy < maxPossibleHeight; dy++) {
         boolean blockFound = false;

         for (int dx = 0; dx < scanX && !blockFound; dx++) {
            for (int dz = 0; dz < scanZ && !blockFound; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (!level.getBlockState(pos).isAir()) {
                  blockFound = true;
               }
            }
         }

         if (blockFound) {
            height = dy + 1;
         } else if (dy >= minHeight) {
            break;
         }
      }

      return Math.max(height, 1);
   }

   public static Vec3i computeScanSize(ImportTableBlockEntity be) {
      return new Vec3i(be.getWidth(), be.getHeight(), be.getLength());
   }

   public static long computeBlocksHash(ServerLevel level, ImportTableBlockEntity be) {
      BlockPos origin = computeScanOrigin(be);
      Vec3i size = computeScanSize(be);
      long hash = 1469598103934665603L;
      MutableBlockPos cursor = new MutableBlockPos();
      int sx = size.getX();
      int sy = size.getY();
      int sz = size.getZ();

      for (int dx = 0; dx < sx; dx++) {
         for (int dy = 0; dy < sy; dy++) {
            for (int dz = 0; dz < sz; dz++) {
               cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
               BlockState state = level.getBlockState(cursor);
               BlockState hashState;
               if (state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.LAVA) {
                  hashState = state;
               } else {
                  hashState = state.getFluidState().isSource() ? state.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState();
               }

               hash ^= Block.getId(hashState);
               hash *= 1099511628211L;
               BlockEntity blockEntity = level.getBlockEntity(cursor);
               if (blockEntity != null) {
                  try {
                     CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
                     hash ^= tag.hashCode();
                     hash *= 1099511628211L;
                  } catch (Exception var17) {
                  }
               }
            }
         }
      }

      return hash;
   }

   public static Rotation orientationToRotation(int orientation) {
      return switch (orientation) {
         case 1 -> Rotation.CLOCKWISE_90;
         case 2 -> Rotation.CLOCKWISE_180;
         case 3 -> Rotation.COUNTERCLOCKWISE_90;
         default -> Rotation.NONE;
      };
   }

   public static Path getExportDir(ServerLevel level, @Nullable String cultureKey, @Nullable String category) {
      return computeExportDir(level.getServer().getServerDirectory(), cultureKey, category);
   }

   static Path computeExportDir(Path gameDir, @Nullable String cultureKey, @Nullable String category) {
      String culturePath = culturePathComponent(cultureKey);
      String categoryDir = category != null && !category.isEmpty() ? category : "lone";
      return gameDir.resolve("millenaire-custom").resolve("exported").resolve("cultures").resolve(culturePath).resolve("buildings").resolve(categoryDir);
   }

   public static Path getLegacyExportDir(ServerLevel level) {
      return getLegacyExportDir(level.getServer().getServerDirectory());
   }

   public static Path getLegacyExportDir(Path gameDir) {
      return gameDir.resolve("millenaire-custom").resolve("exports");
   }

   public static Path getExportedRoot(ServerLevel level) {
      return getExportedRoot(level.getServer().getServerDirectory());
   }

   public static Path getExportedRoot(Path gameDir) {
      return gameDir.resolve("millenaire-custom").resolve("exported");
   }

   @Deprecated
   public static Path getExportDir(ServerLevel level) {
      return getLegacyExportDir(level);
   }

   public static String culturePathComponent(@Nullable String cultureKey) {
      if (cultureKey == null || cultureKey.isEmpty()) {
         return "custom";
      }

      if (!ImportTableIdentifierValidator.isValidCultureKey(cultureKey)) {
         return "custom";
      }

      ResourceLocation rl = ResourceLocation.tryParse(cultureKey);
      return rl != null && !rl.getPath().isEmpty() ? rl.getPath() : cultureKey;
   }

   static boolean rejectIfBeIdentifiersUnsafe(ServerPlayer player, ImportTableBlockEntity be) {
      if (ImportTableIdentifierValidator.isValidIdentifier(be.getBuildingId()) && ImportTableIdentifierValidator.isValidIdentifier(be.getVariant())) {
         return false;
      }

      LOGGER.warn(
         "ImportTable export refused: BE has path-unsafe identifiers (buildingId='{}', variant='{}') — possible forged save",
         be.getBuildingId(),
         be.getVariant()
      );
      player.sendSystemMessage(Component.literal("§c[ImportTable] Refusing to export: building id or variant is path-unsafe"));
      return true;
   }

   public static Optional<Path> findExportedNbt(ServerLevel level, String buildingId, String variant, int upgradeLevel) {
      return findExportedNbt(level, null, buildingId, variant, upgradeLevel);
   }

   public static Optional<Path> findExportedNbt(ServerLevel level, @Nullable String culturePath, String buildingId, String variant, int upgradeLevel) {
      if (ImportTableIdentifierValidator.isValidIdentifier(buildingId) && ImportTableIdentifierValidator.isValidIdentifier(variant)) {
         String fileName = buildingId + "_" + variant + "_" + upgradeLevel + ".nbt";
         Path gameDir = level.getServer().getServerDirectory();
         Path legacy = getLegacyExportDir(gameDir).resolve(fileName);
         return Files.exists(legacy) ? Optional.of(legacy) : firstMatchInExported(gameDir, culturePath, fileName);
      } else {
         return Optional.empty();
      }
   }

   public static Optional<Path> findExportedJson(ServerLevel level, String buildingId) {
      return findExportedJson(level, null, buildingId);
   }

   public static Optional<Path> findExportedJson(ServerLevel level, @Nullable String culturePath, String buildingId) {
      if (!ImportTableIdentifierValidator.isValidIdentifier(buildingId)) {
         return Optional.empty();
      }

      String fileName = buildingId + ".json";
      Path gameDir = level.getServer().getServerDirectory();
      Path legacy = getLegacyExportDir(gameDir).resolve(fileName);
      return Files.exists(legacy) ? Optional.of(legacy) : firstMatchInExported(gameDir, culturePath, fileName);
   }

   private static Optional<Path> firstMatchInExported(Path gameDir, @Nullable String culturePath, String fileName) {
      for (Path categoryDir : exportedCategoryDirs(gameDir, culturePath)) {
         Path candidate = categoryDir.resolve(fileName);
         if (Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
         }
      }

      return Optional.empty();
   }

   private static List<Path> exportedCategoryDirs(Path gameDir, @Nullable String culturePath) {
      Path culturesRoot = getExportedRoot(gameDir).resolve("cultures");
      if (!Files.isDirectory(culturesRoot)) {
         return List.of();
      }

      List<Path> cultureDirs;
      if (culturePath != null && !culturePath.isEmpty()) {
         if (!ImportTableIdentifierValidator.isValidIdentifier(culturePath)) {
            return List.of();
         }

         Path scoped = culturesRoot.resolve(culturePath);
         cultureDirs = Files.isDirectory(scoped) ? List.of(scoped) : List.of();
      } else {
         cultureDirs = listSortedDirs(culturesRoot);
      }

      List<Path> out = new ArrayList<>();

      for (Path cultureDir : cultureDirs) {
         Path buildingsDir = cultureDir.resolve("buildings");
         if (Files.isDirectory(buildingsDir)) {
            out.addAll(listSortedDirs(buildingsDir));
         }
      }

      return out;
   }

   private static List<Path> listSortedDirs(Path dir) {
      try (Stream<Path> s = Files.list(dir)) {
         return s.filter(x$0 -> Files.isDirectory(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
      } catch (IOException e) {
         LOGGER.warn("Failed to list {}: {}", dir, e.getMessage());
         return List.of();
      }
   }

   public static String categoryFor(@Nullable String cultureKey, @Nullable String buildingId) {
      return cultureKey != null && !cultureKey.isEmpty() && buildingId != null && !buildingId.isEmpty()
         ? ImportBuildingResolver.resolveBuilding(cultureKey, buildingId)
            .map(ImportBuildingResolver.ResolvedBuilding::category)
            .filter(c -> !c.isEmpty())
            .orElse("lone")
         : "lone";
   }

   public static Path getExportDirFor(ServerLevel level, ImportTableBlockEntity be) {
      Path gameDir = level.getServer().getServerDirectory();
      String cultureKey = be.getCultureKey();
      String buildingId = be.getBuildingId();
      String culturePath = culturePathComponent(cultureKey);
      LegacyExportSinkMigrator.migrate(gameDir, buildingId);
      String scope = cultureKey != null && !cultureKey.isEmpty() ? culturePath : null;
      Optional<Path> existing = findExportedDir(gameDir, scope, buildingId);
      if (existing.isPresent()) {
         return existing.get();
      }

      String category = categoryFor(cultureKey, buildingId);
      if ("lone".equals(category) && be.hasParentContext() && !be.getParentBuildingId().isEmpty()) {
         category = categoryFor(cultureKey, be.getParentBuildingId());
      }

      return getExportDir(level, cultureKey, category);
   }

   public static List<BuildingExporter.ExportedBuilding> listExports(Path gameDir) {
      List<BuildingExporter.ExportedBuilding> out = new ArrayList<>();
      Path culturesRoot = getExportedRoot(gameDir).resolve("cultures");
      if (!Files.isDirectory(culturesRoot)) {
         return out;
      }

      for (Path cultureDir : listSortedDirs(culturesRoot)) {
         String culturePath = cultureDir.getFileName().toString();
         Path buildingsDir = cultureDir.resolve("buildings");
         if (Files.isDirectory(buildingsDir)) {
            for (Path categoryDir : listSortedDirs(buildingsDir)) {
               String category = categoryDir.getFileName().toString();
               collectExportedPlanSets(categoryDir, culturePath, category, out);
            }
         }
      }

      out.sort(Comparator.comparing(BuildingExporter.ExportedBuilding::culturePath).thenComparing(BuildingExporter.ExportedBuilding::buildingId));
      return out;
   }

   private static void collectExportedPlanSets(Path dir, String culturePath, String category, List<BuildingExporter.ExportedBuilding> out) {
      List<Path> files;
      try (Stream<Path> s = Files.list(dir)) {
         files = s.filter(x$0 -> Files.isRegularFile(x$0)).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
      } catch (IOException e) {
         LOGGER.warn("Failed to list {}: {}", dir, e.getMessage());
         return;
      }

      for (Path file : files) {
         String name = file.getFileName().toString();
         if (name.endsWith(".json") && !name.startsWith("_") && !name.endsWith("_special_points.json")) {
            String stem = name.substring(0, name.length() - ".json".length());
            if (stem.indexOf(46) <= 0) {
               JsonObject root = readJsonFile(file);
               if (root != null) {
                  String cultureKey = root.has("culture") && root.get("culture").isJsonPrimitive() ? root.get("culture").getAsString() : "";
                  out.add(new BuildingExporter.ExportedBuilding(culturePath, cultureKey, category, stem, ImportBuildingResolver.variantLevels(root)));
               }
            }
         }
      }
   }

   @Nullable
   static JsonObject readJsonFile(Path file) {
      try {
         return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      } catch (Exception e) {
         LOGGER.warn("Failed to read {}: {}", file, e.getMessage());
         return null;
      }
   }

   public static Optional<Path> findExportedDir(Path gameDir, @Nullable String culturePath, String buildingId) {
      if (!ImportTableIdentifierValidator.isValidIdentifier(buildingId)) {
         return Optional.empty();
      }

      String fileName = buildingId + ".json";

      for (Path categoryDir : exportedCategoryDirs(gameDir, culturePath)) {
         if (Files.isRegularFile(categoryDir.resolve(fileName))) {
            return Optional.of(categoryDir);
         }
      }

      return Optional.empty();
   }

   public static Optional<Path> findExportedDir(Path gameDir, String buildingId) {
      return findExportedDir(gameDir, null, buildingId);
   }

   private static void ensureDir(Path dir) {
      try {
         Files.createDirectories(dir);
      } catch (IOException e) {
         LOGGER.error("Failed to create export directory: {}", dir, e);
      }
   }

   public static void refreshOverlay() {
      CustomContentIndex.rebuild(BuiltInCultures.IDS, true);
   }

   public static void reclaimAbandonedStaging(ServerLevel level) {
      ExportTransaction.reclaimStaging(getExportedRoot(level));
   }

   static BuildingExporter.CaptureResult captureLevelCore(ServerLevel level, ImportTableBlockEntity be, Path exportDir, @Nullable ServerPlayer player) {
      BlockPos origin = computeScanOrigin(be);
      int detectedHeight = detectBuildingHeight(level, origin, be, be.getUpgradeLevel());
      be.setHeight(detectedHeight);
      Vec3i size = computeScanSize(be);
      StructureTemplate template = new StructureTemplate();
      template.fillFromWorld(level, origin, size, false, null);
      CompoundTag nbt = template.save(new CompoundTag());
      postProcessNbt(nbt, be);
      stripPreviousLevelBlocks(nbt, be, level);
      compactPalette(nbt);
      String buildingId = be.getBuildingId();
      String nbtFileName = buildingId + "_" + be.getVariant() + "_" + be.getUpgradeLevel() + ".nbt";
      String culturePath = culturePathComponent(be.getCultureKey());

      int copiedLevels;
      try (ExportTransaction tx = ExportTransaction.begin(getExportedRoot(level), culturePath, buildingId, exportDir)) {
         tx.stageNbt(nbtFileName, nbt);
         JsonObject root = readOrCreateExportJson(exportDir, be);
         updateLevelEntry(root, be);
         ImportBuildingResolver.ResolvedBuilding resolved = ImportBuildingResolver.resolveBuilding(be.getCultureKey(), buildingId).orElse(null);
         copiedLevels = stageMissingPlanLevels(level, be, tx, resolved);
         if (resolved != null) {
            applyPlanToJson(root, buildingId, be.getVariant(), be.getUpgradeLevel(), resolved.unionPlan(), tx::willExist);
         }

         tx.stageMarker(buildingId + ".json", GSON.toJson(root));
         tx.commit();
      } catch (IOException | RuntimeException e) {
         LOGGER.error("Failed to export {} into {}", new Object[]{nbtFileName, exportDir, e});
         if (player != null) {
            player.sendSystemMessage(Component.literal("§c[ImportTable] Failed to export: " + e.getMessage()));
         }

         return new BuildingExporter.CaptureResult(false, detectedHeight, 0);
      }

      return new BuildingExporter.CaptureResult(true, detectedHeight, copiedLevels);
   }

   static boolean captureAndWriteLevel(ServerLevel level, ImportTableBlockEntity be, Path exportDir, @Nullable ServerPlayer player) {
      if (!captureLevelCore(level, be, exportDir, player).success()) {
         return false;
      }

      be.captureSavedState(computeBlocksHash(level, be));
      be.setDirty(false);
      return true;
   }

   private static void extractBlocksFromTemplateRelative(
      StructureTemplate template, StructurePlaceSettings settings, int yOffset, ServerLevel level, Map<BlockPos, BlockState> combined, boolean isUpgradeLayer
   ) {
      CompoundTag nbt = template.save(new CompoundTag());
      List<BlockState> palette = new ArrayList<>();
      ListTag paletteTag = NbtPaletteHelper.resolvePaletteTag(nbt);
      if (paletteTag != null) {
         for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag stateTag = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
            palette.add(state);
         }

         if (!palette.isEmpty()) {
            ListTag blocksTag = nbt.getList("blocks", 10);

            for (int i = 0; i < blocksTag.size(); i++) {
               CompoundTag blockEntry = blocksTag.getCompound(i);
               ListTag posTag = blockEntry.getList("pos", 3);
               BlockPos templatePos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
               int stateIndex = blockEntry.getInt("state");
               if (stateIndex >= 0 && stateIndex < palette.size()) {
                  BlockState state = palette.get(stateIndex);
                  BlockPos rotatedPos = StructureTemplate.calculateRelativePosition(settings, templatePos);
                  BlockPos relPos = rotatedPos.offset(0, yOffset, 0);
                  if (state.isAir()) {
                     if (isUpgradeLayer) {
                        combined.remove(relPos);
                     }
                  } else {
                     BlockState rotatedState = state.rotate(settings.getRotation());
                     combined.put(relPos, rotatedState);
                  }
               }
            }
         }
      }
   }

   record CaptureResult(boolean success, int detectedHeight, int copiedLevels) {
   }

   enum DiffOutcome {
      INHERIT,
      DELETE,
      OVERRIDE,
      OWN,
      NONE;
   }

   public record ExportedBuilding(String culturePath, String cultureKey, String category, String buildingId, Map<String, List<Integer>> variantLevels) {
   }

   static final class IncompletePlanSetException extends RuntimeException {
      IncompletePlanSetException(String message, @Nullable Throwable cause) {
         super(message, cause);
      }
   }

   public enum SubRefPatchResult {
      ADDED,
      ALREADY_PRESENT,
      LEVEL_NOT_FOUND,
      PARENT_NOT_FOUND;
   }
}
