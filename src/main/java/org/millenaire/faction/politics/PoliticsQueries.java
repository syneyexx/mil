package org.millenaire.faction.politics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Read-only Phase 8 politics accessors for Phase 9 and admin tooling.
 */
public final class PoliticsQueries {
   private PoliticsQueries() {
   }

   public static PoliticsManager manager(ServerLevel level) {
      return PoliticsSavedData.get(level).getPoliticsManager();
   }

   @Nullable
   public static FactionPoliticalState state(ServerLevel level, FactionId factionId) {
      return manager(level).getState(factionId);
   }

   @Nullable
   public static Government government(ServerLevel level, FactionId factionId) {
      return manager(level).getActiveGovernment(factionId);
   }

   @Nullable
   public static PoliticalLeader leader(ServerLevel level, FactionId factionId) {
      return manager(level).getLeader(factionId);
   }

   public static int legitimacy(ServerLevel level, FactionId factionId) {
      Government government = government(level, factionId);
      return government == null ? 0 : government.getLegitimacy();
   }

   public static int popularity(ServerLevel level, FactionId factionId) {
      PoliticalLeader leader = leader(level, factionId);
      return leader == null ? 0 : leader.getPopularity();
   }

   public static int stability(ServerLevel level, FactionId factionId) {
      Government government = government(level, factionId);
      return government == null ? 0 : government.getStability();
   }

   public static int politicalPressure(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? 0 : state.getPoliticalPressure();
   }

   public static int polarization(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? 0 : state.getPolarization();
   }

   public static int rebellionPressure(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? 0 : state.getRebellionPressure();
   }

   public static InternalConflictState conflictState(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? InternalConflictState.STABLE : state.getConflictState();
   }

   public static Map<PoliticalGroupId, PoliticalGroup> groups(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? Map.of() : state.getGroups();
   }

   @Nullable
   public static PoliticalCoalition rulingCoalition(ServerLevel level, FactionId factionId) {
      Government government = government(level, factionId);
      FactionPoliticalState state = state(level, factionId);
      if (government == null || state == null || government.getRulingCoalitionId() == null) {
         return null;
      }

      return state.getCoalitions().get(government.getRulingCoalitionId());
   }

   public static Collection<PoliticalGroupId> opposition(ServerLevel level, FactionId factionId) {
      Government government = government(level, factionId);
      return government == null ? List.of() : government.getOppositionGroupIds();
   }

   public static Map<PolicyType, GovernmentPolicy> policies(ServerLevel level, FactionId factionId) {
      Government government = government(level, factionId);
      return government == null ? Map.of() : government.getPolicies();
   }

   public static Collection<PoliticalCrisis> crises(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? List.of() : state.getCrises().values();
   }

   public static boolean successionPending(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state != null && state.isSuccessionPending();
   }

   public static List<PoliticalHistoryEntry> history(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? List.of() : state.getHistory();
   }

   @Nullable
   public static LocalAdministrator localAdministrator(ServerLevel level, FactionId factionId, VillageId settlementId) {
      FactionPoliticalState state = state(level, factionId);
      return state == null ? null : state.getLocalAdministrators().get(settlementId);
   }

   public static boolean isGovernmentStable(ServerLevel level, FactionId factionId) {
      return PoliticsEvaluator.isGovernmentStable(state(level, factionId), government(level, factionId));
   }

   public static boolean isGovernmentVulnerable(ServerLevel level, FactionId factionId) {
      return PoliticsEvaluator.isGovernmentVulnerable(state(level, factionId), government(level, factionId));
   }

   public static int foreignPolicyPressure(ServerLevel level, FactionId factionId, ForeignPolicyPreference preference) {
      int total = 0;
      for (PoliticalGroup group : groups(level, factionId).values()) {
         total += PoliticsEvaluator.foreignPolicyPressure(group, preference);
      }

      return Math.min(100, total);
   }
}
