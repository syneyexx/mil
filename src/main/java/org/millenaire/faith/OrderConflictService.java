package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/** Applies Last Flame conflict outcomes from quest action data. */
public final class OrderConflictService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private OrderConflictService() {
   }

   public static void onActionData(ServerLevel level, ServerPlayer player, @Nullable Village village, String key, String value) {
      if (key == null || !key.startsWith("lastflame_")) {
         return;
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      Chapter chapter = village != null ? data.getChapterForVillage(village.getId()) : null;
      switch (key) {
         case "lastflame_join" -> handleJoin(level, data, player, village, chapter);
         case "lastflame_shrine" -> handleShrine(data, village, chapter);
         case "lastflame_support" -> handleSupport(data, village, chapter);
         case "lastflame_reform" -> handleReform(data, village, chapter);
         case "lastflame_leave" -> handleLeave(data, player, village, chapter);
         case "lastflame_oppose" -> handleOppose(level, data, village, chapter);
         default -> {
         }
      }
   }

   private static void handleShrine(ReligionSavedData data, @Nullable Village village, @Nullable Chapter chapter) {
      if (chapter == null || village == null) {
         return;
      }
      if (chapter.stage() == Chapter.Stage.SEEDED) {
         chapter.setStage(Chapter.Stage.SHRINE_READY);
      }
      BuildingInstance shrine = null;
      for (BuildingInstance b : village.getOperationalBuildingsWithTag("praying")) {
         shrine = b;
         break;
      }
      if (shrine == null) {
         for (BuildingInstance b : village.getOperationalBuildingsWithTag("religious")) {
            shrine = b;
            break;
         }
      }
      if (shrine != null) {
         chapter.setShrineBuildingId(shrine.getId());
      }
      data.setDirty();
      village.markDirty();
   }

   private static void handleJoin(
      ServerLevel level, ReligionSavedData data, ServerPlayer player, @Nullable Village village, @Nullable Chapter chapter
   ) {
      if (chapter == null || village == null) {
         return;
      }
      ReligionService.joinPlayerChapter(data, chapter, player.getUUID(), OrderRole.INITIATE, 25, level.getGameTime());
      if (chapter.stage() == Chapter.Stage.SEEDED || chapter.stage() == Chapter.Stage.SHRINE_READY) {
         chapter.setStage(Chapter.Stage.ACTIVE);
      }
      // Start initiation ritual if possible
      RitualCoordinator.startPlayerRitual(
         level, village, ResourceLocations.initiation(), player.getUUID(), pickLeader(data, village, chapter)
      );
      data.setDirty();
      village.markDirty();
   }

   private static void handleSupport(ReligionSavedData data, @Nullable Village village, @Nullable Chapter chapter) {
      if (chapter == null || village == null) {
         return;
      }
      chapter.addCohesion(10);
      chapter.addSecrecy(-15);
      chapter.setTensionContribution(chapter.tensionContribution() + 20);
      village.addReligionTension(15);
      data.setDirty();
      village.markDirty();
   }

   private static void handleReform(ReligionSavedData data, @Nullable Village village, @Nullable Chapter chapter) {
      if (chapter == null || village == null) {
         return;
      }
      chapter.addCohesion(15);
      chapter.addSecrecy(5);
      chapter.setTensionContribution(Math.max(0, chapter.tensionContribution() - 10));
      village.addReligionTension(-10);
      data.setDirty();
      village.markDirty();
   }

   private static void handleLeave(
      ReligionSavedData data, ServerPlayer player, @Nullable Village village, @Nullable Chapter chapter
   ) {
      data.removeMembership(Membership.SubjectType.PLAYER, player.getUUID());
      if (village != null) {
         village.addReligionTension(-2);
         village.markDirty();
      }
      data.setDirty();
   }

   private static void handleOppose(ServerLevel level, ReligionSavedData data, @Nullable Village village, @Nullable Chapter chapter) {
      if (chapter == null || village == null) {
         return;
      }
      chapter.setStage(Chapter.Stage.EXPOSED);
      OrderInstance order = data.getOrder(chapter.orderInstanceId());
      if (order != null) {
         order.setExposed(true);
      }
      for (Membership m : data.membershipsForChapter(chapter.id())) {
         if (m.subjectType() == Membership.SubjectType.VILLAGER) {
            VillagerRecord rec = village.getVillagerRecord(m.subjectId());
            if (rec != null) {
               ReligionService.leaveChapter(data, village, rec);
            }
         } else {
            data.removeMembership(Membership.SubjectType.PLAYER, m.subjectId());
         }
      }
      chapter.setStage(Chapter.Stage.DISBANDED);
      if (order != null) {
         order.setDisbanded(true);
      }
      village.addReligionTension(30);
      village.setChapterId(null);
      village.recordChronicleEvent(level, VillageEventType.ORDER_EXPOSED, chapter.id().toString(), "last_flame");
      data.setDirty();
      village.markDirty();
      LOGGER.info("[Religion] Chapter {} exposed and disbanded in {}", chapter.id(), village.getVillageName());
   }

   @Nullable
   private static UUID pickLeader(ReligionSavedData data, Village village, Chapter chapter) {
      UUID best = null;
      int bestDev = -1;
      for (Membership m : data.membershipsForChapter(chapter.id())) {
         if (m.subjectType() != Membership.SubjectType.VILLAGER) {
            continue;
         }
         if (m.role().canLeadRitual()) {
            return m.subjectId();
         }
         if (m.devotion() > bestDev) {
            bestDev = m.devotion();
            best = m.subjectId();
         }
      }
      return best;
   }

   private static final class ResourceLocations {
      static net.minecraft.resources.ResourceLocation initiation() {
         return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("millenaire", "initiation");
      }
   }
}
