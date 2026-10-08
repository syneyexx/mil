package org.millenaire.faction.territory;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Read-only Phase 7 territory accessors for Phase 8 and debug tooling.
 */
public final class TerritoryQueries {
   private TerritoryQueries() {
   }

   public static TerritoryManager manager(ServerLevel level) {
      return TerritorySavedData.get(level).getTerritoryManager();
   }

   @Nullable
   public static Territory territory(ServerLevel level, TerritoryId id) {
      return manager(level).getTerritory(id);
   }

   @Nullable
   public static Territory forSettlement(ServerLevel level, VillageId settlementId) {
      return manager(level).getTerritoryForSettlement(settlementId);
   }

   @Nullable
   public static FactionId owner(ServerLevel level, TerritoryId id) {
      return manager(level).getOwner(id);
   }

   @Nullable
   public static FactionId controller(ServerLevel level, TerritoryId id) {
      return manager(level).getController(id);
   }

   @Nullable
   public static FactionId occupier(ServerLevel level, TerritoryId id) {
      return manager(level).getOccupier(id);
   }

   @Nullable
   public static FactionId previousOwner(ServerLevel level, TerritoryId id) {
      return manager(level).getPreviousOwner(id);
   }

   public static Collection<Territory> ownedBy(ServerLevel level, FactionId factionId) {
      return manager(level).getFactionOwnedTerritories(factionId);
   }

   public static Collection<Territory> occupiedBy(ServerLevel level, FactionId factionId) {
      return manager(level).getFactionOccupiedTerritories(factionId);
   }

   public static Collection<Territory> integrating(ServerLevel level, FactionId factionId) {
      return manager(level).getFactionIntegratingTerritories(factionId);
   }

   public static Collection<TerritoryClaim> claims(ServerLevel level, FactionId factionId) {
      return manager(level).getFactionClaims(factionId);
   }

   public static Collection<TerritorialDispute> disputes(ServerLevel level, TerritoryId territoryId) {
      return manager(level).getDisputesForTerritory(territoryId);
   }

   public static Collection<Territory> neighbors(ServerLevel level, TerritoryId territoryId) {
      return manager(level).getNeighborTerritories(territoryId);
   }

   public static int resistance(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory == null ? 0 : territory.getResistance();
   }

   public static int stability(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory == null ? 0 : territory.getStability();
   }

   public static int integration(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory == null ? 0 : territory.getIntegrationProgress();
   }

   public static int territorialValue(ServerLevel level, TerritoryId id) {
      return manager(level).getTerritorialValue(id);
   }

   public static FactionTerritorialStatus factionStatus(ServerLevel level, FactionId factionId) {
      return manager(level).getFactionTerritorialStatus(factionId);
   }

   public static int overextension(ServerLevel level, FactionId factionId) {
      return manager(level).getOverextension(factionId);
   }

   public static List<TerritoryHistoryEntry> history(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory == null ? List.of() : territory.getHistory();
   }

   public static Collection<PeaceTreaty> treaties(ServerLevel level) {
      return manager(level).getAllTreaties();
   }

   public static Collection<OwnershipTransfer> transfers(ServerLevel level) {
      return manager(level).getAllTransfers();
   }

   public static int economicEfficiencyPermille(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory == null ? 1000 : territory.getEconomicEfficiencyPermille();
   }

   public static int economicEfficiencyForSettlement(ServerLevel level, VillageId settlementId) {
      Territory territory = manager(level).getTerritoryForSettlement(settlementId);
      return territory == null ? 1000 : territory.getEconomicEfficiencyPermille();
   }

   public static boolean isOccupied(ServerLevel level, TerritoryId id) {
      Territory territory = manager(level).getTerritory(id);
      return territory != null && territory.isOccupied();
   }

   public static Collection<Territory> all(ServerLevel level) {
      return manager(level).getAllTerritories();
   }

   public static Collection<TerritorialDispute> allDisputes(ServerLevel level) {
      return manager(level).getAllDisputes();
   }

   public static Collection<TerritoryClaim> allClaims(ServerLevel level) {
      return manager(level).getAllClaims();
   }
}
