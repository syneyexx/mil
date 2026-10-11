package org.millenaire.religion.presentation;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.religion.ReligionService;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.ReligionDefinition;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.FaithAffiliation;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.religion.supply.RitualSupplyService;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelLine;
import org.millenaire.village.panel.PanelType;

/**
 * Derived UI snapshots for Village Book / Travel Book / dialogue. Never authoritative for mutations.
 */
public final class ReligionPresentationService {
   public ReligionPresentationService() {
   }

   public PanelContent villageBookSection(ServerLevel level, Village village, @Nullable ServerPlayer player) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.panel_type.religion";
      if (level == null) {
         lines.add(PanelLine.translatable("religion.millenaire.book.degraded"));
         return new PanelContent(PanelType.RELIGION, titleKey, lines, true, null);
      }
      NpcFaithService.ensureVillageCommunity(level, village);
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      if (community.traditionIds().isEmpty()) {
         lines.add(PanelLine.translatable("religion.millenaire.book.no_organized"));
         return new PanelContent(PanelType.RELIGION, titleKey, lines, true, null);
      }
      lines.add(PanelLine.translatableWithArgs("religion.millenaire.book.policy", community.policy().id()));
      for (ResourceLocation tid : community.traditionIds()) {
         ReligionDefinition def = ReligionContentRegistry.traditionOrNull(tid);
         String nameKey = def != null ? def.nameKey() : tid.toString();
         boolean host = tid.equals(community.hostTraditionId());
         lines.add(PanelLine.translatableWithArgs(
            host ? "religion.millenaire.book.tradition_host" : "religion.millenaire.book.tradition",
            Component.translatable(nameKey).getString()
         ));
         var clergy = ReligionService.get().clergy().findClergy(level, village, tid);
         lines.add(PanelLine.translatableWithArgs("religion.millenaire.book.clergy_count", String.valueOf(clergy.size())));
      }
      CeremonyInstance active = ReligionService.get().ceremonies().activeCeremony(level, village);
      if (active != null) {
         RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(active.ritualId());
         String ritualName = ritual != null ? Component.translatable(ritual.nameKey()).getString() : active.ritualId().toString();
         lines.add(PanelLine.translatableWithArgs("religion.millenaire.book.active_ceremony", ritualName, active.state().id()));
         lines.add(PanelLine.translatableWithArgs(
            "religion.millenaire.book.participants",
            String.valueOf(active.arrivedParticipants().size())
         ));
         if (ritual != null) {
            List<RitualSupplyService.Shortage> missing = ReligionService.get().supplies().missingSupplies(level, village, ritual);
            if (!missing.isEmpty() && !active.suppliesConsumed()) {
               for (RitualSupplyService.Shortage s : missing) {
                  lines.add(PanelLine.translatableWithArgs("religion.millenaire.book.missing_supply", s.itemId(), String.valueOf(s.missing())));
               }
            }
         }
      } else {
         lines.add(PanelLine.translatable("religion.millenaire.book.no_active_ceremony"));
      }
      int hist = 0;
      for (String h : community.history()) {
         if (hist++ >= 8) {
            break;
         }
         lines.add(PanelLine.text(h));
      }
      return new PanelContent(PanelType.RELIGION, titleKey, lines, true, null);
   }

   public List<String> dialogueContextLines(ServerLevel level, Village village, VillagerRecord record) {
      List<String> lines = new ArrayList<>();
      NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, village, record);
      FaithAffiliation primary = profile.primaryAffiliation();
      if (primary == null) {
         lines.add("religion: none");
         return lines;
      }
      ReligionDefinition def = ReligionContentRegistry.traditionOrNull(primary.traditionId());
      lines.add("tradition=" + (def != null ? def.id() : primary.traditionId()) + " devotion=" + primary.devotion());
      if (ReligionService.get().clergy().isClergy(level, village, record)) {
         lines.add("role=clergy");
      }
      CeremonyInstance active = ReligionService.get().ceremonies().activeCeremony(level, village);
      if (active != null) {
         lines.add("active_ceremony=" + active.ritualId() + " state=" + active.state().id());
         RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(active.ritualId());
         if (ritual != null && !active.suppliesConsumed()) {
            var missing = ReligionService.get().supplies().missingSupplies(level, village, ritual);
            if (!missing.isEmpty()) {
               lines.add("missing_supplies=" + missing.get(0).itemId() + "x" + missing.get(0).missing());
            }
         }
      }
      return lines;
   }

   public List<TravelTraditionPage> travelBookTraditions() {
      List<TravelTraditionPage> pages = new ArrayList<>();
      for (ReligionDefinition def : ReligionContentRegistry.allTraditions()) {
         pages.add(new TravelTraditionPage(def.id(), def.nameKey(), def.descriptionKey(), def.cultureIds()));
      }
      return pages;
   }

   public record TravelTraditionPage(ResourceLocation id, String nameKey, String descKey, List<String> cultures) {
   }
}
