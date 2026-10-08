package org.millenaire.faction.politics;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class SuccessionCandidate {
   private final CandidateId id;
   private final FactionId factionId;
   @Nullable
   private final LeaderId leaderId;
   @Nullable
   private final PoliticalGroupId politicalGroupId;
   private int legitimacy;
   private int popularity;
   private int militaryInfluence;
   private int economicInfluence;
   private int politicalInfluence;
   private int successionClaim;
   private int support;
   @Nullable
   private String displayName;

   public SuccessionCandidate(
      CandidateId id,
      FactionId factionId,
      @Nullable LeaderId leaderId,
      @Nullable PoliticalGroupId politicalGroupId,
      @Nullable String displayName
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.leaderId = leaderId;
      this.politicalGroupId = politicalGroupId;
      this.displayName = displayName;
   }

   public CandidateId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public LeaderId getLeaderId() {
      return this.leaderId;
   }

   @Nullable
   public PoliticalGroupId getPoliticalGroupId() {
      return this.politicalGroupId;
   }

   public int getLegitimacy() {
      return this.legitimacy;
   }

   public int getPopularity() {
      return this.popularity;
   }

   public int getMilitaryInfluence() {
      return this.militaryInfluence;
   }

   public int getEconomicInfluence() {
      return this.economicInfluence;
   }

   public int getPoliticalInfluence() {
      return this.politicalInfluence;
   }

   public int getSuccessionClaim() {
      return this.successionClaim;
   }

   public int getSupport() {
      return this.support;
   }

   @Nullable
   public String getDisplayName() {
      return this.displayName;
   }

   public int score(SuccessionMechanism mechanism) {
      return switch (mechanism) {
         case HEREDITARY -> this.successionClaim * 2 + this.legitimacy;
         case ELECTIVE -> this.popularity * 2 + this.support;
         case COUNCIL_SELECTION -> this.politicalInfluence * 2 + this.support;
         case MILITARY_SELECTION -> this.militaryInfluence * 2 + this.legitimacy;
         case APPOINTMENT, CUSTOM -> this.politicalInfluence + this.legitimacy + this.support;
      };
   }

   void setScores(int legitimacy, int popularity, int military, int economic, int political, int claim, int support) {
      this.legitimacy = PoliticsConfig.clamp(legitimacy);
      this.popularity = PoliticsConfig.clamp(popularity);
      this.militaryInfluence = PoliticsConfig.clamp(military);
      this.economicInfluence = PoliticsConfig.clamp(economic);
      this.politicalInfluence = PoliticsConfig.clamp(political);
      this.successionClaim = PoliticsConfig.clamp(claim);
      this.support = PoliticsConfig.clamp(support);
   }

   void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   void loadState(int legitimacy, int popularity, int military, int economic, int political, int claim, int support, @Nullable String displayName) {
      this.setScores(legitimacy, popularity, military, economic, political, claim, support);
      this.displayName = displayName;
   }
}
