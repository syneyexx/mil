package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PoliticalGroup {
   private final PoliticalGroupId id;
   private final FactionId factionId;
   private final PoliticalGroupType type;
   private int populationSupport = 30;
   private int politicalPower = 20;
   private int wealthInfluence = 20;
   private int militaryInfluence = 20;
   private int approval = 50;
   private ForeignPolicyPreference foreignPreference = ForeignPolicyPreference.DIPLOMACY;
   private boolean active = true;
   private final Map<InterestType, PoliticalInterest> interests = new EnumMap<>(InterestType.class);
   private final Set<String> demands = new LinkedHashSet<>();
   @Nullable
   private CoalitionId coalitionId;

   public PoliticalGroup(PoliticalGroupId id, FactionId factionId, PoliticalGroupType type) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.type = type == null ? PoliticalGroupType.COMMONERS : type;
      this.applyDefaultInterests();
   }

   public PoliticalGroupId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public PoliticalGroupType getType() {
      return this.type;
   }

   public int getPopulationSupport() {
      return this.populationSupport;
   }

   public int getPoliticalPower() {
      return this.politicalPower;
   }

   public int getWealthInfluence() {
      return this.wealthInfluence;
   }

   public int getMilitaryInfluence() {
      return this.militaryInfluence;
   }

   public int getApproval() {
      return this.approval;
   }

   public ForeignPolicyPreference getForeignPreference() {
      return this.foreignPreference;
   }

   public boolean isActive() {
      return this.active;
   }

   public Map<InterestType, PoliticalInterest> getInterests() {
      return Collections.unmodifiableMap(this.interests);
   }

   public int getInterestStrength(InterestType type) {
      PoliticalInterest interest = this.interests.get(type);
      return interest == null ? 0 : interest.getStrength();
   }

   public Set<String> getDemands() {
      return Collections.unmodifiableSet(this.demands);
   }

   @Nullable
   public CoalitionId getCoalitionId() {
      return this.coalitionId;
   }

   void setPopulationSupport(int value) {
      this.populationSupport = PoliticsConfig.clamp(value);
   }

   void setPoliticalPower(int value) {
      this.politicalPower = PoliticsConfig.clamp(value);
   }

   void setWealthInfluence(int value) {
      this.wealthInfluence = PoliticsConfig.clamp(value);
   }

   void setMilitaryInfluence(int value) {
      this.militaryInfluence = PoliticsConfig.clamp(value);
   }

   void setApproval(int value) {
      this.approval = PoliticsConfig.clamp(value);
   }

   void setForeignPreference(ForeignPolicyPreference preference) {
      this.foreignPreference = preference == null ? ForeignPolicyPreference.DIPLOMACY : preference;
   }

   void setCoalitionId(@Nullable CoalitionId coalitionId) {
      this.coalitionId = coalitionId;
   }

   void setInterest(InterestType type, int strength) {
      if (type == null) {
         return;
      }

      this.interests.put(type, new PoliticalInterest(type, strength));
   }

   void addDemand(String demand) {
      if (demand != null && !demand.isBlank()) {
         this.demands.add(demand.trim());
      }
   }

   void clearDemand(String demand) {
      this.demands.remove(demand);
   }

   void deactivate() {
      this.active = false;
      this.coalitionId = null;
   }

   void loadState(
      int populationSupport,
      int politicalPower,
      int wealthInfluence,
      int militaryInfluence,
      int approval,
      ForeignPolicyPreference foreignPreference,
      boolean active,
      @Nullable CoalitionId coalitionId,
      Map<InterestType, Integer> interests,
      Set<String> demands
   ) {
      this.populationSupport = PoliticsConfig.clamp(populationSupport);
      this.politicalPower = PoliticsConfig.clamp(politicalPower);
      this.wealthInfluence = PoliticsConfig.clamp(wealthInfluence);
      this.militaryInfluence = PoliticsConfig.clamp(militaryInfluence);
      this.approval = PoliticsConfig.clamp(approval);
      this.foreignPreference = foreignPreference == null ? ForeignPolicyPreference.DIPLOMACY : foreignPreference;
      this.active = active;
      this.coalitionId = coalitionId;
      this.interests.clear();
      if (interests != null) {
         for (Map.Entry<InterestType, Integer> entry : interests.entrySet()) {
            if (entry.getKey() != null) {
               this.interests.put(entry.getKey(), new PoliticalInterest(entry.getKey(), entry.getValue() == null ? 0 : entry.getValue()));
            }
         }
      }

      this.demands.clear();
      if (demands != null) {
         this.demands.addAll(demands);
      }
   }

   private void applyDefaultInterests() {
      switch (this.type) {
         case MILITARY -> {
            this.setInterest(InterestType.MILITARY_SPENDING, 80);
            this.setInterest(InterestType.EXPANSION, 70);
            this.setInterest(InterestType.PEACE, 20);
            this.foreignPreference = ForeignPolicyPreference.WAR;
            this.militaryInfluence = 70;
            this.politicalPower = 45;
            this.populationSupport = 25;
         }
         case MERCHANTS -> {
            this.setInterest(InterestType.TRADE, 85);
            this.setInterest(InterestType.ECONOMIC_GROWTH, 75);
            this.setInterest(InterestType.TAXATION, 30);
            this.foreignPreference = ForeignPolicyPreference.TRADE;
            this.wealthInfluence = 70;
            this.politicalPower = 40;
            this.populationSupport = 30;
         }
         case FARMERS -> {
            this.setInterest(InterestType.TAXATION, 25);
            this.setInterest(InterestType.LOCAL_AUTONOMY, 65);
            this.setInterest(InterestType.PEACE, 70);
            this.foreignPreference = ForeignPolicyPreference.PEACE;
            this.populationSupport = 60;
            this.politicalPower = 25;
         }
         case NOBILITY, RULING_ELITE -> {
            this.setInterest(InterestType.CENTRALIZATION, 70);
            this.setInterest(InterestType.TRADITION, 60);
            this.setInterest(InterestType.MILITARY_SPENDING, 55);
            this.foreignPreference = ForeignPolicyPreference.ALLIANCE;
            this.politicalPower = 65;
            this.populationSupport = 20;
         }
         case CLERGY, TRADITIONALISTS -> {
            this.setInterest(InterestType.TRADITION, 85);
            this.setInterest(InterestType.CULTURAL_PRESERVATION, 80);
            this.setInterest(InterestType.REFORM, 15);
            this.foreignPreference = ForeignPolicyPreference.ISOLATION;
            this.populationSupport = 35;
            this.politicalPower = 35;
         }
         case REFORMERS -> {
            this.setInterest(InterestType.REFORM, 85);
            this.setInterest(InterestType.ECONOMIC_GROWTH, 60);
            this.setInterest(InterestType.TRADITION, 20);
            this.foreignPreference = ForeignPolicyPreference.DIPLOMACY;
            this.populationSupport = 30;
            this.politicalPower = 30;
         }
         case COMMONERS -> {
            this.setInterest(InterestType.TAXATION, 20);
            this.setInterest(InterestType.PEACE, 65);
            this.setInterest(InterestType.LOCAL_AUTONOMY, 50);
            this.foreignPreference = ForeignPolicyPreference.PEACE;
            this.populationSupport = 70;
            this.politicalPower = 20;
         }
      }
   }
}
