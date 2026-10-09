package org.millenaire.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerIdentity;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public final class NpcPersonaBuilder {
   private NpcPersonaBuilder() {
   }

   public static NpcPersona build(
      ServerPlayer player,
      MillVillager villager,
      NpcMind mind,
      NpcMindSavedData data,
      String sanitizedSpeech,
      String languageCode,
      int reputation,
      String reputationLabel
   ) {
      VillagerIdentity identity = villager.getIdentity();
      VillagerType vType = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      Village village = villager.getVillageId() != null ? Village.resolve(player.serverLevel(), villager.getVillageId()) : null;
      ResourceLocation cultureId = village != null ? village.getCultureId() : (vType != null ? vType.culture() : null);
      Culture culture = cultureId != null ? ModCultures.getCulture(cultureId) : null;
      VillageType villageType = village != null ? ModCultures.getVillageType(village.getVillageTypeId()) : null;

      String name = identity.getFirstName();
      if (name == null || name.isBlank()) {
         name = villager.getVillagerDisplayName();
      }
      String family = identity.getFamilyName() == null ? "" : identity.getFamilyName();
      String role = identity.getRoleName() == null ? "" : identity.getRoleName();
      String nativeName = vType != null && vType.nativeName() != null ? vType.nativeName() : "";
      String gender = vType != null && vType.gender() != null ? vType.gender().name().toLowerCase() : "unknown";
      boolean child = vType != null && vType.isChild();
      List<String> tags = vType != null ? List.copyOf(vType.tags()) : List.of();

      String languageAside = "";
      if (cultureId != null) {
         int langScore;
         if (!(Boolean)org.millenaire.config.MillenaireServerConfig.SERVER.languageLearning.get()) {
            langScore = Integer.MAX_VALUE;
         } else {
            langScore = PlayerCultureReputation.get(player.serverLevel()).getLanguageKnowledge(player.getUUID(), cultureId);
         }
         if (langScore >= PlayerCultureReputation.LANGUAGE_FLUENT) {
            languageAside = "the stranger speaks your tongue well";
         } else if (langScore < PlayerCultureReputation.LANGUAGE_BEGINNER) {
            languageAside = "the stranger speaks your tongue poorly";
         }
      }

      ServerLevel level = player.serverLevel();
      long dayTime = level.getDayTime() % 24000L;
      String timeOfDay;
      if (dayTime < 6000L) {
         timeOfDay = "morning";
      } else if (dayTime < 12000L) {
         timeOfDay = "afternoon";
      } else if (dayTime < 18000L) {
         timeOfDay = "evening";
      } else {
         timeOfDay = "night";
      }

      NpcOpinion opinion = mind.getOpinion(player.getUUID());
      List<String> quoteLines = new ArrayList<>();
      for (NpcQuote q : mind.recentQuotes(player.getUUID(), 12)) {
         quoteLines.add("- " + q.text());
      }
      for (NpcQuote q : mind.matchingOlderQuotes(player.getUUID(), sanitizedSpeech, 12, 4)) {
         quoteLines.add("- " + q.text());
      }
      List<String> turnLines = new ArrayList<>();
      for (NpcTurn t : mind.recentTurns(player.getUUID(), 8)) {
         turnLines.add(t.role() + ": " + t.text());
      }
      List<String> factLines = new ArrayList<>();
      for (NpcFact f : mind.selectFactsForPrompt(player.getUUID(), sanitizedSpeech, 8)) {
         factLines.add("- [" + f.importance() + "] " + f.text());
      }
      List<String> rumorLines = buildRumorLines(mind, data, village, level);

      boolean hired = player.getUUID().equals(villager.getHiredBy());
      String goal = villager.getGoalLabel();
      if (goal == null || goal.isBlank()) {
         goal = "idle";
      }

      return new NpcPersona(
         name,
         family,
         role,
         nativeName,
         gender,
         child,
         nullToEmpty(identity.getFathersName()),
         nullToEmpty(identity.getMothersName()),
         nullToEmpty(identity.getSpousesName()),
         cultureId != null ? cultureId.toString() : "",
         culture != null ? culture.displayName() : "",
         village != null && village.getVillageName() != null ? village.getVillageName() : "",
         villageType != null ? villageType.id().getPath() : "",
         tags,
         goal,
         village != null && village.isUnderAttack(),
         timeOfDay,
         level.isRaining(),
         reputation,
         reputationLabel,
         languageAside,
         hired,
         mind.getMood(),
         mind.getLastThought(),
         opinion.trust(),
         opinion.fear(),
         opinion.affection(),
         mind.getSummary(),
         factLines,
         quoteLines,
         turnLines,
         rumorLines,
         languageCode,
         sanitizedSpeech
      );
   }

   public static List<String> buildRumorLinesPublic(NpcMind mind, NpcMindSavedData data, @Nullable Village village) {
      return buildRumorLines(mind, data, village, null);
   }

   private static List<String> buildRumorLines(NpcMind mind, NpcMindSavedData data, @Nullable Village village, @Nullable ServerLevel level) {
      List<NpcRumorKnowledge> known = new ArrayList<>(mind.getKnowledge());
      known.sort(
         Comparator.comparingDouble(NpcRumorKnowledge::confidence)
            .reversed()
            .thenComparingLong(NpcRumorKnowledge::learnedDay)
            .reversed()
      );
      List<String> lines = new ArrayList<>();
      for (NpcRumorKnowledge k : known) {
         if (lines.size() >= 5) {
            break;
         }
         NpcRumor rumor = data.getRumor(k.rumorId());
         if (rumor == null || !rumor.isActive()) {
            continue;
         }
         if (village != null && NpcGossipRules.cultureMismatch(rumor.cultureId(), village.getCultureId())) {
            continue;
         }
         lines.add(formatRumorLine(k, rumor, data, village));
      }
      return lines;
   }

   private static String formatRumorLine(NpcRumorKnowledge k, NpcRumor rumor, NpcMindSavedData data, @Nullable Village village) {
      if (k.hop() == 0) {
         return "FIRSTHAND (you heard this from the player, not as gossip): " + rumor.canonical();
      }
      String conf = NpcGossipRules.confidenceLabel(k.confidence());
      if (k.hop() == 1) {
         String tellerName = "someone in your village";
         String villageName = village != null && village.getVillageName() != null ? village.getVillageName() : "your village";
         if (k.toldBy() != null && village != null) {
            VillagerRecord record = village.getVillagerRecord(k.toldBy());
            if (record != null && record.getFirstName() != null && !record.getFirstName().isBlank()) {
               tellerName = record.getFirstName();
            } else {
               Village other = findRecordVillage(data, k.toldBy());
               if (other != null) {
                  VillagerRecord rec = other.getVillagerRecord(k.toldBy());
                  if (rec != null && rec.getFirstName() != null && !rec.getFirstName().isBlank()) {
                     tellerName = rec.getFirstName();
                  }
                  if (other.getVillageName() != null) {
                     villageName = other.getVillageName();
                  }
               }
            }
         }
         return "GOSSIP you believe (confidence "
            + conf
            + "). "
            + tellerName
            + " in "
            + villageName
            + " told you: "
            + rumor.canonical()
            + ". You did not hear this from the player. Do not add facts.";
      }
      return "VAGUE GOSSIP (confidence LOW). You do not remember who told you. You only know: "
         + rumor.canonical()
         + ". You are unsure. Do not add facts. You may say you might be wrong.";
   }

   @Nullable
   private static Village findRecordVillage(NpcMindSavedData data, UUID villagerId) {
      // Best-effort: search is done by callers through VillageManager when needed.
      return null;
   }

   private static String nullToEmpty(@Nullable String value) {
      return value == null ? "" : value;
   }
}
