package org.millenaire.faction.conflict;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;

/**
 * Read-only Phase 9 internal conflict accessors for admin tooling and later phases.
 */
public final class ConflictQueries {
   private ConflictQueries() {
   }

   public static InternalConflictManager manager(ServerLevel level) {
      return ConflictSavedData.get(level).getConflictManager();
   }

   public static Collection<InternalConflict> activeConflicts(ServerLevel level) {
      return manager(level).getActiveConflicts();
   }

   public static Collection<InternalConflict> activeConflicts(ServerLevel level, FactionId factionId) {
      return manager(level).getActiveConflicts(factionId);
   }

   public static Collection<InternalConflict> historicalConflicts(ServerLevel level) {
      return manager(level).getHistoricalConflicts();
   }

   public static Collection<InternalConflict> allConflicts(ServerLevel level) {
      return manager(level).getAllConflicts();
   }

   @Nullable
   public static InternalConflict conflictInfo(ServerLevel level, ConflictId conflictId) {
      return manager(level).getConflict(conflictId);
   }

   public static Collection<Unrest> unrest(ServerLevel level) {
      return manager(level).getActiveUnrest();
   }

   public static Collection<Unrest> unrest(ServerLevel level, FactionId factionId) {
      return manager(level).getActiveUnrest(factionId);
   }

   @Nullable
   public static Unrest unrest(ServerLevel level, UnrestId unrestId) {
      return manager(level).getUnrest(unrestId);
   }

   @Nullable
   public static PostConflictRecovery recovery(ServerLevel level, ConflictId conflictId) {
      return manager(level).getRecovery(conflictId);
   }

   @Nullable
   public static NegotiatedSettlement settlement(ServerLevel level, ConflictId conflictId) {
      return manager(level).getSettlement(conflictId);
   }

   @Nullable
   public static MilitaryLoyalty armyLoyalty(ServerLevel level, ArmyId armyId) {
      return manager(level).getArmyLoyalty(armyId);
   }

   public static Set<FactionId> recognitions(ServerLevel level, ConflictId conflictId) {
      return manager(level).getRecognitions(conflictId);
   }

   public static int conflictScore(ServerLevel level, ConflictId conflictId) {
      return manager(level).calculateConflictScore(level, conflictId);
   }

   public static int storedRebelConflictScore(ServerLevel level, ConflictId conflictId) {
      InternalConflict conflict = conflictInfo(level, conflictId);
      if (conflict == null) {
         return 0;
      }

      ConflictSide rebel = conflict.getPrimaryRebelSide();
      return rebel == null ? 0 : rebel.getConflictScore();
   }

   public static int storedGovernmentConflictScore(ServerLevel level, ConflictId conflictId) {
      InternalConflict conflict = conflictInfo(level, conflictId);
      if (conflict == null) {
         return 0;
      }

      ConflictSide government = conflict.getGovernmentSide();
      return government == null ? 0 : government.getConflictScore();
   }

   public static List<ConflictHistoryEntry> history(ServerLevel level, ConflictId conflictId) {
      InternalConflict conflict = conflictInfo(level, conflictId);
      return conflict == null ? List.of() : conflict.getHistory();
   }

   public static boolean hasActiveConflict(ServerLevel level, FactionId factionId) {
      return !activeConflicts(level, factionId).isEmpty();
   }

   public static boolean hasActiveUnrest(ServerLevel level, FactionId factionId) {
      return !unrest(level, factionId).isEmpty();
   }
}
