package org.millenaire.religion.daily;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.impl.VisitBuildingGoal;
import org.millenaire.living.social.NpcSocialProfile;
import org.millenaire.living.social.NpcSocialService;
import org.millenaire.living.social.SocialSavedData;
import org.millenaire.religion.ReligionService;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.FaithAffiliation;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Credits completed religious visit goals as daily practice (once per day per NPC).
 */
public final class DailyPracticeService {
   public DailyPracticeService() {
   }

   public void onReligiousVisitCompleted(
      ServerLevel level,
      Village village,
      VillagerRecord record,
      VisitBuildingGoal goal,
      StopReason reason
   ) {
      if (reason != StopReason.COMPLETED || !enabled()) {
         return;
      }
      NpcFaithProfile profile = NpcFaithService.ensureInitialized(level, village, record);
      FaithAffiliation primary = profile.primaryAffiliation();
      if (primary == null || primary.devotion() < 10) {
         return;
      }
      long day = ReligionService.get().calendar().dayNumber(level);
      if (profile.lastDailyPracticeDay() == day) {
         return;
      }
      String tag = goal.buildingTag();
      if (tag == null || tag.isEmpty()) {
         return;
      }
      boolean matched = false;
      for (ResourceLocation ritualId : ReligionContentRegistry.allRituals().stream().map(RitualDefinition::id).toList()) {
         RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(ritualId);
         if (ritual == null || ritual.kind() != org.millenaire.religion.model.RitualKind.DAILY) {
            continue;
         }
         if (!ritual.traditionId().equals(primary.traditionId())) {
            continue;
         }
         if (ritual.matchesLocationTag(tag)) {
            matched = true;
            break;
         }
      }
      // Also accept legacy religious tags even if ritual JSON differs slightly.
      if (!matched) {
         matched = isLegacyReligiousTag(tag);
      }
      if (!matched) {
         return;
      }
      profile.setLastDailyPracticeDay(day);
      primary.adjustDevotion(1);
      ReligionSavedData.get(level).setDirty();

      // Mild positive mood when Living Villages personality is on.
      try {
         if (MillenaireServerConfig.SERVER.npcPersonalityEnabled.get()) {
            SocialSavedData social = SocialSavedData.get(level);
            NpcSocialProfile sp = social.getOrCreate(record.getUuid());
            NpcSocialService.ensurePersonality(sp, record);
            sp.setMood("calm", level.getGameTime());
            social.setDirty();
         }
      } catch (Exception ignored) {
      }
   }

   private static boolean isLegacyReligiousTag(String tag) {
      return switch (tag.toLowerCase()) {
         case "praying", "pujas", "sacrifices", "mosque", "mosquefemaleside", "pilgrimsite" -> true;
         default -> false;
      };
   }

   private static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.religionEnabled.get();
      } catch (Exception e) {
         return true;
      }
   }
}
