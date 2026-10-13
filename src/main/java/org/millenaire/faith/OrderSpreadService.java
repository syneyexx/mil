package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Bounded order spread via concrete completed visits / contacts — not a world-wide conversion sim.
 */
public final class OrderSpreadService {
   private static final int MAX_SPREADS_PER_EVENT = 1;

   private OrderSpreadService() {
   }

   /**
    * After a completed pilgrimage or successful ritual contact, optionally mark one INTERESTED NPC
    * in another chapter-bearing village. Caps prevent unbounded growth.
    */
   public static void onSuccessfulContact(ServerLevel level, Village source, @Nullable UUID contactRecordUuid) {
      if (!MillenaireServerConfig.SERVER.ordersEnabled.getAsBoolean()) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.npcOrderConversionEnabled.getAsBoolean()) {
         return;
      }
      ReligionSavedData data = ReligionSavedData.get(level);
      if (data.allMemberships().size() >= 4000) {
         return;
      }
      Chapter sourceChapter = data.getChapterForVillage(source.getId());
      if (sourceChapter == null || sourceChapter.stage() == Chapter.Stage.DISBANDED) {
         return;
      }
      int spreads = 0;
      for (Village other : org.millenaire.village.VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (spreads >= MAX_SPREADS_PER_EVENT) {
            break;
         }
         if (other.getId().equals(source.getId()) || other.isLoneBuilding()) {
            continue;
         }
         Chapter otherChapter = data.getChapterForVillage(other.getId());
         if (otherChapter == null || otherChapter.stage() == Chapter.Stage.DISBANDED) {
            continue;
         }
         // Prefer contact villager if provided and local; else first clergy-like member-less adult
         VillagerRecord candidate = contactRecordUuid != null ? other.getVillagerRecord(contactRecordUuid) : null;
         if (candidate == null) {
            for (VillagerRecord rec : other.getVillagerRecords().values()) {
               if (rec.isKilled()) {
                  continue;
               }
               if (data.getMembership(Membership.SubjectType.VILLAGER, rec.getUuid()) != null) {
                  continue;
               }
               candidate = rec;
               break;
            }
         }
         if (candidate != null && data.getMembership(Membership.SubjectType.VILLAGER, candidate.getUuid()) == null) {
            ReligionService.joinChapter(data, other, candidate, OrderRole.INTERESTED, 5, level.getGameTime());
            spreads++;
         }
      }
   }
}
