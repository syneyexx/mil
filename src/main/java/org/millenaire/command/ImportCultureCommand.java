package org.millenaire.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.millenaire.block.ImportTableBlock;
import org.millenaire.block.ImportTableBlockEntity;
import org.millenaire.block.ModBlocks;
import org.millenaire.building.BuildingImporter;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ImportTablePlanResolver;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public final class ImportCultureCommand {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int BUILDING_GROUP_SPACING = 5;
   private static final SuggestionProvider<CommandSourceStack> CULTURE_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ModCultures.getAllCultures().keySet().stream().map(ResourceLocation::getPath), builder
   );

   private ImportCultureCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> parent) {
      parent.then(
         Commands.literal("importculture")
            .then(
               ((RequiredArgumentBuilder)Commands.argument("culture", StringArgumentType.string())
                     .suggests(CULTURE_SUGGESTIONS)
                     .executes(ImportCultureCommand::execute))
                  .then(
                     Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(ImportCultureCommand::execute))
                  )
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = resolvePlayer(ctx);
      String cultureArg = StringArgumentType.getString(ctx, "culture");
      ResourceLocation cultureId = resolveCultureId(cultureArg);
      if (ModCultures.getCulture(cultureId) == null) {
         source.sendFailure(
            Component.literal(
               "Unknown culture: "
                  + cultureArg
                  + ". Available: "
                  + ModCultures.getAllCultures().keySet().stream().map(ResourceLocation::getPath).sorted().toList()
            )
         );
         return 0;
      }

      List<BuildingPlanSet> planSets = ModCultures.getAllBuildingPlanSets()
         .values()
         .stream()
         .filter(ps -> cultureId.equals(ps.culture()))
         .filter(ps -> !ps.isSubBuilding())
         .filter(ps -> !ps.tags().contains("marvel"))
         .sorted(Comparator.comparing(BuildingPlanSet::buildingId))
         .toList();
      if (planSets.isEmpty()) {
         source.sendFailure(Component.literal("No top-level building plan sets found for culture: " + cultureId));
         return 0;
      }

      ServerLevel level = player.serverLevel();
      BlockPos startPos = resolveStartPos(ctx, player, level);
      int nextBuildingMinX = startPos.getX();
      int importedBuildings = 0;
      int importedPlans = 0;
      int failedCount = 0;
      int importedSubs = 0;
      int importedSubTables = 0;
      Set<String> placedSubs = new HashSet<>();
      List<String> fromExportsNames = new ArrayList<>();

      for (BuildingPlanSet planSet : planSets) {
         BuildingPlan firstPlan = resolveFirstPlan(planSet);
         if (firstPlan == null) {
            source.sendSystemMessage(Component.literal("§c[ImportCulture] No usable plan for " + planSet.id()));
            failedCount++;
         } else {
            BlockPos tablePos = BuildingImporter.tablePosForBuildingAabb(nextBuildingMinX, startPos.getZ(), startPos.getY());
            ImportTableBlockEntity table = placeImportTable(level, tablePos, player.getDirection());
            if (table == null) {
               source.sendSystemMessage(
                  Component.literal("§c[ImportCulture] Failed to create ImportTable for " + planSet.id() + " at " + tablePos.toShortString())
               );
               failedCount++;
               nextBuildingMinX += 5;
            } else {
               source.sendSystemMessage(Component.literal("§f[ImportCulture] Importing " + planSet.id()));
               BuildingImporter.ImportAabb aabb = BuildingImporter.importAllVariantsFromCulture(
                  level, table, player, cultureId.toString(), planSet.buildingId()
               );
               if (aabb != null) {
                  importedBuildings++;
                  importedPlans += countPlans(planSet);
                  if (ImportTablePlanResolver.isServedFromExports(cultureId.toString(), planSet.buildingId())) {
                     fromExportsNames.add(planSet.buildingId());
                  }

                  ImportCultureCommand.SubBandResult band = placeSubBuildingBand(level, player, cultureId, planSet, aabb, startPos.getY(), placedSubs);
                  importedSubs += band.subsPlaced();
                  importedSubTables += band.subTablesPlaced();
                  failedCount += band.failed();
                  nextBuildingMinX = aabb.maxX() + 1 + 5;
               } else {
                  failedCount++;
                  nextBuildingMinX += 5;
               }
            }
         }
      }

      int importedBuildingsFinal = importedBuildings;
      int importedPlansFinal = importedPlans;
      int failedFinal = failedCount;
      int importedSubsFinal = importedSubs;
      int importedSubTablesFinal = importedSubTables;
      source.sendSuccess(
         () -> Component.literal(
            "Imported culture "
               + cultureId.getPath()
               + " (buildings="
               + importedBuildingsFinal
               + "/"
               + planSets.size()
               + ", plans="
               + importedPlansFinal
               + ", subs="
               + importedSubsFinal
               + ", subTables="
               + importedSubTablesFinal
               + ", failed="
               + failedFinal
               + ")"
         ),
         true
      );
      ImportTablePlanResolver.formatFromExportsMessage("ImportCulture", fromExportsNames).ifPresent(msg -> source.sendSystemMessage(Component.literal(msg)));
      long unregistered = ImportTablePlanResolver.listCulture(cultureId.toString()).stream().filter(e -> !e.registered()).count();
      if (unregistered > 0L) {
         source.sendSystemMessage(
            Component.literal(
               "§e[ImportCulture] "
                  + unregistered
                  + " building(s) exist on disk but not in the frozen registry and were skipped — use the Import Table's culture tab, or reload the world."
            )
         );
      }

      return 1;
   }

   private static BlockPos resolveStartPos(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ServerLevel level) {
      Integer x = getOptionalInt(ctx, "x");
      Integer z = getOptionalInt(ctx, "z");
      if (x != null && z != null) {
         int y = TerrainPreparer.getGroundHeight(level, x, z);
         return new BlockPos(x, y, z);
      } else {
         BlockPos inFront = player.blockPosition().relative(player.getDirection(), 2);
         int y = TerrainPreparer.getGroundHeight(level, inFront.getX(), inFront.getZ());
         return new BlockPos(inFront.getX(), y, inFront.getZ());
      }
   }

   private static ImportTableBlockEntity placeImportTable(ServerLevel level, BlockPos pos, Direction playerDirection) {
      Direction facing = playerDirection.getOpposite();
      level.setBlock(pos, (BlockState)((ImportTableBlock)ModBlocks.IMPORT_TABLE.get()).defaultBlockState().setValue(ImportTableBlock.FACING, facing), 2);
      if (level.getBlockEntity(pos) instanceof ImportTableBlockEntity table) {
         table.setBuildingId("");
         table.setCultureKey("");
         table.setOrientation(0);
         table.setImportMockBlocks(true);
         return table;
      } else {
         return null;
      }
   }

   private static ImportCultureCommand.SubBandResult placeSubBuildingBand(
      ServerLevel level,
      ServerPlayer player,
      ResourceLocation cultureId,
      BuildingPlanSet parent,
      BuildingImporter.ImportAabb parentAabb,
      int startY,
      Set<String> placedSubs
   ) {
      String cultureKey = cultureId.toString();
      List<BuildingImporter.SubRef> refs = BuildingImporter.listReferencedSubs(cultureKey, parent.buildingId());
      if (refs.isEmpty()) {
         return ImportCultureCommand.SubBandResult.EMPTY;
      }

      Optional<ImportTablePlanResolver.PlanView> parentPlan = ImportTablePlanResolver.resolvePlan(cultureKey, parent.buildingId());
      int subMinX = parentAabb.minX();
      int nextSubMinZ = parentAabb.maxZ() + 1 + 5;
      int subsPlaced = 0;
      int subTablesPlaced = 0;
      int failed = 0;

      for (BuildingImporter.SubRef ref : refs) {
         if (!placedSubs.contains(ref.subId())) {
            Optional<ImportTablePlanResolver.PlanView> subPlan = ImportTablePlanResolver.resolvePlan(cultureKey, ref.subId());
            if (!subPlan.isEmpty() && BuildingImporter.readIsSubBuildingFlag(cultureKey, ref.subId())) {
               Map<String, ImportTablePlanResolver.VariantView> subVariants = subPlan.get().variants();
               if (subVariants.isEmpty()) {
                  LOGGER.warn("ImportCulture: sub '{}' has no variants — skipped", ref.subId());
                  failed++;
               } else {
                  String subVariant = subVariants.keySet().iterator().next();
                  ImportTablePlanResolver.VariantView vv = subVariants.get(subVariant);
                  if (vv != null && !vv.levels().isEmpty()) {
                     List<Integer> sortedLevels = vv.levels().keySet().stream().sorted().toList();
                     String parentVariant = ref.parentVariant().isEmpty() ? firstVariant(parent) : ref.parentVariant();
                     int parentDepth = parentDepthForVariant(parentPlan, parentVariant);
                     boolean anyLevelPlaced = false;

                     for (int levelNum : sortedLevels) {
                        BlockPos tablePos = BuildingImporter.tablePosForBuildingAabb(subMinX, nextSubMinZ, startY);
                        ImportTableBlockEntity subTable = placeImportTable(level, tablePos, player.getDirection());
                        if (subTable == null) {
                           player.sendSystemMessage(
                              Component.literal("§c[ImportCulture] Failed to create ImportTable for sub " + ref.subId() + " at " + tablePos.toShortString())
                           );
                           failed++;
                           nextSubMinZ += 5;
                        } else {
                           BuildingImporter.importLevelFromCulture(
                              level, subTable, player, cultureKey, ref.subId(), subVariant, levelNum, parent.buildingId(), parentVariant
                           );
                           if (!ref.subId().equals(subTable.getBuildingId())) {
                              LOGGER.warn(
                                 "ImportCulture: sub '{}' under parent '{}/{}' rejected by importer at L{} (trigger unresolved or template missing) — skipped",
                                 new Object[]{ref.subId(), parent.buildingId(), parentVariant, levelNum}
                              );
                              failed++;
                              nextSubMinZ += 5;
                           } else {
                              int compositeDepth = Math.max(subTable.getLength(), parentDepth);
                              nextSubMinZ += compositeDepth + 5;
                              subTablesPlaced++;
                              anyLevelPlaced = true;
                           }
                        }
                     }

                     if (anyLevelPlaced) {
                        placedSubs.add(ref.subId());
                        subsPlaced++;
                     }
                  } else {
                     LOGGER.warn("ImportCulture: sub '{}' variant '{}' has no levels — skipped", ref.subId(), subVariant);
                     failed++;
                  }
               }
            } else {
               LOGGER.warn("ImportCulture: sub '{}' referenced by '{}' is missing or not a sub-building — skipped", ref.subId(), parent.buildingId());
               failed++;
            }
         }
      }

      return new ImportCultureCommand.SubBandResult(subsPlaced, subTablesPlaced, failed);
   }

   private static int parentDepthForVariant(Optional<ImportTablePlanResolver.PlanView> parentPlan, String variant) {
      if (parentPlan.isEmpty()) {
         return 0;
      }

      ImportTablePlanResolver.VariantView vv = parentPlan.get().variants().get(variant);
      return vv == null ? 0 : vv.levels().values().stream().mapToInt(ImportTablePlanResolver.LevelView::depth).max().orElse(0);
   }

   private static String firstVariant(BuildingPlanSet planSet) {
      return planSet.variants().keySet().stream().sorted().findFirst().orElse("a");
   }

   private static int countPlans(BuildingPlanSet planSet) {
      return planSet.variants().values().stream().mapToInt(List::size).sum();
   }

   @Nullable
   private static BuildingPlan resolveFirstPlan(BuildingPlanSet planSet) {
      for (String variantKey : planSet.variants().keySet().stream().sorted().toList()) {
         List<BuildingPlanSet.LevelDef> levels = planSet.variants().get(variantKey);
         if (levels != null && !levels.isEmpty()) {
            BuildingPlanSet.LevelDef first = levels.stream().min(Comparator.comparingInt(BuildingPlanSet.LevelDef::level)).orElse(null);
            if (first != null) {
               BuildingPlan plan = ModCultures.getBuildingPlan(first.planId());
               if (plan != null) {
                  return plan;
               }
            }
         }
      }

      return null;
   }

   private static ResourceLocation resolveCultureId(String arg) {
      if (arg.contains(":")) {
         ResourceLocation parsed = ResourceLocation.tryParse(arg);
         return parsed != null ? parsed : ResourceLocation.fromNamespaceAndPath("millenaire", arg);
      } else {
         return ResourceLocation.fromNamespaceAndPath("millenaire", arg);
      }
   }

   private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      return (ServerPlayer)(player != null ? player : FakePlayerFactory.getMinecraft(((CommandSourceStack)ctx.getSource()).getLevel()));
   }

   private static Integer getOptionalInt(CommandContext<CommandSourceStack> ctx, String name) {
      try {
         return IntegerArgumentType.getInteger(ctx, name);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private record SubBandResult(int subsPlaced, int subTablesPlaced, int failed) {
      static final ImportCultureCommand.SubBandResult EMPTY = new ImportCultureCommand.SubBandResult(0, 0, 0);
   }
}
