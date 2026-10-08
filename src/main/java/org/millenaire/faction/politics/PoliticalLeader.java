package org.millenaire.faction.politics;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Political leader record. May reference an existing Millénaire NPC UUID without duplicating NPC identity.
 */
public final class PoliticalLeader {
   private final LeaderId id;
   private final FactionId factionId;
   @Nullable
   private String displayName;
   private LeadershipRole role;
   private int legitimacy = PoliticsConfig.LEGITIMACY_BASE;
   private int popularity = PoliticsConfig.POPULARITY_BASE;
   private int competence = 50;
   private int militaryInfluence = 40;
   private int economicInfluence = 40;
   private int diplomaticInfluence = 40;
   private int politicalInfluence = 50;
   private final long leadershipStartGameTime;
   private boolean active = true;
   private long endedGameTime;
   @Nullable
   private VillageId originSettlementId;
   @Nullable
   private java.util.UUID linkedNpcUuid;

   public PoliticalLeader(LeaderId id, FactionId factionId, LeadershipRole role, long gameTime, @Nullable String displayName) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.role = role == null ? LeadershipRole.RULER : role;
      this.leadershipStartGameTime = gameTime;
      this.displayName = displayName;
   }

   public LeaderId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public String getDisplayName() {
      return this.displayName;
   }

   public LeadershipRole getRole() {
      return this.role;
   }

   public int getLegitimacy() {
      return this.legitimacy;
   }

   public int getPopularity() {
      return this.popularity;
   }

   public int getCompetence() {
      return this.competence;
   }

   public int getMilitaryInfluence() {
      return this.militaryInfluence;
   }

   public int getEconomicInfluence() {
      return this.economicInfluence;
   }

   public int getDiplomaticInfluence() {
      return this.diplomaticInfluence;
   }

   public int getPoliticalInfluence() {
      return this.politicalInfluence;
   }

   public long getLeadershipStartGameTime() {
      return this.leadershipStartGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   @Nullable
   public VillageId getOriginSettlementId() {
      return this.originSettlementId;
   }

   @Nullable
   public java.util.UUID getLinkedNpcUuid() {
      return this.linkedNpcUuid;
   }

   void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   void setRole(LeadershipRole role) {
      this.role = role == null ? LeadershipRole.RULER : role;
   }

   void setLegitimacy(int legitimacy) {
      this.legitimacy = PoliticsConfig.clamp(legitimacy);
   }

   void setPopularity(int popularity) {
      this.popularity = PoliticsConfig.clamp(popularity);
   }

   void setCompetence(int competence) {
      this.competence = PoliticsConfig.clamp(competence);
   }

   void setMilitaryInfluence(int value) {
      this.militaryInfluence = PoliticsConfig.clamp(value);
   }

   void setEconomicInfluence(int value) {
      this.economicInfluence = PoliticsConfig.clamp(value);
   }

   void setDiplomaticInfluence(int value) {
      this.diplomaticInfluence = PoliticsConfig.clamp(value);
   }

   void setPoliticalInfluence(int value) {
      this.politicalInfluence = PoliticsConfig.clamp(value);
   }

   void setOriginSettlementId(@Nullable VillageId settlementId) {
      this.originSettlementId = settlementId;
   }

   void setLinkedNpcUuid(@Nullable java.util.UUID npcUuid) {
      this.linkedNpcUuid = npcUuid;
   }

   void end(long gameTime) {
      this.active = false;
      this.endedGameTime = gameTime;
   }

   void loadState(
      @Nullable String displayName,
      LeadershipRole role,
      int legitimacy,
      int popularity,
      int competence,
      int militaryInfluence,
      int economicInfluence,
      int diplomaticInfluence,
      int politicalInfluence,
      boolean active,
      long ended,
      @Nullable VillageId originSettlementId,
      @Nullable java.util.UUID linkedNpcUuid
   ) {
      this.displayName = displayName;
      this.role = role == null ? LeadershipRole.RULER : role;
      this.legitimacy = PoliticsConfig.clamp(legitimacy);
      this.popularity = PoliticsConfig.clamp(popularity);
      this.competence = PoliticsConfig.clamp(competence);
      this.militaryInfluence = PoliticsConfig.clamp(militaryInfluence);
      this.economicInfluence = PoliticsConfig.clamp(economicInfluence);
      this.diplomaticInfluence = PoliticsConfig.clamp(diplomaticInfluence);
      this.politicalInfluence = PoliticsConfig.clamp(politicalInfluence);
      this.active = active;
      this.endedGameTime = Math.max(0L, ended);
      this.originSettlementId = originSettlementId;
      this.linkedNpcUuid = linkedNpcUuid;
   }
}
