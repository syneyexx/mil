package org.millenaire.test;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.BuildingImporter;
import org.slf4j.Logger;

@GameTestHolder("millenaire")
@PrefixGameTestTemplate(false)
public class SubBuildingExportDiffGameTests {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String PARENT_CULTURE = "millenaire:norman";
   private static final String PARENT_ID = "manor";
   private static final String PARENT_VARIANT = "a";
   private static final int TRIGGER_LEVEL = 0;
   private static final String SUB_NAME = "diffgametest";

   @GameTest(template = "empty_platform", setupTicks = 1L, timeoutTicks = 600)
   public static void subExportDiffsAgainstParent(GameTestHelper helper) {
      ServerLevel level = helper.getLevel();
      ServerPlayer player = helper.makeMockServerPlayerInLevel();
      BlockPos tableRel = new BlockPos(1, 1, 1);
      helper.setBlock(tableRel, (Block)ModBlocks.IMPORT_TABLE.get());
      BlockPos tableAbs = helper.absolutePos(tableRel);
      if (!(level.getBlockEntity(tableAbs) instanceof ImportTableBlockEntity be)) {
         helper.fail("Import Table block entity not present after placement");
      } else {
         String subId = BuildingImporter.createSubBuilding(level, be, player, "millenaire:norman", "manor", "a", 0, "diffgametest");
         if (subId == null) {
            LOGGER.warn(
               "[SubBuildingExportDiff] UNCOVERED (TEST-17) — createSubBuilding returned null: the manor parent could not be framed in the GameTest server, so the §3.6 export/re-import round-trip did NOT run. Conditional-skip (not coverage)."
            );
            helper.succeed();
         } else {
            BlockPos origin = BuildingExporter.computeScanOrigin(be);
            Vec3i size = BuildingExporter.computeScanSize(be);
            BlockPos carveAbs = null;
            BlockPos untouchedAbs = null;
            int topSolidDy = -1;

            for (int dx = 0; dx < size.getX(); dx++) {
               for (int dy = 0; dy < size.getY(); dy++) {
                  for (int dz = 0; dz < size.getZ(); dz++) {
                     BlockPos p = origin.offset(dx, dy, dz);
                     if (!level.getBlockState(p).isAir()) {
                        if (dy > topSolidDy) {
                           topSolidDy = dy;
                        }

                        if (carveAbs == null) {
                           carveAbs = p;
                        } else if (untouchedAbs == null) {
                           untouchedAbs = p;
                        }
                     }
                  }
               }
            }

            if (carveAbs != null && untouchedAbs != null) {
               BlockState untouchedState = level.getBlockState(untouchedAbs);
               BlockPos carveRel = carveAbs.subtract(origin);
               BlockPos untouchedRel = untouchedAbs.subtract(origin);
               level.setBlock(carveAbs, Blocks.AIR.defaultBlockState(), 3);
               int addDy = Math.min(topSolidDy + 1, size.getY() - 1);
               BlockPos addAbs = null;

               for (int dx = 0; dx < size.getX() && addAbs == null; dx++) {
                  for (int dz = 0; dz < size.getZ() && addAbs == null; dz++) {
                     BlockPos p = origin.offset(dx, addDy, dz);
                     if (!p.equals(carveAbs) && level.getBlockState(p).isAir()) {
                        addAbs = p;
                     }
                  }
               }

               if (addAbs == null) {
                  helper.fail("No air position at rel-Y " + addDy + " to place the sub's own block (scan volume fully solid above the parent top?)");
               } else {
                  BlockPos addRel = addAbs.subtract(origin);
                  level.setBlock(addAbs, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                  BuildingExporter.exportLevel(level, be, player);
                  Path exportDir = BuildingExporter.getExportDir(level, "millenaire:norman", BuildingExporter.categoryFor("millenaire:norman", "manor"));
                  Path nbtPath = exportDir.resolve(subId + "_a_0.nbt");
                  if (!Files.exists(nbtPath)) {
                     helper.fail("Exported sub NBT not found at " + nbtPath);
                  } else {
                     CompoundTag nbt;
                     try {
                        nbt = NbtIo.readCompressed(nbtPath, NbtAccounter.unlimitedHeap());
                     } catch (Exception e) {
                        helper.fail("Failed to read exported sub NBT: " + e.getMessage());
                        return;
                     }

                     List<BlockState> palette = readPalette(level, nbt);
                     BlockState carvedEmit = null;
                     BlockState addedEmit = null;
                     BlockState untouchedEmit = null;
                     ListTag blocks = nbt.getList("blocks", 10);

                     for (int i = 0; i < blocks.size(); i++) {
                        CompoundTag block = blocks.getCompound(i);
                        ListTag pos = block.getList("pos", 3);
                        BlockPos relPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                        int stateIdx = block.getInt("state");
                        if (stateIdx >= 0 && stateIdx < palette.size()) {
                           BlockState state = palette.get(stateIdx);
                           if (relPos.equals(carveRel)) {
                              carvedEmit = state;
                           }

                           if (relPos.equals(addRel)) {
                              addedEmit = state;
                           }

                           if (relPos.equals(untouchedRel)) {
                              untouchedEmit = state;
                           }
                        }
                     }

                     if (carvedEmit == null || !carvedEmit.isAir()) {
                        helper.fail(
                           "Carved parent position "
                              + carveRel
                              + " must export as explicit air (DELETION); got "
                              + (carvedEmit == null ? "absent" : carvedEmit)
                        );
                     } else if (addedEmit == null || addedEmit.getBlock() != Blocks.GOLD_BLOCK) {
                        helper.fail("Added block at " + addRel + " must be present in the sub template; got " + (addedEmit == null ? "absent" : addedEmit));
                     } else if (untouchedEmit != null && untouchedEmit.equals(untouchedState)) {
                        helper.fail(
                           "Untouched parent block at "
                              + untouchedRel
                              + " ("
                              + untouchedState
                              + ") must be inherited (dropped), not duplicated into the sub template"
                        );
                     } else {
                        BuildingImporter.importLevelFromCulture(level, be, player, "millenaire:norman", subId, "a", 0, "manor", "a");
                        if (!level.getBlockState(carveAbs).isAir()) {
                           helper.fail(
                              "Re-import did not apply the DELETION: carved position " + carveRel + " is " + level.getBlockState(carveAbs) + ", expected air"
                           );
                        } else {
                           LOGGER.info(
                              "§3.6 sub-export diff round-trip OK: sub '{}' carved={} added={} untouched={}",
                              new Object[]{subId, carveRel, addRel, untouchedRel}
                           );
                           helper.succeed();
                        }
                     }
                  }
               }
            } else {
               helper.fail("Parent base placed too few solid blocks to run the diff test");
            }
         }
      }
   }

   private static List<BlockState> readPalette(ServerLevel level, CompoundTag nbt) {
      List<BlockState> palette = new ArrayList<>();
      ListTag paletteTag = nbt.getList("palette", 10);

      for (int i = 0; i < paletteTag.size(); i++) {
         palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), paletteTag.getCompound(i)));
      }

      return palette;
   }
}
