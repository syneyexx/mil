package org.millenaire.worldgen.highway.travel;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.village.Village;
import org.millenaire.worldgen.highway.HighwayDiscoveryService;
import org.millenaire.worldgen.highway.HighwayNode;
import org.millenaire.worldgen.highway.HighwayNodeKind;
import org.millenaire.worldgen.highway.HighwayNodeSavedData;
import org.millenaire.worldgen.highway.HighwayRoute;
import org.millenaire.worldgen.highway.HighwayRouteSavedData;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.slf4j.Logger;

/**
 * Logical road journeys + near-player MillVillager materialization.
 */
public final class RoadJourneyScheduler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int ADVANCE_INTERVAL = 40;
   private static final int MOVE_BLOCKS = 6;
   private static final long ROUTE_COOLDOWN = 6000L;
   private static final int SPAWN_CHECK_RADIUS = 96;
   private static final int MAX_SPAWN_PER_TICK = 2;
   private static final int MAX_ADVANCE_PER_TICK = 8;

   private RoadJourneyScheduler() {
   }

   public static void tick(ServerLevel level) {
      if (!HighwayDiscoveryService.isEnabled() || level == null) {
         return;
      }
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayTravelers.get())) {
         return;
      }
      RoadJourneySavedData store = RoadJourneySavedData.get(level);
      store.purgeTerminal();
      maybeSpawnJourneys(level, store);
      advanceJourneys(level, store);
      syncEntities(level, store);
   }

   private static void maybeSpawnJourneys(ServerLevel level, RoadJourneySavedData store) {
      int max = MillenaireServerConfig.SERVER.highwayMaxJourneys.getAsInt();
      if (store.size() >= max || !MaterializationTickBudget.hasTime()) {
         return;
      }
      List<HighwayRoute> connected = new ArrayList<>();
      for (HighwayRoute route : HighwayRouteSavedData.get(level).allRoutes()) {
         if (route.status() == HighwayRoute.Status.CONNECTED
            && !store.onCooldown(route.routeId(), level.getGameTime(), ROUTE_COOLDOWN)
            && !hasActiveJourney(store, route.routeId())) {
            connected.add(route);
         }
      }
      if (connected.isEmpty()) {
         return;
      }
      connected.sort(Comparator.comparing(r -> r.routeId()));
      HighwayRoute route = connected.get(ThreadLocalRandom.current().nextInt(connected.size()));
      RoadJourneyKind kind = pickKind(level.getGameTime());
      ResourceLocation typeId = pickVillagerType(kind);
      if (typeId == null || route.waypoints().isEmpty()) {
         return;
      }
      boolean reverse = ThreadLocalRandom.current().nextBoolean();
      List<BlockPos> wps = route.waypoints();
      BlockPos start = reverse ? wps.get(wps.size() - 1) : wps.get(0);
      int startIndex = reverse ? wps.size() - 1 : 0;
      RoadJourney journey = new RoadJourney(UUID.randomUUID(), route.routeId(), kind, typeId, snap(level, start));
      journey.setWaypointIndex(startIndex);
      journey.setCreatedAt(level.getGameTime());
      journey.setLastAdvanceAt(level.getGameTime());
      journey.setHostVillageId(resolveHostVillage(level, route));
      if (reverse) {
         // Walk toward index 0 — store dest as from side for merchant hooks later.
         journey.setDestVillageId(villageIdForNode(level, route.fromNodeKey()));
         journey.setSrcVillageId(villageIdForNode(level, route.toNodeKey()));
      } else {
         journey.setSrcVillageId(villageIdForNode(level, route.fromNodeKey()));
         journey.setDestVillageId(villageIdForNode(level, route.toNodeKey()));
      }
      store.put(journey);
      store.setCooldown(route.routeId(), level.getGameTime() + ROUTE_COOLDOWN);
      LOGGER.debug("[Millenaire] Highway journey {} ({}) on route {}", journey.journeyId(), kind, route.routeId());
   }

   private static boolean hasActiveJourney(RoadJourneySavedData store, UUID routeId) {
      for (RoadJourney j : store.all()) {
         if (routeId.equals(j.routeId()) && j.phase() == RoadJourney.Phase.EN_ROUTE) {
            return true;
         }
      }
      return false;
   }

   private static void advanceJourneys(ServerLevel level, RoadJourneySavedData store) {
      int advanced = 0;
      for (RoadJourney journey : store.all()) {
         if (advanced >= MAX_ADVANCE_PER_TICK || !MaterializationTickBudget.hasTime()) {
            break;
         }
         if (journey.phase() != RoadJourney.Phase.EN_ROUTE) {
            continue;
         }
         if (level.getGameTime() - journey.lastAdvanceAt() < ADVANCE_INTERVAL) {
            continue;
         }
         HighwayRoute route = HighwayRouteSavedData.get(level).get(journey.routeId());
         if (route == null || route.waypoints().isEmpty()) {
            journey.setPhase(RoadJourney.Phase.CANCELLED);
            store.setDirty();
            continue;
         }
         List<BlockPos> wps = route.waypoints();
         boolean forward = true;
         UUID fromVill = villageIdForNode(level, route.fromNodeKey());
         if (journey.srcVillageId() != null && fromVill != null) {
            forward = journey.srcVillageId().equals(fromVill);
         } else if (journey.waypointIndex() >= wps.size() - 1) {
            forward = false;
         } else if (journey.waypointIndex() == 0) {
            forward = true;
         }

         int targetIndex = forward
            ? Math.min(wps.size() - 1, journey.waypointIndex() + MOVE_BLOCKS)
            : Math.max(0, journey.waypointIndex() - MOVE_BLOCKS);
         BlockPos target = wps.get(targetIndex);
         BlockPos next = snap(level, target);
         journey.setLogicalPos(next);
         journey.setWaypointIndex(targetIndex);
         journey.setLastAdvanceAt(level.getGameTime());
         boolean arrived = forward ? targetIndex >= wps.size() - 1 : targetIndex <= 0;
         if (arrived) {
            journey.setPhase(RoadJourney.Phase.ARRIVED);
            discardEntity(level, journey);
         }
         store.setDirty();
         advanced++;
      }
   }

   private static void syncEntities(ServerLevel level, RoadJourneySavedData store) {
      int spawned = 0;
      for (RoadJourney journey : store.all()) {
         if (journey.phase() != RoadJourney.Phase.EN_ROUTE) {
            continue;
         }
         BlockPos pos = journey.logicalPos();
         boolean ticking = level.isPositionEntityTicking(pos);
         Entity existing = journey.entityUuid() != null ? level.getEntity(journey.entityUuid()) : null;
         if (!ticking) {
            if (existing != null) {
               discardEntity(level, journey);
               store.setDirty();
            }
            continue;
         }
         if (existing instanceof MillVillager villager) {
            // Nudge toward logical position / next waypoint.
            villager.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
            continue;
         }
         if (spawned >= MAX_SPAWN_PER_TICK || !MaterializationTickBudget.hasTime()) {
            continue;
         }
         if (!level.getServer().getPlayerList().getPlayers().isEmpty()) {
            boolean nearPlayer = level.getServer().getPlayerList().getPlayers().stream()
               .anyMatch(p -> p.blockPosition().closerThan(pos, SPAWN_CHECK_RADIUS));
            if (!nearPlayer) {
               continue;
            }
         }
         MillVillager villager = VillagerSpawnFactory.spawnTransient(level, journey.villagerTypeId(), pos);
         if (villager == null) {
            continue;
         }
         journey.setEntityUuid(villager.getUUID());
         if (journey.mounted()) {
            Horse horse = EntityType.HORSE.create(level);
            if (horse != null) {
               horse.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
               horse.setTamed(true);
               if (level.addFreshEntity(horse)) {
                  villager.startRiding(horse, true);
                  journey.setHorseUuid(horse.getUUID());
               }
            }
         }
         store.setDirty();
         spawned++;
      }
   }

   private static void discardEntity(ServerLevel level, RoadJourney journey) {
      if (journey.entityUuid() != null) {
         Entity e = level.getEntity(journey.entityUuid());
         if (e != null) {
            e.discard();
         }
         journey.setEntityUuid(null);
      }
      if (journey.horseUuid() != null) {
         Entity h = level.getEntity(journey.horseUuid());
         if (h != null) {
            h.discard();
         }
         journey.setHorseUuid(null);
      }
   }

   private static RoadJourneyKind pickKind(long gameTime) {
      int roll = ThreadLocalRandom.current().nextInt(100);
      boolean night = gameTime % 24000L >= 13000L;
      if (night && roll < 35) {
         return RoadJourneyKind.BANDIT;
      }
      if (roll < 40) {
         return RoadJourneyKind.TRADER;
      }
      if (roll < 55) {
         return RoadJourneyKind.MOUNTED_TRADER;
      }
      if (roll < 80) {
         return RoadJourneyKind.WARRIOR;
      }
      if (roll < 92) {
         return RoadJourneyKind.MOUNTED_WARRIOR;
      }
      return RoadJourneyKind.BANDIT;
   }

   @Nullable
   private static ResourceLocation pickVillagerType(RoadJourneyKind kind) {
      List<ResourceLocation> matches = new ArrayList<>();
      for (var entry : ModCultures.getAllVillagerTypes().entrySet()) {
         VillagerType vt = entry.getValue();
         boolean ok = switch (kind) {
            case TRADER, MOUNTED_TRADER -> vt.hasTag("localmerchant") || vt.hasTag("foreignmerchant");
            case WARRIOR, MOUNTED_WARRIOR -> vt.isHelpInAttacks() || vt.hasTag("soldier") || vt.hasTag("guard");
            case BANDIT -> vt.hasTag("hostile") || vt.isRaider();
         };
         if (ok) {
            matches.add(entry.getKey());
         }
      }
      if (matches.isEmpty()) {
         // Fallback: any adult male-ish type
         for (var entry : ModCultures.getAllVillagerTypes().entrySet()) {
            matches.add(entry.getKey());
            if (matches.size() >= 8) {
               break;
            }
         }
      }
      if (matches.isEmpty()) {
         return null;
      }
      matches.sort(Comparator.comparing(ResourceLocation::toString));
      return matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
   }

   @Nullable
   private static UUID resolveHostVillage(ServerLevel level, HighwayRoute route) {
      UUID a = villageIdForNode(level, route.fromNodeKey());
      if (a != null) {
         return a;
      }
      return villageIdForNode(level, route.toNodeKey());
   }

   @Nullable
   private static UUID villageIdForNode(ServerLevel level, String nodeKey) {
      HighwayNode node = HighwayNodeSavedData.get(level).get(nodeKey);
      if (node == null) {
         return null;
      }
      if (node.millenaireVillageId() != null) {
         return node.millenaireVillageId();
      }
      if (node.kind() == HighwayNodeKind.MILLENAIRE || node.kind() == HighwayNodeKind.MILLENAIRE_PLAN) {
         return node.millenaireVillageId();
      }
      return null;
   }

   private static BlockPos snap(ServerLevel level, BlockPos pos) {
      if (level.hasChunkAt(pos)) {
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
         return new BlockPos(pos.getX(), y, pos.getZ());
      }
      return pos;
   }

   /**
    * Start a merchant journey along a connected highway when available.
    * @return true if a journey was started (caller should skip teleport)
    */
   public static boolean tryStartMerchantJourney(
      ServerLevel level,
      Village srcVillage,
      Village destVillage,
      ResourceLocation villagerTypeId,
      UUID merchantUuid
   ) {
      if (!HighwayDiscoveryService.isEnabled()
         || !Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayTravelers.get())
         || level == null
         || srcVillage == null
         || destVillage == null) {
         return false;
      }
      String fromKey = "millenaire:" + srcVillage.getId().uuid();
      String toKey = "millenaire:" + destVillage.getId().uuid();
      HighwayRouteSavedData routes = HighwayRouteSavedData.get(level);
      HighwayRoute match = null;
      for (HighwayRoute route : routes.allRoutes()) {
         if (route.status() == HighwayRoute.Status.CONNECTED && route.connects(fromKey, toKey)) {
            match = route;
            break;
         }
      }
      if (match == null || match.waypoints().isEmpty()) {
         return false;
      }
      boolean forward = match.fromNodeKey().equals(fromKey);
      BlockPos start = forward ? match.waypoints().get(0) : match.waypoints().get(match.waypoints().size() - 1);
      RoadJourney journey = new RoadJourney(
         UUID.randomUUID(),
         match.routeId(),
         RoadJourneyKind.TRADER,
         villagerTypeId,
         snap(level, start)
      );
      journey.setWaypointIndex(forward ? 0 : match.waypoints().size() - 1);
      journey.setSrcVillageId(srcVillage.getId().uuid());
      journey.setDestVillageId(destVillage.getId().uuid());
      journey.setHostVillageId(srcVillage.getId().uuid());
      journey.setCreatedAt(level.getGameTime());
      journey.setLastAdvanceAt(level.getGameTime());
      RoadJourneySavedData.get(level).put(journey);
      LOGGER.info(
         "[Millenaire] Merchant {} walking highway from {} to {}",
         merchantUuid,
         srcVillage.getVillageName(),
         destVillage.getVillageName()
      );
      return true;
   }
}
