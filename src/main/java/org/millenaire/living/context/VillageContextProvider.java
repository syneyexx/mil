package org.millenaire.living.context;

import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.bridge.CivilizationContextProvider;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.behavior.VillageActivityCoordinator;
import org.millenaire.living.events.LivingEvent;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.village.Village;

public final class VillageContextProvider {
   private VillageContextProvider() {
   }

   public static void appendVerifiedVillageState(ServerLevel level, Village village, DialogueGameplayContext ctx) {
      if (village == null || ctx == null) {
         return;
      }
      ctx.addVerified("villageName=" + village.getVillageName());
      ctx.addVerified("underAttack=" + village.isUnderAttack());
      ctx.addVerified("residentCount=" + village.getVillagerRecords().size());
      if (village.getPendingProject() != null) {
         ctx.addVerified("pendingProject=" + village.getPendingProject().planSetId());
      }
      VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(level, village, false);
      for (VillageNeed need : snap.needs()) {
         if (need.shortage().isEmpty() && need.type().name().contains("SHORTAGE")) {
            continue;
         }
         StringBuilder sb = new StringBuilder();
         sb.append("need=").append(need.type().name()).append(" severity=").append(need.severity().name());
         if (!need.summaryDetail().isBlank()) {
            sb.append(" detail=").append(need.summaryDetail());
         }
         if (!need.shortage().isEmpty()) {
            sb.append(" missing=");
            boolean first = true;
            for (Entry<ResourceLocation, Integer> e : need.shortage().entrySet()) {
               if (!first) {
                  sb.append(',');
               }
               first = false;
               sb.append(e.getValue()).append('x').append(e.getKey());
            }
         }
         ctx.addVerified(sb.toString());
      }
      String focus = VillageActivityCoordinator.chiefFocusLine(snap, village.isUnderAttack());
      if (focus != null) {
         ctx.addVerified("priorityFocus=" + focus);
      }
      for (LivingEvent event : LivingEventService.recentPublic(level, village.getId(), 4)) {
         ctx.addVerified("recentEvent=" + event.eventType().name() + ":" + event.details());
      }
      CivilizationContextProvider.appendVerified(level, village, ctx);
   }
}
