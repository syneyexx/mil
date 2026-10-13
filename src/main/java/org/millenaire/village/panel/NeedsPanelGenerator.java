package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.economy.Need;
import org.millenaire.economy.NeedService;
import org.millenaire.economy.ProductionChoice;
import org.millenaire.logistics.DeliveryContract;
import org.millenaire.logistics.LogisticsService;
import org.millenaire.village.Village;

/**
 * Village book section: what the village is trying to achieve and why it is blocked.
 * Uses cached NeedService / logistics summaries — no full world scan.
 */
public final class NeedsPanelGenerator {
   private NeedsPanelGenerator() {
   }

   public static PanelContent generate(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      NeedService needs = village.getNeedService();
      ProductionChoice choice = needs.lastChoice();
      String summary = needs.lastPlanSummary();
      lines.add(PanelLine.text("Plan: " + choice.name() + (summary.isEmpty() ? "" : " — " + summary)));
      String advice = village.getBudget().lastCouncilAdvice();
      if (advice != null && !advice.isEmpty()) {
         lines.add(PanelLine.text(advice));
      }
      String[] priorities = village.getBudget().activePriorities();
      lines.add(PanelLine.text("Priorities: " + priorities[0] + ", " + priorities[1] + ", " + priorities[2]));

      List<Need> active = needs.activeNeeds();
      if (active.isEmpty()) {
         lines.add(PanelLine.text("No active needs."));
      } else {
         lines.add(PanelLine.text("Needs (" + active.size() + "):"));
         int shown = 0;
         for (Need need : active) {
            if (shown >= 8) {
               lines.add(PanelLine.text("…"));
               break;
            }
            String itemName = itemLabel(need.item());
            String blocked = need.blockedReason().isEmpty() ? "" : " [" + need.blockedReason() + "]";
            lines.add(
               PanelLine.text(
                  "- "
                     + need.category().name()
                     + ": "
                     + itemName
                     + " x"
                     + need.remaining()
                     + " ("
                     + need.sourceRef()
                     + ")"
                     + blocked
               )
            );
            shown++;
         }
      }

      Need topBlocked = needs.topBlocked();
      if (topBlocked != null) {
         lines.add(PanelLine.text("Blocked: " + itemLabel(topBlocked.item()) + " — " + topBlocked.blockedReason()));
      }

      if (level != null) {
         List<DeliveryContract> contracts = LogisticsService.openContractsFor(level, village.getId().uuid());
         if (!contracts.isEmpty()) {
            lines.add(PanelLine.text("Contracts (" + contracts.size() + "):"));
            int shown = 0;
            for (DeliveryContract c : contracts) {
               if (shown >= 4) {
                  lines.add(PanelLine.text("…"));
                  break;
               }
               lines.add(
                  PanelLine.text(
                     "- "
                        + c.state().name()
                        + " "
                        + itemLabel(c.item())
                        + " x"
                        + c.amount()
                        + (c.reason().isEmpty() ? "" : " (" + c.reason() + ")")
                  )
               );
               shown++;
            }
         }
         for (org.millenaire.diplomacy.Treaty t : org.millenaire.diplomacy.TreatySavedData.get(level).forVillage(village.getId().uuid())) {
            lines.add(PanelLine.text("Treaty: " + t.type().name() + " trust=" + t.trust()));
         }
         for (org.millenaire.story.StorylineInstance s : org.millenaire.story.StorySavedData.get(level).activeFor(village.getId().uuid())) {
            lines.add(PanelLine.text("Story: " + s.kind() + " — " + s.summary()));
         }
         for (org.millenaire.founding.FoundingOperation op : org.millenaire.founding.FoundingSavedData.get(level).openOps()) {
            if (op.parentVillageId().equals(village.getId().uuid())) {
               lines.add(PanelLine.text("Founding: " + op.state().name() + " — " + op.reason()));
            }
         }
      }

      return new PanelContent(PanelType.NEEDS, "Needs & Trade", lines);
   }

   private static String itemLabel(Item item) {
      return BuiltInRegistries.ITEM.getKey(item).getPath();
   }
}
