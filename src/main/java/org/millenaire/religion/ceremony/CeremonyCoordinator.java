package org.millenaire.religion.ceremony;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.civilization.interaction.FestivalWindow;
import org.millenaire.civilization.interaction.InteractionSavedData;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.religion.ReligionService;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.CeremonyState;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.model.RitualKind;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Plans and advances ceremony state machines. Does not replace GoalScheduler.
 */
public final class CeremonyCoordinator {
   private static final Logger LOGGER = LogUtils.getLogger();

   public CeremonyCoordinator() {
   }

   public void tickVillage(ServerLevel level, Village village) {
      NpcFaithService.ensureVillageCommunity(level, village);
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());

      // Advance active ceremony first.
      if (community.activeCeremonyId() != null) {
         CeremonyInstance active = data.getCeremony(community.activeCeremonyId());
         if (active != null && !active.state().isTerminal()) {
            advance(level, village, active, community);
            data.setDirty();
            return;
         }
         community.setActiveCeremonyId(null);
      }

      if (village.isUnderAttack()) {
         return;
      }

      // Initialize resident faith profiles opportunistically (budgeted).
      int inited = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (inited >= 4) {
            break;
         }
         if (!record.isKilled()) {
            NpcFaithService.ensureInitialized(level, village, record);
            inited++;
         }
      }

      maybePlan(level, village, community);
      data.setDirty();
   }

   private void maybePlan(ServerLevel level, Village village, VillageFaithCommunity community) {
      if (community.traditionIds().isEmpty()) {
         return;
      }
      long day = ReligionService.get().calendar().dayNumber(level);
      RitualDefinition candidate = selectCandidate(level, village, community, day);
      if (candidate == null) {
         return;
      }
      if (!ReligionService.get().policy().allowsCommunal(community, candidate.traditionId())) {
         return;
      }
      BuildingInstance location = findLocation(village, community, candidate);
      if (location == null || !location.isOperational()) {
         return;
      }
      VillagerRecord leader = null;
      if (candidate.requiresLeader()) {
         leader = ReligionService.get().clergy().findLeader(level, village, candidate.traditionId(), candidate.requiredLeaderRoles());
         if (leader == null) {
            return;
         }
      }
      if (!ReligionService.get().supplies().hasRequiredSupplies(level, village, candidate)
         && !candidate.supplies().isEmpty()) {
         // Stay in planning domain via deferred marker in history; Living request bridge may open later.
         return;
      }

      CeremonyInstance ceremony = new CeremonyInstance(
         UUID.randomUUID(),
         village.getId().uuid(),
         candidate.id(),
         candidate.traditionId(),
         level.getGameTime()
      );
      ceremony.setBuildingId(location.getId());
      if (leader != null) {
         ceremony.setLeaderVillagerId(leader.getUuid());
      }
      ceremony.setPlannedStartGameTime(level.getGameTime());
      ceremony.setState(CeremonyState.PREPARING);
      if (candidate.alignWithFestival()) {
         String link = activeFestivalLink(level, village);
         ceremony.setFestivalLink(link);
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      data.putCeremony(ceremony);
      community.setActiveCeremonyId(ceremony.ceremonyId());
      community.addHistory("planned:" + candidate.id() + ":" + ceremony.ceremonyId());
      if (debug()) {
         LOGGER.debug("[Religion] Planned {} in village {}", candidate.id(), village.getVillageName());
      }
   }

   @Nullable
   private RitualDefinition selectCandidate(ServerLevel level, Village village, VillageFaithCommunity community, long day) {
      List<RitualDefinition> options = new ArrayList<>();
      for (ResourceLocation tid : community.traditionIds()) {
         if (!ReligionService.get().policy().allowsCommunal(community, tid)) {
            continue;
         }
         var def = ReligionContentRegistry.traditionOrNull(tid);
         if (def == null) {
            continue;
         }
         collect(options, def.serviceRitualIds(), level, community, day);
         collect(options, def.feastRitualIds(), level, community, day);
         // Event-driven memorials / consecrations are planned via hooks, not calendar spam.
      }
      if (options.isEmpty()) {
         // Catch-up at most once per day after time skip.
         if (community.lastCatchupDay() != day) {
            community.setLastCatchupDay(day);
         }
         return null;
      }
      // Deterministic pick: highest priority then id.
      options.sort((a, b) -> {
         int c = Integer.compare(b.priority(), a.priority());
         return c != 0 ? c : a.id().compareTo(b.id());
      });
      return options.get(0);
   }

   private void collect(List<RitualDefinition> out, List<ResourceLocation> ids, ServerLevel level, VillageFaithCommunity community, long day) {
      var cal = ReligionService.get().calendar();
      for (ResourceLocation id : ids) {
         RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(id);
         if (ritual == null) {
            continue;
         }
         if (!cal.cooldownElapsed(community.lastCeremonyDay(), ritual, day)) {
            continue;
         }
         if (!cal.matchesRecurringDay(level, ritual)) {
            continue;
         }
         if (!cal.withinHourWindow(level, ritual)) {
            continue;
         }
         if (ritual.alignWithFestival() && activeFestivalLink(level, null) == null) {
            // Feasts prefer festival windows when configured; still allow if day_modulo matches.
            if (ritual.dayModulo() <= 0) {
               continue;
            }
         }
         out.add(ritual);
      }
   }

   private void advance(ServerLevel level, Village village, CeremonyInstance ceremony, VillageFaithCommunity community) {
      RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(ceremony.ritualId());
      if (ritual == null) {
         ceremony.setState(CeremonyState.CANCELLED);
         ceremony.setInterruptReason("missing_ritual");
         community.setActiveCeremonyId(null);
         return;
      }
      if (village.isUnderAttack()) {
         if (ceremony.state() == CeremonyState.GATHERING || ceremony.state() == CeremonyState.ACTIVE || ceremony.state() == CeremonyState.PREPARING) {
            ceremony.setState(CeremonyState.INTERRUPTED);
            ceremony.setInterruptReason("attack");
         }
         return;
      }
      if (ceremony.state() == CeremonyState.INTERRUPTED) {
         // Resume gathering if still valid.
         ceremony.setState(CeremonyState.GATHERING);
         ceremony.setGatherDeadlineGameTime(level.getGameTime() + ritual.gatherTicks());
      }

      switch (ceremony.state()) {
         case PREPARING -> {
            BuildingInstance building = resolveBuilding(village, ceremony);
            if (building == null || !building.isOperational()) {
               ceremony.setState(CeremonyState.DEFERRED);
               ceremony.setInterruptReason("missing_location");
               community.setActiveCeremonyId(null);
               return;
            }
            if (ritual.requiresLeader()) {
               VillagerRecord leader = ceremony.leaderVillagerId() != null
                  ? village.getVillagerRecords().get(ceremony.leaderVillagerId())
                  : null;
               if (leader == null || leader.isKilled()) {
                  leader = ReligionService.get().clergy().findLeader(level, village, ceremony.traditionId(), ritual.requiredLeaderRoles());
                  if (leader == null) {
                     ceremony.setState(CeremonyState.DEFERRED);
                     ceremony.setInterruptReason("missing_leader");
                     community.setActiveCeremonyId(null);
                     return;
                  }
                  ceremony.setLeaderVillagerId(leader.getUuid());
               }
            }
            if (!ReligionService.get().supplies().consumeForCeremony(level, village, ceremony, ritual)) {
               ceremony.setState(CeremonyState.DEFERRED);
               ceremony.setInterruptReason("missing_supplies");
               community.setActiveCeremonyId(null);
               return;
            }
            ceremony.setState(CeremonyState.GATHERING);
            ceremony.setGatherDeadlineGameTime(level.getGameTime() + ritual.gatherTicks());
         }
         case GATHERING -> {
            creditNearbyParticipants(level, village, ceremony, ritual);
            if (ceremony.arrivedParticipants().size() >= ritual.minParticipants()) {
               ceremony.setState(CeremonyState.ACTIVE);
               ceremony.setActiveEndGameTime(level.getGameTime() + ritual.activeTicks());
            } else if (level.getGameTime() >= ceremony.gatherDeadlineGameTime()) {
               if (ceremony.arrivedParticipants().size() >= Math.max(1, ritual.minParticipants() / 2)) {
                  // Soft success with reduced participation.
                  ceremony.setState(CeremonyState.ACTIVE);
                  ceremony.setActiveEndGameTime(level.getGameTime() + ritual.activeTicks());
               } else {
                  ceremony.setState(CeremonyState.CANCELLED);
                  ceremony.setInterruptReason("insufficient_participants");
                  community.setActiveCeremonyId(null);
               }
            }
         }
         case ACTIVE -> {
            creditNearbyParticipants(level, village, ceremony, ritual);
            if (level.getGameTime() >= ceremony.activeEndGameTime()) {
               // Credit arrived participants who haven't been credited.
               ceremony.creditedParticipants().addAll(ceremony.arrivedParticipants());
               if (ceremony.creditedParticipants().size() < Math.max(1, ritual.minParticipants() / 2)) {
                  ceremony.setState(CeremonyState.CANCELLED);
                  ceremony.setInterruptReason("insufficient_participants");
               } else {
                  ceremony.setState(CeremonyState.COMPLETED);
                  CeremonyEffects.applyIfNeeded(level, village, ceremony, ritual);
               }
               community.setActiveCeremonyId(null);
            }
         }
         default -> {
         }
      }
   }

   private void creditNearbyParticipants(ServerLevel level, Village village, CeremonyInstance ceremony, RitualDefinition ritual) {
      BuildingInstance building = resolveBuilding(village, ceremony);
      if (building == null) {
         return;
      }
      BlockPos center = building.getFirstPointPos("craftingPos");
      if (center == null) {
         center = building.getOrigin();
      }
      int cap = ritual.maxParticipants();
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            continue;
         }
         if (ceremony.arrivedParticipants().size() >= cap) {
            break;
         }
         NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, village, record);
         if (!profile.hasTradition(ceremony.traditionId())) {
            continue;
         }
         if (profile.primaryAffiliation() != null && profile.primaryAffiliation().devotion() < 8) {
            continue;
         }
         var entity = level.getEntity(record.getUuid());
         if (entity == null) {
            continue;
         }
         if (entity.blockPosition().distSqr(center) <= 64.0) {
            ceremony.arrivedParticipants().add(record.getUuid());
         }
      }
      // Leader always counts if present in village records.
      if (ceremony.leaderVillagerId() != null) {
         ceremony.arrivedParticipants().add(ceremony.leaderVillagerId());
      }
   }

   @Nullable
   private BuildingInstance resolveBuilding(Village village, CeremonyInstance ceremony) {
      if (ceremony.buildingId() == null) {
         return null;
      }
      return village.getBuilding(ceremony.buildingId());
   }

   @Nullable
   private BuildingInstance findLocation(Village village, VillageFaithCommunity community, RitualDefinition ritual) {
      for (String tag : ritual.locationTags()) {
         for (BuildingInstance b : village.getOperationalBuildingsWithTag(tag)) {
            return b;
         }
      }
      for (BuildingId id : community.designatedShrines()) {
         BuildingInstance b = village.getBuilding(id);
         if (b != null && b.isOperational()) {
            return b;
         }
      }
      // SHRINE purpose designated buildings
      for (BuildingInstance b : village.getBuildings()) {
         if (b.getPurpose() == org.millenaire.building.BuildingPurpose.SHRINE && b.isOperational()) {
            return b;
         }
      }
      return null;
   }

   @Nullable
   private String activeFestivalLink(ServerLevel level, @Nullable Village village) {
      try {
         long now = level.getGameTime();
         for (FestivalWindow f : InteractionSavedData.get(level).festivals()) {
            if (f.isActive(now)) {
               if (village != null && f.hostVillage() != null && !f.hostVillage().isEmpty()) {
                  if (!f.hostVillage().equals(village.getId().uuid().toString())
                     && !f.hostVillage().equalsIgnoreCase(village.getVillageName())) {
                     continue;
                  }
               }
               return f.cultureId() + ":" + f.startGameTime();
            }
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   public void planEventCeremony(
      ServerLevel level,
      Village village,
      RitualKind kind,
      @Nullable ResourceLocation preferredTradition,
      @Nullable BuildingId buildingId
   ) {
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      if (community.activeCeremonyId() != null) {
         return;
      }
      long day = ReligionService.get().calendar().dayNumber(level);
      if (kind == RitualKind.MEMORIAL && community.lastMemorialDay() == day) {
         return;
      }
      ResourceLocation tradition = preferredTradition != null
         ? preferredTradition
         : community.hostTraditionId();
      if (tradition == null) {
         return;
      }
      var def = ReligionContentRegistry.traditionOrNull(tradition);
      if (def == null) {
         return;
      }
      List<ResourceLocation> ids = switch (kind) {
         case MEMORIAL -> def.memorialRitualIds();
         case CONSECRATION -> def.consecrationRitualIds();
         case FAMILY -> def.familyRitualIds();
         case PILGRIMAGE -> def.pilgrimageRitualIds();
         default -> List.of();
      };
      if (ids.isEmpty()) {
         return;
      }
      RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(ids.get(0));
      if (ritual == null) {
         return;
      }
      BuildingInstance location = buildingId != null ? village.getBuilding(buildingId) : findLocation(village, community, ritual);
      if (location == null) {
         return;
      }
      CeremonyInstance ceremony = new CeremonyInstance(
         UUID.randomUUID(), village.getId().uuid(), ritual.id(), tradition, level.getGameTime()
      );
      ceremony.setBuildingId(location.getId());
      ceremony.setPlannedStartGameTime(level.getGameTime());
      ceremony.setState(CeremonyState.PREPARING);
      data.putCeremony(ceremony);
      community.setActiveCeremonyId(ceremony.ceremonyId());
      community.addHistory("event_planned:" + ritual.id());
      data.setDirty();
   }

   @Nullable
   public CeremonyInstance activeCeremony(ServerLevel level, Village village) {
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.getCommunity(village.getId().uuid());
      if (community == null || community.activeCeremonyId() == null) {
         return null;
      }
      return data.getCeremony(community.activeCeremonyId());
   }

   private static boolean debug() {
      try {
         return MillenaireServerConfig.SERVER.religionDebugLogging.get();
      } catch (Exception e) {
         return false;
      }
   }
}
