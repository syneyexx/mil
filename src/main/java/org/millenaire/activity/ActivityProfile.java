package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public final class ActivityProfile {
   private final String id;
   private final Set<String> eligibleRoles;
   private final Set<String> eligibleTags;
   private final Set<ActivityKind> allowedKinds;
   private final Set<String> huntingPreyTags;
   private final int groupSizeMin;
   private final int groupSizeMax;
   private final float aggression;
   private final float riskTolerance;
   private final float retreatThreshold;
   private final List<String> biomePreferences;
   private final List<String> routePreferences;
   private final int activityRange;
   private final int minHomeDefense;
   private final float maxAwayFraction;
   private final long cooldownTicks;
   private final String lootPolicy;
   private final String organizationId;
   private final Set<String> rivalOrganizations;
   private final boolean bandit;
   private final boolean hunter;
   private final boolean patrol;

   public ActivityProfile(
      String id,
      Set<String> eligibleRoles,
      Set<String> eligibleTags,
      Set<ActivityKind> allowedKinds,
      Set<String> huntingPreyTags,
      int groupSizeMin,
      int groupSizeMax,
      float aggression,
      float riskTolerance,
      float retreatThreshold,
      List<String> biomePreferences,
      List<String> routePreferences,
      int activityRange,
      int minHomeDefense,
      float maxAwayFraction,
      long cooldownTicks,
      String lootPolicy,
      String organizationId,
      Set<String> rivalOrganizations,
      boolean bandit,
      boolean hunter,
      boolean patrol
   ) {
      this.id = id;
      this.eligibleRoles = Set.copyOf(eligibleRoles);
      this.eligibleTags = Set.copyOf(eligibleTags);
      this.allowedKinds = Set.copyOf(allowedKinds);
      this.huntingPreyTags = Set.copyOf(huntingPreyTags);
      this.groupSizeMin = groupSizeMin;
      this.groupSizeMax = groupSizeMax;
      this.aggression = aggression;
      this.riskTolerance = riskTolerance;
      this.retreatThreshold = retreatThreshold;
      this.biomePreferences = List.copyOf(biomePreferences);
      this.routePreferences = List.copyOf(routePreferences);
      this.activityRange = activityRange;
      this.minHomeDefense = minHomeDefense;
      this.maxAwayFraction = maxAwayFraction;
      this.cooldownTicks = cooldownTicks;
      this.lootPolicy = lootPolicy;
      this.organizationId = organizationId;
      this.rivalOrganizations = Set.copyOf(rivalOrganizations);
      this.bandit = bandit;
      this.hunter = hunter;
      this.patrol = patrol;
   }

   public String id() { return this.id; }
   public Set<String> eligibleRoles() { return this.eligibleRoles; }
   public Set<String> eligibleTags() { return this.eligibleTags; }
   public Set<ActivityKind> allowedKinds() { return this.allowedKinds; }
   public Set<String> huntingPreyTags() { return this.huntingPreyTags; }
   public int groupSizeMin() { return this.groupSizeMin; }
   public int groupSizeMax() { return this.groupSizeMax; }
   public float aggression() { return this.aggression; }
   public float riskTolerance() { return this.riskTolerance; }
   public float retreatThreshold() { return this.retreatThreshold; }
   public List<String> biomePreferences() { return this.biomePreferences; }
   public List<String> routePreferences() { return this.routePreferences; }
   public int activityRange() { return this.activityRange; }
   public int minHomeDefense() { return this.minHomeDefense; }
   public float maxAwayFraction() { return this.maxAwayFraction; }
   public long cooldownTicks() { return this.cooldownTicks; }
   public String lootPolicy() { return this.lootPolicy; }
   public String organizationId() { return this.organizationId; }
   public Set<String> rivalOrganizations() { return this.rivalOrganizations; }
   public boolean bandit() { return this.bandit; }
   public boolean hunter() { return this.hunter; }
   public boolean patrol() { return this.patrol; }

   public boolean allows(ActivityKind kind) {
      return this.allowedKinds.contains(kind);
   }

   public boolean matchesTags(List<String> tags, @Nullable String roleName) {
      if (roleName != null && !this.eligibleRoles.isEmpty()) {
         for (String r : this.eligibleRoles) {
            if (roleName.toLowerCase().contains(r.toLowerCase())) {
               return true;
            }
         }
      }
      if (this.eligibleTags.isEmpty()) {
         return !this.eligibleRoles.isEmpty();
      }
      for (String t : tags) {
         if (this.eligibleTags.contains(t)) {
            return true;
         }
      }
      return false;
   }

   public static Builder builder(String id) {
      return new Builder(id);
   }

   public static final class Builder {
      private final String id;
      private Set<String> eligibleRoles = new HashSet<>();
      private Set<String> eligibleTags = new HashSet<>();
      private Set<ActivityKind> allowedKinds = new HashSet<>();
      private Set<String> huntingPreyTags = new HashSet<>();
      private int groupSizeMin = 2;
      private int groupSizeMax = 3;
      private float aggression = 0.3f;
      private float riskTolerance = 0.4f;
      private float retreatThreshold = 0.35f;
      private List<String> biomePreferences = new ArrayList<>();
      private List<String> routePreferences = new ArrayList<>();
      private int activityRange = 256;
      private int minHomeDefense = 2;
      private float maxAwayFraction = 0.20f;
      private long cooldownTicks = 6000L;
      private String lootPolicy = "real_drops";
      private String organizationId = "";
      private Set<String> rivalOrganizations = new HashSet<>();
      private boolean bandit;
      private boolean hunter;
      private boolean patrol;

      Builder(String id) { this.id = id; }
      public Builder roles(String... v) { Collections.addAll(this.eligibleRoles, v); return this; }
      public Builder tags(String... v) { Collections.addAll(this.eligibleTags, v); return this; }
      public Builder kinds(ActivityKind... v) { Collections.addAll(this.allowedKinds, v); return this; }
      public Builder prey(String... v) { Collections.addAll(this.huntingPreyTags, v); return this; }
      public Builder size(int min, int max) { this.groupSizeMin = min; this.groupSizeMax = max; return this; }
      public Builder aggression(float v) { this.aggression = v; return this; }
      public Builder risk(float v) { this.riskTolerance = v; return this; }
      public Builder retreat(float v) { this.retreatThreshold = v; return this; }
      public Builder biomes(String... v) { Collections.addAll(this.biomePreferences, v); return this; }
      public Builder routes(String... v) { Collections.addAll(this.routePreferences, v); return this; }
      public Builder range(int v) { this.activityRange = v; return this; }
      public Builder homeDefense(int v) { this.minHomeDefense = v; return this; }
      public Builder awayFraction(float v) { this.maxAwayFraction = v; return this; }
      public Builder cooldown(long v) { this.cooldownTicks = v; return this; }
      public Builder loot(String v) { this.lootPolicy = v; return this; }
      public Builder org(String v) { this.organizationId = v; return this; }
      public Builder rivals(String... v) { Collections.addAll(this.rivalOrganizations, v); return this; }
      public Builder bandit(boolean v) { this.bandit = v; return this; }
      public Builder hunter(boolean v) { this.hunter = v; return this; }
      public Builder patrol(boolean v) { this.patrol = v; return this; }

      public ActivityProfile build() {
         return new ActivityProfile(
            this.id, this.eligibleRoles, this.eligibleTags, this.allowedKinds, this.huntingPreyTags,
            this.groupSizeMin, this.groupSizeMax, this.aggression, this.riskTolerance, this.retreatThreshold,
            this.biomePreferences, this.routePreferences, this.activityRange, this.minHomeDefense,
            this.maxAwayFraction, this.cooldownTicks, this.lootPolicy, this.organizationId,
            this.rivalOrganizations, this.bandit, this.hunter, this.patrol
         );
      }
   }
}
