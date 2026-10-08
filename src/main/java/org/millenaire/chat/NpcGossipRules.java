package org.millenaire.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class NpcGossipRules {
   public static final int MAX_ACTIVE_RUMORS_PER_CULTURE = 48;
   public static final int DEDUP_DAY_WINDOW = 3;
   private static final Set<String> GREETINGS = Set.of(
      "hallo",
      "hoi",
      "hey",
      "hi",
      "hello",
      "bonjour",
      "salut",
      "bonsoir",
      "goedemorgen",
      "goedenavond",
      "goedendag",
      "dag",
      "yo",
      "merci",
      "thanks",
      "dankjewel",
      "dank je"
   );
   private static final Set<String> STOP_WORDS = Set.of("de", "het", "een", "and", "the", "that", "with", "vous", "dans");

   private NpcGossipRules() {
   }

   public static boolean isGreetingOnly(String sanitized) {
      if (sanitized == null) {
         return true;
      }
      String trimmed = sanitized.trim().toLowerCase(Locale.ROOT);
      return GREETINGS.contains(trimmed);
   }

   public static String normalizeCanonical(String text) {
      if (text == null) {
         return "";
      }
      return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
   }

   public static String buildCanonical(@Nullable String gossipLine, String playerName, String playerText) {
      String line = gossipLine == null ? "" : gossipLine.trim().replace('\n', ' ').replace('\r', ' ');
      if (!line.isEmpty()) {
         return NpcMind.truncate(line, 160);
      }
      return NpcMind.truncate(playerName + " said: " + playerText, 160);
   }

   public static boolean canCreateGossip(
      boolean configGossip,
      boolean hasVillageCulture,
      boolean isChild,
      boolean secretDetector,
      boolean keepSecret,
      int gossipWorthy,
      String sanitizedPlayerText
   ) {
      if (!configGossip || !hasVillageCulture || isChild) {
         return false;
      }
      if (secretDetector || keepSecret) {
         return false;
      }
      if (gossipWorthy != 1 && gossipWorthy != 2) {
         return false;
      }
      if (sanitizedPlayerText == null || sanitizedPlayerText.trim().length() < 12) {
         return false;
      }
      return !isGreetingOnly(sanitizedPlayerText);
   }

   @Nullable
   public static NpcRumor findDedup(List<NpcRumor> active, ResourceLocation cultureId, UUID aboutPlayer, String canonical, long currentDay) {
      String norm = normalizeCanonical(canonical);
      for (NpcRumor rumor : active) {
         if (!rumor.isActive()) {
            continue;
         }
         if (!rumor.cultureId().equals(cultureId) || !rumor.aboutPlayer().equals(aboutPlayer)) {
            continue;
         }
         if (!normalizeCanonical(rumor.canonical()).equals(norm)) {
            continue;
         }
         if (currentDay - rumor.createdDay() < DEDUP_DAY_WINDOW) {
            return rumor;
         }
      }
      return null;
   }

   @Nullable
   public static NpcRumor findCorrectionTarget(List<NpcRumor> active, ResourceLocation cultureId, UUID aboutPlayer, String canonical, int gossipWorthy) {
      if (gossipWorthy != 2) {
         return null;
      }
      List<String> words = contentWords(canonical);
      if (words.size() < 4) {
         return null;
      }
      for (NpcRumor rumor : active) {
         if (!rumor.isActive()) {
            continue;
         }
         if (!rumor.cultureId().equals(cultureId) || !rumor.aboutPlayer().equals(aboutPlayer)) {
            continue;
         }
         if (normalizeCanonical(rumor.canonical()).equals(normalizeCanonical(canonical))) {
            continue;
         }
         if (sharedContentWords(words, contentWords(rumor.canonical())) >= 4) {
            return rumor;
         }
      }
      return null;
   }

   public static boolean canAcceptNewActiveRumor(List<NpcRumor> activeForCulture, List<NpcRumorKnowledge> allKnowledge, long currentDay) {
      long activeCount = activeForCulture.stream().filter(NpcRumor::isActive).count();
      if (activeCount < MAX_ACTIVE_RUMORS_PER_CULTURE) {
         return true;
      }
      return findEvictableRumor(activeForCulture, allKnowledge, currentDay) != null;
   }

   @Nullable
   public static NpcRumor findEvictableRumor(List<NpcRumor> activeForCulture, List<NpcRumorKnowledge> allKnowledge, long currentDay) {
      NpcRumor oldest = null;
      for (NpcRumor rumor : activeForCulture) {
         if (!rumor.isActive()) {
            continue;
         }
         long newestLearn = rumor.createdDay();
         for (NpcRumorKnowledge k : allKnowledge) {
            if (k.rumorId() == rumor.id() && k.learnedDay() > newestLearn) {
               newestLearn = k.learnedDay();
            }
         }
         if (currentDay - newestLearn < 7L) {
            continue;
         }
         if (oldest == null || rumor.createdDay() < oldest.createdDay() || rumor.createdDay() == oldest.createdDay() && rumor.id() < oldest.id()) {
            oldest = rumor;
         }
      }
      return oldest;
   }

   public static boolean maySpreadToday(long currentDay, long createdDay) {
      return currentDay > createdDay;
   }

   public static double sameVillageChance(boolean sameHome, boolean tellingInfluential) {
      double chance = sameHome ? 0.45 : 0.30;
      if (tellingInfluential) {
         chance += 0.15;
      }
      return Math.min(0.85, chance);
   }

   public static double crossVillageChance(boolean related, boolean tellingInfluential) {
      double chance = 0.06;
      if (related) {
         chance += 0.06;
      }
      if (tellingInfluential) {
         chance += 0.10;
      }
      return Math.min(0.40, chance);
   }

   public static boolean roll(long rumorId, long currentDay, UUID knowerUuid, double chance) {
      Random random = new Random(rumorId * 31L + currentDay * 131L + knowerUuid.hashCode());
      return random.nextDouble() < chance;
   }

   public static int nextHop(int tellerHop) {
      return tellerHop + 1;
   }

   public static float nextConfidence(float tellerConfidence) {
      return Math.max(0.15F, tellerConfidence * 0.8F);
   }

   public static boolean cultureMismatch(ResourceLocation rumorCulture, ResourceLocation targetCulture) {
      return rumorCulture == null || targetCulture == null || !rumorCulture.equals(targetCulture);
   }

   public static boolean isInfluential(boolean chief, boolean seller) {
      return chief || seller;
   }

   public static String confidenceLabel(float confidence) {
      if (confidence >= 0.75F) {
         return "HIGH";
      }
      if (confidence >= 0.4F) {
         return "MEDIUM";
      }
      return "LOW";
   }

   public static List<String> contentWords(String text) {
      List<String> words = new ArrayList<>();
      for (String word : NpcMind.significantWords(text)) {
         if (!STOP_WORDS.contains(word)) {
            words.add(word);
         }
      }
      return words;
   }

   private static int sharedContentWords(List<String> a, List<String> b) {
      Set<String> set = new HashSet<>(b);
      int count = 0;
      for (String w : a) {
         if (set.contains(w)) {
            count++;
         }
      }
      return count;
   }

   public static List<UUID> sortedUuids(List<UUID> input) {
      List<UUID> copy = new ArrayList<>(input);
      copy.sort(Comparator.naturalOrder());
      return copy;
   }

   public static boolean shouldExpire(long currentDay, long createdDay, int expiryDays, long newestLearnDay) {
      return currentDay - createdDay > expiryDays && currentDay - newestLearnDay > 10L;
   }
}
