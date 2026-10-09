package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.DisplayUtils;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEvent;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

public final class VillageOverviewPanelGenerator {
   private static final int SUMMARY_CHRONICLE_LIMIT = 20;

   private VillageOverviewPanelGenerator() {
   }

   public static PanelContent generateSummary(Village village, @Nullable BuildingInstance building, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.village_summary";
      String[] titleArgs = new String[]{village.getVillageName()};
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType != null) {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.village_type_value", villageType.name()));
      }

      VillageOverviewPanelGenerator.PopulationCounts population = countPopulation(village);
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.population_value", String.valueOf(population.total())));
      lines.add(
         PanelLine.translatableWithArgs(
            "panel.millenaire.adults_value", String.valueOf(population.adults()), String.valueOf(population.men()), String.valueOf(population.women())
         )
      );
      appendCivilizationLines(lines, village, level);
      if (population.children() > 0) {
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.children_value", String.valueOf(population.children())));
      }

      lines.add(PanelLine.separator());
      BuildingInstance currentProject = null;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            currentProject = b;
            break;
         }
      }

      if (currentProject != null) {
         String projectNative = BuildingNameHelper.getServerFallbackName(currentProject);
         if (currentProject.getStatus() == BuildingInstance.Status.UPGRADING) {
            lines.add(
               PanelLine.translatableWithMixedArgs(
                  "panel.millenaire.building_project_upgrading",
                  1,
                  "panel.millenaire.building_project",
                  projectNative,
                  String.valueOf(currentProject.getLevel())
               )
            );
         } else {
            lines.add(
               PanelLine.translatableWithMixedArgs("panel.millenaire.building_project_constructing", 1, "panel.millenaire.building_project", projectNative)
            );
         }

         lines.add(PanelLine.text(""));
         if (currentProject.getPlanSetId() != null && currentProject.getVariant() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(currentProject.getPlanSetId());
            if (planSet != null) {
               BuildingPlanSet.LevelDef levelDef = planSet.getLevel(currentProject.getVariant(), currentProject.getLevel());
               if (levelDef != null && !levelDef.requiredResources().isEmpty()) {
                  lines.add(PanelLine.translatable("panel.millenaire.resources_needed"));
                  PanelContentGenerator.addResourceLines(lines, levelDef.requiredResources(), village, level);
               }
            }
         }
      } else {
         Village.PendingProject pending = PanelHelper.resolveDisplayProject(village);
         if (pending != null) {
            String pendingKey = PanelHelper.getPendingProjectKey(pending);
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.pending_project_value", 1, pendingKey));
            lines.add(PanelLine.translatable("panel.millenaire.awaiting_resources"));
         } else {
            lines.add(PanelLine.translatable("panel.millenaire.goals_completed"));
         }
      }

      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.current_constructions"));
      boolean anyConstruction = false;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            anyConstruction = true;
            String nameArg = BuildingNameHelper.getServerFallbackName(b);
            PanelHelper.DirectionInfo dir = PanelHelper.computeDirectionInfo(village.getCenter(), b.getOrigin());
            if (b.getStatus() == BuildingInstance.Status.UPGRADING) {
               if (dir.atCenter()) {
                  lines.add(
                     PanelLine.translatableWithMixedArgs("panel.millenaire.construction_line_upgrading_center", 0, nameArg, String.valueOf(b.getLevel()))
                  );
               } else {
                  lines.add(
                     PanelLine.translatableWithMixedArgs(
                        "panel.millenaire.construction_line_upgrading_dir",
                        8,
                        nameArg,
                        String.valueOf(b.getLevel()),
                        String.valueOf(dir.distance()),
                        dir.cardinalKey()
                     )
                  );
               }
            } else if (dir.atCenter()) {
               lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.construction_line_building_center", 0, nameArg));
            } else {
               lines.add(
                  PanelLine.translatableWithMixedArgs(
                     "panel.millenaire.construction_line_building_dir", 4, nameArg, String.valueOf(dir.distance()), dir.cardinalKey()
                  )
               );
            }
         }
      }

      if (!anyConstruction) {
         lines.add(PanelLine.translatable("panel.millenaire.no_construction"));
      }

      if (!village.getRelations().isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("panel.millenaire.diplomacy"));
         VillageManager vm = level != null ? VillageSavedData.get(level).getVillageManager() : null;

         for (Entry<VillageId, Integer> relEntry : village.getRelations().entrySet()) {
            Village other = vm != null ? vm.getVillage(relEntry.getKey()) : null;
            if (other != null
               && (village.getParentVillageId() == null || !village.getParentVillageId().equals(other.getId()))
               && (other.getParentVillageId() == null || !other.getParentVillageId().equals(village.getId()))) {
               String otherName = other.getVillageName() != null ? other.getVillageName() : other.getVillageTypeId().getPath();
               int rel = relEntry.getValue();
               String relKey = VillageRelations.getRelationKey(rel);
               String diplomacyKey;
               if (rel >= 50) {
                  diplomacyKey = "panel.millenaire.diplomacy_friendly";
               } else if (rel >= 0) {
                  diplomacyKey = "panel.millenaire.diplomacy_neutral";
               } else if (rel > -50) {
                  diplomacyKey = "panel.millenaire.diplomacy_cold";
               } else {
                  diplomacyKey = "panel.millenaire.diplomacy_hostile";
               }

               lines.add(PanelLine.translatableWithMixedArgs(diplomacyKey, 2, otherName, relKey));
            }
         }
      }

      appendChronicleLines(lines, village);
      return new PanelContent(PanelType.VILLAGE_SUMMARY, titleKey, lines, true, titleArgs);
   }

   private static void appendCivilizationLines(List<PanelLine> lines, Village village, @Nullable ServerLevel level) {
      if (level == null) {
         return;
      }
      org.millenaire.civilization.presentation.CivilizationVillageSnapshot snap =
         org.millenaire.civilization.presentation.CivilizationPresentationService.villageSnapshot(level, village, null);
      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("panel.millenaire.civilization"));
      if (snap.factionId() == null) {
         lines.add(PanelLine.translatable("panel.millenaire.civilization_independent"));
      } else {
         String factionLabel = snap.factionDisplayName().isBlank()
            ? Component.translatable("civilization.millenaire.faction.unnamed").getString()
            : snap.factionDisplayName();
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.civilization_faction", factionLabel));
         lines.add(PanelLine.translatable(snap.developmentLabelKey()));
      }
      lines.add(
         PanelLine.translatableWithArgs(
            "civilization.millenaire.overview.housing",
            String.valueOf(snap.residents()),
            String.valueOf(snap.housingCapacity())
         )
      );
      if (snap.overcrowded()) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.overcrowded"));
      }
      if (snap.inventoryCertainty() != org.millenaire.civilization.presentation.InventoryCertainty.KNOWN) {
         lines.add(PanelLine.translatable(snap.inventoryCertainty().labelKey()));
      }
      if (!snap.topNeeds().isEmpty()) {
         var need = snap.topNeeds().getFirst();
         String shortageText = need.shortageProven()
            ? String.valueOf(need.shortage())
            : String.valueOf(need.observedShortage());
         lines.add(PanelLine.translatableWithArgs(need.summaryKey(), need.detail(), need.severityCode(), shortageText));
      }
      if (snap.colonization() != null) {
         lines.add(PanelLine.translatable(snap.colonization().statusLabelKey()));
         lines.add(PanelLine.translatable(snap.colonization().reasonLabelKey()));
         lines.add(
            PanelLine.translatableWithArgs(
               "civilization.millenaire.overview.founders",
               String.valueOf(snap.colonization().migratedFounders()),
               String.valueOf(snap.colonization().founderTarget())
            )
         );
      }
      if (snap.connection() != null) {
         lines.add(
            PanelLine.translatableWithArgs(
               "civilization.millenaire.overview.connection",
               snap.connection().endpointName(),
               Component.translatable("civilization.millenaire.status." + snap.connection().statusCode()).getString()
            )
         );
      }
      lines.add(PanelLine.translatable("civilization.millenaire.overview.see_civilization_section"));
   }

   public static PanelContent generateCivilization(Village village, @Nullable ServerLevel level, @Nullable net.minecraft.server.level.ServerPlayer player) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.civilization";
      String[] titleArgs = new String[]{village.getVillageName()};
      if (level == null) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.unavailable"));
         return new PanelContent(PanelType.CIVILIZATION, titleKey, lines, true, titleArgs);
      }
      org.millenaire.civilization.presentation.CivilizationVillageSnapshot snap =
         org.millenaire.civilization.presentation.CivilizationPresentationService.villageSnapshot(level, village, player);
      if (snap.factionId() == null) {
         lines.add(PanelLine.translatable("panel.millenaire.civilization_independent"));
      } else {
         String factionLabel = snap.factionDisplayName().isBlank()
            ? Component.translatable("civilization.millenaire.faction.unnamed").getString()
            : snap.factionDisplayName();
         lines.add(PanelLine.translatableWithArgs("panel.millenaire.civilization_faction", factionLabel));
      }
      lines.add(PanelLine.translatable(snap.developmentLabelKey()));
      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("civilization.millenaire.section.population"));
      lines.add(
         PanelLine.translatableWithArgs(
            "civilization.millenaire.overview.housing",
            String.valueOf(snap.residents()),
            String.valueOf(snap.housingCapacity())
         )
      );
      if (!snap.housingCapacityKnown()) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.housing_capacity_unknown"));
      } else if (snap.overcrowded()) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.overcrowded"));
      }
      if (snap.inventoryCertainty() != org.millenaire.civilization.presentation.InventoryCertainty.KNOWN) {
         lines.add(PanelLine.translatable(snap.inventoryCertainty().labelKey()));
      }
      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("civilization.millenaire.section.needs"));
      if (snap.topNeeds().isEmpty()) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.no_needs"));
      } else {
         for (var need : snap.topNeeds()) {
            if (need.shortageProven()) {
               lines.add(PanelLine.translatableWithArgs(need.summaryKey(), need.detail(), need.severityCode(), String.valueOf(need.shortage())));
            } else {
               lines.add(PanelLine.translatableWithArgs(need.summaryKey(), need.detail(), need.severityCode(), String.valueOf(need.observedShortage())));
               lines.add(PanelLine.translatableWithArgs("civilization.millenaire.overview.shortage_unproven", String.valueOf(need.observedShortage())));
            }
         }
      }
      if (snap.currentProject() != null) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.building"));
         lines.add(PanelLine.translatableWithArgs("civilization.millenaire.overview.project", snap.currentProject().nameKeyOrLiteral()));
      }
      if (!snap.tradeLines().isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.trade"));
         for (var trade : snap.tradeLines()) {
            String kindKey = "civilization.millenaire.overview.trade_kind." + trade.kindCode();
            lines.add(PanelLine.translatable(kindKey));
            lines.add(
               PanelLine.translatableWithArgs(
                  "civilization.millenaire.overview.trade",
                  trade.itemLabel(),
                  Component.translatable("civilization.millenaire.status." + trade.statusCode()).getString()
               )
            );
            if ("trade_recovery".equals(trade.kindCode()) && "legacy_unrecoverable".equals(trade.statusCode())) {
               lines.add(PanelLine.translatable("civilization.millenaire.overview.legacy_trade_limited"));
            }
         }
      }
      if (!snap.migrations().isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.migration"));
         for (var migration : snap.migrations()) {
            lines.add(PanelLine.translatable("civilization.millenaire.overview.migration_kind." + migration.kindCode()));
            lines.add(
               PanelLine.translatableWithArgs(
                  "civilization.millenaire.overview.migration",
                  migration.originName(),
                  migration.destinationName(),
                  String.valueOf(migration.migrated()),
                  String.valueOf(migration.target())
               )
            );
            lines.add(PanelLine.translatable(org.millenaire.civilization.presentation.CivilizationStatusCodes.reasonLabelKey(migration.reasonCode())));
         }
      }
      if (snap.colonization() != null) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.colonization"));
         lines.add(PanelLine.translatable(snap.colonization().statusLabelKey()));
         lines.add(PanelLine.translatable(snap.colonization().reasonLabelKey()));
         lines.add(
            PanelLine.translatableWithArgs(
               "civilization.millenaire.overview.founders",
               String.valueOf(snap.colonization().migratedFounders()),
               String.valueOf(snap.colonization().founderTarget())
            )
         );
         lines.add(PanelLine.translatable(snap.colonization().nextStepKey()));
         if (snap.colonization().refundOutstanding()) {
            lines.add(PanelLine.translatable("civilization.millenaire.reason.refund_outstanding"));
         }
      } else if (!Boolean.TRUE.equals(org.millenaire.config.MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get())) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.colonization"));
         lines.add(PanelLine.translatable("civilization.millenaire.overview.colonization_off"));
      }
      if (snap.connection() != null) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.connections"));
         lines.add(
            PanelLine.translatableWithArgs(
               "civilization.millenaire.overview.connection",
               snap.connection().endpointName(),
               Component.translatable("civilization.millenaire.status." + snap.connection().statusCode()).getString()
            )
         );
         if (snap.connection().reasonCode() != null && !"none".equals(snap.connection().reasonCode()) && !snap.connection().reasonCode().isBlank()) {
            lines.add(PanelLine.translatable(org.millenaire.civilization.presentation.CivilizationStatusCodes.reasonLabelKey(snap.connection().reasonCode())));
         }
      }
      lines.add(PanelLine.separator());
      lines.add(PanelLine.translatable("civilization.millenaire.section.actions"));
      boolean anyAction = false;
      for (var action : snap.allowedActions()) {
         anyAction = true;
         String targetId = action.targetId() == null ? "" : action.targetId();
         if (action.enabled()) {
            lines.add(
               PanelLine.clickableTranslatable(
                  action.labelKey(),
                  new PanelLine.PanelNavTarget("civ_action", action.actionId().name(), targetId, "")
               )
            );
         } else {
            String blocked = action.blockedReasonKey() == null ? "" : action.blockedReasonKey();
            if (!blocked.isBlank()) {
               lines.add(
                  PanelLine.translatableWithMixedArgsColored(
                     "civilization.millenaire.action.blocked_format",
                     0xFF888888,
                     3,
                     action.labelKey(),
                     blocked
                  )
               );
            } else {
               lines.add(PanelLine.translatableColored(action.labelKey(), 0xFF888888));
            }
         }
      }
      if (!anyAction) {
         lines.add(PanelLine.translatable("civilization.millenaire.overview.no_actions"));
      }
      if (!snap.recentHistory().isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("civilization.millenaire.section.history"));
         for (var h : snap.recentHistory()) {
            lines.add(PanelLine.translatableWithArgs(h.typeKey(), h.summary()));
         }
      }
      return new PanelContent(PanelType.CIVILIZATION, titleKey, lines, true, titleArgs);
   }

   public static PanelContent generatePopulation(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.population";
      String[] titleArgs = new String[]{village.getVillageName()};
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();
      if (villagerTypes.isEmpty()) {
         lines.add(PanelLine.translatable("panel.millenaire.no_inhabitants"));
         return new PanelContent(PanelType.POPULATION, titleKey, lines, true, titleArgs);
      }

      for (Entry<UUID, ResourceLocation> entry : villagerTypes.entrySet()) {
         UUID uuid = entry.getKey();
         ResourceLocation typeId = entry.getValue();
         String roleKey = PanelHelper.resolveRoleKey(typeId);
         String displayName = "???";
         boolean entityLoaded = false;
         String goalLabel = null;
         boolean isMissing = false;
         boolean isAbsent = false;
         if (level != null) {
            if (level.getEntity(uuid) instanceof MillVillager mv) {
               entityLoaded = true;
               String first = mv.getFirstName();
               String family = mv.getFamilyName();
               if (first != null && !first.isEmpty()) {
                  displayName = first + (family != null && !family.isEmpty() ? " " + family : "");
               }

               goalLabel = mv.getGoalLabel();
            } else {
               int missing = village.getMissingCount(uuid);
               if (missing >= 3) {
                  isMissing = true;
               } else {
                  isAbsent = true;
               }
            }
         }

         VillagerType vtype = ModCultures.getVillagerType(typeId);
         String iconItem = vtype != null && vtype.icon() != null ? vtype.icon() : "";
         PanelLine.PanelNavTarget nav = PanelHelper.villagerNavTarget(typeId);
         String nameText = "§0" + displayName;
         if (!iconItem.isEmpty()) {
            if (nav != null) {
               lines.add(PanelLine.clickableWithIcon(nameText, "", iconItem, nav));
            } else {
               lines.add(PanelLine.withIcon(nameText, "", iconItem));
            }
         } else if (nav != null) {
            lines.add(PanelLine.clickableText(nameText, nav));
         } else {
            lines.add(PanelLine.text(nameText));
         }

         VillagerRecord record = village.getVillagerRecord(uuid);
         boolean awayHired = record != null && record.isAwayHired();
         if (awayHired) {
            lines.add(
               PanelLine.translatableWithMixedArgs(
                  "panel.millenaire.role_and_activity", 1, roleKey, Component.translatable("panel.millenaire.awayhired").getString()
               )
            );
         } else if (isMissing) {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.missing_villager_status", 1, roleKey));
         } else if (isAbsent) {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.absent_status", 1, roleKey));
         } else if (goalLabel != null && !goalLabel.isEmpty()) {
            int argMask = DisplayUtils.isGoalTranslationKey(goalLabel) ? 3 : 1;
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.villager_goal", argMask, roleKey, goalLabel));
         } else {
            lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.role_and_activity", 1, roleKey, ""));
         }
      }

      return new PanelContent(PanelType.POPULATION, titleKey, lines, true, titleArgs);
   }

   public static void appendChronicleLines(List<PanelLine> lines, Village village) {
      List<VillageEvent> events = village.getChronicle();
      if (!events.isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatable("gui.millenaire.book.section.chronicle"));
         int start = Math.max(0, events.size() - 20);

         for (int i = events.size() - 1; i >= start; i--) {
            VillageEvent event = events.get(i);
            long dayNumber = event.gameTime() / 24000L + 1L;
            String dayStr = String.valueOf(dayNumber);
            if (event.param2() != null) {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1(), event.param2()));
            } else {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1()));
            }
         }
      }
   }

   public static PanelContent generateChronicle(Village village) {
      List<PanelLine> lines = new ArrayList<>();
      List<VillageEvent> events = village.getChronicle();
      if (events.isEmpty()) {
         lines.add(PanelLine.translatable("chronicle.millenaire.empty"));
      } else {
         for (int i = events.size() - 1; i >= 0; i--) {
            VillageEvent event = events.get(i);
            long dayNumber = event.gameTime() / 24000L + 1L;
            String dayStr = String.valueOf(dayNumber);
            if (event.param2() != null) {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1(), event.param2()));
            } else {
               lines.add(PanelLine.translatableWithArgs(event.type().i18nKey(), dayStr, event.param1()));
            }
         }
      }

      return new PanelContent(PanelType.CHRONICLE, "panel.millenaire.panel_type.chronicle", lines, true, null);
   }

   private static VillageOverviewPanelGenerator.PopulationCounts countPopulation(Village village) {
      Map<UUID, ResourceLocation> villagerTypes = village.getVillagerTypes();
      int nbMen = 0;
      int nbWomen = 0;
      int nbChildren = 0;

      for (ResourceLocation typeId : villagerTypes.values()) {
         VillagerType vType = ModCultures.getVillagerType(typeId);
         if (vType != null) {
            if (vType.isChild()) {
               nbChildren++;
            } else if (vType.gender() == Gender.FEMALE) {
               nbWomen++;
            } else {
               nbMen++;
            }
         }
      }

      return new VillageOverviewPanelGenerator.PopulationCounts(villagerTypes.size(), nbMen, nbWomen, nbChildren);
   }

   static void addSummary3D(PanelContentGenerator.DisplayData data, Village village) {
      String banner = "minecraft:white_banner";
      data.addCenteredWithIcons(village.getVillageName(), banner, banner);
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (villageType != null) {
         data.addCentered(villageType.name());
      }

      data.addCentered("");
      data.addCentered(PanelHelper.t("panel.millenaire.population") + ": " + village.getVillagerUuids().size());
      data.addCentered("");
   }

   static void addPopulation3D(PanelContentGenerator.DisplayData data, Village village) {
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.population_title"), "minecraft:blue_orchid", "minecraft:pink_tulip");
      data.addCentered("");
      VillageOverviewPanelGenerator.PopulationCounts population = countPopulation(village);
      data.addCentered(PanelHelper.t("panel.millenaire.adults") + ": " + population.adults() + " (" + population.men() + "M / " + population.women() + "F)");
      if (population.children() > 0) {
         data.addCentered(PanelHelper.t("panel.millenaire.children_section") + ": " + population.children());
      }
   }

   private record PopulationCounts(int total, int men, int women, int children) {
      int adults() {
         return this.men + this.women;
      }
   }
}
