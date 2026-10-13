package org.millenaire.combat.war;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Worker output; validated and applied on the server thread. */
public record WarPlanResult(
   UUID campaignId,
   long worldSessionId,
   Kind kind,
   @Nullable UUID frontVillageId,
   @Nullable UUID attackerVillageId,
   @Nullable UUID defenderVillageId,
   long nextWaveGameTime,
   List<UUID> sabotageTargets,
   String note
) {
   public enum Kind {
      NONE,
      PICK_FRONT,
      SCHEDULE_WAVE,
      SET_SABOTAGE,
      END_CAMPAIGN,
      FAILED
   }

   public WarPlanResult {
      sabotageTargets = sabotageTargets == null ? List.of() : List.copyOf(sabotageTargets);
      note = note == null ? "" : note;
   }

   public static WarPlanResult none(UUID campaignId, long session) {
      return new WarPlanResult(campaignId, session, Kind.NONE, null, null, null, -1L, List.of(), "");
   }

   public static WarPlanResult failed(UUID campaignId, long session, String note) {
      return new WarPlanResult(campaignId, session, Kind.FAILED, null, null, null, -1L, List.of(), note);
   }
}
