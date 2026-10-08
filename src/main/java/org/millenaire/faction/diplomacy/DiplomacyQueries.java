package org.millenaire.faction.diplomacy;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;

/**
 * Internal/debug accessors for faction diplomacy. Not a player-facing GUI.
 */
public final class DiplomacyQueries {
   private DiplomacyQueries() {
   }

   public static DiplomaticManager manager(ServerLevel level) {
      return DiplomacySavedData.get(level).getDiplomaticManager();
   }

   public static DiplomaticStanding standing(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).getDiplomaticStanding(level, a, b);
   }

   public static boolean canTrade(ServerLevel level, FactionId a, FactionId b) {
      // Embargoes imposed at war declaration already restrict trade via DiplomaticManager.
      return manager(level).canTrade(a, b);
   }

   public static boolean areAtWar(ServerLevel level, FactionId a, FactionId b) {
      return org.millenaire.faction.war.WarQueries.areAtWar(level, a, b);
   }

   public static boolean isTradeRestricted(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).isTradeRestricted(a, b);
   }

   public static boolean isAllied(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).isAllied(a, b);
   }

   public static boolean hasNonAggressionPact(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).hasNonAggressionPact(a, b);
   }

   public static boolean hasTradeAgreement(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).hasTradeAgreement(a, b);
   }

   public static boolean hasDefensivePact(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).hasDefensivePact(a, b);
   }

   public static boolean isEmbargoed(ServerLevel level, FactionId imposer, FactionId target) {
      return manager(level).isEmbargoed(imposer, target);
   }

   public static boolean isPeaceful(ServerLevel level, FactionId a, FactionId b) {
      return manager(level).isPeaceful(a, b);
   }

   public static Collection<FactionId> allies(ServerLevel level, FactionId factionId) {
      return manager(level).getAllies(factionId);
   }

   public static Collection<FactionId> defensivePactPartners(ServerLevel level, FactionId factionId) {
      return manager(level).getDefensivePactPartners(factionId);
   }

   public static Collection<DiplomaticAgreement> agreementsInvolving(ServerLevel level, FactionId factionId) {
      return manager(level).getAgreementsInvolving(factionId);
   }

   public static Collection<DiplomaticAgreement> activeAgreementsInvolving(ServerLevel level, FactionId factionId) {
      return manager(level).getActiveAgreementsInvolving(factionId);
   }

   @Nullable
   public static DiplomaticAgreement agreement(ServerLevel level, AgreementId id) {
      return manager(level).getAgreement(id);
   }

   public static List<DiplomaticHistoryEntry> history(ServerLevel level) {
      return manager(level).getDiplomaticHistory();
   }
}
