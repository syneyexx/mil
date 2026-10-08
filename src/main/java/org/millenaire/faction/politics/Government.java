package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class Government {
   private final GovernmentId id;
   private final FactionId factionId;
   private GovernmentType type;
   @Nullable
   private LeaderId currentLeaderId;
   private int legitimacy = PoliticsConfig.LEGITIMACY_BASE;
   private int stability = PoliticsConfig.STABILITY_BASE;
   private int centralization = PoliticsConfig.CENTRALIZATION_BASE;
   private SuccessionMechanism successionMechanism;
   private InterimGovernmentType interimType = InterimGovernmentType.NONE;
   @Nullable
   private CoalitionId rulingCoalitionId;
   private final Map<PolicyType, GovernmentPolicy> policies = new EnumMap<>(PolicyType.class);
   private final Set<PoliticalGroupId> oppositionGroupIds = new LinkedHashSet<>();
   private final long createdGameTime;
   private long lastMajorChangeGameTime;
   private boolean active = true;

   public Government(GovernmentId id, FactionId factionId, GovernmentType type, long gameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.type = type == null ? GovernmentType.CHIEFDOM : type;
      GovernmentTypeDefinition definition = GovernmentTypeDefinition.of(this.type);
      this.successionMechanism = definition.getSuccessionMechanism();
      this.centralization = definition.centralizationBias();
      this.createdGameTime = gameTime;
      this.lastMajorChangeGameTime = gameTime;
      this.seedDefaultPolicies(gameTime);
   }

   public GovernmentId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public GovernmentType getType() {
      return this.type;
   }

   @Nullable
   public LeaderId getCurrentLeaderId() {
      return this.currentLeaderId;
   }

   public int getLegitimacy() {
      return this.legitimacy;
   }

   public int getStability() {
      return this.stability;
   }

   public int getCentralization() {
      return this.centralization;
   }

   public SuccessionMechanism getSuccessionMechanism() {
      return this.successionMechanism;
   }

   public InterimGovernmentType getInterimType() {
      return this.interimType;
   }

   @Nullable
   public CoalitionId getRulingCoalitionId() {
      return this.rulingCoalitionId;
   }

   public Map<PolicyType, GovernmentPolicy> getPolicies() {
      return Collections.unmodifiableMap(this.policies);
   }

   @Nullable
   public GovernmentPolicy getPolicy(PolicyType type) {
      return type == null ? null : this.policies.get(type);
   }

   public Set<PoliticalGroupId> getOppositionGroupIds() {
      return Collections.unmodifiableSet(this.oppositionGroupIds);
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getLastMajorChangeGameTime() {
      return this.lastMajorChangeGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public boolean isInterim() {
      return this.interimType.isActive();
   }

   void setType(GovernmentType type, long gameTime) {
      this.type = type == null ? GovernmentType.CHIEFDOM : type;
      GovernmentTypeDefinition definition = GovernmentTypeDefinition.of(this.type);
      this.successionMechanism = definition.getSuccessionMechanism();
      this.lastMajorChangeGameTime = gameTime;
   }

   void setCurrentLeaderId(@Nullable LeaderId leaderId) {
      this.currentLeaderId = leaderId;
   }

   void setLegitimacy(int legitimacy) {
      this.legitimacy = PoliticsConfig.clamp(legitimacy);
   }

   void setStability(int stability) {
      this.stability = PoliticsConfig.clamp(stability);
   }

   void setCentralization(int centralization) {
      this.centralization = PoliticsConfig.clamp(centralization);
   }

   void setSuccessionMechanism(SuccessionMechanism mechanism) {
      this.successionMechanism = mechanism == null ? SuccessionMechanism.APPOINTMENT : mechanism;
   }

   void setInterimType(InterimGovernmentType interimType, long gameTime) {
      this.interimType = interimType == null ? InterimGovernmentType.NONE : interimType;
      this.lastMajorChangeGameTime = gameTime;
   }

   void setRulingCoalitionId(@Nullable CoalitionId coalitionId) {
      this.rulingCoalitionId = coalitionId;
   }

   void putPolicy(GovernmentPolicy policy) {
      if (policy != null) {
         this.policies.put(policy.getType(), policy);
      }
   }

   void setOpposition(Set<PoliticalGroupId> opposition) {
      this.oppositionGroupIds.clear();
      if (opposition != null) {
         this.oppositionGroupIds.addAll(opposition);
      }
   }

   void removeOpposition(PoliticalGroupId groupId) {
      if (groupId != null) {
         this.oppositionGroupIds.remove(groupId);
      }
   }

   void addOpposition(PoliticalGroupId groupId) {
      if (groupId != null) {
         this.oppositionGroupIds.add(groupId);
      }
   }

   void markMajorChange(long gameTime) {
      this.lastMajorChangeGameTime = gameTime;
   }

   void deactivate() {
      this.active = false;
   }

   void loadState(
      GovernmentType type,
      @Nullable LeaderId leaderId,
      int legitimacy,
      int stability,
      int centralization,
      SuccessionMechanism successionMechanism,
      InterimGovernmentType interimType,
      @Nullable CoalitionId rulingCoalitionId,
      Map<PolicyType, GovernmentPolicy> policies,
      Set<PoliticalGroupId> opposition,
      long lastMajorChange,
      boolean active
   ) {
      this.type = type == null ? GovernmentType.CHIEFDOM : type;
      this.currentLeaderId = leaderId;
      this.legitimacy = PoliticsConfig.clamp(legitimacy);
      this.stability = PoliticsConfig.clamp(stability);
      this.centralization = PoliticsConfig.clamp(centralization);
      this.successionMechanism = successionMechanism == null ? this.type.defaultSuccession() : successionMechanism;
      this.interimType = interimType == null ? InterimGovernmentType.NONE : interimType;
      this.rulingCoalitionId = rulingCoalitionId;
      this.policies.clear();
      if (policies != null) {
         this.policies.putAll(policies);
      }

      this.oppositionGroupIds.clear();
      if (opposition != null) {
         this.oppositionGroupIds.addAll(opposition);
      }

      this.lastMajorChangeGameTime = Math.max(0L, lastMajorChange);
      this.active = active;
   }

   private void seedDefaultPolicies(long gameTime) {
      this.policies.put(PolicyType.TAX_POLICY, new GovernmentPolicy(PolicyType.TAX_POLICY, PolicyStance.NORMAL, gameTime));
      this.policies.put(PolicyType.MILITARY_SPENDING, new GovernmentPolicy(PolicyType.MILITARY_SPENDING, PolicyStance.NORMAL, gameTime));
      this.policies.put(PolicyType.TRADE_POLICY, new GovernmentPolicy(PolicyType.TRADE_POLICY, PolicyStance.OPEN, gameTime));
      this.policies.put(PolicyType.CENTRALIZATION, new GovernmentPolicy(PolicyType.CENTRALIZATION, PolicyStance.NORMAL, gameTime));
      this.policies.put(PolicyType.LOCAL_AUTONOMY, new GovernmentPolicy(PolicyType.LOCAL_AUTONOMY, PolicyStance.NORMAL, gameTime));
      this.policies.put(PolicyType.DIPLOMATIC_POSTURE, new GovernmentPolicy(PolicyType.DIPLOMATIC_POSTURE, PolicyStance.OPEN, gameTime));
      this.policies.put(PolicyType.CONSCRIPTION, new GovernmentPolicy(PolicyType.CONSCRIPTION, PolicyStance.NORMAL, gameTime));
      this.policies.put(PolicyType.CULTURAL_POLICY, new GovernmentPolicy(PolicyType.CULTURAL_POLICY, PolicyStance.TRADITIONAL, gameTime));
      this.policies.put(PolicyType.OCCUPATION_POLICY, new GovernmentPolicy(PolicyType.OCCUPATION_POLICY, PolicyStance.NORMAL, gameTime));
   }
}
