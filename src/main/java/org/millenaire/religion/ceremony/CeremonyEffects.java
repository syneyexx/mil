package org.millenaire.religion.ceremony;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.living.social.NpcRelationship;
import org.millenaire.living.social.NpcRelationshipType;
import org.millenaire.living.social.NpcSocialProfile;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.living.social.SocialSavedData;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Applies social/economic outcomes once per completed ceremony.
 */
public final class CeremonyEffects {
   private CeremonyEffects() {
   }

   public static void applyIfNeeded(ServerLevel level, Village village, CeremonyInstance ceremony, RitualDefinition ritual) {
      if (ceremony.outcomeApplied()) {
         return;
      }
      ceremony.setOutcomeApplied(true);
      ReligionSavedData data = ReligionSavedData.get(level);
      var community = data.communityFor(village.getId().uuid());
      community.addHistory("completed:" + ritual.id() + ":" + ceremony.ceremonyId());
      community.setLastCeremonyDay(level.getDayTime() / 24000L);
      if (ritual.kind() == org.millenaire.religion.model.RitualKind.MEMORIAL) {
         community.setLastMemorialDay(level.getDayTime() / 24000L);
      }
      if (ritual.kind() == org.millenaire.religion.model.RitualKind.CONSECRATION && ceremony.buildingId() != null) {
         community.consecratedBuildings().add(ceremony.buildingId());
      }
      data.setDirty();

      LivingEventService.emit(
         level,
         LivingEventType.CEREMONY_COMPLETED,
         LivingEventSource.SYSTEM,
         village,
         ceremony.leaderVillagerId(),
         null,
         ritual.id().toString(),
         "public",
         "ceremony_done:" + ceremony.ceremonyId()
      );

      if (!livingSocialEnabled()) {
         return;
      }
      SocialSavedData social = SocialSavedData.get(level);
      long now = level.getGameTime();
      UUID[] participants = ceremony.creditedParticipants().toArray(UUID[]::new);
      for (UUID id : participants) {
         VillagerRecord record = village.getVillagerRecords().get(id);
         if (record == null || record.isKilled()) {
            continue;
         }
         NpcFaithProfile faith = NpcFaithService.ensureInitialized(level, village, record);
         if (faith.primaryAffiliation() != null) {
            faith.primaryAffiliation().adjustDevotion(2);
            faith.recordParticipation(ceremony.ceremonyId().toString());
         }
         NpcSocialProfile profile = social.getOrCreate(id);
         NpcSocialService.ensurePersonality(profile, record);
         profile.setMood(moodFor(ritual), now);
         // Bounded relationship boost among co-participants (cap fan-out).
         int links = 0;
         for (UUID other : participants) {
            if (other.equals(id) || links >= 4) {
               continue;
            }
            NpcRelationship rel = profile.getOrCreateRelationship(other, NpcRelationshipType.ACQUAINTANCE);
            rel.adjust(2, 1, 2, 1, 0, now);
            links++;
         }
      }
      social.setDirty();
      data.setDirty();
   }

   private static String moodFor(RitualDefinition ritual) {
      return switch (ritual.kind()) {
         case MEMORIAL -> "sad";
         case FEAST -> "happy";
         default -> "calm";
      };
   }

   private static boolean livingSocialEnabled() {
      try {
         return MillenaireServerConfig.SERVER.livingVillagesEnabled.get()
            && MillenaireServerConfig.SERVER.npcSocialEnabled.get();
      } catch (Exception e) {
         return true;
      }
   }
}
