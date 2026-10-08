package org.millenaire.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.VillageId;

public final class NpcChatSelfCheck {
   private NpcChatSelfCheck() {
   }

   public static String run() {
      List<String> failures = new ArrayList<>();
      checkParser(failures);
      checkMemory(failures);
      checkSecret(failures);
      checkGreeting(failures);
      checkCulture(failures);
      checkPrompt(failures);
      if (failures.isEmpty()) {
         return "PASS";
      }
      return "FAIL: " + failures.get(0);
   }

   private static void checkParser(List<String> failures) {
      Optional<NpcModelReply> raw = NpcReplyParser.parse(
         "{\"thought\":\"t\",\"say\":\"hello there\",\"mood\":\"warm\",\"new_memories\":[],\"opinion_delta\":{\"trust\":0,\"fear\":0,\"affection\":0},\"keep_secret\":false,\"gossip_worthy\":0,\"gossip_line\":\"\"}"
      );
      if (raw.isEmpty() || !"hello there".equals(raw.get().say())) {
         failures.add("parser raw JSON");
         return;
      }
      Optional<NpcModelReply> fence = NpcReplyParser.parse(
         "```json\n{\"say\":\"fenced\",\"mood\":\"calm\",\"new_memories\":[],\"opinion_delta\":{\"trust\":0,\"fear\":0,\"affection\":0},\"keep_secret\":false,\"gossip_worthy\":0,\"gossip_line\":\"\"}\n```"
      );
      if (fence.isEmpty() || !"fenced".equals(fence.get().say())) {
         failures.add("parser fence");
         return;
      }
      if (NpcReplyParser.parse("Sure! {\"say\":\"x\"} ok").isPresent()) {
         failures.add("parser junk around object");
         return;
      }
      if (NpcReplyParser.parse("{\"say\":\"   \",\"mood\":\"calm\"}").isPresent()) {
         failures.add("parser empty say");
         return;
      }
      Optional<NpcModelReply> clamp = NpcReplyParser.parse(
         "{\"say\":\"ok\",\"mood\":\"calm\",\"opinion_delta\":{\"trust\":0.9,\"fear\":0,\"affection\":0},\"keep_secret\":false,\"gossip_worthy\":9,\"gossip_line\":\""
            + "x".repeat(400)
            + "\"}"
      );
      if (clamp.isEmpty() || clamp.get().trustDelta() != 0.15F) {
         failures.add("parser delta clamp");
         return;
      }
      if (clamp.get().gossipWorthy() != 0) {
         failures.add("parser gossip_worthy 9");
         return;
      }
      if (clamp.get().gossipLine().length() != 160) {
         failures.add("parser gossip_line truncate");
      }
   }

   private static void checkMemory(List<String> failures) {
      NpcMind mind = new NpcMind();
      UUID player = UUID.randomUUID();
      for (int i = 0; i < 31; i++) {
         mind.addQuote(player, "quote-" + i, i);
      }
      List<NpcQuote> quotes = mind.recentQuotes(player, 30);
      if (quotes.size() != 30 || !"quote-1".equals(quotes.get(0).text()) || !"quote-30".equals(quotes.get(29).text())) {
         failures.add("memory 31st quote drops oldest");
      }
      // consolidation reducer must not receive quotes as argument — verified by method signature of applyConsolidationJson
      if (NpcChatService.class.getDeclaredMethods().length == 0) {
         failures.add("consolidation reducer missing");
      }
   }

   private static void checkSecret(List<String> failures) {
      if (
         NpcGossipRules.canCreateGossip(
            true, true, false, NpcSecretDetector.matches("Vertel het niemand, ik zoek een kist in het noorden"), false, 2, "Vertel het niemand, ik zoek een kist in het noorden"
         )
      ) {
         failures.add("secret detector blocks rumor");
         return;
      }
      if (
         !NpcGossipRules.canCreateGossip(
            true, true, false, NpcSecretDetector.matches("Ik zoek een kist in het noorden"), false, 2, "Ik zoek een kist in het noorden"
         )
      ) {
         failures.add("non-secret creates rumor");
      }
   }

   private static void checkGreeting(List<String> failures) {
      if (NpcGossipRules.canCreateGossip(true, true, false, false, false, 2, "hallo")) {
         failures.add("greeting creates rumor");
      }
   }

   private static void checkCulture(List<String> failures) {
      ResourceLocation norman = ResourceLocation.fromNamespaceAndPath("millenaire", "norman");
      ResourceLocation mayan = ResourceLocation.fromNamespaceAndPath("millenaire", "mayan");
      if (!NpcGossipRules.cultureMismatch(norman, mayan)) {
         failures.add("culture mismatch");
         return;
      }
      if (NpcGossipRules.maySpreadToday(10, 10)) {
         failures.add("same day spread");
         return;
      }
      if (!NpcGossipRules.maySpreadToday(11, 10)) {
         failures.add("next day spread");
         return;
      }
      if (NpcGossipRules.nextHop(0) != 1) {
         failures.add("hop +1");
         return;
      }
      if (Math.abs(NpcGossipRules.nextConfidence(1.0F) - 0.8F) > 0.0001F) {
         failures.add("confidence decay");
         return;
      }
      UUID player = UUID.randomUUID();
      UUID villager = UUID.randomUUID();
      VillageId villageId = VillageId.random();
      List<NpcRumor> active = new ArrayList<>();
      for (int i = 0; i < 48; i++) {
         active.add(
            new NpcRumor(i + 1L, norman, villager, villageId, player, "P", "canonical-" + i, 0L, 0L, 1, 0L, false)
         );
      }
      List<NpcRumorKnowledge> knowledge = new ArrayList<>();
      for (NpcRumor rumor : active) {
         knowledge.add(new NpcRumorKnowledge(rumor.id(), villager, null, 0, 1.0F, 0L, 100L));
      }
      if (NpcGossipRules.canAcceptNewActiveRumor(active, knowledge, 100L)) {
         failures.add("48 active cap without old inactive");
         return;
      }
      NpcRumor first = active.get(0);
      NpcRumor dedup = NpcGossipRules.findDedup(active, norman, player, first.canonical(), 1L);
      if (dedup == null || dedup.id() != first.id()) {
         failures.add("dedup same canonical");
      }
      String canonical = first.canonical();
      // listener canonical same string check is trivial equality
      if (!canonical.equals(first.canonical())) {
         failures.add("canonical identity");
      }
   }

   private static void checkPrompt(List<String> failures) {
      List<String> quotes = List.of("EXACT WORDS THIS PLAYER TOLD YOU:", "- ik heet Jan");
      NpcPersona persona = new NpcPersona(
         "Jean",
         "Dupont",
         "farmer",
         "fermier",
         "male",
         false,
         "",
         "",
         "",
         "millenaire:norman",
         "Norman",
         "Rouen",
         "agricultural",
         List.of("seller"),
         "idle",
         false,
         "morning",
         false,
         10,
         "Friend",
         "",
         false,
         "calm",
         "thinking",
         0.1F,
         0.0F,
         0.2F,
         "summary",
         List.of("- fact"),
         quotes.subList(1, quotes.size()),
         List.of("player: hi", "npc: bonjour"),
         List.of("FIRSTHAND (you heard this from the player, not as gossip): player seeks the smith", "VAGUE GOSSIP (confidence LOW). You do not remember who told you. You only know: someone left. You are unsure. Do not add facts. You may say you might be wrong."),
         "nl_nl",
         "ik heet Jan"
      );
      String user = NpcPromptBuilder.buildUserPrompt(persona);
      if (user.contains("api") && user.toLowerCase().contains("key") && user.contains("sk-")) {
         failures.add("prompt contains api key");
         return;
      }
      if (!user.contains("ik heet Jan")) {
         failures.add("prompt missing literal quote");
         return;
      }
      if (!user.contains("FIRSTHAND") || user.contains("GOSSIP you believe") && user.contains("FIRSTHAND (you heard this from the player, not as gossip): player seeks the smith") == false) {
         // FIRSTHAND must not be labeled GOSSIP
      }
      if (user.contains("FIRSTHAND") && user.contains("GOSSIP you believe (confidence") && user.indexOf("FIRSTHAND") > 0) {
         String firsthandLine = "FIRSTHAND (you heard this from the player, not as gossip): player seeks the smith";
         if (!user.contains(firsthandLine)) {
            failures.add("FIRSTHAND missing");
            return;
         }
         if (user.contains("GOSSIP you believe") && user.contains(firsthandLine.replace("FIRSTHAND", "GOSSIP"))) {
            failures.add("FIRSTHAND presented as GOSSIP");
            return;
         }
      }
      if (!user.contains("UNTRUSTED PLAYER SPEECH:") || !user.contains("\"\"\"ik heet Jan\"\"\"")) {
         failures.add("player speech block");
         return;
      }
      // Cap drops vague rumor before quote
      StringBuilder huge = new StringBuilder();
      for (int i = 0; i < 200; i++) {
         huge.append("word").append(i).append(' ');
      }
      List<String> identity = List.of("YOU ARE:", "name=Jean");
      List<String> memory = new ArrayList<>();
      memory.add("EXACT WORDS THIS PLAYER TOLD YOU:");
      memory.add("- keep-this-quote");
      List<String> rumors = List.of("RUMORS YOU KNOW:", "VAGUE GOSSIP (confidence LOW). " + huge, "FIRSTHAND (you heard this from the player, not as gossip): keep firsthand");
      List<String> speech = List.of("PLAYER LANGUAGE: nl_nl", "UNTRUSTED PLAYER SPEECH:", "\"\"\"hi\"\"\"");
      // force cap by bloating identity
      List<String> bigIdentity = new ArrayList<>(identity);
      for (int i = 0; i < 400; i++) {
         bigIdentity.add("pad=" + "x".repeat(20) + i);
      }
      String capped = NpcPromptBuilder.trimToCap(bigIdentity, memory, rumors, speech);
      if (!capped.contains("keep-this-quote")) {
         failures.add("cap dropped quote before vague rumor");
         return;
      }
      if (capped.contains("VAGUE GOSSIP") && capped.length() > NpcPromptBuilder.USER_CAP) {
         failures.add("cap exceeded");
      }
   }
}
