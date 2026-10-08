package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PoliticalCoalition {
   private final CoalitionId id;
   private final FactionId factionId;
   private final Set<PoliticalGroupId> memberGroupIds = new LinkedHashSet<>();
   private boolean ruling;
   private int support;
   private int politicalPower;
   private int stability = 60;
   private final long createdGameTime;
   private boolean active = true;
   private long dissolvedGameTime;
   @Nullable
   private String sharedInterestNote;

   public PoliticalCoalition(CoalitionId id, FactionId factionId, long gameTime, boolean ruling) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.createdGameTime = gameTime;
      this.ruling = ruling;
   }

   public CoalitionId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public Set<PoliticalGroupId> getMemberGroupIds() {
      return Collections.unmodifiableSet(this.memberGroupIds);
   }

   public boolean isRuling() {
      return this.ruling;
   }

   public int getSupport() {
      return this.support;
   }

   public int getPoliticalPower() {
      return this.politicalPower;
   }

   public int getStability() {
      return this.stability;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getDissolvedGameTime() {
      return this.dissolvedGameTime;
   }

   @Nullable
   public String getSharedInterestNote() {
      return this.sharedInterestNote;
   }

   boolean addMember(PoliticalGroupId groupId) {
      return groupId != null && this.memberGroupIds.add(groupId);
   }

   boolean removeMember(PoliticalGroupId groupId) {
      return this.memberGroupIds.remove(groupId);
   }

   void setRuling(boolean ruling) {
      this.ruling = ruling;
   }

   void setSupport(int support) {
      this.support = PoliticsConfig.clamp(support);
   }

   void setPoliticalPower(int power) {
      this.politicalPower = PoliticsConfig.clamp(power);
   }

   void setStability(int stability) {
      this.stability = PoliticsConfig.clamp(stability);
   }

   void setSharedInterestNote(@Nullable String note) {
      this.sharedInterestNote = note;
   }

   void dissolve(long gameTime) {
      this.active = false;
      this.ruling = false;
      this.dissolvedGameTime = gameTime;
   }

   void loadState(
      Set<PoliticalGroupId> members,
      boolean ruling,
      int support,
      int politicalPower,
      int stability,
      boolean active,
      long dissolved,
      @Nullable String sharedInterestNote
   ) {
      this.memberGroupIds.clear();
      if (members != null) {
         this.memberGroupIds.addAll(members);
      }

      this.ruling = ruling;
      this.support = PoliticsConfig.clamp(support);
      this.politicalPower = PoliticsConfig.clamp(politicalPower);
      this.stability = PoliticsConfig.clamp(stability);
      this.active = active;
      this.dissolvedGameTime = Math.max(0L, dissolved);
      this.sharedInterestNote = sharedInterestNote;
   }
}
