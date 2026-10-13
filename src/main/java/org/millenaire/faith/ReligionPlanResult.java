package org.millenaire.faith;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Worker output; validated and applied on the server thread. */
public record ReligionPlanResult(
   UUID villageId,
   long worldSessionId,
   int contentGeneration,
   Kind kind,
   @Nullable ResourceLocation orderTemplateId,
   @Nullable UUID suggestedLeaderUuid,
   @Nullable ResourceLocation ritualToSchedule,
   long ritualStartGameTime,
   List<UUID> inviteRecordUuids,
   String note
) {
   public enum Kind {
      NONE,
      SEED_CHAPTER,
      SCHEDULE_RITUAL,
      PROMOTE_LEADER,
      EXPIRE_HINT,
      FAILED
   }

   public ReligionPlanResult {
      inviteRecordUuids = inviteRecordUuids == null ? List.of() : List.copyOf(inviteRecordUuids);
      note = note == null ? "" : note;
   }

   public static ReligionPlanResult none(UUID villageId, long session, int gen) {
      return new ReligionPlanResult(villageId, session, gen, Kind.NONE, null, null, null, -1L, List.of(), "");
   }

   public static ReligionPlanResult failed(UUID villageId, long session, int gen, String note) {
      return new ReligionPlanResult(villageId, session, gen, Kind.FAILED, null, null, null, -1L, List.of(), note);
   }
}
