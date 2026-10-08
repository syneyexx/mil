package org.millenaire.faction.territory;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;

public final class OccupationAdministration {
   private FactionId occupierFactionId;
   @Nullable
   private FactionId administratorFactionId;
   private OccupationPolicy policy = OccupationPolicy.STANDARD;
   private long startedGameTime;
   private int requiredGarrison;
   private int presentGarrison;
   @Nullable
   private ArmyId garrisonArmyId;
   private int rebellionPressure;
   private boolean active = true;
   private long endedGameTime;

   public OccupationAdministration(FactionId occupierFactionId, long startedGameTime, int requiredGarrison) {
      this.occupierFactionId = Objects.requireNonNull(occupierFactionId, "occupierFactionId");
      this.administratorFactionId = occupierFactionId;
      this.startedGameTime = startedGameTime;
      this.requiredGarrison = Math.max(0, requiredGarrison);
   }

   public FactionId getOccupierFactionId() {
      return this.occupierFactionId;
   }

   @Nullable
   public FactionId getAdministratorFactionId() {
      return this.administratorFactionId;
   }

   public OccupationPolicy getPolicy() {
      return this.policy;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getDurationTicks(long now) {
      if (!this.active) {
         return Math.max(0L, this.endedGameTime - this.startedGameTime);
      }

      return Math.max(0L, now - this.startedGameTime);
   }

   public int getRequiredGarrison() {
      return this.requiredGarrison;
   }

   public int getPresentGarrison() {
      return this.presentGarrison;
   }

   @Nullable
   public ArmyId getGarrisonArmyId() {
      return this.garrisonArmyId;
   }

   public int getRebellionPressure() {
      return this.rebellionPressure;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   public boolean hasAdequateGarrison() {
      return this.presentGarrison >= this.requiredGarrison;
   }

   void setPolicy(OccupationPolicy policy) {
      this.policy = policy == null ? OccupationPolicy.STANDARD : policy;
   }

   void setAdministrator(@Nullable FactionId administrator) {
      this.administratorFactionId = administrator;
   }

   void setRequiredGarrison(int required) {
      this.requiredGarrison = Math.max(0, required);
   }

   void setPresentGarrison(int present) {
      this.presentGarrison = Math.max(0, present);
   }

   void setGarrisonArmyId(@Nullable ArmyId armyId) {
      this.garrisonArmyId = armyId;
   }

   void setRebellionPressure(int pressure) {
      this.rebellionPressure = TerritoryConfig.clampPercent(pressure);
   }

   void end(long gameTime) {
      this.active = false;
      this.endedGameTime = gameTime;
   }

   void loadState(
      FactionId occupier,
      @Nullable FactionId administrator,
      OccupationPolicy policy,
      long started,
      int requiredGarrison,
      int presentGarrison,
      @Nullable ArmyId garrisonArmyId,
      int rebellionPressure,
      boolean active,
      long ended
   ) {
      this.occupierFactionId = Objects.requireNonNull(occupier, "occupier");
      this.administratorFactionId = administrator;
      this.policy = policy == null ? OccupationPolicy.STANDARD : policy;
      this.startedGameTime = Math.max(0L, started);
      this.requiredGarrison = Math.max(0, requiredGarrison);
      this.presentGarrison = Math.max(0, presentGarrison);
      this.garrisonArmyId = garrisonArmyId;
      this.rebellionPressure = TerritoryConfig.clampPercent(rebellionPressure);
      this.active = active;
      this.endedGameTime = Math.max(0L, ended);
   }
}
