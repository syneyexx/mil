package org.millenaire.living.context;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded verified gameplay facts for prompt injection. Distinguishes certainty.
 */
public final class DialogueGameplayContext {
   private final List<String> verifiedFacts = new ArrayList<>();
   private final List<String> firsthandKnowledge = new ArrayList<>();
   private final List<String> secondhandKnowledge = new ArrayList<>();
   private final List<String> rumors = new ArrayList<>();
   private final List<String> unknowns = new ArrayList<>();
   private final List<String> actionHints = new ArrayList<>();

   public void addVerified(String line) {
      if (line != null && !line.isBlank() && this.verifiedFacts.size() < 12) {
         this.verifiedFacts.add(trim(line));
      }
   }

   public void addFirsthand(String line) {
      if (line != null && !line.isBlank() && this.firsthandKnowledge.size() < 8) {
         this.firsthandKnowledge.add(trim(line));
      }
   }

   public void addSecondhand(String line) {
      if (line != null && !line.isBlank() && this.secondhandKnowledge.size() < 8) {
         this.secondhandKnowledge.add(trim(line));
      }
   }

   public void addRumor(String line) {
      if (line != null && !line.isBlank() && this.rumors.size() < 6) {
         this.rumors.add(trim(line));
      }
   }

   public void addUnknown(String line) {
      if (line != null && !line.isBlank() && this.unknowns.size() < 4) {
         this.unknowns.add(trim(line));
      }
   }

   public void addActionHint(String line) {
      if (line != null && !line.isBlank() && this.actionHints.size() < 6) {
         this.actionHints.add(trim(line));
      }
   }

   public List<String> verifiedFacts() {
      return List.copyOf(this.verifiedFacts);
   }

   public List<String> firsthandKnowledge() {
      return List.copyOf(this.firsthandKnowledge);
   }

   public List<String> secondhandKnowledge() {
      return List.copyOf(this.secondhandKnowledge);
   }

   public List<String> rumors() {
      return List.copyOf(this.rumors);
   }

   public List<String> unknowns() {
      return List.copyOf(this.unknowns);
   }

   public List<String> actionHints() {
      return List.copyOf(this.actionHints);
   }

   public boolean isEmpty() {
      return this.verifiedFacts.isEmpty()
         && this.firsthandKnowledge.isEmpty()
         && this.secondhandKnowledge.isEmpty()
         && this.rumors.isEmpty()
         && this.actionHints.isEmpty();
   }

   public List<String> toPromptLines() {
      List<String> lines = new ArrayList<>();
      if (!this.verifiedFacts.isEmpty()) {
         lines.add("VERIFIED GAME STATE (authoritative; do not invent beyond this):");
         lines.addAll(this.verifiedFacts);
      }
      if (!this.firsthandKnowledge.isEmpty()) {
         lines.add("YOUR FIRSTHAND KNOWLEDGE:");
         lines.addAll(this.firsthandKnowledge);
      }
      if (!this.secondhandKnowledge.isEmpty()) {
         lines.add("SECONDHAND INFORMATION (do not claim you witnessed these):");
         lines.addAll(this.secondhandKnowledge);
      }
      if (!this.rumors.isEmpty()) {
         lines.add("RUMORS (uncertain):");
         lines.addAll(this.rumors);
      }
      if (!this.unknowns.isEmpty()) {
         lines.add("UNKNOWN / EXPRESS UNCERTAINTY IF ASKED:");
         lines.addAll(this.unknowns);
      }
      if (!this.actionHints.isEmpty()) {
         lines.add("POSSIBLE PLAYER HELP (server-validated only; never invent quests/rewards/items):");
         lines.addAll(this.actionHints);
      }
      lines.add("Never invent shortages, deliveries, rewards, or completed actions that are not listed above.");
      return lines;
   }

   private static String trim(String s) {
      return s.length() > 220 ? s.substring(0, 217) + "..." : s;
   }
}
