package org.millenaire.faction.crime.hunter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.building.SpecialPoint;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;

/**
 * Resolves a valid jail destination for bounty-hunter custody.
 * Prefers jails of the issuing culture, then any culture jail in the world.
 * Hunter works for anyone — any culture's jail is acceptable as fallback.
 */
public final class BountyHunterJailResolver {
   private BountyHunterJailResolver() {
   }

   public record JailDestination(Village village, BlockPos cellPos, BlockPos releasePos) {
   }

   public static Optional<JailDestination> resolve(ServerLevel level, ServerPlayer player, ResourceLocation preferredCulture) {
      if (level == null) {
         return Optional.empty();
      }
      Vec3 from = player != null ? player.position() : Vec3.ZERO;
      List<JailCandidate> preferred = new ArrayList<>();
      List<JailCandidate> any = new ArrayList<>();

      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (village == null) {
            continue;
         }
         BuildingInstance jail = findJailBuilding(village);
         BlockPos cell = resolveCellPos(village, jail);
         if (cell == null || !isSafeStandPos(level, cell)) {
            continue;
         }
         BlockPos release = resolveReleasePos(village, jail, cell);
         JailCandidate candidate = new JailCandidate(village, cell, release, from.distanceToSqr(Vec3.atCenterOf(cell)));
         if (preferredCulture != null && preferredCulture.equals(village.getCultureId())) {
            preferred.add(candidate);
         } else {
            any.add(candidate);
         }
      }

      Comparator<JailCandidate> byDistance = Comparator.comparingDouble(JailCandidate::distanceSq);
      preferred.sort(byDistance);
      any.sort(byDistance);

      if (!preferred.isEmpty()) {
         JailCandidate best = preferred.get(0);
         return Optional.of(new JailDestination(best.village(), best.cell(), best.release()));
      }
      if (!any.isEmpty()) {
         JailCandidate best = any.get(0);
         return Optional.of(new JailDestination(best.village(), best.cell(), best.release()));
      }
      return Optional.empty();
   }

   @Nullable
   private static BuildingInstance findJailBuilding(Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building != null && building.getPurpose() == BuildingPurpose.JAIL) {
            return building;
         }
      }
      return null;
   }

   @Nullable
   private static BlockPos resolveCellPos(Village village, @Nullable BuildingInstance jail) {
      if (jail != null) {
         BlockPos sleep = jail.getFirstPointPos(SpecialPoint.SLEEPING_POS);
         if (sleep != null) {
            return sleep;
         }
         BlockPos sell = jail.getFirstPointPos(SpecialPoint.SELLING_POS);
         if (sell != null) {
            return sell;
         }
         if (jail.getOrigin() != null) {
            return jail.getOrigin().above();
         }
      }
      // Soft fallback: town hall interior if no dedicated jail exists yet.
      BuildingInstance th = village.getTownhall();
      if (th != null) {
         BlockPos sleep = th.getFirstPointPos(SpecialPoint.SLEEPING_POS);
         if (sleep != null) {
            return sleep;
         }
         if (th.getOrigin() != null) {
            return th.getOrigin().above();
         }
      }
      return village.getCenter();
   }

   private static BlockPos resolveReleasePos(Village village, @Nullable BuildingInstance jail, BlockPos cell) {
      if (jail != null) {
         BlockPos path = jail.getFirstPointPos(SpecialPoint.PATH_START_POS);
         if (path != null) {
            return path;
         }
         if (jail.getOrigin() != null) {
            return jail.getOrigin();
         }
      }
      if (village.getCenter() != null) {
         return village.getCenter();
      }
      return cell;
   }

   public static boolean isSafeStandPos(ServerLevel level, BlockPos pos) {
      if (pos == null || level == null) {
         return false;
      }
      if (!level.isLoaded(pos)) {
         // Accept unresolved distant jails; teleport validation happens at transfer time.
         return true;
      }
      BlockState feet = level.getBlockState(pos);
      BlockState head = level.getBlockState(pos.above());
      BlockState ground = level.getBlockState(pos.below());
      if (!feet.getCollisionShape(level, pos).isEmpty()) {
         return false;
      }
      if (!head.getCollisionShape(level, pos.above()).isEmpty()) {
         return false;
      }
      return !ground.isAir() && !ground.liquid();
   }

   private record JailCandidate(Village village, BlockPos cell, BlockPos release, double distanceSq) {
   }
}
