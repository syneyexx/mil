package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.faith.Chapter;
import org.millenaire.faith.FaithService;
import org.millenaire.faith.Membership;
import org.millenaire.faith.OrderInstance;
import org.millenaire.faith.ReligionSavedData;
import org.millenaire.faith.RitualInstance;
import org.millenaire.quest.QuestRegistry;
import org.millenaire.village.PlayerQuestData;
import org.millenaire.village.Village;

public final class ReligionPanelGenerator {
   private ReligionPanelGenerator() {
   }

   public static PanelContent generate(Village village, @Nullable ServerLevel level, @Nullable ServerPlayer player) {
      List<PanelLine> lines = new ArrayList<>();
      String faithName = level != null ? FaithService.resolveDisplayName(level, village) : FaithService.resolveDisplayName(village);
      lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.faith", faithName));
      if (FaithService.hasWorshipBuilding(village)) {
         lines.add(PanelLine.translatable("gui.millenaire.book.religion.shrine_present"));
      } else if (FaithService.isWorshipAlreadyQueued(village)) {
         lines.add(PanelLine.translatable("gui.millenaire.book.religion.shrine_planned"));
      } else {
         lines.add(PanelLine.translatable("gui.millenaire.book.religion.shrine_missing"));
      }
      if (level != null && village.isFestivalActive(level.getGameTime())) {
         lines.add(PanelLine.translatable("gui.millenaire.book.religion.festival_active"));
      }
      if (village.getReligionTension() > 0) {
         lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.tension", String.valueOf(village.getReligionTension())));
      }

      if (level != null && player != null) {
         ReligionSavedData data = ReligionSavedData.get(level);
         Chapter chapter = data.getChapterForVillage(village.getId());
         Membership membership = data.getMembership(Membership.SubjectType.PLAYER, player.getUUID());
         boolean discovered = membership != null
            || player.getPersistentData().getBoolean("millenaire_lastflame_aware")
            || hasPlayerTag(player, level, "lastflame_aware");
         if (chapter != null && discovered) {
            OrderInstance order = data.getOrder(chapter.orderInstanceId());
            String orderName = order != null ? order.resolveDisplayName() : "Order";
            lines.add(PanelLine.separator());
            lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.order", orderName));
            lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.stage", chapter.stage().name()));
            lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.secrecy", String.valueOf(chapter.secrecy())));
            lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.cohesion", String.valueOf(chapter.cohesion())));
            if (membership != null && membership.chapterId().equals(chapter.id())) {
               lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.role", membership.role().name()));
               lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.devotion", String.valueOf(membership.devotion())));
            }
            RitualInstance ritual = data.findActiveRitualForChapter(chapter.id());
            if (ritual != null) {
               lines.add(PanelLine.translatableWithArgs("gui.millenaire.book.religion.ritual", ritual.state().name()));
            }
         } else if (chapter != null && !discovered) {
            lines.add(PanelLine.separator());
            lines.add(PanelLine.translatable("gui.millenaire.book.religion.whispers"));
         }
      }

      return new PanelContent(PanelType.RELIGION, "gui.millenaire.book.section.religion", lines, true, null);
   }

   private static boolean hasPlayerTag(ServerPlayer player, ServerLevel level, String tag) {
      try {
         PlayerQuestData data = PlayerQuestData.get(level, QuestRegistry::get);
         return data.hasPlayerTag(player.getUUID(), tag);
      } catch (Exception e) {
         return false;
      }
   }
}
