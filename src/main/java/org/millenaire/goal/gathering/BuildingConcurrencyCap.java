package org.millenaire.goal.gathering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;

public final class BuildingConcurrencyCap {
   private BuildingConcurrencyCap() {
   }

   public static <T> List<T> filterAvailable(
      List<T> candidates, Function<T, BuildingId> idOf, Map<BuildingId, Integer> occupancy, int maxSimultaneous, @Nullable BuildingId pinned
   ) {
      if (maxSimultaneous > 0 && !candidates.isEmpty() && !occupancy.isEmpty()) {
         List<T> available = null;

         for (int i = 0; i < candidates.size(); i++) {
            T candidate = candidates.get(i);
            BuildingId id = idOf.apply(candidate);
            boolean ok = id != null && (id.equals(pinned) || occupancy.getOrDefault(id, 0) < maxSimultaneous);
            if (ok) {
               if (available != null) {
                  available.add(candidate);
               }
            } else if (available == null) {
               available = new ArrayList<>(candidates.size() - 1);
               available.addAll(candidates.subList(0, i));
            }
         }

         return available != null ? available : candidates;
      } else {
         return candidates;
      }
   }

   public static List<BuildingInstance> filter(GoalContext ctx, GatheringType type, List<BuildingInstance> candidates) {
      int max = type.maxSimultaneousInBuilding();
      if (max > 0 && !candidates.isEmpty()) {
         Map<BuildingId, Integer> occupancy = occupancy(ctx, type.id());
         return occupancy.isEmpty() ? candidates : filterAvailable(candidates, BuildingInstance::getId, occupancy, max, currentTargetBuilding(ctx, type.id()));
      } else {
         return candidates;
      }
   }

   private static Map<BuildingId, Integer> occupancy(GoalContext ctx, ResourceLocation goalId) {
      MillVillager self = ctx.villager();
      Map<BuildingId, Integer> counts = null;

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (ctx.level().getEntity(uuid) instanceof MillVillager other && other != self) {
            GoalScheduler scheduler = other.getGoalScheduler();
            if (scheduler != null && goalId.equals(scheduler.getCurrentGoalId()) && scheduler.getCurrentTask() instanceof GatheringTask gathering) {
               BuildingId target = gathering.getTargetBuildingId();
               if (target != null) {
                  if (counts == null) {
                     counts = new HashMap<>(4);
                  }

                  counts.merge(target, 1, Integer::sum);
               }
            }
         }
      }

      return counts == null ? Map.of() : counts;
   }

   @Nullable
   private static BuildingId currentTargetBuilding(GoalContext ctx, ResourceLocation goalId) {
      GoalScheduler scheduler = ctx.villager().getGoalScheduler();
      if (scheduler != null && goalId.equals(scheduler.getCurrentGoalId())) {
         return scheduler.getCurrentTask() instanceof GatheringTask gathering ? gathering.getTargetBuildingId() : null;
      } else {
         return null;
      }
   }
}
