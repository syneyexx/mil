package org.millenaire.faction.territory;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.war.PeaceOutcome;
import org.millenaire.faction.war.WarId;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public final class TerritorySavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_territory";

   private final TerritoryManager territoryManager = new TerritoryManager(this::setDirty);

   private TerritorySavedData() {
   }

   public static Factory<TerritorySavedData> factory() {
      return new Factory<>(TerritorySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static TerritorySavedData get(ServerLevel level) {
      return (TerritorySavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public TerritoryManager getTerritoryManager() {
      return this.territoryManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_update", this.territoryManager.getLastUpdateGameTime());

      ListTag territories = new ListTag();
      for (Territory territory : this.territoryManager.getAllTerritories()) {
         try {
            territories.add(saveTerritory(territory));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize territory {}", territory.getId(), e);
         }
      }
      root.put("territories", territories);

      ListTag claims = new ListTag();
      for (TerritoryClaim claim : this.territoryManager.getAllClaims()) {
         claims.add(saveClaim(claim));
      }
      root.put("claims", claims);

      ListTag disputes = new ListTag();
      for (TerritorialDispute dispute : this.territoryManager.getAllDisputes()) {
         disputes.add(saveDispute(dispute));
      }
      root.put("disputes", disputes);

      ListTag transfers = new ListTag();
      for (OwnershipTransfer transfer : this.territoryManager.getAllTransfers()) {
         transfers.add(saveTransfer(transfer));
      }
      root.put("transfers", transfers);

      ListTag treaties = new ListTag();
      for (PeaceTreaty treaty : this.territoryManager.getAllTreaties()) {
         treaties.add(saveTreaty(treaty));
      }
      root.put("treaties", treaties);

      ListTag statuses = new ListTag();
      for (Map.Entry<FactionId, FactionTerritorialStatusHolder> entry : this.territoryManager.getFactionStatusMap().entrySet()) {
         CompoundTag tag = new CompoundTag();
         tag.putString("faction", entry.getKey().asString());
         tag.putString("status", entry.getValue().getStatus().name());
         tag.putInt("overextension", entry.getValue().getOverextension());
         tag.putInt("admin_capacity", entry.getValue().getAdministrativeCapacity());
         statuses.add(tag);
      }
      root.put("faction_status", statuses);

      ListTag history = new ListTag();
      for (TerritoryHistoryEntry entry : this.territoryManager.getGlobalHistory()) {
         history.add(saveHistory(entry));
      }
      root.put("history", history);
      return root;
   }

   private static CompoundTag saveTerritory(Territory territory) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", territory.getId().uuid());
      tag.putUUID("settlement", territory.getSettlementId().uuid());
      tag.putString("owner", territory.getOwnerFactionId().asString());
      if (territory.getPreviousOwnerFactionId() != null) {
         tag.putString("previous_owner", territory.getPreviousOwnerFactionId().asString());
      }
      if (territory.getControllerFactionId() != null) {
         tag.putString("controller", territory.getControllerFactionId().asString());
      }
      if (territory.getOccupierFactionId() != null) {
         tag.putString("occupier", territory.getOccupierFactionId().asString());
      }
      tag.putString("state", territory.getState().name());
      tag.putInt("resistance", territory.getResistance());
      tag.putInt("stability", territory.getStability());
      tag.putInt("integration", territory.getIntegrationProgress());
      tag.putInt("strategic", territory.getStrategicValue());
      tag.putInt("economic", territory.getEconomicValue());
      tag.putLong("owned_since", territory.getOwnedSinceGameTime());
      tag.putLong("last_change", territory.getLastStateChangeGameTime());
      if (territory.getDimensionId() != null) {
         tag.putString("dimension", territory.getDimensionId().toString());
      }
      if (territory.getCenter() != null) {
         tag.putLong("center", territory.getCenter().asLong());
      }
      if (territory.getOccupation() != null) {
         tag.put("occupation", saveOccupation(territory.getOccupation()));
      }
      ListTag neighbors = new ListTag();
      for (TerritoryId neighbor : territory.getNeighborIds()) {
         CompoundTag n = new CompoundTag();
         n.putUUID("id", neighbor.uuid());
         neighbors.add(n);
      }
      tag.put("neighbors", neighbors);
      ListTag history = new ListTag();
      for (TerritoryHistoryEntry entry : territory.getHistory()) {
         history.add(saveHistory(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveOccupation(OccupationAdministration occupation) {
      CompoundTag tag = new CompoundTag();
      tag.putString("occupier", occupation.getOccupierFactionId().asString());
      if (occupation.getAdministratorFactionId() != null) {
         tag.putString("administrator", occupation.getAdministratorFactionId().asString());
      }
      tag.putString("policy", occupation.getPolicy().name());
      tag.putLong("started", occupation.getStartedGameTime());
      tag.putInt("required_garrison", occupation.getRequiredGarrison());
      tag.putInt("present_garrison", occupation.getPresentGarrison());
      if (occupation.getGarrisonArmyId() != null) {
         tag.putUUID("garrison_army", occupation.getGarrisonArmyId().uuid());
      }
      tag.putInt("rebellion", occupation.getRebellionPressure());
      tag.putBoolean("active", occupation.isActive());
      tag.putLong("ended", occupation.getEndedGameTime());
      return tag;
   }

   private static CompoundTag saveClaim(TerritoryClaim claim) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", claim.getId().uuid());
      tag.putString("claimant", claim.getClaimantFactionId().asString());
      tag.putUUID("territory", claim.getTerritoryId().uuid());
      tag.putString("type", claim.getType().name());
      tag.putInt("strength", claim.getStrength());
      if (claim.getReason() != null) {
         tag.putString("reason", claim.getReason());
      }
      tag.putLong("created", claim.getCreatedGameTime());
      tag.putLong("expiration", claim.getExpirationGameTime());
      tag.putBoolean("active", claim.isActive());
      tag.putLong("resolved", claim.getResolvedGameTime());
      if (claim.getOriginWarId() != null) {
         tag.putUUID("war", claim.getOriginWarId().uuid());
      }
      return tag;
   }

   private static CompoundTag saveDispute(TerritorialDispute dispute) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", dispute.getId().uuid());
      tag.putUUID("territory", dispute.getTerritoryId().uuid());
      tag.putString("reason", dispute.getReason());
      tag.putLong("created", dispute.getCreatedGameTime());
      tag.putString("status", dispute.getStatus().name());
      if (dispute.getRelatedWarId() != null) {
         tag.putUUID("war", dispute.getRelatedWarId().uuid());
      }
      if (dispute.getRelatedPeaceTreatyId() != null) {
         tag.putUUID("treaty", dispute.getRelatedPeaceTreatyId().uuid());
      }
      tag.putLong("resolved", dispute.getResolvedGameTime());
      if (dispute.getResolutionReason() != null) {
         tag.putString("resolution", dispute.getResolutionReason());
      }
      ListTag claimants = new ListTag();
      for (FactionId claimant : dispute.getClaimantFactionIds()) {
         CompoundTag c = new CompoundTag();
         c.putString("id", claimant.asString());
         claimants.add(c);
      }
      tag.put("claimants", claimants);
      return tag;
   }

   private static CompoundTag saveTransfer(OwnershipTransfer transfer) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", transfer.getId().uuid());
      tag.putUUID("territory", transfer.getTerritoryId().uuid());
      tag.putUUID("settlement", transfer.getSettlementId().uuid());
      tag.putString("previous", transfer.getPreviousOwnerFactionId().asString());
      tag.putString("new", transfer.getNewOwnerFactionId().asString());
      tag.putString("type", transfer.getTransferType().name());
      if (transfer.getWarId() != null) {
         tag.putUUID("war", transfer.getWarId().uuid());
      }
      if (transfer.getPeaceTreatyId() != null) {
         tag.putUUID("treaty", transfer.getPeaceTreatyId().uuid());
      }
      tag.putLong("time", transfer.getTransferredGameTime());
      if (transfer.getReason() != null) {
         tag.putString("reason", transfer.getReason());
      }
      return tag;
   }

   private static CompoundTag saveTreaty(PeaceTreaty treaty) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", treaty.getId().uuid());
      tag.putUUID("war", treaty.getWarId().uuid());
      tag.putString("outcome", treaty.getOutcome().name());
      tag.putLong("settled", treaty.getSettledGameTime());
      tag.putInt("score", treaty.getFinalWarScore());
      if (treaty.getReason() != null) {
         tag.putString("reason", treaty.getReason());
      }
      ListTag participants = new ListTag();
      for (FactionId factionId : treaty.getParticipantFactionIds()) {
         CompoundTag p = new CompoundTag();
         p.putString("id", factionId.asString());
         participants.add(p);
      }
      tag.put("participants", participants);
      ListTag terms = new ListTag();
      for (PeaceTerm term : treaty.getTerms()) {
         CompoundTag t = new CompoundTag();
         t.putString("type", term.getType().name());
         if (term.getTerritoryId() != null) {
            t.putUUID("territory", term.getTerritoryId().uuid());
         }
         if (term.getFromFactionId() != null) {
            t.putString("from", term.getFromFactionId().asString());
         }
         if (term.getToFactionId() != null) {
            t.putString("to", term.getToFactionId().asString());
         }
         t.putInt("quantity", term.getQuantity());
         if (term.getNote() != null) {
            t.putString("note", term.getNote());
         }
         terms.add(t);
      }
      tag.put("terms", terms);
      ListTag transferIds = new ListTag();
      for (TransferId transferId : treaty.getTransferIds()) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", transferId.uuid());
         transferIds.add(t);
      }
      tag.put("transfers", transferIds);
      return tag;
   }

   private static CompoundTag saveHistory(TerritoryHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      tag.putUUID("territory", entry.getTerritoryId().uuid());
      if (entry.getOldOwner() != null) {
         tag.putString("old_owner", entry.getOldOwner().asString());
      }
      if (entry.getNewOwner() != null) {
         tag.putString("new_owner", entry.getNewOwner().asString());
      }
      if (entry.getController() != null) {
         tag.putString("controller", entry.getController().asString());
      }
      if (entry.getOccupier() != null) {
         tag.putString("occupier", entry.getOccupier().asString());
      }
      if (entry.getWarId() != null) {
         tag.putUUID("war", entry.getWarId().uuid());
      }
      if (entry.getPeaceTreatyId() != null) {
         tag.putUUID("treaty", entry.getPeaceTreatyId().uuid());
      }
      tag.putInt("quantity", entry.getQuantity());
      if (entry.getDetail() != null) {
         tag.putString("detail", entry.getDetail());
      }
      return tag;
   }

   private static TerritorySavedData load(CompoundTag root) {
      TerritorySavedData data = new TerritorySavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] TerritorySavedData version {} newer than supported {}", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading TerritorySavedData version {}", version);
      }

      if (root.contains("last_update")) {
         data.territoryManager.setLastUpdateGameTime(root.getLong("last_update"));
      }

      if (root.contains("territories")) {
         ListTag list = root.getList("territories", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               Territory territory = readTerritory(list.getCompound(i));
               if (territory != null) {
                  data.territoryManager.loadTerritory(territory);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed territory at {}", i, e);
            }
         }
      }

      if (root.contains("claims")) {
         ListTag list = root.getList("claims", 10);
         for (int i = 0; i < list.size(); i++) {
            TerritoryClaim claim = readClaim(list.getCompound(i));
            if (claim != null) {
               data.territoryManager.loadClaim(claim);
            }
         }
      }

      if (root.contains("disputes")) {
         ListTag list = root.getList("disputes", 10);
         for (int i = 0; i < list.size(); i++) {
            TerritorialDispute dispute = readDispute(list.getCompound(i));
            if (dispute != null) {
               data.territoryManager.loadDispute(dispute);
            }
         }
      }

      if (root.contains("transfers")) {
         ListTag list = root.getList("transfers", 10);
         for (int i = 0; i < list.size(); i++) {
            OwnershipTransfer transfer = readTransfer(list.getCompound(i));
            if (transfer != null) {
               data.territoryManager.loadTransfer(transfer);
            }
         }
      }

      if (root.contains("treaties")) {
         ListTag list = root.getList("treaties", 10);
         for (int i = 0; i < list.size(); i++) {
            PeaceTreaty treaty = readTreaty(list.getCompound(i));
            if (treaty != null) {
               data.territoryManager.loadTreaty(treaty);
            }
         }
      }

      if (root.contains("faction_status")) {
         ListTag list = root.getList("faction_status", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
            if (factionId == null) {
               continue;
            }

            FactionTerritorialStatusHolder holder = new FactionTerritorialStatusHolder();
            holder.setStatus(FactionTerritorialStatus.tryParse(tag.contains("status") ? tag.getString("status") : null));
            holder.setOverextension(tag.contains("overextension") ? tag.getInt("overextension") : 0);
            holder.setAdministrativeCapacity(tag.contains("admin_capacity") ? tag.getInt("admin_capacity") : TerritoryConfig.ADMIN_CAPACITY_BASE);
            data.territoryManager.loadFactionStatus(factionId, holder);
         }
      }

      if (root.contains("history")) {
         ListTag list = root.getList("history", 10);
         List<TerritoryHistoryEntry> history = new ArrayList<>();
         for (int i = 0; i < list.size(); i++) {
            TerritoryHistoryEntry entry = readHistory(list.getCompound(i));
            if (entry != null) {
               history.add(entry);
            }
         }

         data.territoryManager.loadGlobalHistory(history);
      }

      return data;
   }

   @Nullable
   private static Territory readTerritory(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("settlement")) {
         return null;
      }

      FactionId owner = FactionId.tryParse(tag.contains("owner") ? tag.getString("owner") : null);
      if (owner == null) {
         return null;
      }

      Territory territory = new Territory(
         new TerritoryId(tag.getUUID("id")),
         new VillageId(tag.getUUID("settlement")),
         owner,
         tag.contains("owned_since") ? tag.getLong("owned_since") : 0L
      );
      OccupationAdministration occupation = tag.contains("occupation") ? readOccupation(tag.getCompound("occupation")) : null;
      Set<TerritoryId> neighbors = new LinkedHashSet<>();
      if (tag.contains("neighbors")) {
         ListTag list = tag.getList("neighbors", 10);
         for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).hasUUID("id")) {
               neighbors.add(new TerritoryId(list.getCompound(i).getUUID("id")));
            }
         }
      }

      List<TerritoryHistoryEntry> history = new ArrayList<>();
      if (tag.contains("history")) {
         ListTag list = tag.getList("history", 10);
         for (int i = 0; i < list.size(); i++) {
            TerritoryHistoryEntry entry = readHistory(list.getCompound(i));
            if (entry != null) {
               history.add(entry);
            }
         }
      }

      ResourceLocation dimension = null;
      if (tag.contains("dimension")) {
         try {
            dimension = ResourceLocation.parse(tag.getString("dimension"));
         } catch (RuntimeException ignored) {
         }
      }

      territory.loadState(
         owner,
         FactionId.tryParse(tag.contains("previous_owner") ? tag.getString("previous_owner") : null),
         FactionId.tryParse(tag.contains("controller") ? tag.getString("controller") : null),
         FactionId.tryParse(tag.contains("occupier") ? tag.getString("occupier") : null),
         TerritoryState.tryParse(tag.contains("state") ? tag.getString("state") : null),
         tag.contains("resistance") ? tag.getInt("resistance") : 0,
         tag.contains("stability") ? tag.getInt("stability") : 70,
         tag.contains("integration") ? tag.getInt("integration") : 100,
         tag.contains("strategic") ? tag.getInt("strategic") : 10,
         tag.contains("economic") ? tag.getInt("economic") : 10,
         tag.contains("owned_since") ? tag.getLong("owned_since") : 0L,
         tag.contains("last_change") ? tag.getLong("last_change") : 0L,
         occupation,
         dimension,
         tag.contains("center") ? BlockPos.of(tag.getLong("center")) : null,
         neighbors,
         history
      );
      return territory;
   }

   @Nullable
   private static OccupationAdministration readOccupation(CompoundTag tag) {
      FactionId occupier = FactionId.tryParse(tag.contains("occupier") ? tag.getString("occupier") : null);
      if (occupier == null) {
         return null;
      }

      OccupationAdministration occupation = new OccupationAdministration(
         occupier, tag.contains("started") ? tag.getLong("started") : 0L, tag.contains("required_garrison") ? tag.getInt("required_garrison") : 0
      );
      occupation.loadState(
         occupier,
         FactionId.tryParse(tag.contains("administrator") ? tag.getString("administrator") : null),
         OccupationPolicy.tryParse(tag.contains("policy") ? tag.getString("policy") : null),
         tag.contains("started") ? tag.getLong("started") : 0L,
         tag.contains("required_garrison") ? tag.getInt("required_garrison") : 0,
         tag.contains("present_garrison") ? tag.getInt("present_garrison") : 0,
         tag.hasUUID("garrison_army") ? new ArmyId(tag.getUUID("garrison_army")) : null,
         tag.contains("rebellion") ? tag.getInt("rebellion") : 0,
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("ended") ? tag.getLong("ended") : 0L
      );
      return occupation;
   }

   @Nullable
   private static TerritoryClaim readClaim(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("territory")) {
         return null;
      }

      FactionId claimant = FactionId.tryParse(tag.contains("claimant") ? tag.getString("claimant") : null);
      if (claimant == null) {
         return null;
      }

      TerritoryClaim claim = new TerritoryClaim(
         new ClaimId(tag.getUUID("id")),
         claimant,
         new TerritoryId(tag.getUUID("territory")),
         ClaimType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.contains("strength") ? tag.getInt("strength") : 0,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.contains("reason") ? tag.getString("reason") : null
      );
      claim.loadState(
         tag.contains("strength") ? tag.getInt("strength") : 0,
         tag.contains("expiration") ? tag.getLong("expiration") : 0L,
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("resolved") ? tag.getLong("resolved") : 0L,
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : null
      );
      return claim;
   }

   @Nullable
   private static TerritorialDispute readDispute(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("territory")) {
         return null;
      }

      TerritorialDispute dispute = new TerritorialDispute(
         new DisputeId(tag.getUUID("id")),
         new TerritoryId(tag.getUUID("territory")),
         tag.contains("reason") ? tag.getString("reason") : "unspecified",
         tag.contains("created") ? tag.getLong("created") : 0L
      );
      Set<FactionId> claimants = new LinkedHashSet<>();
      if (tag.contains("claimants")) {
         ListTag list = tag.getList("claimants", 10);
         for (int i = 0; i < list.size(); i++) {
            FactionId id = FactionId.tryParse(list.getCompound(i).contains("id") ? list.getCompound(i).getString("id") : null);
            if (id != null) {
               claimants.add(id);
            }
         }
      }

      dispute.loadState(
         claimants,
         DisputeStatus.tryParse(tag.contains("status") ? tag.getString("status") : null),
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : null,
         tag.hasUUID("treaty") ? new PeaceTreatyId(tag.getUUID("treaty")) : null,
         tag.contains("resolved") ? tag.getLong("resolved") : 0L,
         tag.contains("resolution") ? tag.getString("resolution") : null
      );
      return dispute;
   }

   @Nullable
   private static OwnershipTransfer readTransfer(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("territory") || !tag.hasUUID("settlement")) {
         return null;
      }

      FactionId previous = FactionId.tryParse(tag.contains("previous") ? tag.getString("previous") : null);
      FactionId neu = FactionId.tryParse(tag.contains("new") ? tag.getString("new") : null);
      if (previous == null || neu == null) {
         return null;
      }

      return new OwnershipTransfer(
         new TransferId(tag.getUUID("id")),
         new TerritoryId(tag.getUUID("territory")),
         new VillageId(tag.getUUID("settlement")),
         previous,
         neu,
         ConquestType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.contains("time") ? tag.getLong("time") : 0L,
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : null,
         tag.hasUUID("treaty") ? new PeaceTreatyId(tag.getUUID("treaty")) : null,
         tag.contains("reason") ? tag.getString("reason") : null
      );
   }

   @Nullable
   private static PeaceTreaty readTreaty(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("war")) {
         return null;
      }

      PeaceTreaty treaty = new PeaceTreaty(
         new PeaceTreatyId(tag.getUUID("id")),
         new WarId(tag.getUUID("war")),
         PeaceOutcome.tryParse(tag.contains("outcome") ? tag.getString("outcome") : null),
         tag.contains("settled") ? tag.getLong("settled") : 0L,
         tag.contains("score") ? tag.getInt("score") : 0,
         tag.contains("reason") ? tag.getString("reason") : null
      );
      Set<FactionId> participants = new LinkedHashSet<>();
      if (tag.contains("participants")) {
         ListTag list = tag.getList("participants", 10);
         for (int i = 0; i < list.size(); i++) {
            FactionId id = FactionId.tryParse(list.getCompound(i).contains("id") ? list.getCompound(i).getString("id") : null);
            if (id != null) {
               participants.add(id);
            }
         }
      }

      treaty.loadParticipants(participants);
      List<PeaceTerm> terms = new ArrayList<>();
      if (tag.contains("terms")) {
         ListTag list = tag.getList("terms", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            PeaceTermType type = PeaceTermType.tryParse(t.contains("type") ? t.getString("type") : null);
            if (type == null) {
               continue;
            }

            terms.add(
               new PeaceTerm(
                  type,
                  t.hasUUID("territory") ? new TerritoryId(t.getUUID("territory")) : null,
                  FactionId.tryParse(t.contains("from") ? t.getString("from") : null),
                  FactionId.tryParse(t.contains("to") ? t.getString("to") : null),
                  t.contains("quantity") ? t.getInt("quantity") : 0,
                  t.contains("note") ? t.getString("note") : null
               )
            );
         }
      }

      treaty.loadTerms(terms);
      List<TransferId> transferIds = new ArrayList<>();
      if (tag.contains("transfers")) {
         ListTag list = tag.getList("transfers", 10);
         for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).hasUUID("id")) {
               transferIds.add(new TransferId(list.getCompound(i).getUUID("id")));
            }
         }
      }

      treaty.loadTransferIds(transferIds);
      return treaty;
   }

   @Nullable
   private static TerritoryHistoryEntry readHistory(CompoundTag tag) {
      TerritoryEventType eventType = TerritoryEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      if (eventType == null || !tag.hasUUID("territory")) {
         return null;
      }

      return new TerritoryHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         new TerritoryId(tag.getUUID("territory")),
         FactionId.tryParse(tag.contains("old_owner") ? tag.getString("old_owner") : null),
         FactionId.tryParse(tag.contains("new_owner") ? tag.getString("new_owner") : null),
         FactionId.tryParse(tag.contains("controller") ? tag.getString("controller") : null),
         FactionId.tryParse(tag.contains("occupier") ? tag.getString("occupier") : null),
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : null,
         tag.hasUUID("treaty") ? new PeaceTreatyId(tag.getUUID("treaty")) : null,
         tag.contains("quantity") ? tag.getInt("quantity") : 0,
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }
}
