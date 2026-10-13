package org.millenaire.village.panel;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.VillagePanelBlock;
import org.millenaire.block.VillagePanelBlockEntity;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.SpecialPoint;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public final class PanelPlacer {
   private static final Logger LOGGER = LogUtils.getLogger();

   private PanelPlacer() {
   }

   public static void placePanels(ServerLevel level, Village village, BuildingInstance building) {
      BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
      if (planSet != null && planSet.isHoF()) {
         fillHoFSigns(level, village, building);
      } else {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType == null || villageType.showTownHallSigns()) {
            List<SpecialPoint> signPoints = building.getPointsByType("signPos");
            if (!signPoints.isEmpty()) {
               int[] signOrder = resolveSignOrder(planSet, building);

               for (int i = 0; i < signPoints.size(); i++) {
                  SpecialPoint sp = signPoints.get(i);
                  BlockPos pos = sp.pos();
                  int logicalIndex = applySignOrder(signOrder, i);
                  boolean isMarvel = villageType != null && villageType.isMarvel();
                  boolean isPlayerControlled = villageType != null && villageType.playerControlled();
                  PanelType panelType = determinePanelType(logicalIndex, planSet, building, isMarvel, isPlayerControlled);
                  Direction facing = resolveFacing(level, sp, pos, village.getCenter());
                  BlockState panelState = (BlockState)((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get())
                     .defaultBlockState()
                     .setValue(VillagePanelBlock.FACING, facing);
                  level.setBlock(pos, panelState, 3);
                  if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity panelBe) {
                     panelBe.setBuildingId(building.getId());
                     panelBe.setPanelType(panelType);
                     panelBe.setVillageId(village.getId().uuid());
                     panelBe.setCultureId(village.getCultureId());
                     panelBe.setSignIndex(logicalIndex);
                     PanelContentGenerator.DisplayData displayData = PanelContentGenerator.generateDisplayLines(
                        panelType, village, building, level, logicalIndex
                     );
                     panelBe.updateDisplayLines(displayData.displayLines());
                     level.sendBlockUpdated(pos, panelState, panelState, 3);
                  }
               }

               LOGGER.debug("[Millenaire] {} panels placed for {}", signPoints.size(), building.getPlanId().getPath());
            }
         }
      }
   }

   static PanelType determinePanelType(int index, @Nullable BuildingPlanSet planSet, BuildingInstance building, boolean isMarvel) {
      return determinePanelType(index, planSet, building, isMarvel, false);
   }

   static PanelType determinePanelType(int index, @Nullable BuildingPlanSet planSet, BuildingInstance building, boolean isMarvel, boolean isPlayerControlled) {
      boolean isTownhall = planSet != null && planSet.isTownHall();
      if (isTownhall && isMarvel) {
         return switch (index) {
            case 0 -> PanelType.VILLAGE_SUMMARY;
            case 1 -> PanelType.RESOURCES;
            case 2 -> PanelType.WALLS;
            case 3 -> PanelType.MARVEL_PROJECTS;
            case 4 -> PanelType.PROJECTS;
            case 5 -> PanelType.CONSTRUCTIONS;
            case 6 -> PanelType.POPULATION;
            case 7 -> PanelType.VILLAGE_MAP;
            case 8 -> PanelType.MILITARY;
            case 9 -> PanelType.MARVEL_RESOURCES;
            case 10 -> PanelType.MARVEL_DONATIONS;
            default -> PanelType.VILLAGE_SUMMARY;
         };
      } else if (isTownhall) {
         return switch (index) {
            case 0 -> PanelType.VILLAGE_SUMMARY;
            case 1 -> PanelType.RESOURCES;
            case 2 -> PanelType.WALLS;
            case 3 -> PanelType.PLAYER_BUILDINGS;
            case 4 -> isPlayerControlled ? PanelType.CONTROLLED_PROJECTS : PanelType.PROJECTS;
            case 5 -> PanelType.CONSTRUCTIONS;
            case 6 -> isPlayerControlled ? PanelType.CONTROLLED_CITY : PanelType.POPULATION;
            case 7 -> PanelType.VILLAGE_MAP;
            case 8 -> isPlayerControlled ? PanelType.CONTROLLED_MILITARY : PanelType.MILITARY;
            default -> PanelType.VILLAGE_SUMMARY;
         };
      } else {
         if (planSet != null) {
            if (planSet.hasVisitors()) {
               return planSet.isMarket() ? PanelType.MARKET_MERCHANTS : PanelType.VISITORS;
            }

            if (planSet.isInn()) {
               return index == 0 ? PanelType.INN_VISITORS : PanelType.INN_TRADE_GOODS;
            }

            if (planSet.isArchives()) {
               return PanelType.ARCHIVES;
            }

            if (planSet.isBorderPost()) {
               return PanelType.VILLAGE_SUMMARY;
            }
         }

         return planSet == null || planSet.maleResidents().isEmpty() && planSet.femaleResidents().isEmpty()
            ? PanelType.BUILDING_DEFAULT
            : PanelType.BUILDING_DEFAULT;
      }
   }

   static List<SpecialPoint> sortedHoFSignPoints(List<SpecialPoint> signPoints) {
      List<SpecialPoint> sorted = new ArrayList<>(signPoints);
      sorted.sort((a, b) -> {
         int cz = Integer.compare(b.pos().getZ(), a.pos().getZ());
         if (cz != 0) {
            return cz;
         }

         int cx = Integer.compare(a.pos().getX(), b.pos().getX());
         return cx != 0 ? cx : Integer.compare(b.pos().getY(), a.pos().getY());
      });
      return sorted;
   }

   private static void fillHoFSigns(ServerLevel level, Village village, BuildingInstance building) {
      List<SpecialPoint> signPoints = building.getPointsByType("signPos");
      if (signPoints.isEmpty()) {
         LOGGER.debug("[Millenaire] No signPos found for HoF in {}", building.getPlanId().getPath());
      } else {
         signPoints = sortedHoFSignPoints(signPoints);
         List<String> hofData = HallOfFameLoader.getHoFData();
         LOGGER.info("[HoF] {} signPos found, {} HoF entries", signPoints.size(), hofData.size());

         for (int dbg = 0; dbg < Math.min(10, signPoints.size()); dbg++) {
            BlockPos p = signPoints.get(dbg).pos();
            String entry = dbg < hofData.size() ? hofData.get(dbg) : "N/A";
            LOGGER.info(
               "[HoF] sign[{}] pos=({},{},{}) -> {}", new Object[]{dbg, p.getX(), p.getY(), p.getZ(), entry.length() > 40 ? entry.substring(0, 40) : entry}
            );
         }

         int signNb = 0;

         for (SpecialPoint sp : signPoints) {
            BlockPos pos = sp.pos();
            Direction facing = resolveFacing(level, sp, pos, village.getCenter());
            BlockState panelState = (BlockState)((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get())
               .defaultBlockState()
               .setValue(VillagePanelBlock.FACING, facing);
            level.setBlock(pos, panelState, 3);
            if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity panelBe) {
               panelBe.setBuildingId(building.getId());
               panelBe.setPanelType(PanelType.HALL_OF_FAME);
               if (signNb < hofData.size()) {
                  List<PanelContentGenerator.DisplayLine> lines = buildHoFDisplayLines(hofData.get(signNb));
                  panelBe.updateDisplayLines(lines);
               }

               level.sendBlockUpdated(pos, panelState, panelState, 3);
               signNb++;
            }
         }

         LOGGER.debug(
            "[Millenaire] HoF: {} panels filled out of {} signPos for {}",
            new Object[]{Math.min(signNb, hofData.size()), signPoints.size(), building.getPlanId().getPath()}
         );
      }
   }

   static List<PanelContentGenerator.DisplayLine> buildHoFDisplayLines(String entry) {
      String[] parts = entry.split(";", -1);
      List<PanelContentGenerator.DisplayLine> lines = new ArrayList<>();
      boolean twoPatrons = isTwoPatronEntry(parts);

      for (int i = 0; i < Math.min(4, parts.length); i++) {
         if (twoPatrons && i == 2) {
            lines.add(PanelContentGenerator.DisplayLine.centered(""));
         }

         String text = parts[i];
         if (!text.isEmpty() && text.startsWith("hof.")) {
            lines.add(PanelContentGenerator.DisplayLine.centeredTranslatable(text));
         } else {
            lines.add(PanelContentGenerator.DisplayLine.centered(text));
         }
      }

      return lines;
   }

   private static boolean isTwoPatronEntry(String[] parts) {
      return parts.length == 4 && isName(parts[0]) && isKey(parts[1]) && isName(parts[2]) && isKey(parts[3]);
   }

   private static boolean isName(String field) {
      return !field.isEmpty() && !field.startsWith("hof.");
   }

   private static boolean isKey(String field) {
      return field.startsWith("hof.");
   }

   public static void recreatePanel(ServerLevel level, Village village, BuildingInstance building, BlockPos pos) {
      BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
      List<SpecialPoint> signPoints = building.getPointsByType("signPos");
      int index = 0;
      SpecialPoint matchedSp = null;

      for (int i = 0; i < signPoints.size(); i++) {
         if (signPoints.get(i).pos().equals(pos)) {
            index = i;
            matchedSp = signPoints.get(i);
            break;
         }
      }

      if (planSet != null && planSet.isHoF()) {
         recreateHoFPanel(level, village, building, pos, matchedSp, signPoints);
      } else {
         int[] signOrder = resolveSignOrder(planSet, building);
         int logicalIndex = applySignOrder(signOrder, index);
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         boolean isMarvel = villageType != null && villageType.isMarvel();
         PanelType panelType = determinePanelType(logicalIndex, planSet, building, isMarvel);
         Direction facing = resolveFacing(level, matchedSp, pos, village.getCenter());
         BlockState panelState = (BlockState)((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get()).defaultBlockState().setValue(VillagePanelBlock.FACING, facing);
         level.setBlock(pos, panelState, 3);
         if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity panelBe) {
            panelBe.setBuildingId(building.getId());
            panelBe.setPanelType(panelType);
            panelBe.setVillageId(village.getId().uuid());
            panelBe.setCultureId(village.getCultureId());
            panelBe.setSignIndex(logicalIndex);
            PanelContentGenerator.DisplayData displayData = PanelContentGenerator.generateDisplayLines(panelType, village, building, level, logicalIndex);
            panelBe.updateDisplayLines(displayData.displayLines());
            level.sendBlockUpdated(pos, panelState, panelState, 3);
         }

         LOGGER.debug("[Millenaire] Panel recreated at {} for {}", pos, building.getPlanId().getPath());
      }
   }

   private static void recreateHoFPanel(
      ServerLevel level, Village village, BuildingInstance building, BlockPos pos, @Nullable SpecialPoint matchedSp, List<SpecialPoint> signPoints
   ) {
      List<SpecialPoint> sorted = sortedHoFSignPoints(signPoints);
      int hofIndex = -1;

      for (int i = 0; i < sorted.size(); i++) {
         if (sorted.get(i).pos().equals(pos)) {
            hofIndex = i;
            break;
         }
      }

      Direction facing = resolveFacing(level, matchedSp, pos, village.getCenter());
      BlockState panelState = (BlockState)((VillagePanelBlock)ModBlocks.VILLAGE_PANEL.get()).defaultBlockState().setValue(VillagePanelBlock.FACING, facing);
      level.setBlock(pos, panelState, 3);
      if (level.getBlockEntity(pos) instanceof VillagePanelBlockEntity panelBe) {
         panelBe.setBuildingId(building.getId());
         panelBe.setPanelType(PanelType.HALL_OF_FAME);
         panelBe.setVillageId(village.getId().uuid());
         panelBe.setCultureId(village.getCultureId());
         List<String> hofData = HallOfFameLoader.getHoFData();
         if (hofIndex >= 0 && hofIndex < hofData.size()) {
            panelBe.updateDisplayLines(buildHoFDisplayLines(hofData.get(hofIndex)));
         }

         level.sendBlockUpdated(pos, panelState, panelState, 3);
      }

      LOGGER.debug("[Millenaire] HoF panel recreated at {} (hofIndex={}) for {}", new Object[]{pos, hofIndex, building.getPlanId().getPath()});
   }

   private static Direction resolveFacing(ServerLevel level, @Nullable SpecialPoint sp, BlockPos panelPos, BlockPos villageCenter) {
      if (sp != null && sp.orientation() != null && !"guess".equals(sp.orientation())) {
         Direction fixed = parseDirection(sp.orientation());
         if (fixed != null) {
            return fixed;
         }
      }

      BlockPos pos = sp != null ? sp.pos() : panelPos;
      Direction detected = detectWallFacing(level, pos);
      return detected != null ? detected : computeFacing(villageCenter, pos);
   }

   @Nullable
   private static Direction parseDirection(String name) {
      return switch (name.toLowerCase(Locale.ROOT)) {
         case "north" -> Direction.NORTH;
         case "south" -> Direction.SOUTH;
         case "east" -> Direction.EAST;
         case "west" -> Direction.WEST;
         default -> null;
      };
   }

   @Nullable
   private static Direction detectWallFacing(ServerLevel level, BlockPos panelPos) {
      Direction[] priority = new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

      for (Direction dir : priority) {
         BlockPos adjacent = panelPos.relative(dir);
         if (level.getBlockState(adjacent).isSolidRender(level, adjacent)) {
            return dir.getOpposite();
         }
      }

      return null;
   }

   @Nullable
   static int[] resolveSignOrder(@Nullable BuildingPlanSet planSet, BuildingInstance building) {
      if (planSet == null) {
         return null;
      }

      String variant = building.getVariant();
      if (variant == null) {
         return null;
      }

      for (int lvl = building.getLevel(); lvl >= 0; lvl--) {
         BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, lvl);
         if (levelDef != null && levelDef.signOrder() != null) {
            return levelDef.signOrder();
         }
      }

      return null;
   }

   static int applySignOrder(@Nullable int[] signOrder, int physicalIndex) {
      return signOrder != null && physicalIndex < signOrder.length ? signOrder[physicalIndex] : physicalIndex;
   }

   private static Direction computeFacing(BlockPos from, BlockPos panelPos) {
      int dx = from.getX() - panelPos.getX();
      int dz = from.getZ() - panelPos.getZ();
      if (Math.abs(dx) > Math.abs(dz)) {
         return dx > 0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz > 0 ? Direction.SOUTH : Direction.NORTH;
      }
   }
}
