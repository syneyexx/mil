package org.millenaire.faction.politics;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Local political administrator for a settlement. References settlement/NPC identity; does not duplicate NPCs.
 */
public final class LocalAdministrator {
   private final VillageId settlementId;
   private final FactionId factionId;
   private LeadershipRole role = LeadershipRole.GOVERNOR;
   private int autonomy = 50;
   private int loyalty = 60;
   private int localInfluence = 40;
   @Nullable
   private LeaderId linkedLeaderId;
   @Nullable
   private java.util.UUID linkedNpcUuid;
   @Nullable
   private String displayName;
   private boolean active = true;

   public LocalAdministrator(VillageId settlementId, FactionId factionId) {
      this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
   }

   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public LeadershipRole getRole() {
      return this.role;
   }

   public int getAutonomy() {
      return this.autonomy;
   }

   public int getLoyalty() {
      return this.loyalty;
   }

   public int getLocalInfluence() {
      return this.localInfluence;
   }

   @Nullable
   public LeaderId getLinkedLeaderId() {
      return this.linkedLeaderId;
   }

   @Nullable
   public java.util.UUID getLinkedNpcUuid() {
      return this.linkedNpcUuid;
   }

   @Nullable
   public String getDisplayName() {
      return this.displayName;
   }

   public boolean isActive() {
      return this.active;
   }

   void setRole(LeadershipRole role) {
      this.role = role == null ? LeadershipRole.GOVERNOR : role;
   }

   void setAutonomy(int autonomy) {
      this.autonomy = PoliticsConfig.clamp(autonomy);
   }

   void setLoyalty(int loyalty) {
      this.loyalty = PoliticsConfig.clamp(loyalty);
   }

   void setLocalInfluence(int influence) {
      this.localInfluence = PoliticsConfig.clamp(influence);
   }

   void setLinkedLeaderId(@Nullable LeaderId leaderId) {
      this.linkedLeaderId = leaderId;
   }

   void setLinkedNpcUuid(@Nullable java.util.UUID npcUuid) {
      this.linkedNpcUuid = npcUuid;
   }

   void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   void deactivate() {
      this.active = false;
   }

   void loadState(
      LeadershipRole role,
      int autonomy,
      int loyalty,
      int localInfluence,
      @Nullable LeaderId linkedLeaderId,
      @Nullable java.util.UUID linkedNpcUuid,
      @Nullable String displayName,
      boolean active
   ) {
      this.role = role == null ? LeadershipRole.GOVERNOR : role;
      this.autonomy = PoliticsConfig.clamp(autonomy);
      this.loyalty = PoliticsConfig.clamp(loyalty);
      this.localInfluence = PoliticsConfig.clamp(localInfluence);
      this.linkedLeaderId = linkedLeaderId;
      this.linkedNpcUuid = linkedNpcUuid;
      this.displayName = displayName;
      this.active = active;
   }
}
