package org.millenaire.chat.dialogue;

import java.util.List;

/**
 * Adapter-extracted prompt context. Omitted fields stay empty rather than fabricated.
 */
public record NpcDialogueContext(
   String displayName,
   String roleLine,
   String reputationLabel,
   String speciesOrTypeLabel,
   String occupation,
   String cultureId,
   String cultureDisplayName,
   String settlementName,
   String faction,
   String ageCategory,
   String gender,
   boolean child,
   String familyFather,
   String familyMother,
   String familyPartner,
   List<String> tags,
   String currentTask,
   boolean underAttack,
   String timeOfDay,
   boolean raining,
   int reputation,
   String languageAside,
   boolean hiredByPlayer,
   String communicationInstructions,
   String fallbackLine,
   String systemPromptOverride
) {
   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder {
      private String displayName = "";
      private String roleLine = "";
      private String reputationLabel = "";
      private String speciesOrTypeLabel = "";
      private String occupation = "";
      private String cultureId = "";
      private String cultureDisplayName = "";
      private String settlementName = "";
      private String faction = "";
      private String ageCategory = "adult";
      private String gender = "unknown";
      private boolean child;
      private String familyFather = "";
      private String familyMother = "";
      private String familyPartner = "";
      private List<String> tags = List.of();
      private String currentTask = "idle";
      private boolean underAttack;
      private String timeOfDay = "day";
      private boolean raining;
      private int reputation;
      private String languageAside = "";
      private boolean hiredByPlayer;
      private String communicationInstructions = "";
      private String fallbackLine = "";
      private String systemPromptOverride = "";

      public Builder displayName(String v) {
         this.displayName = nullToEmpty(v);
         return this;
      }

      public Builder roleLine(String v) {
         this.roleLine = nullToEmpty(v);
         return this;
      }

      public Builder reputationLabel(String v) {
         this.reputationLabel = nullToEmpty(v);
         return this;
      }

      public Builder speciesOrTypeLabel(String v) {
         this.speciesOrTypeLabel = nullToEmpty(v);
         return this;
      }

      public Builder occupation(String v) {
         this.occupation = nullToEmpty(v);
         return this;
      }

      public Builder cultureId(String v) {
         this.cultureId = nullToEmpty(v);
         return this;
      }

      public Builder cultureDisplayName(String v) {
         this.cultureDisplayName = nullToEmpty(v);
         return this;
      }

      public Builder settlementName(String v) {
         this.settlementName = nullToEmpty(v);
         return this;
      }

      public Builder faction(String v) {
         this.faction = nullToEmpty(v);
         return this;
      }

      public Builder ageCategory(String v) {
         this.ageCategory = nullToEmpty(v);
         return this;
      }

      public Builder gender(String v) {
         this.gender = nullToEmpty(v);
         return this;
      }

      public Builder child(boolean v) {
         this.child = v;
         return this;
      }

      public Builder familyFather(String v) {
         this.familyFather = nullToEmpty(v);
         return this;
      }

      public Builder familyMother(String v) {
         this.familyMother = nullToEmpty(v);
         return this;
      }

      public Builder familyPartner(String v) {
         this.familyPartner = nullToEmpty(v);
         return this;
      }

      public Builder tags(List<String> v) {
         this.tags = v == null ? List.of() : List.copyOf(v);
         return this;
      }

      public Builder currentTask(String v) {
         this.currentTask = nullToEmpty(v);
         return this;
      }

      public Builder underAttack(boolean v) {
         this.underAttack = v;
         return this;
      }

      public Builder timeOfDay(String v) {
         this.timeOfDay = nullToEmpty(v);
         return this;
      }

      public Builder raining(boolean v) {
         this.raining = v;
         return this;
      }

      public Builder reputation(int v) {
         this.reputation = v;
         return this;
      }

      public Builder languageAside(String v) {
         this.languageAside = nullToEmpty(v);
         return this;
      }

      public Builder hiredByPlayer(boolean v) {
         this.hiredByPlayer = v;
         return this;
      }

      public Builder communicationInstructions(String v) {
         this.communicationInstructions = nullToEmpty(v);
         return this;
      }

      public Builder fallbackLine(String v) {
         this.fallbackLine = nullToEmpty(v);
         return this;
      }

      public Builder systemPromptOverride(String v) {
         this.systemPromptOverride = nullToEmpty(v);
         return this;
      }

      public NpcDialogueContext build() {
         return new NpcDialogueContext(
            this.displayName,
            this.roleLine,
            this.reputationLabel,
            this.speciesOrTypeLabel,
            this.occupation,
            this.cultureId,
            this.cultureDisplayName,
            this.settlementName,
            this.faction,
            this.ageCategory,
            this.gender,
            this.child,
            this.familyFather,
            this.familyMother,
            this.familyPartner,
            this.tags,
            this.currentTask,
            this.underAttack,
            this.timeOfDay,
            this.raining,
            this.reputation,
            this.languageAside,
            this.hiredByPlayer,
            this.communicationInstructions,
            this.fallbackLine,
            this.systemPromptOverride
         );
      }

      private static String nullToEmpty(String v) {
         return v == null ? "" : v;
      }
   }
}
