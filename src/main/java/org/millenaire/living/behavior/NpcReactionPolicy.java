package org.millenaire.living.behavior;

import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedType;
import org.millenaire.living.needs.VillageNeedsSnapshot;

/**
 * Deterministic crisis prioritization for dialogue/reaction context.
 * Does not replace GoalScheduler priorities.
 */
public final class NpcReactionPolicy {
   private NpcReactionPolicy() {
   }

   public enum PriorityBand {
      SECURITY_EMERGENCY,
      CRITICAL_RESOURCE_BLOCK,
      IMPORTANT_VILLAGE_PROBLEM,
      PLAYER_INTERACTION,
      SOCIAL,
      AMBIENT
   }

   public static PriorityBand bandFor(VillageNeedsSnapshot snapshot, boolean playerInteracting, boolean underAttack) {
      if (underAttack) {
         return PriorityBand.SECURITY_EMERGENCY;
      }
      if (snapshot != null) {
         for (VillageNeed need : snapshot.needs()) {
            if (need.type() == VillageNeedType.VILLAGE_SECURITY_THREAT) {
               return PriorityBand.SECURITY_EMERGENCY;
            }
         }
         for (VillageNeed need : snapshot.needs()) {
            if (need.type() == VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE && need.severity().rank() >= 3) {
               return PriorityBand.CRITICAL_RESOURCE_BLOCK;
            }
         }
         if (!snapshot.isEmpty()) {
            return PriorityBand.IMPORTANT_VILLAGE_PROBLEM;
         }
      }
      if (playerInteracting) {
         return PriorityBand.PLAYER_INTERACTION;
      }
      return PriorityBand.AMBIENT;
   }
}
