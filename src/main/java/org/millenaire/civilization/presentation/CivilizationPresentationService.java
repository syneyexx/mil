package org.millenaire.civilization.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.AgreementStatus;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsQueries;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.living.requests.VillageRequest;
import org.millenaire.living.requests.VillageRequestSavedData;
import org.millenaire.living.requests.VillageRequestState;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.worldgen.net.RegionalRoadMaterializer;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;

/**
 * Builds bounded, server-derived civilization snapshots for UI, chronicle and NPC context.
 * Never force-loads chunks. Unknown inventory is not reported as zero stock.
 */
public final class CivilizationPresentationService {
   private static final int MAX_NEEDS = 5;
   private static final int MAX_HISTORY = 8;
   private static final int MAX_SETTLEMENT_NAMES = 12;

   private CivilizationPresentationService() {
   }

   public static CivilizationVillageSnapshot villageSnapshot(
      ServerLevel level,
      Village village,
      @Nullable ServerPlayer viewer
   ) {
      long now = level.getGameTime();
      PopulationSnapshot pop = PopulationAggregator.aggregateVillage(village, now);
      boolean overcrowded = pop.housingCapacity() > 0 && pop.residents() > pop.housingCapacity();

      FactionId factionId = village.getFactionId();
      String factionDisplay = factionDisplayName(level, factionId);
      String development = CivilizationSavedData.get(level).getDevelopmentLabel(village.getId().uuid());
      if (development == null || development.isBlank()) {
         development = "unknown";
      }
      String developmentKey = "civilization.millenaire.development." + development.toLowerCase(Locale.ROOT);

      boolean inventoryKnown = hasLoadedInventoryChunk(level, village);

      List<CivilizationVillageSnapshot.NeedLine> needs = new ArrayList<>();
      VillageNeedsSnapshot needSnap = LivingVillagesService.get().needsEngine().evaluate(level, village, false);
      if (needSnap != null) {
         for (VillageNeed need : needSnap.needs()) {
            if (needs.size() >= MAX_NEEDS) {
               break;
            }
            if (need.shortage().isEmpty()) {
               continue;
            }
            needs.add(new CivilizationVillageSnapshot.NeedLine(
               need.needId(),
               need.summaryKey(),
               need.summaryDetail(),
               need.totalShortage(),
               need.severity().name().toLowerCase(Locale.ROOT)
            ));
         }
      }

      CivilizationVillageSnapshot.ProjectLine project = resolveProject(village);
      CivilizationVillageSnapshot.TradeLine trade = resolveTrade(level, village);
      CivilizationVillageSnapshot.MigrationLine migration = resolveMigration(level, village);
      CivilizationVillageSnapshot.ColonizationLine colonization = resolveColonization(level, village);
      CivilizationVillageSnapshot.ConnectionLine connection = resolveConnection(level, village);
      List<CivilizationVillageSnapshot.HistoryLine> history = resolveHistory(level, village);
      List<CivilizationVillageSnapshot.AllowedAction> actions = resolveActions(level, village, viewer, needSnap, colonization);

      return new CivilizationVillageSnapshot(
         village.getId().uuid(),
         villageName(village),
         factionId == null ? null : factionId.asString(),
         factionDisplay,
         development,
         developmentKey,
         pop.residents(),
         pop.housingCapacity(),
         overcrowded,
         inventoryKnown,
         List.copyOf(needs),
         project,
         trade,
         migration,
         colonization,
         connection,
         List.copyOf(history),
         List.copyOf(actions),
         now
      );
   }

   public static CivilizationFactionSnapshot factionSnapshot(ServerLevel level, FactionId factionId) {
      long now = level.getGameTime();
      Faction faction = factionId == null ? null : FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      String display = faction != null ? faction.getDisplayName() : factionDisplayName(level, factionId);
      PoliticalLeader leader = factionId == null ? null : PoliticsQueries.leader(level, factionId);
      String leaderName = leader != null && leader.getDisplayName() != null && !leader.getDisplayName().isBlank()
         ? leader.getDisplayName()
         : null;

      int residents = 0;
      int housing = 0;
      List<String> settlementNames = new ArrayList<>();
      int colonizations = 0;
      if (faction != null) {
         for (VillageId vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v == null) {
               continue;
            }
            PopulationSnapshot pop = PopulationAggregator.aggregateVillage(v, now);
            residents += pop.residents();
            housing += pop.housingCapacity();
            if (settlementNames.size() < MAX_SETTLEMENT_NAMES) {
               settlementNames.add(villageName(v));
            }
            ColonizationOperation op = ColonizationService.findActiveForParent(level, vid.uuid());
            if (op != null && op.isActive()) {
               colonizations++;
            }
         }
      }

      FactionEconomicProfile profile = factionId == null
         ? null
         : EconomySavedData.get(level).getTradeManager().getProfile(factionId);
      String economyKey = profile == null
         ? "civilization.millenaire.economy.unknown"
         : "civilization.millenaire.economy." + profile.getBalanceLevel().name().toLowerCase(Locale.ROOT);

      boolean atWar = factionId != null && WarSavedData.get(level).getWarManager().isAtWar(factionId);
      int treaties = 0;
      if (factionId != null) {
         for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getAllAgreements()) {
            if (agreement.getStatus() != AgreementStatus.ACTIVE) {
               continue;
            }
            if (factionId.equals(agreement.getPrimaryFactionId()) || factionId.equals(agreement.getSecondaryFactionId())) {
               treaties++;
            }
         }
      }

      List<CivilizationVillageSnapshot.HistoryLine> events = new ArrayList<>();
      // Bound faction history via first settlement journal if present.
      if (faction != null) {
         for (VillageId vid : faction.getSettlementIds()) {
            Village v = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (v == null) {
               continue;
            }
            events.addAll(resolveHistory(level, v));
            break;
         }
      }

      return new CivilizationFactionSnapshot(
         factionId == null ? "" : factionId.asString(),
         display,
         leaderName,
         faction == null ? 0 : faction.getSettlementCount(),
         residents,
         housing,
         housing > 0 && residents > housing,
         economyKey,
         atWar,
         treaties,
         colonizations,
         0,
         List.copyOf(settlementNames),
         List.copyOf(events.subList(0, Math.min(MAX_HISTORY, events.size()))),
         now
      );
   }

   public static String factionDisplayName(ServerLevel level, @Nullable FactionId factionId) {
      if (factionId == null) {
         return "";
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction != null && faction.getDisplayName() != null && !faction.getDisplayName().isBlank()) {
         return faction.getDisplayName();
      }
      return "";
   }

   public static String factionDisplayOrFallback(ServerLevel level, @Nullable FactionId factionId) {
      String name = factionDisplayName(level, factionId);
      return name.isBlank() ? null : name;
   }

   private static String villageName(Village village) {
      String name = village.getVillageName();
      return name == null || name.isBlank() ? "???" : name;
   }

   private static boolean hasLoadedInventoryChunk(ServerLevel level, Village village) {
      if (village.getCenter() == null) {
         return false;
      }
      return level.hasChunk(village.getCenter().getX() >> 4, village.getCenter().getZ() >> 4);
   }

   @Nullable
   private static CivilizationVillageSnapshot.ProjectLine resolveProject(Village village) {
      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt()) {
            String name = BuildingNameHelper.getServerFallbackName(b);
            return new CivilizationVillageSnapshot.ProjectLine(
               name,
               b.getStatus() == BuildingInstance.Status.UPGRADING,
               b.getLevel(),
               b.getStatus().name().toLowerCase(Locale.ROOT)
            );
         }
      }
      ControlledQueuedProject head = village.controlledQueueHead();
      if (head != null) {
         return new CivilizationVillageSnapshot.ProjectLine(
            head.planSetId() == null ? "project" : head.planSetId().toString(),
            false,
            head.level(),
            "queued"
         );
      }
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null && pending.planSetId() != null) {
         return new CivilizationVillageSnapshot.ProjectLine(
            pending.planSetId().toString(),
            pending.isUpgrade(),
            pending.level(),
            "pending_resources"
         );
      }
      return null;
   }

   @Nullable
   private static CivilizationVillageSnapshot.TradeLine resolveTrade(ServerLevel level, Village village) {
      VillageRequest active = null;
      for (VillageRequest req : VillageRequestSavedData.get(level).forVillage(village.getId().uuid())) {
         if (req.state() == VillageRequestState.ACCEPTED
            || req.state() == VillageRequestState.IN_PROGRESS
            || req.state() == VillageRequestState.OFFERED) {
            active = req;
            break;
         }
      }
      if (active == null) {
         return null;
      }
      String itemLabel = active.required().isEmpty()
         ? active.type().name().toLowerCase(Locale.ROOT)
         : active.required().entrySet().iterator().next().getKey().toString();
      return new CivilizationVillageSnapshot.TradeLine(
         active.state().name().toLowerCase(Locale.ROOT),
         villageName(village),
         itemLabel,
         "inbound_support",
         active.lastUpdateGameTime()
      );
   }

   @Nullable
   private static CivilizationVillageSnapshot.MigrationLine resolveMigration(ServerLevel level, Village village) {
      ColonizationOperation op = ColonizationService.findActiveForParent(level, village.getId().uuid());
      if (op == null || (op.status() != ColonizationOperation.Status.MIGRATING && op.status() != ColonizationOperation.Status.ACTIVATED)) {
         return null;
      }
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      String dest = plan != null && plan.villageUuid() != null
         ? villageNameOrFallback(level, plan.villageUuid(), op.targetCenter().toShortString())
         : op.targetCenter().toShortString();
      return new CivilizationVillageSnapshot.MigrationLine(
         op.statusCode(),
         CivilizationStatusCodes.normalizeReason(op.reason()),
         villageName(village),
         dest,
         op.migratedCount(),
         op.founderTarget()
      );
   }

   @Nullable
   private static CivilizationVillageSnapshot.ColonizationLine resolveColonization(ServerLevel level, Village village) {
      ColonizationOperation op = ColonizationService.findActiveForParent(level, village.getId().uuid());
      if (op == null) {
         // Also show when this village is the child colony.
         for (ColonizationOperation candidate : ColonizationService.listForVillage(level, village.getId().uuid(), 4)) {
            op = candidate;
            break;
         }
      }
      if (op == null) {
         return null;
      }
      String reasonCode = CivilizationStatusCodes.normalizeReason(op.reason());
      return new CivilizationVillageSnapshot.ColonizationLine(
         op.operationId(),
         op.statusCode(),
         reasonCode,
         CivilizationStatusCodes.statusLabelKey(op.statusCode()),
         CivilizationStatusCodes.reasonLabelKey(reasonCode),
         CivilizationStatusCodes.nextStepKey(reasonCode),
         op.migratedCount(),
         op.founderTarget(),
         !op.refundOutstanding().isEmpty(),
         op.resourcesReserved(),
         op.costsConsumed(),
         op.targetCenter().toShortString()
      );
   }

   @Nullable
   private static CivilizationVillageSnapshot.ConnectionLine resolveConnection(ServerLevel level, Village village) {
      RegionalRoadMaterializer.RouteProgress progress = RegionalRoadMaterializer.findProgressForVillage(level, village);
      if (progress == null) {
         return null;
      }
      UUID selfPlan = null;
      for (SettlementPlan plan : SettlementPlanSavedData.get(level).allPlans()) {
         if (village.getId().uuid().equals(plan.villageUuid())) {
            selfPlan = plan.planId();
            break;
         }
      }
      return new CivilizationVillageSnapshot.ConnectionLine(
         progress.status.name().toLowerCase(Locale.ROOT),
         RegionalRoadMaterializer.endpointLabel(level, progress, selfPlan),
         CivilizationStatusCodes.normalizeReason(progress.blockReason)
      );
   }

   private static List<CivilizationVillageSnapshot.HistoryLine> resolveHistory(ServerLevel level, Village village) {
      List<CivilizationVillageSnapshot.HistoryLine> out = new ArrayList<>();
      List<LivingEvent> events = LivingEventService.recentPublic(level, village.getId(), MAX_HISTORY);
      for (LivingEvent event : events) {
         out.add(new CivilizationVillageSnapshot.HistoryLine(
            event.eventType().i18nKey(),
            event.details() == null ? "" : event.details(),
            event.gameTime()
         ));
      }
      return out;
   }

   private static List<CivilizationVillageSnapshot.AllowedAction> resolveActions(
      ServerLevel level,
      Village village,
      @Nullable ServerPlayer viewer,
      @Nullable VillageNeedsSnapshot needSnap,
      @Nullable CivilizationVillageSnapshot.ColonizationLine colonization
   ) {
      List<CivilizationVillageSnapshot.AllowedAction> actions = new ArrayList<>();
      boolean controlled = viewer != null && village.isControlledBy(viewer.getUUID());
      boolean civilizationOn = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationEvolutionEnabled.get());

      VillageNeed topNeed = needSnap == null ? null : needSnap.mostSevere().orElse(null);
      boolean hasNeed = topNeed != null && !topNeed.shortage().isEmpty();
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SUPPORT_NEED,
         hasNeed,
         CivilizationActionId.SUPPORT_NEED.i18nKey(),
         hasNeed ? "" : "civilization.millenaire.action.blocked.no_need",
         hasNeed ? topNeed.needId() : null
      ));

      ControlledQueuedProject housing = findHousingProject(village);
      boolean canPrioritize = controlled && housing != null;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.PRIORITIZE_HOUSING,
         canPrioritize,
         CivilizationActionId.PRIORITIZE_HOUSING.i18nKey(),
         !controlled
            ? "civilization.millenaire.action.blocked.not_controller"
            : housing == null ? "civilization.millenaire.action.blocked.no_housing_project" : "",
         housing == null ? null : housing.planSetId().toString()
      ));

      boolean foundingEnabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get());
      boolean hasColonization = colonization != null;
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.VIEW_COLONIZATION,
         civilizationOn && (hasColonization || foundingEnabled),
         CivilizationActionId.VIEW_COLONIZATION.i18nKey(),
         !civilizationOn
            ? "civilization.millenaire.action.blocked.civilization_off"
            : !hasColonization && !foundingEnabled
               ? "civilization.millenaire.action.blocked.colonization_off"
               : "",
         hasColonization ? colonization.operationId().toString() : null
      ));

      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS,
         hasColonization && hasNeed,
         CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS.i18nKey(),
         !hasColonization
            ? "civilization.millenaire.action.blocked.no_colonization"
            : !hasNeed ? "civilization.millenaire.action.blocked.no_need" : "",
         hasNeed ? topNeed.needId() : null
      ));

      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.AUTHORIZE_COLONIZATION,
         controlled && foundingEnabled && hasColonization,
         CivilizationActionId.AUTHORIZE_COLONIZATION.i18nKey(),
         !controlled
            ? "civilization.millenaire.action.blocked.not_controller"
            : !foundingEnabled
               ? "civilization.millenaire.action.blocked.colonization_off"
               : !hasColonization ? "civilization.millenaire.action.blocked.no_colonization" : "",
         hasColonization ? colonization.operationId().toString() : null
      ));

      boolean interrupted = colonization != null
         && ("failed".equals(colonization.statusCode())
            || "cancelled".equals(colonization.statusCode())
            || colonization.refundOutstanding()
            || "chunk_not_loaded".equals(colonization.reasonCode())
            || "refund_outstanding".equals(colonization.reasonCode()));
      actions.add(new CivilizationVillageSnapshot.AllowedAction(
         CivilizationActionId.VIEW_INTERRUPTED_OPERATION,
         interrupted,
         CivilizationActionId.VIEW_INTERRUPTED_OPERATION.i18nKey(),
         interrupted ? "" : "civilization.millenaire.action.blocked.no_interrupted",
         interrupted ? colonization.operationId().toString() : null
      ));

      return actions;
   }

   @Nullable
   private static ControlledQueuedProject findHousingProject(Village village) {
      List<ControlledQueuedProject> queue = village.getControlledQueue();
      for (int i = 0; i < queue.size(); i++) {
         ControlledQueuedProject p = queue.get(i);
         if (p.planSetId() == null) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(p.planSetId());
         if (set != null && set.isResidential()) {
            return p;
         }
      }
      return null;
   }

   private static String villageNameOrFallback(ServerLevel level, UUID villageUuid, String fallback) {
      Village v = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
      return v == null ? fallback : villageName(v);
   }
}
