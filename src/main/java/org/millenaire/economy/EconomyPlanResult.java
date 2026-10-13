package org.millenaire.economy;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;

public record EconomyPlanResult(
   UUID villageId,
   long worldSessionId,
   Kind kind,
   ProductionChoice choice,
   @Nullable UUID focusNeedId,
   @Nullable Item focusItem,
   @Nullable UUID tradePartnerVillageId,
   int suggestedAmount,
   String summary,
   List<NeedMutation> needMutations
) {
   public enum Kind {
      NONE,
      UPDATE_PLAN,
      FAILED
   }

   public record NeedMutation(
      NeedCategory category,
      Item item,
      int amount,
      int priority,
      long deadlineGameTime,
      String sourceRef,
      @Nullable String blockedReason
   ) {
   }

   public static EconomyPlanResult none(UUID villageId, long worldSessionId) {
      return new EconomyPlanResult(
         villageId, worldSessionId, Kind.NONE, ProductionChoice.WAIT, null, null, null, 0, "", List.of()
      );
   }

   public static EconomyPlanResult failed(UUID villageId, long worldSessionId, String reason) {
      return new EconomyPlanResult(
         villageId, worldSessionId, Kind.FAILED, ProductionChoice.WAIT, null, null, null, 0, reason, List.of()
      );
   }
}
