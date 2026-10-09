package org.millenaire.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

public final class NpcMind {
   public static final int MAX_FACTS = 40;
   public static final int MAX_TURNS = 12;
   public static final int MAX_QUOTES = 30;
   public static final int MAX_KNOWLEDGE = 8;
   public static final List<String> VALID_MOODS = List.of(
      "calm", "warm", "wary", "busy", "afraid", "proud", "angry", "playful", "grieving"
   );

   private String mood = "calm";
   private String lastThought = "";
   private String summary = "";
   private final List<NpcFact> facts = new ArrayList<>();
   private final Map<UUID, NpcOpinion> opinions = new HashMap<>();
   private final Map<UUID, List<NpcTurn>> turns = new HashMap<>();
   private final Map<UUID, List<NpcQuote>> quotes = new HashMap<>();
   private final Map<UUID, Long> lastTalkGameTime = new HashMap<>();
   private final List<NpcRumorKnowledge> knowledge = new ArrayList<>();
   private long nextFactId = 1L;
   private long nextQuoteId = 1L;
   /** Game time of last dialogue touch for transient (non-persistent) NPC minds. 0 = persistent / unspecified. */
   private long lastTransientTouchGameTime = 0L;
   private boolean transientMind = false;

   public String getMood() {
      return this.mood;
   }

   public void setMood(String mood) {
      this.mood = normalizeMood(mood);
   }

   public String getLastThought() {
      return this.lastThought;
   }

   public void setLastThought(String lastThought) {
      this.lastThought = truncate(lastThought == null ? "" : lastThought, 240);
   }

   public String getSummary() {
      return this.summary;
   }

   public void setSummary(String summary) {
      this.summary = truncate(summary == null ? "" : summary, 1200);
   }

   public List<NpcFact> getFacts() {
      return this.facts;
   }

   public Map<UUID, NpcOpinion> getOpinions() {
      return this.opinions;
   }

   public Map<UUID, List<NpcTurn>> getTurns() {
      return this.turns;
   }

   public Map<UUID, List<NpcQuote>> getQuotes() {
      return this.quotes;
   }

   public Map<UUID, Long> getLastTalkGameTime() {
      return this.lastTalkGameTime;
   }

   public List<NpcRumorKnowledge> getKnowledge() {
      return this.knowledge;
   }

   public long getNextFactId() {
      return this.nextFactId;
   }

   public void setNextFactId(long nextFactId) {
      this.nextFactId = Math.max(1L, nextFactId);
   }

   public long getNextQuoteId() {
      return this.nextQuoteId;
   }

   public void setNextQuoteId(long nextQuoteId) {
      this.nextQuoteId = Math.max(1L, nextQuoteId);
   }

   public NpcOpinion getOpinion(UUID playerId) {
      return this.opinions.getOrDefault(playerId, NpcOpinion.neutral());
   }

   public void applyOpinionDelta(UUID playerId, float trust, float fear, float affection) {
      NpcOpinion current = this.getOpinion(playerId);
      this.opinions.put(playerId, current.withDelta(clampDelta(trust), clampDelta(fear), clampDelta(affection)));
   }

   public NpcQuote addQuote(UUID playerId, String text, long gameTime) {
      List<NpcQuote> list = this.quotes.computeIfAbsent(playerId, k -> new ArrayList<>());
      NpcQuote quote = new NpcQuote(this.nextQuoteId++, text, gameTime);
      list.add(quote);
      while (list.size() > MAX_QUOTES) {
         list.remove(0);
      }
      this.lastTalkGameTime.put(playerId, gameTime);
      return quote;
   }

   public void addTurn(UUID playerId, String role, String text, long gameTime) {
      List<NpcTurn> list = this.turns.computeIfAbsent(playerId, k -> new ArrayList<>());
      list.add(new NpcTurn(role, text, gameTime));
      while (list.size() > MAX_TURNS) {
         list.remove(0);
      }
   }

   public void addFact(String text, long gameTime, int importance, @Nullable UUID aboutPlayer) {
      int imp = Math.max(1, Math.min(3, importance));
      this.facts.add(new NpcFact(this.nextFactId++, truncate(text, 140), gameTime, imp, aboutPlayer));
      while (this.facts.size() > MAX_FACTS) {
         this.facts.remove(0);
      }
   }

   public void replaceFacts(List<NpcFact> newFacts) {
      this.facts.clear();
      for (NpcFact fact : newFacts) {
         if (this.facts.size() >= MAX_FACTS) {
            break;
         }
         this.facts.add(fact);
      }
   }

   public void addOrRefreshKnowledge(NpcRumorKnowledge entry) {
      this.knowledge.removeIf(k -> k.rumorId() == entry.rumorId());
      this.knowledge.add(entry);
      while (this.knowledge.size() > MAX_KNOWLEDGE) {
         int dropIndex = findDroppableKnowledgeIndex();
         if (dropIndex < 0) {
            break;
         }
         this.knowledge.remove(dropIndex);
      }
   }

   public void removeKnowledge(long rumorId) {
      this.knowledge.removeIf(k -> k.rumorId() == rumorId);
   }

   public boolean knowsRumor(long rumorId) {
      for (NpcRumorKnowledge k : this.knowledge) {
         if (k.rumorId() == rumorId) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   public NpcRumorKnowledge getKnowledge(long rumorId) {
      for (NpcRumorKnowledge k : this.knowledge) {
         if (k.rumorId() == rumorId) {
            return k;
         }
      }
      return null;
   }

   public List<NpcQuote> recentQuotes(UUID playerId, int max) {
      List<NpcQuote> list = this.quotes.getOrDefault(playerId, List.of());
      if (list.isEmpty() || max <= 0) {
         return List.of();
      }
      int from = Math.max(0, list.size() - max);
      return List.copyOf(list.subList(from, list.size()));
   }

   public List<NpcTurn> recentTurns(UUID playerId, int max) {
      List<NpcTurn> list = this.turns.getOrDefault(playerId, List.of());
      if (list.isEmpty() || max <= 0) {
         return List.of();
      }
      int from = Math.max(0, list.size() - max);
      return List.copyOf(list.subList(from, list.size()));
   }

   public List<NpcQuote> matchingOlderQuotes(UUID playerId, String playerText, int recentKeep, int maxOlder) {
      List<NpcQuote> all = this.quotes.getOrDefault(playerId, List.of());
      if (all.isEmpty() || maxOlder <= 0) {
         return List.of();
      }
      int recentStart = Math.max(0, all.size() - recentKeep);
      List<String> queryWords = significantWords(playerText);
      if (queryWords.isEmpty()) {
         return List.of();
      }
      List<NpcQuote> matched = new ArrayList<>();
      for (int i = 0; i < recentStart && matched.size() < maxOlder; i++) {
         NpcQuote quote = all.get(i);
         for (String word : significantWords(quote.text())) {
            if (queryWords.contains(word)) {
               matched.add(quote);
               break;
            }
         }
      }
      return matched;
   }

   public List<NpcFact> selectFactsForPrompt(UUID playerId, String playerText, int max) {
      List<NpcFact> scored = new ArrayList<>(this.facts);
      scored.sort((a, b) -> {
         boolean aPlayer = playerId.equals(a.aboutPlayer());
         boolean bPlayer = playerId.equals(b.aboutPlayer());
         if (aPlayer != bPlayer) {
            return aPlayer ? -1 : 1;
         }
         if (a.importance() != b.importance()) {
            return Integer.compare(b.importance(), a.importance());
         }
         int aOverlap = wordOverlapCount(a.text(), playerText);
         int bOverlap = wordOverlapCount(b.text(), playerText);
         if (aOverlap != bOverlap) {
            return Integer.compare(bOverlap, aOverlap);
         }
         return Long.compare(b.gameTime(), a.gameTime());
      });
      if (scored.size() <= max) {
         return List.copyOf(scored);
      }
      return List.copyOf(scored.subList(0, max));
   }

   private int findDroppableKnowledgeIndex() {
      int best = -1;
      float bestConf = Float.MAX_VALUE;
      long bestDay = Long.MAX_VALUE;
      for (int i = 0; i < this.knowledge.size(); i++) {
         NpcRumorKnowledge k = this.knowledge.get(i);
         if (k.hop() == 0) {
            continue;
         }
         if (k.confidence() < bestConf || k.confidence() == bestConf && k.learnedDay() < bestDay) {
            best = i;
            bestConf = k.confidence();
            bestDay = k.learnedDay();
         }
      }
      if (best >= 0) {
         return best;
      }
      for (int i = 0; i < this.knowledge.size(); i++) {
         NpcRumorKnowledge k = this.knowledge.get(i);
         if (k.confidence() < bestConf || k.confidence() == bestConf && k.learnedDay() < bestDay) {
            best = i;
            bestConf = k.confidence();
            bestDay = k.learnedDay();
         }
      }
      return best;
   }

   public static String normalizeMood(String mood) {
      if (mood == null) {
         return "calm";
      }
      String normalized = mood.trim().toLowerCase(Locale.ROOT);
      return VALID_MOODS.contains(normalized) ? normalized : "calm";
   }

   public static float clampDelta(float value) {
      return Math.max(-0.15F, Math.min(0.15F, value));
   }

   public static String truncate(String text, int max) {
      if (text == null) {
         return "";
      }
      return text.length() <= max ? text : text.substring(0, max);
   }

   public static List<String> significantWords(String text) {
      if (text == null || text.isBlank()) {
         return List.of();
      }
      String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-zA-Zà-ÿÀ-Ÿ0-9]+");
      List<String> words = new ArrayList<>();
      for (String part : parts) {
         if (part.length() >= 4) {
            words.add(part);
         }
      }
      return words;
   }

   public static int wordOverlapCount(String a, String b) {
      List<String> aw = significantWords(a);
      List<String> bw = significantWords(b);
      int count = 0;
      for (String w : aw) {
         if (bw.contains(w)) {
            count++;
         }
      }
      return count;
   }

   public void pruneOldestFactsAbove(int keep) {
      while (this.facts.size() > keep) {
         this.facts.remove(0);
      }
   }

   public void removeKnowledgeOlderThan(long minLearnedDay, java.util.function.LongPredicate rumorExpiredOrSuperseded) {
      Iterator<NpcRumorKnowledge> it = this.knowledge.iterator();
      while (it.hasNext()) {
         NpcRumorKnowledge k = it.next();
         if (k.learnedDay() < minLearnedDay && rumorExpiredOrSuperseded.test(k.rumorId())) {
            it.remove();
         }
      }
   }

   public void touchTransient(long gameTime) {
      this.transientMind = true;
      this.lastTransientTouchGameTime = gameTime;
   }

   public boolean isTransientMind() {
      return this.transientMind;
   }

   public void setTransientMind(boolean transientMind) {
      this.transientMind = transientMind;
   }

   public long lastTransientTouchGameTime() {
      return this.lastTransientTouchGameTime;
   }

   public void setLastTransientTouchGameTime(long gameTime) {
      this.lastTransientTouchGameTime = gameTime;
   }

   public boolean isTransientExpired(long nowGameTime, long retentionTicks) {
      return this.transientMind && this.lastTransientTouchGameTime > 0L && nowGameTime - this.lastTransientTouchGameTime > retentionTicks;
   }
}
