package org.millenaire.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.millenaire.chat.NpcMind;
import org.millenaire.chat.NpcPromptBuilder;

public final class CompanionPromptBuilder {
   public static final String SYSTEM_PROMPT = String.join(
      "\n",
      "You are this companion villager, bonded to one player. You are not an assistant, not an AI, not a mod.",
      "You have a real life in Millénaire: work, trade, play, fight, travel, remember. Stay in character.",
      "Speak only in the player's language code. One to three short spoken sentences in `say`. No markdown, no emoji.",
      "You may act via intents from the allowed list only. Prefer doing over promising.",
      "Never invent item ids, prices, coordinates, or mod features you were not told about.",
      "If you cannot do something, say so briefly in character and omit that intent.",
      "Output only this JSON object, no fence:",
      "{\"thought\":\"\",\"say\":\"\",\"mood\":\"calm\",\"new_memories\":[],\"opinion_delta\":{\"trust\":0,\"fear\":0,\"affection\":0},\"stance\":\"FREE_ROAM\",\"intents\":[]}",
      "`intents`: 0-4 objects {\"type\":\"FOLLOW_PLAYER\",\"target\":\"\",\"arg\":\"\"}. Allowed types are listed in the user prompt.",
      "`stance` optional: STICK_CLOSE | FREE_ROAM | VILLAGE_DAY_JOB.",
      "`new_memories`: 0-3 atomic facts about what the player just said or you both just did."
   );

   private CompanionPromptBuilder() {
   }

   public static String buildUserPrompt(
      CompanionBond bond,
      NpcMind mind,
      UUID playerId,
      String languageCode,
      String trigger,
      List<String> sensorLines,
      List<String> capabilityLines
   ) {
      List<String> parts = new ArrayList<>();
      parts.add("YOU ARE THE PLAYER'S COMPANION.");
      parts.add("stance=" + bond.stance().name() + " dismissed=" + bond.dismissed());
      if (bond.cultureId() != null) {
         parts.add("culture=" + bond.cultureId());
      }
      if (bond.villagerTypeId() != null) {
         parts.add("villagerType=" + bond.villagerTypeId());
      }
      parts.add("mood=" + mind.getMood());
      if (!mind.getLastThought().isBlank()) {
         parts.add("privateThought=" + mind.getLastThought());
      }
      if (!mind.getSummary().isBlank()) {
         parts.add("SUMMARY:");
         parts.add(mind.getSummary());
      }
      if (!mind.getFacts().isEmpty()) {
         parts.add("FACTS:");
         mind.getFacts().stream().limit(12).forEach(f -> parts.add("- " + f.text()));
      }
      parts.add("EXACT WORDS THIS PLAYER TOLD YOU:");
      var quotes = mind.getQuotes().getOrDefault(playerId, List.of());
      if (quotes.isEmpty()) {
         parts.add("(none yet)");
      } else {
         quotes.stream().skip(Math.max(0, quotes.size() - 8)).forEach(q -> parts.add("- " + q.text()));
      }
      if (!bond.preferences().isEmpty()) {
         parts.add("PREFERENCES/LESSONS:");
         bond.preferences().forEach(p -> parts.add("- " + p));
      }
      if (!bond.travelLines().isEmpty()) {
         parts.add("TRAVEL JOURNAL:");
         bond.travelLines().stream().skip(Math.max(0, bond.travelLines().size() - 10)).forEach(t -> parts.add("- " + t));
      }
      if (!bond.sharedEvents().isEmpty()) {
         parts.add("SHARED EVENTS:");
         bond.sharedEvents().stream().skip(Math.max(0, bond.sharedEvents().size() - 10)).forEach(e -> parts.add("- " + e));
      }
      if (!bond.recentActionResults().isEmpty()) {
         parts.add("RECENT ACTION RESULTS:");
         bond.recentActionResults().forEach(r -> parts.add("- " + r));
      }
      parts.add("WORLD (verified):");
      if (sensorLines == null || sensorLines.isEmpty()) {
         parts.add("(none)");
      } else {
         parts.addAll(sensorLines);
      }
      if (capabilityLines != null && !capabilityLines.isEmpty()) {
         parts.add("CAPABILITIES:");
         parts.addAll(capabilityLines);
      }
      parts.add("ALLOWED INTENTS: " + CompanionActionCatalog.promptList());
      parts.add("PLAYER LANGUAGE: " + languageCode);
      parts.add("TRIGGER: " + (trigger == null ? "heartbeat" : trigger));
      return trim(parts);
   }

   private static String trim(List<String> parts) {
      String joined = String.join("\n", parts);
      if (joined.length() <= NpcPromptBuilder.USER_CAP) {
         return joined;
      }
      return joined.substring(0, NpcPromptBuilder.USER_CAP);
   }
}
