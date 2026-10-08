package org.millenaire.faction.economy;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryQueries;
import org.millenaire.village.VillageId;

/**
 * Internal/debug accessors for faction economy and trade. Not a player-facing GUI.
 */
public final class EconomyQueries {
   private EconomyQueries() {
   }

   public static TradeManager manager(ServerLevel level) {
      return EconomySavedData.get(level).getTradeManager();
   }

   @Nullable
   public static FactionEconomicProfile profile(ServerLevel level, FactionId factionId) {
      return manager(level).getProfile(factionId);
   }

   public static FactionEconomicProfile profileOrCreate(ServerLevel level, FactionId factionId) {
      return manager(level).getOrCreateProfile(factionId);
   }

   public static Map<EconomicResourceId, ResourceBalance> balances(ServerLevel level, FactionId factionId) {
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction == null) {
         return Map.of();
      }

      FactionEconomicProfile profile = manager(level).getProfile(factionId);
      if (profile != null && !profile.getLastBalances().isEmpty()) {
         return profile.getLastBalances();
      }

      return FactionEconomyAggregator.aggregateBalances(level, faction);
   }

   @Nullable
   public static TradeRoute route(ServerLevel level, FactionId source, FactionId target) {
      return manager(level).getRoute(source, target);
   }

   public static Collection<TradeRoute> routesFrom(ServerLevel level, FactionId source) {
      return manager(level).getRoutesFrom(source);
   }

   public static Collection<TradeRoute> routesInvolving(ServerLevel level, FactionId factionId) {
      return manager(level).getRoutesInvolving(factionId);
   }

   public static int economicInfluence(ServerLevel level, FactionId importer, FactionId exporter) {
      return manager(level).getEconomicInfluence(importer, exporter);
   }

   public static Collection<ResourceDependency> dependencies(ServerLevel level, FactionId importer) {
      return manager(level).getDependenciesForImporter(importer);
   }

   @Nullable
   public static EconomicBalanceLevel balanceLevel(ServerLevel level, FactionId factionId) {
      FactionEconomicProfile profile = manager(level).getProfile(factionId);
      return profile == null ? null : profile.getBalanceLevel();
   }

   /**
    * Phase 5 military maintenance snapshot. Economy remains authoritative for stock; military only reports needs.
    */
   public static org.millenaire.faction.military.MilitaryResourceRequirements militaryRequirements(ServerLevel level, FactionId factionId) {
      return org.millenaire.faction.military.MilitaryResourceRequirements.of(level, factionId);
   }

   /**
    * Phase 7 occupation/integration economic efficiency for a settlement (permille, 1000 = full).
    */
   public static int settlementEconomicEfficiencyPermille(ServerLevel level, VillageId settlementId) {
      return FactionEconomyAggregator.settlementEconomicEfficiencyPermille(level, settlementId);
   }

   /**
    * Average economic efficiency across a faction's owned territories (permille).
    */
   public static int factionTerritorialEfficiencyPermille(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return 1000;
      }

      int total = 0;
      int count = 0;
      for (Territory territory : TerritoryQueries.ownedBy(level, factionId)) {
         total += territory.getEconomicEfficiencyPermille();
         count++;
      }

      return count == 0 ? 1000 : total / count;
   }
}
