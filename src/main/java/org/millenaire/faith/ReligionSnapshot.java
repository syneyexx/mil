package org.millenaire.faith;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable compact copy for background religion planning. No live world objects.
 */
public record ReligionSnapshot(
   UUID villageId,
   @Nullable UUID faithInstanceId,
   @Nullable ResourceLocation faithTemplateId,
   @Nullable UUID chapterId,
   @Nullable ResourceLocation orderTemplateId,
   @Nullable BlockPos shrinePos,
   List<MemberSnap> members,
   int population,
   int religiousBuildingCount,
   long gameTime,
   long dayTime,
   long dayNumber,
   long worldSessionId,
   int contentGeneration,
   long seed,
   boolean religionEnabled,
   boolean ordersEnabled,
   boolean conversionEnabled,
   double orderSpawnChance,
   int minPopForOrder,
   Chapter.Stage chapterStage,
   int secrecy,
   int cohesion,
   int villageTension,
   boolean hasActiveRitual
) {
   public ReligionSnapshot {
      members = List.copyOf(members);
   }

   public record MemberSnap(UUID recordUuid, OrderRole role, int devotion, boolean clergyPreferred) {
   }
}
