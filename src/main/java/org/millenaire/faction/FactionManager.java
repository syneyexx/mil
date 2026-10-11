package org.millenaire.faction;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Central in-memory faction registry for a single world.
 * <p>
 * Persistence is owned by {@link FactionSavedData}; this manager is never the authoritative
 * store by itself. Settlement membership is kept in sync with {@link Village#getFactionId()}.
 * <p>
 * Phase 2 may attach a relationship layer keyed by {@link FactionId} without redesigning this
 * registry.
 */
public final class FactionManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<FactionId, Faction> factions = new LinkedHashMap<>();
   private final Runnable markDirty;

   FactionManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   public boolean exists(FactionId factionId) {
      return factionId != null && this.factions.containsKey(factionId);
   }

   @Nullable
   public Faction getFaction(FactionId factionId) {
      return factionId == null ? null : this.factions.get(factionId);
   }

   public Collection<Faction> getAllFactions() {
      return Collections.unmodifiableCollection(this.factions.values());
   }

   public List<Faction> getFactionsByType(FactionType type) {
      Objects.requireNonNull(type, "type");
      List<Faction> result = new ArrayList<>();

      for (Faction faction : this.factions.values()) {
         if (faction.getType() == type) {
            result.add(faction);
         }
      }

      return Collections.unmodifiableList(result);
   }

   /**
    * Returns all registered factions that use the given culture. Culture and faction are separate:
    * zero, one, or many factions may share a culture id.
    */
   public List<Faction> getFactionsUsingCulture(ResourceLocation cultureId) {
      Objects.requireNonNull(cultureId, "cultureId");
      List<Faction> result = new ArrayList<>();

      for (Faction faction : this.factions.values()) {
         if (cultureId.equals(faction.getCultureId())) {
            result.add(faction);
         }
      }

      return Collections.unmodifiableList(result);
   }

   /**
    * Registers a new faction. Rejects duplicate ids without replacing the existing entry.
    *
    * @return the registered faction, or {@code null} when the id is already taken
    */
   @Nullable
   public Faction createFaction(FactionId id, String displayName, FactionType type, @Nullable ResourceLocation cultureId, long createdAtGameTime) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(type, "type");
      if (this.factions.containsKey(id)) {
         LOGGER.error("[Millenaire] Refusing to register duplicate faction id {}", id);
         return null;
      }

      if (type == FactionType.CULTURE && cultureId == null) {
         LOGGER.error("[Millenaire] Refusing to register CULTURE faction {} without a culture id", id);
         return null;
      }

      Faction faction = new Faction(id, type, displayName, cultureId, createdAtGameTime);
      this.factions.put(id, faction);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Registered faction {} (type={}, culture={})", id, type, cultureId);
      return faction;
   }

   /**
    * Creates a player faction with a fresh UUID-based id.
    */
   @Nullable
   public Faction createPlayerFaction(String displayName, @Nullable ResourceLocation cultureId, long createdAtGameTime) {
      return this.createPlayerFaction(displayName, cultureId, createdAtGameTime, null);
   }

   /**
    * Creates a sovereign player faction owned by {@code sovereignOwnerUuid}.
    */
   @Nullable
   public Faction createPlayerFaction(
      String displayName, @Nullable ResourceLocation cultureId, long createdAtGameTime, @Nullable UUID sovereignOwnerUuid
   ) {
      Faction faction = this.createFaction(FactionIds.playerFaction(UUID.randomUUID()), displayName, FactionType.PLAYER, cultureId, createdAtGameTime);
      if (faction != null && sovereignOwnerUuid != null) {
         faction.setSovereignOwnerUuid(sovereignOwnerUuid);
         faction.setImmigrationPolicy(ImmigrationPolicy.OPEN);
         faction.setTradePolicy(TradePolicy.INBOUND_ONLY);
         this.markDirty.run();
      }
      return faction;
   }

   @Nullable
   public Faction findSovereignFaction(UUID playerId) {
      if (playerId == null) {
         return null;
      }
      for (Faction faction : this.factions.values()) {
         if (faction.getType() == FactionType.PLAYER && faction.isSovereignOwnedBy(playerId)) {
            return faction;
         }
      }
      return null;
   }

   /**
    * Updates a faction's display name without changing its stable {@link FactionId}.
    */
   public boolean setDisplayName(FactionId factionId, String displayName) {
      Faction faction = this.factions.get(factionId);
      if (faction == null) {
         LOGGER.warn("[Millenaire] Cannot rename missing faction {}", factionId);
         return false;
      }

      faction.setDisplayName(displayName);
      this.markDirty.run();
      return true;
   }

   /**
    * Removes a faction. Settlements that still reference it are detached first.
    *
    * @return {@code true} when a faction was removed
    */
   public boolean removeFaction(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      Faction faction = this.factions.get(factionId);
      if (faction == null) {
         LOGGER.warn("[Millenaire] Cannot remove missing faction {}", factionId);
         return false;
      }

      VillageManager villageManager = VillageSavedData.get(level).getVillageManager();
      List<VillageId> settlements = new ArrayList<>(faction.getSettlementIds());

      for (VillageId settlementId : settlements) {
         Village village = villageManager.getVillage(settlementId);
         if (village != null && factionId.equals(village.getFactionId())) {
            village.setFactionId(null);
         }

         faction.removeSettlement(settlementId);
      }

      RelationshipSavedData.get(level).getRelationshipManager().removeAllInvolving(factionId);
      EconomySavedData.get(level).getTradeManager().removeAllInvolving(factionId);
      DiplomacySavedData.get(level).getDiplomaticManager().terminateAllInvolving(level, factionId);
      WarSavedData.get(level).getWarManager().handleFactionRemoved(level, factionId);
      MilitarySavedData.get(level).getMilitaryManager().removeFactionMilitary(level, factionId);
      TerritorySavedData.get(level).getTerritoryManager().handleFactionRemoved(level, factionId);
      PoliticsSavedData.get(level).getPoliticsManager().handleFactionRemoved(level, factionId);
      org.millenaire.faction.conflict.ConflictSavedData.get(level).getConflictManager().handleFactionRemoved(level, factionId);
      this.factions.remove(factionId);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Removed faction {} (detached {} settlement reference(s))", factionId, settlements.size());
      return true;
   }

   /**
    * Returns the default culture faction for {@code cultureId}, creating it when absent.
    */
   public Faction getOrCreateDefaultCultureFaction(ResourceLocation cultureId, long gameTime) {
      Objects.requireNonNull(cultureId, "cultureId");
      FactionId factionId = FactionIds.defaultCultureFaction(cultureId);
      Faction existing = this.factions.get(factionId);
      if (existing != null) {
         return existing;
      }

      String displayName = resolveCultureDisplayName(cultureId);
      Faction created = this.createFaction(factionId, displayName, FactionType.CULTURE, cultureId, gameTime);
      if (created == null) {
         // Concurrent duplicate is unexpected in single-threaded server logic; surface the existing entry.
         Faction raced = this.factions.get(factionId);
         if (raced != null) {
            return raced;
         }

         throw new IllegalStateException("Failed to create default culture faction for " + cultureId);
      }

      return created;
   }

   /**
    * Ensures {@code village} has a valid owning faction, creating a default culture faction when needed.
    * Missing PLAYER faction ids are detached (never rewritten onto a culture faction).
    */
   @Nullable
   public Faction ensureVillageFaction(ServerLevel level, Village village) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(village, "village");
      FactionId currentId = village.getFactionId();
      if (currentId != null) {
         Faction current = this.factions.get(currentId);
         if (current != null) {
            if (!current.hasSettlement(village.getId())) {
               current.addSettlement(village.getId());
               this.markDirty.run();
            }

            MilitarySavedData.get(level).getMilitaryManager().getOrCreateOrganization(current.getId());
            return current;
         }

         if (FactionIds.isPlayerFactionId(currentId)) {
            LOGGER.error(
               "[Millenaire] Village {} references missing PLAYER faction {} — detaching (will not absorb into culture faction)",
               village.getId().uuid(),
               currentId
            );
            village.setFactionId(null);
            this.markDirty.run();
            return null;
         }

         LOGGER.warn(
            "[Millenaire] Village {} references missing faction {} — reassigning to default culture faction",
            village.getId().uuid(),
            currentId
         );
         village.setFactionId(null);
      }

      Faction faction = this.getOrCreateDefaultCultureFaction(village.getCultureId(), level.getGameTime());
      this.assignVillage(village, faction.getId());
      MilitarySavedData.get(level).getMilitaryManager().getOrCreateOrganization(faction.getId());
      return faction;
   }

   /**
    * Assigns a settlement to exactly one faction, updating both the village and the faction index.
    *
    * @return {@code true} when the assignment changed or was confirmed on an existing faction
    */
   public boolean assignVillage(Village village, FactionId factionId) {
      Objects.requireNonNull(village, "village");
      Objects.requireNonNull(factionId, "factionId");
      Faction target = this.factions.get(factionId);
      if (target == null) {
         LOGGER.error("[Millenaire] Cannot assign village {} to missing faction {}", village.getId().uuid(), factionId);
         return false;
      }

      FactionId previousId = village.getFactionId();
      if (factionId.equals(previousId) && target.hasSettlement(village.getId())) {
         return true;
      }

      if (previousId != null && !previousId.equals(factionId)) {
         Faction previous = this.factions.get(previousId);
         if (previous != null && previous.removeSettlement(village.getId())) {
            this.markDirty.run();
         }
      }

      village.setFactionId(factionId);
      if (target.addSettlement(village.getId())) {
         this.markDirty.run();
      }

      return true;
   }

   /**
    * Detaches a settlement from its faction without deleting the faction.
    */
   public void unassignVillage(Village village) {
      if (village == null) {
         return;
      }

      FactionId factionId = village.getFactionId();
      if (factionId != null) {
         Faction faction = this.factions.get(factionId);
         if (faction != null && faction.removeSettlement(village.getId())) {
            this.markDirty.run();
         }
      }

      if (factionId != null) {
         village.setFactionId(null);
      }
   }

   @Nullable
   public Faction getFactionForVillage(Village village) {
      if (village == null) {
         return null;
      }

      FactionId factionId = village.getFactionId();
      return factionId == null ? null : this.factions.get(factionId);
   }

   public Collection<VillageId> getSettlements(FactionId factionId) {
      Faction faction = this.getFaction(factionId);
      return faction == null ? List.of() : faction.getSettlementIds();
   }

   /**
    * Bounded membership repair for one faction: drop missing settlements and re-index villages
    * that claim this faction id. Does not create new factions.
    */
   public void reconcileSettlements(ServerLevel level, Faction faction) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(faction, "faction");
      VillageManager villageManager = VillageSavedData.get(level).getVillageManager();
      boolean dirty = false;
      List<VillageId> known = new ArrayList<>(faction.getSettlementIds());
      for (VillageId settlementId : known) {
         Village village = villageManager.getVillage(settlementId);
         if (village == null) {
            if (faction.removeSettlement(settlementId)) {
               dirty = true;
            }
            continue;
         }
         FactionId claimed = village.getFactionId();
         if (claimed == null) {
            village.setFactionId(faction.getId());
            dirty = true;
         } else if (!claimed.equals(faction.getId())) {
            if (faction.removeSettlement(settlementId)) {
               dirty = true;
            }
         }
      }
      for (Village village : villageManager.getAllVillages()) {
         if (faction.getId().equals(village.getFactionId()) && !faction.hasSettlement(village.getId())) {
            faction.addSettlement(village.getId());
            dirty = true;
         }
      }
      if (dirty) {
         this.markDirty.run();
      }
   }

   /**
    * Rebuilds membership indexes from village data (authoritative for settlement → faction),
    * repairs missing factions, and assigns default culture factions to unassigned settlements.
    * Safe for new worlds and pre-faction existing worlds.
    */
   public void bootstrap(ServerLevel level, VillageManager villageManager) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(villageManager, "villageManager");
      boolean dirty = false;

      for (Faction faction : this.factions.values()) {
         if (!faction.getSettlementIds().isEmpty()) {
            // Rebuild from villages so stale settlement ids are dropped on the next save.
            dirty = true;
         }

         faction.clearSettlements();
      }

      for (Village village : villageManager.getAllVillages()) {
         FactionId storedId = village.getFactionId();
         if (storedId == null) {
            Faction assigned = this.getOrCreateDefaultCultureFaction(village.getCultureId(), level.getGameTime());
            village.setFactionId(assigned.getId());
            assigned.addSettlement(village.getId());
            dirty = true;
            LOGGER.debug(
               "[Millenaire] Assigned village {} ({}) to default culture faction {}",
               village.getId().uuid(),
               village.getCultureId(),
               assigned.getId()
            );
            continue;
         }

         Faction faction = this.factions.get(storedId);
         if (faction == null) {
            if (FactionIds.isPlayerFactionId(storedId)) {
               LOGGER.error(
                  "[Millenaire] Village {} referenced missing PLAYER faction {} — detaching during bootstrap",
                  village.getId().uuid(),
                  storedId
               );
               village.setFactionId(null);
               dirty = true;
               continue;
            }
            LOGGER.warn(
               "[Millenaire] Village {} referenced missing faction {} — creating default culture faction for {}",
               village.getId().uuid(),
               storedId,
               village.getCultureId()
            );
            Faction assigned = this.getOrCreateDefaultCultureFaction(village.getCultureId(), level.getGameTime());
            village.setFactionId(assigned.getId());
            assigned.addSettlement(village.getId());
            dirty = true;
            continue;
         }

         if (faction.addSettlement(village.getId())) {
            dirty = true;
         }
      }

      if (dirty) {
         this.markDirty.run();
      }

      LOGGER.info(
         "[Millenaire] Faction bootstrap complete — {} faction(s), {} settlement(s)",
         this.factions.size(),
         villageManager.getAllVillages().size()
      );
   }

   /**
    * Loads a faction from persisted data. Duplicate ids and invalid records are skipped with logging.
    */
   void loadFaction(Faction faction) {
      Objects.requireNonNull(faction, "faction");
      if (this.factions.containsKey(faction.getId())) {
         LOGGER.error("[Millenaire] Ignoring duplicate persisted faction id {}", faction.getId());
         return;
      }

      this.factions.put(faction.getId(), faction);
   }

   void clear() {
      this.factions.clear();
   }

   private static String resolveCultureDisplayName(ResourceLocation cultureId) {
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
         return culture.displayName();
      }

      return cultureId.getPath();
   }
}
