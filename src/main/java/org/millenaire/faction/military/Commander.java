package org.millenaire.faction.military;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

public final class Commander {
   private final CommanderId id;
   private FactionId factionId;
   private String name;
   private CommanderRank rank;
   @Nullable
   private ArmyId assignedArmyId;
   private int experience;
   private int leadership;
   @Nullable
   private VillageId originSettlementId;
   private final long createdGameTime;
   private long lastUpdatedGameTime;

   public Commander(CommanderId id, FactionId factionId, String name, CommanderRank rank, long gameTime, @Nullable VillageId originSettlementId) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.name = requireName(name);
      this.rank = rank == null ? CommanderRank.OFFICER : rank;
      this.originSettlementId = originSettlementId;
      this.createdGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   public CommanderId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   /**
    * Phase 9 civil-war allegiance transfer. Preserves commander identity.
    */
   void reassignFaction(FactionId newFactionId) {
      this.factionId = Objects.requireNonNull(newFactionId, "newFactionId");
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = requireName(name);
   }

   public CommanderRank getRank() {
      return this.rank;
   }

   public void setRank(CommanderRank rank) {
      this.rank = rank == null ? CommanderRank.OFFICER : rank;
   }

   @Nullable
   public ArmyId getAssignedArmyId() {
      return this.assignedArmyId;
   }

   public int getExperience() {
      return this.experience;
   }

   public int getLeadership() {
      return this.leadership;
   }

   @Nullable
   public VillageId getOriginSettlementId() {
      return this.originSettlementId;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public boolean isAssigned() {
      return this.assignedArmyId != null;
   }

   void assignToArmy(ArmyId armyId, long gameTime) {
      this.assignedArmyId = Objects.requireNonNull(armyId, "armyId");
      this.lastUpdatedGameTime = gameTime;
   }

   void clearAssignment(long gameTime) {
      this.assignedArmyId = null;
      this.lastUpdatedGameTime = gameTime;
   }

   /** Phase 6+ awards battle experience through this API; Phase 5 never auto-generates it. */
   void awardExperience(int amount, long gameTime) {
      if (amount > 0) {
         long next = (long)this.experience + amount;
         this.experience = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)next;
         this.lastUpdatedGameTime = gameTime;
      }
   }

   void setLeadership(int leadership, long gameTime) {
      this.leadership = MilitaryConfig.clampPercent(leadership);
      this.lastUpdatedGameTime = gameTime;
   }

   void clearOriginIfMatches(VillageId settlementId) {
      if (settlementId != null && settlementId.equals(this.originSettlementId)) {
         this.originSettlementId = null;
      }
   }

   void loadState(
      String name,
      CommanderRank rank,
      @Nullable ArmyId assignedArmyId,
      int experience,
      int leadership,
      @Nullable VillageId originSettlementId,
      long lastUpdatedGameTime
   ) {
      this.name = requireName(name);
      this.rank = rank == null ? CommanderRank.OFFICER : rank;
      this.assignedArmyId = assignedArmyId;
      this.experience = MilitaryConfig.clampNonNegative(experience);
      this.leadership = MilitaryConfig.clampPercent(leadership);
      this.originSettlementId = originSettlementId;
      this.lastUpdatedGameTime = Math.max(0L, lastUpdatedGameTime);
   }

   private static String requireName(String name) {
      Objects.requireNonNull(name, "name");
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
         throw new IllegalArgumentException("commander name blank");
      }

      return trimmed;
   }
}
