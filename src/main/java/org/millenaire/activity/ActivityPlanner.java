package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.diplomacy.TreatyService;
import org.millenaire.village.CultureStandingSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/** Pure scoring/selection on bounded snapshots. Runs on the server thread in small steps. */
public final class ActivityPlanner {
   private ActivityPlanner() {}

   public record Proposal(
      ActivityKind kind,
      UUID sourceId,
      boolean sourceIsCamp,
      @Nullable UUID targetSettlementId,
      @Nullable UUID targetOperationId,
      String profileId,
      String organizationId,
      String hostilityReason,
      BlockPos assemblePos,
      List<BlockPos> route,
      List<UUID> candidateMembers,
      double score
   ) {}

   public static List<Proposal> planSlice(
      ServerLevel level,
      ActivitySavedData data,
      ActivitySpatialIndex index,
      ActivityAdmissionController admission,
      int fairnessCursor,
      int maxCandidates
   ) {
      List<Proposal> out = new ArrayList<>();
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      List<Village> villages = new ArrayList<>(vm.getAllVillages());
      if (villages.isEmpty()) {
         return out;
      }
      int start = Math.floorMod(fairnessCursor, villages.size());
      long gameTime = level.getGameTime();
      int examined = 0;
      for (int i = 0; i < villages.size() && examined < maxCandidates && out.size() < 4; i++) {
         Village village = villages.get((start + i) % villages.size());
         if (!admission.trySpendCandidate()) {
            break;
         }
         examined++;
         if (!isPlayerRelevant(level, village.getCenter()) && !hasOngoingFrom(data, village.getId().uuid())) {
            // New visible missions need player interest; camps may still propose if another loaded group is nearby —
            // keep conservative: require nearby non-spectator player for NEW proposals.
            if (!hasNearbyActivatingPlayer(level, village.getCenter())) {
               continue;
            }
         }
         Proposal p = proposeForVillage(level, data, index, admission, village, gameTime);
         if (p != null) {
            out.add(p);
         }
      }
      return out;
   }

   private static boolean hasOngoingFrom(ActivitySavedData data, UUID sourceId) {
      for (ActivityOperation op : data.nonTerminalOperations()) {
         if (sourceId.equals(op.sourceId())) {
            return true;
         }
      }
      return false;
   }

   private static boolean hasNearbyActivatingPlayer(ServerLevel level, BlockPos center) {
      int interest = MillenaireServerConfig.SERVER.regionalActivityInterestRadius.getAsInt();
      double interestSq = (double) interest * interest;
      boolean allowCreative = MillenaireServerConfig.SERVER.regionalActivityCreativeActivates.getAsBoolean();
      boolean spectatorsActivate = MillenaireServerConfig.SERVER.regionalActivitySpectatorActivates.getAsBoolean();
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator() && !spectatorsActivate) {
            continue;
         }
         if (p.isCreative() && !allowCreative) {
            // Creative may SEE activity but by default still can activate (spec: creative may see; only spectator doesn't activate)
            // Config default allowCreative=true.
         }
         if (p.blockPosition().distSqr(center) <= interestSq) {
            return true;
         }
      }
      return false;
   }

   private static boolean isPlayerRelevant(ServerLevel level, BlockPos center) {
      return hasNearbyActivatingPlayer(level, center);
   }

   @Nullable
   private static Proposal proposeForVillage(
      ServerLevel level, ActivitySavedData data, ActivitySpatialIndex index, ActivityAdmissionController admission, Village village, long gameTime
   ) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      if (vt == null) {
         return null;
      }
      boolean camp = vt.loneBuilding() && (vt.carriesRaid() || "illager_camp".equals(vt.specialType()));
      ResourceLocation culture = village.getCultureId();
      List<ActivityProfile> profiles = ActivityProfileLoader.forCulture(culture);
      if (profiles.isEmpty()) {
         return null;
      }
      String sourceKey = village.getId().uuid().toString();
      if (data.sourceOnCooldown(sourceKey, gameTime)) {
         admission.setLastDefer(ActivityReasonCode.COOLDOWN);
         return null;
      }
      // Prefer hunt/patrol for villages; bandit kinds for camps
      List<ActivityProfile> ordered = new ArrayList<>(profiles);
      ordered.sort(Comparator.comparingInt(p -> camp ? (p.bandit() ? 0 : 1) : (p.bandit() ? 2 : 0)));
      for (ActivityProfile profile : ordered) {
         if (camp && !profile.bandit()) {
            continue;
         }
         if (!camp && profile.bandit()) {
            continue;
         }
         for (ActivityKind kind : profile.allowedKinds()) {
            if (kind == ActivityKind.RESPONSE || kind == ActivityKind.SKIRMISH || kind == ActivityKind.AMBUSH) {
               continue; // produced by combat bridge / encounters
            }
            if (!kindEnabled(kind)) {
               continue;
            }
            String coalesceKey = sourceKey + ":" + kind.name();
            if (!data.tryCoalesceProposal(coalesceKey, gameTime)) {
               continue;
            }
            Proposal p = buildProposal(level, data, index, admission, village, camp, profile, kind, gameTime);
            if (p != null) {
               return p;
            }
            data.clearProposal(coalesceKey);
         }
      }
      return null;
   }

   @Nullable
   private static Proposal buildProposal(
      ServerLevel level,
      ActivitySavedData data,
      ActivitySpatialIndex index,
      ActivityAdmissionController admission,
      Village village,
      boolean camp,
      ActivityProfile profile,
      ActivityKind kind,
      long gameTime
   ) {
      List<UUID> members = selectMembers(village, profile, admission, kind);
      if (members.size() < profile.groupSizeMin()) {
         admission.setLastDefer(ActivityReasonCode.NO_ELIGIBLE_MEMBERS);
         return null;
      }
      if (!admission.canActivateNewGroup(level, kind, village.getCenter(), members.size())) {
         return null;
      }
      UUID targetSettlement = null;
      UUID targetOperation = null;
      String hostility = "";
      List<BlockPos> route = new ArrayList<>();
      BlockPos assemble = village.getCenter();
      double score = 0.5;

      if (kind == ActivityKind.HUNT) {
         BlockPos spot = pickHuntSpot(level, village, profile);
         if (spot == null) {
            admission.setLastDefer(ActivityReasonCode.NO_TARGET);
            return null;
         }
         String areaKey = "hunt:" + (spot.getX() >> 6) + ":" + (spot.getZ() >> 6);
         if (data.areaOnCooldown(areaKey, gameTime)) {
            admission.setLastDefer(ActivityReasonCode.COOLDOWN);
            return null;
         }
         route = coarseRoute(assemble, spot);
         score = 0.55;
      } else if (kind == ActivityKind.PATROL) {
         route = buildPatrolRoute(level, index, village, profile);
         if (route.size() < 2) {
            admission.setLastDefer(ActivityReasonCode.NO_TARGET);
            return null;
         }
         score = 0.45;
      } else if (kind == ActivityKind.BANDIT_ROAM) {
         BlockPos ambush = pickBanditStaging(level, index, village, profile);
         if (ambush == null) {
            admission.setLastDefer(ActivityReasonCode.NO_TARGET);
            return null;
         }
         route = coarseRoute(assemble, ambush);
         score = 0.5;
      } else if (kind == ActivityKind.VILLAGE_RAID) {
         if (!camp && !vtCarriesRaid(village)) {
            return null;
         }
         ScoredTarget target = pickRaidTarget(level, data, index, admission, village, profile, gameTime);
         if (target == null) {
            admission.setLastDefer(admission.lastDefer() == ActivityReasonCode.NONE ? ActivityReasonCode.NO_TARGET : admission.lastDefer());
            return null;
         }
         targetSettlement = target.villageId;
         hostility = target.reason;
         route = coarseRoute(assemble, target.center);
         score = target.score;
      } else {
         return null;
      }

      if (!admission.canActivateNewGroup(level, kind, assemble, Math.min(members.size(), profile.groupSizeMax()))) {
         return null;
      }
      List<UUID> chosen = members.subList(0, Math.min(members.size(), profile.groupSizeMax()));
      return new Proposal(
         kind,
         village.getId().uuid(),
         camp,
         targetSettlement,
         targetOperation,
         profile.id(),
         profile.organizationId(),
         hostility,
         assemble,
         route,
         new ArrayList<>(chosen),
         score
      );
   }

   private static boolean vtCarriesRaid(Village village) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      return vt != null && vt.carriesRaid();
   }

   private static boolean kindEnabled(ActivityKind kind) {
      return switch (kind) {
         case HUNT -> MillenaireServerConfig.SERVER.regionalActivityHuntEnabled.getAsBoolean();
         case PATROL, RESPONSE -> MillenaireServerConfig.SERVER.regionalActivityPatrolEnabled.getAsBoolean();
         case BANDIT_ROAM, AMBUSH, SKIRMISH -> MillenaireServerConfig.SERVER.regionalActivityBanditEnabled.getAsBoolean();
         case VILLAGE_RAID -> MillenaireServerConfig.SERVER.regionalActivityRaidEnabled.getAsBoolean();
      };
   }

   static List<UUID> selectMembers(Village village, ActivityProfile profile, ActivityAdmissionController admission, ActivityKind kind) {
      List<VillagerRecord> eligible = new ArrayList<>();
      int defendersHome = 0;
      int adults = 0;
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (r.isKilled()) continue;
         VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
         if (type == null || type.isChild()) continue;
         adults++;
         if (type.isHelpInAttacks() || type.isDefensive()) {
            defendersHome++;
         }
         if (r.isAwayHired() || r.isAwayRaiding() || r.isRaidingVillage()) continue;
         if (admission.isParticipantReserved(r.getUuid())) continue;
         if (!r.getQuestTags().isEmpty()) continue;
         if (!profile.matchesTags(type.tags(), r.getRoleName())) continue;
         if (kind == ActivityKind.VILLAGE_RAID && !type.isRaider() && !type.isHostile()) continue;
         eligible.add(r);
      }
      int maxAway = Math.max(0, (int) Math.floor(adults * profile.maxAwayFraction()));
      if (maxAway < profile.groupSizeMin()) {
         return List.of();
      }
      int alreadyAway = 0;
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (!r.isKilled() && (r.isAwayRaiding() || admission.isParticipantReserved(r.getUuid()))) {
            alreadyAway++;
         }
      }
      int room = Math.max(0, maxAway - alreadyAway);
      if (room < profile.groupSizeMin()) {
         return List.of();
      }
      // Keep home defense
      eligible.sort(Comparator.comparingInt(VillagerRecord::getMilitaryStrength).reversed());
      List<UUID> picked = new ArrayList<>();
      int defenseLeft = defendersHome;
      for (VillagerRecord r : eligible) {
         if (picked.size() >= Math.min(profile.groupSizeMax(), room)) break;
         VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
         boolean defender = type != null && (type.isHelpInAttacks() || type.isDefensive());
         if (defender && defenseLeft - 1 < profile.minHomeDefense()) {
            continue;
         }
         picked.add(r.getUuid());
         if (defender) defenseLeft--;
      }
      if (picked.size() < profile.groupSizeMin()) {
         return List.of();
      }
      return picked;
   }

   @Nullable
   private static BlockPos pickHuntSpot(ServerLevel level, Village village, ActivityProfile profile) {
      BlockPos c = village.getCenter();
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = vt != null ? vt.radius() : 64;
      Random rng = new Random(village.getId().uuid().getMostSignificantBits() ^ level.getGameTime() / 1200L);
      for (int i = 0; i < 8; i++) {
         double ang = rng.nextDouble() * Math.PI * 2;
         int dist = radius + 48 + rng.nextInt(Math.max(1, profile.activityRange() - 48));
         int x = c.getX() + (int) (Math.cos(ang) * dist);
         int z = c.getZ() + (int) (Math.sin(ang) * dist);
         if (!level.hasChunk(x >> 4, z >> 4)) {
            continue;
         }
         BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, c.getY(), z));
         if (pos.closerThan(c, radius + 16)) {
            continue; // avoid settlement footprint
         }
         return pos;
      }
      return null;
   }

   private static List<BlockPos> buildPatrolRoute(ServerLevel level, ActivitySpatialIndex index, Village village, ActivityProfile profile) {
      List<BlockPos> pts = new ArrayList<>();
      BlockPos c = village.getCenter();
      pts.add(c);
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int r = vt != null ? Math.max(24, vt.radius()) : 48;
      pts.add(c.offset(r, 0, 0));
      pts.add(c.offset(0, 0, r));
      pts.add(c.offset(-r, 0, 0));
      pts.add(c.offset(0, 0, -r));
      List<BlockPos> known = index.routePointsNear(c, 1, 4);
      for (BlockPos p : known) {
         if (pts.size() >= 8) break;
         pts.add(p);
      }
      return pts;
   }

   @Nullable
   private static BlockPos pickBanditStaging(ServerLevel level, ActivitySpatialIndex index, Village camp, ActivityProfile profile) {
      BlockPos c = camp.getCenter();
      List<BlockPos> roads = index.routePointsNear(c, 2, 8);
      if (!roads.isEmpty()) {
         return roads.get(Math.floorMod((int) (camp.getId().uuid().getLeastSignificantBits() ^ level.getGameTime()), roads.size()));
      }
      // Fallback: edge between cover and approach to nearest non-camp settlement
      List<ActivitySpatialIndex.IndexedSettlement> near = index.settlementsNear(c, 2);
      for (ActivitySpatialIndex.IndexedSettlement s : near) {
         if (!s.camp() && !s.loneBuilding()) {
            BlockPos mid = new BlockPos((c.getX() + s.center().getX()) / 2, c.getY(), (c.getZ() + s.center().getZ()) / 2);
            return mid;
         }
      }
      Random rng = new Random(camp.getId().uuid().hashCode());
      return c.offset(rng.nextInt(64) - 32, 0, rng.nextInt(64) - 32);
   }

   private record ScoredTarget(UUID villageId, BlockPos center, double score, String reason) {}

   @Nullable
   private static ScoredTarget pickRaidTarget(
      ServerLevel level, ActivitySavedData data, ActivitySpatialIndex index, ActivityAdmissionController admission, Village attacker, ActivityProfile profile, long gameTime
   ) {
      List<ActivitySpatialIndex.IndexedSettlement> near = index.settlementsNear(attacker.getCenter(), 3);
      List<ScoredTarget> scored = new ArrayList<>();
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      CultureStandingSavedData standing = CultureStandingSavedData.get(level);
      for (ActivitySpatialIndex.IndexedSettlement s : near) {
         if (s.id().equals(attacker.getId().uuid())) continue;
         if (s.camp() || s.loneBuilding()) continue;
         if (admission.isTargetReserved(s.id())) {
            admission.setLastDefer(ActivityReasonCode.TARGET_RESERVED);
            continue;
         }
         if (data.targetOnCooldown(s.id().toString(), gameTime)) {
            admission.setLastDefer(ActivityReasonCode.COOLDOWN);
            continue;
         }
         if (data.inGrace(s.id(), gameTime)) {
            admission.setLastDefer(ActivityReasonCode.TARGET_PROTECTED);
            continue;
         }
         Village target = vm.getVillage(new VillageId(s.id()));
         if (target == null || target.isUnderAttack()) continue;
         if (TreatyService.blocksWar(level, attacker.getId().uuid(), target.getId().uuid())) {
            admission.setLastDefer(ActivityReasonCode.TREATY_BLOCKS);
            continue;
         }
         Integer rel = attacker.getRelations().get(target.getId());
         boolean atWar = standing.isAtWar(attacker.getCultureId(), target.getCultureId());
         boolean hostileRel = rel != null && rel < -90;
         boolean bandit = profile.bandit();
         if (!bandit && !atWar && !hostileRel) {
            continue;
         }
         if (!level.isPositionEntityTicking(target.getCenter()) && !hasNearbyActivatingPlayer(level, target.getCenter())) {
            // Physical action needs loaded area eventually; still allow planning travel into loaded path later
         }
         double loot = clamp01(1.0 - target.getVillageDefendingStrength() / 40.0);
         double vulnerability = clamp01(1.0 - target.getVillageDefendingStrength() / (attacker.getVillageRaidingStrength() + 1.0));
         double routeExposure = 0.5;
         double rivalry = clamp01(data.rivalry(profile.organizationId(), s.culturePath() + "_village") / 20.0);
         double travelCost = clamp01(Math.sqrt(attacker.getCenter().distSqr(target.getCenter())) / profile.activityRange());
         double recentLoss = clamp01(data.rivalry("loss:" + attacker.getId().uuid(), s.id().toString()) / 10.0);
         double score = 0.30 * loot + 0.25 * vulnerability + 0.20 * routeExposure + 0.15 * rivalry - 0.25 * travelCost - 0.35 * recentLoss;
         if (score <= 0) continue;
         String reason = bandit ? "bandit_opportunity" : (atWar ? "culture_war" : "hostile_relation");
         scored.add(new ScoredTarget(s.id(), target.getCenter(), score, reason));
      }
      if (scored.isEmpty()) return null;
      scored.sort(Comparator.comparingDouble(ScoredTarget::score).reversed());
      int top = Math.min(3, scored.size());
      Random rng = new Random(attacker.getId().uuid().getMostSignificantBits() ^ gameTime);
      return scored.get(rng.nextInt(top));
   }

   private static double clamp01(double v) {
      return Math.max(0.0, Math.min(1.0, v));
   }

   static List<BlockPos> coarseRoute(BlockPos from, BlockPos to) {
      List<BlockPos> route = new ArrayList<>();
      route.add(from);
      int dx = to.getX() - from.getX();
      int dz = to.getZ() - from.getZ();
      int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
      int segments = Math.max(1, dist / 32);
      for (int i = 1; i < segments; i++) {
         double t = i / (double) segments;
         route.add(new BlockPos(from.getX() + (int) (dx * t), from.getY(), from.getZ() + (int) (dz * t)));
      }
      route.add(to);
      return route;
   }
}
