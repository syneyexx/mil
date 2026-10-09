package org.millenaire.living.behavior;

import javax.annotation.Nullable;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedType;
import org.millenaire.living.needs.VillageNeedsSnapshot;

/**
 * Adapter between village reaction priorities and dialogue/context — not a second GoalScheduler.
 */
public final class VillageActivityCoordinator {
   private VillageActivityCoordinator() {
   }

   @Nullable
   public static String builderFocusLine(VillageNeedsSnapshot snapshot) {
      if (snapshot == null) {
         return null;
      }
      return snapshot.firstOfType(VillageNeedType.CONSTRUCTION_MATERIAL_SHORTAGE)
         .map(VillageActivityCoordinator::formatShortage)
         .orElse(null);
   }

   @Nullable
   public static String chiefFocusLine(VillageNeedsSnapshot snapshot, boolean underAttack) {
      if (underAttack) {
         return "security:under_attack";
      }
      if (snapshot == null) {
         return null;
      }
      return snapshot.mostSevere().map(n -> n.type().name() + ":" + n.summaryDetail()).orElse(null);
   }

   @Nullable
   public static String guardFocusLine(boolean underAttack, VillageNeedsSnapshot snapshot) {
      if (underAttack) {
         return "security:defending";
      }
      if (snapshot != null && snapshot.firstOfType(VillageNeedType.VILLAGE_SECURITY_THREAT).isPresent()) {
         return "security:alert";
      }
      return null;
   }

   private static String formatShortage(VillageNeed need) {
      return "construction_shortage:" + need.summaryDetail() + ":missing=" + need.totalShortage();
   }
}
