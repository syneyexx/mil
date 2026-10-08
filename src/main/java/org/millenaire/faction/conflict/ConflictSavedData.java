package org.millenaire.faction.conflict;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.politics.LeaderId;
import org.millenaire.faction.politics.PoliticalGroupId;
import org.millenaire.faction.territory.TerritoryId;
import org.millenaire.faction.war.BattleId;
import org.millenaire.faction.war.WarId;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for Phase 9 internal conflict state.
 */
public final class ConflictSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = ConflictConfig.SERIALIZATION_VERSION;
   public static final String DATA_NAME = "millenaire_faction_conflict";

   private final InternalConflictManager conflictManager = new InternalConflictManager(this::setDirty);

   private ConflictSavedData() {
   }

   public static Factory<ConflictSavedData> factory() {
      return new Factory<>(ConflictSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static ConflictSavedData get(ServerLevel level) {
      return (ConflictSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public InternalConflictManager getConflictManager() {
      return this.conflictManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_update", this.conflictManager.getLastUpdateGameTime());

      ListTag unrestList = new ListTag();
      for (Unrest unrest : this.conflictManager.getUnrestRecordsMap().values()) {
         try {
            unrestList.add(saveUnrest(unrest));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize unrest {}", unrest.getId(), e);
         }
      }
      root.put("unrest", unrestList);

      ListTag conflicts = new ListTag();
      for (InternalConflict conflict : this.conflictManager.getConflictsMap().values()) {
         try {
            conflicts.add(saveConflict(conflict));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize conflict {}", conflict.getId(), e);
         }
      }
      root.put("conflicts", conflicts);

      ListTag recoveries = new ListTag();
      for (PostConflictRecovery recovery : this.conflictManager.getRecoveriesMap().values()) {
         try {
            recoveries.add(saveRecovery(recovery));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize recovery {}", recovery.getConflictId(), e);
         }
      }
      root.put("recoveries", recoveries);

      ListTag settlements = new ListTag();
      for (NegotiatedSettlement settlement : this.conflictManager.getSettlementsMap().values()) {
         try {
            settlements.add(saveSettlement(settlement));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize negotiated settlement {}", settlement.getConflictId(), e);
         }
      }
      root.put("negotiated_settlements", settlements);

      ListTag armyLoyalty = new ListTag();
      for (Entry<ArmyId, MilitaryLoyalty> entry : this.conflictManager.getArmyLoyaltyMap().entrySet()) {
         try {
            CompoundTag row = new CompoundTag();
            row.putUUID("army", entry.getKey().uuid());
            row.putString("loyalty", entry.getValue().name());
            armyLoyalty.add(row);
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize army loyalty {}", entry.getKey(), e);
         }
      }
      root.put("army_loyalty", armyLoyalty);

      ListTag recognition = new ListTag();
      for (Entry<ConflictId, Set<FactionId>> entry : this.conflictManager.getForeignRecognitionMap().entrySet()) {
         try {
            CompoundTag row = new CompoundTag();
            row.putUUID("conflict", entry.getKey().uuid());
            ListTag factions = new ListTag();
            for (FactionId factionId : entry.getValue()) {
               factions.add(StringTag.valueOf(factionId.asString()));
            }
            row.put("factions", factions);
            recognition.add(row);
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize foreign recognition {}", entry.getKey(), e);
         }
      }
      root.put("foreign_recognition", recognition);

      ListTag cooldowns = new ListTag();
      for (Entry<String, Long> entry : this.conflictManager.getLastIdenticalConflictEndMap().entrySet()) {
         CompoundTag row = new CompoundTag();
         row.putString("key", entry.getKey());
         row.putLong("ended", entry.getValue());
         cooldowns.add(row);
      }
      root.put("cooldowns", cooldowns);

      LOGGER.debug(
         "[Millenaire] Saved conflict data: {} conflict(s), {} unrest, {} recovery(ies), {} settlement(s)",
         conflicts.size(),
         unrestList.size(),
         recoveries.size(),
         settlements.size()
      );
      return root;
   }

   private static CompoundTag saveConflict(InternalConflict conflict) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", conflict.getId().uuid());
      tag.putString("origin", conflict.getOriginFactionId().asString());
      tag.putString("type", conflict.getType().name());
      tag.putString("stage", conflict.getStage().name());
      tag.putLong("started", conflict.getStartedGameTime());
      tag.putLong("last_updated", conflict.getLastUpdatedGameTime());
      tag.putLong("ended", conflict.getEndedGameTime());
      if (conflict.getGovernmentSideId() != null) {
         tag.putUUID("government_side", conflict.getGovernmentSideId().uuid());
      }
      if (conflict.getPrimaryRebelSideId() != null) {
         tag.putUUID("primary_rebel_side", conflict.getPrimaryRebelSideId().uuid());
      }
      if (conflict.getRebelFactionId() != null) {
         tag.putString("rebel_faction", conflict.getRebelFactionId().asString());
      }
      if (conflict.getLinkedWarId() != null) {
         tag.putUUID("linked_war", conflict.getLinkedWarId().uuid());
      }
      if (conflict.getResolution() != null) {
         tag.putString("resolution", conflict.getResolution().name());
      }
      if (conflict.getCauseDetail() != null) {
         tag.putString("cause", conflict.getCauseDetail());
      }
      tag.putBoolean("negotiating", conflict.isNegotiating());
      tag.putBoolean("active", conflict.isMarkedActive());
      tag.putLong("recovery_until", conflict.getRecoveryUntilGameTime());

      ListTag relatedUnrest = new ListTag();
      for (UnrestId unrestId : conflict.getRelatedUnrestIds()) {
         CompoundTag u = new CompoundTag();
         u.putUUID("id", unrestId.uuid());
         relatedUnrest.add(u);
      }
      tag.put("related_unrest", relatedUnrest);

      ListTag modifiers = new ListTag();
      for (PostConflictModifier modifier : conflict.getPostModifiers()) {
         modifiers.add(StringTag.valueOf(modifier.name()));
      }
      tag.put("post_modifiers", modifiers);

      ListTag sides = new ListTag();
      for (ConflictSide side : conflict.getSides().values()) {
         sides.add(saveSide(side));
      }
      tag.put("sides", sides);

      ListTag history = new ListTag();
      for (ConflictHistoryEntry entry : conflict.getHistory()) {
         history.add(saveHistory(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveSide(ConflictSide side) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", side.getId().uuid());
      tag.putUUID("conflict", side.getConflictId().uuid());
      tag.putString("faction", side.getFactionId().asString());
      tag.putBoolean("government", side.isGovernmentSide());
      if (side.getLeaderId() != null) {
         tag.putUUID("leader", side.getLeaderId().uuid());
      }
      if (side.getDisplayName() != null) {
         tag.putString("display_name", side.getDisplayName());
      }
      tag.putInt("conflict_score", side.getConflictScore());
      tag.putInt("casualties", side.getCasualties());
      tag.putInt("exhaustion", side.getExhaustion());
      tag.putInt("support", side.getSupport());
      tag.putInt("legitimacy", side.getLegitimacy());
      tag.putString("objective", side.getPrimaryObjective().name());
      tag.putBoolean("active", side.isActive());

      ListTag supporters = new ListTag();
      for (PoliticalGroupId groupId : side.getSupporterGroupIds()) {
         CompoundTag g = new CompoundTag();
         g.putUUID("id", groupId.uuid());
         supporters.add(g);
      }
      tag.put("supporters", supporters);

      ListTag armies = new ListTag();
      for (ArmyId armyId : side.getArmyIds()) {
         CompoundTag a = new CompoundTag();
         a.putUUID("id", armyId.uuid());
         armies.add(a);
      }
      tag.put("armies", armies);

      ListTag territories = new ListTag();
      for (TerritoryId territoryId : side.getControlledTerritoryIds()) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", territoryId.uuid());
         territories.add(t);
      }
      tag.put("territories", territories);

      ListTag settlements = new ListTag();
      for (VillageId settlementId : side.getControlledSettlementIds()) {
         CompoundTag s = new CompoundTag();
         s.putUUID("id", settlementId.uuid());
         settlements.add(s);
      }
      tag.put("settlements", settlements);
      return tag;
   }

   private static CompoundTag saveHistory(ConflictHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      tag.putUUID("conflict", entry.getConflictId().uuid());
      tag.putString("faction", entry.getFactionId().asString());
      if (entry.getSideId() != null) {
         tag.putUUID("side", entry.getSideId().uuid());
      }
      if (entry.getWarId() != null) {
         tag.putUUID("war", entry.getWarId().uuid());
      }
      if (entry.getBattleId() != null) {
         tag.putUUID("battle", entry.getBattleId().uuid());
      }
      if (entry.getSettlementId() != null) {
         tag.putUUID("settlement", entry.getSettlementId().uuid());
      }
      tag.putInt("quantity", entry.getQuantity());
      if (entry.getDetail() != null) {
         tag.putString("detail", entry.getDetail());
      }
      return tag;
   }

   private static CompoundTag saveUnrest(Unrest unrest) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", unrest.getId().uuid());
      tag.putString("faction", unrest.getFactionId().asString());
      tag.putString("cause", unrest.getCause().name());
      tag.putString("scope", unrest.getScope().name());
      tag.putInt("severity", unrest.getSeverity());
      tag.putInt("escalation", unrest.getEscalationPressure());
      if (unrest.getPoliticalGroupId() != null) {
         tag.putUUID("group", unrest.getPoliticalGroupId().uuid());
      }
      if (unrest.getSettlementId() != null) {
         tag.putUUID("settlement", unrest.getSettlementId().uuid());
      }
      if (unrest.getTerritoryId() != null) {
         tag.putUUID("territory", unrest.getTerritoryId().uuid());
      }
      tag.putLong("started", unrest.getStartedGameTime());
      tag.putLong("last_updated", unrest.getLastUpdatedGameTime());
      tag.putBoolean("active", unrest.isActive());
      tag.putLong("resolved", unrest.getResolvedGameTime());
      if (unrest.getDetail() != null) {
         tag.putString("detail", unrest.getDetail());
      }
      return tag;
   }

   private static CompoundTag saveRecovery(PostConflictRecovery recovery) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("conflict", recovery.getConflictId().uuid());
      tag.putString("faction", recovery.getFactionId().asString());
      tag.putLong("started", recovery.getStartedGameTime());
      tag.putLong("ends", recovery.getEndsGameTime());
      tag.putInt("stability_penalty", recovery.getRemainingStabilityPenalty());
      tag.putInt("pressure_bonus", recovery.getRemainingPressureBonus());
      tag.putBoolean("active", recovery.isActive());
      if (recovery.getResolvedAs() != null) {
         tag.putString("resolved_as", recovery.getResolvedAs().name());
      }

      ListTag modifiers = new ListTag();
      for (PostConflictModifier modifier : recovery.getModifiers()) {
         modifiers.add(StringTag.valueOf(modifier.name()));
      }
      tag.put("modifiers", modifiers);
      return tag;
   }

   private static CompoundTag saveSettlement(NegotiatedSettlement settlement) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("conflict", settlement.getConflictId().uuid());
      tag.putString("origin", settlement.getOriginFactionId().asString());
      tag.putString("outcome", settlement.getOutcome().name());
      tag.putLong("settled", settlement.getSettledGameTime());
      tag.putInt("autonomy", settlement.getAutonomyGranted());
      tag.putBoolean("leadership_changed", settlement.isLeadershipChanged());
      tag.putBoolean("government_reformed", settlement.isGovernmentReformed());
      tag.putBoolean("territory_ceded", settlement.isTerritoryCeded());
      if (settlement.getDetail() != null) {
         tag.putString("detail", settlement.getDetail());
      }

      ListTag terms = new ListTag();
      for (String term : settlement.getTerms()) {
         terms.add(StringTag.valueOf(term));
      }
      tag.put("terms", terms);
      return tag;
   }

   private static ConflictSavedData load(CompoundTag root) {
      ConflictSavedData data = new ConflictSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] ConflictSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading ConflictSavedData version {}", version);
      }

      if (root.contains("last_update")) {
         data.conflictManager.setLastUpdateGameTime(root.getLong("last_update"));
      }

      if (root.contains("unrest")) {
         ListTag list = root.getList("unrest", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               Unrest unrest = readUnrest(list.getCompound(i));
               if (unrest != null) {
                  data.conflictManager.putUnrestForLoad(unrest);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed unrest at index {}", i, e);
            }
         }
      }

      if (root.contains("conflicts")) {
         ListTag list = root.getList("conflicts", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               InternalConflict conflict = readConflict(list.getCompound(i));
               if (conflict != null) {
                  data.conflictManager.putConflictForLoad(conflict);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed conflict at index {}", i, e);
            }
         }
      }

      if (root.contains("recoveries")) {
         ListTag list = root.getList("recoveries", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               PostConflictRecovery recovery = readRecovery(list.getCompound(i));
               if (recovery != null) {
                  data.conflictManager.putRecoveryForLoad(recovery);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed recovery at index {}", i, e);
            }
         }
      }

      if (root.contains("negotiated_settlements")) {
         ListTag list = root.getList("negotiated_settlements", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               NegotiatedSettlement settlement = readSettlement(list.getCompound(i));
               if (settlement != null) {
                  data.conflictManager.putSettlementForLoad(settlement);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed negotiated settlement at index {}", i, e);
            }
         }
      }

      if (root.contains("army_loyalty")) {
         ListTag list = root.getList("army_loyalty", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               CompoundTag row = list.getCompound(i);
               if (!row.hasUUID("army")) {
                  continue;
               }

               MilitaryLoyalty loyalty = MilitaryLoyalty.tryParse(row.contains("loyalty") ? row.getString("loyalty") : null);
               if (loyalty != null) {
                  data.conflictManager.putArmyLoyaltyForLoad(new ArmyId(row.getUUID("army")), loyalty);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed army loyalty at index {}", i, e);
            }
         }
      }

      if (root.contains("foreign_recognition")) {
         ListTag list = root.getList("foreign_recognition", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               CompoundTag row = list.getCompound(i);
               ConflictId conflictId = row.hasUUID("conflict")
                  ? new ConflictId(row.getUUID("conflict"))
                  : ConflictId.tryParse(row.contains("conflict") ? row.getString("conflict") : null);
               if (conflictId == null) {
                  continue;
               }

               Set<FactionId> factions = new LinkedHashSet<>();
               if (row.contains("factions")) {
                  ListTag factionList = row.getList("factions", 8);
                  for (int j = 0; j < factionList.size(); j++) {
                     FactionId factionId = FactionId.tryParse(factionList.getString(j));
                     if (factionId != null) {
                        factions.add(factionId);
                     }
                  }
               }

               data.conflictManager.putForeignRecognitionForLoad(conflictId, factions);
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed foreign recognition at index {}", i, e);
            }
         }
      }

      if (root.contains("cooldowns")) {
         ListTag list = root.getList("cooldowns", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               CompoundTag row = list.getCompound(i);
               String key = row.contains("key") ? row.getString("key") : null;
               if (key != null && !key.isBlank()) {
                  data.conflictManager.putIdenticalCooldownForLoad(key, row.contains("ended") ? row.getLong("ended") : 0L);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed conflict cooldown at index {}", i, e);
            }
         }
      }

      return data;
   }

   @Nullable
   private static InternalConflict readConflict(CompoundTag tag) {
      ConflictId conflictId = tag.hasUUID("id")
         ? new ConflictId(tag.getUUID("id"))
         : ConflictId.tryParse(tag.contains("id") ? tag.getString("id") : null);
      FactionId origin = FactionId.tryParse(tag.contains("origin") ? tag.getString("origin") : null);
      if (conflictId == null || origin == null) {
         return null;
      }

      ConflictType type = ConflictType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      InternalConflict conflict = new InternalConflict(
         conflictId,
         origin,
         type,
         tag.contains("started") ? tag.getLong("started") : 0L
      );

      Map<ConflictSideId, ConflictSide> sides = new LinkedHashMap<>();
      if (tag.contains("sides")) {
         ListTag sideList = tag.getList("sides", 10);
         for (int i = 0; i < sideList.size(); i++) {
            ConflictSide side = readSide(sideList.getCompound(i), conflictId);
            if (side != null) {
               sides.put(side.getId(), side);
            }
         }
      }

      Set<UnrestId> relatedUnrest = new LinkedHashSet<>();
      if (tag.contains("related_unrest")) {
         ListTag list = tag.getList("related_unrest", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag u = list.getCompound(i);
            if (u.hasUUID("id")) {
               relatedUnrest.add(new UnrestId(u.getUUID("id")));
            } else {
               UnrestId parsed = UnrestId.tryParse(u.contains("id") ? u.getString("id") : null);
               if (parsed != null) {
                  relatedUnrest.add(parsed);
               }
            }
         }
      }

      Set<PostConflictModifier> modifiers = new LinkedHashSet<>();
      if (tag.contains("post_modifiers")) {
         ListTag list = tag.getList("post_modifiers", 8);
         for (int i = 0; i < list.size(); i++) {
            PostConflictModifier modifier = PostConflictModifier.tryParse(list.getString(i));
            if (modifier != null) {
               modifiers.add(modifier);
            }
         }
      }

      List<ConflictHistoryEntry> history = new ArrayList<>();
      if (tag.contains("history")) {
         ListTag list = tag.getList("history", 10);
         for (int i = 0; i < list.size(); i++) {
            ConflictHistoryEntry entry = readHistory(list.getCompound(i), conflictId);
            if (entry != null) {
               history.add(entry);
            }
         }
      }

      ConflictSideId governmentSideId = tag.hasUUID("government_side")
         ? new ConflictSideId(tag.getUUID("government_side"))
         : ConflictSideId.tryParse(tag.contains("government_side") ? tag.getString("government_side") : null);
      ConflictSideId primaryRebelSideId = tag.hasUUID("primary_rebel_side")
         ? new ConflictSideId(tag.getUUID("primary_rebel_side"))
         : ConflictSideId.tryParse(tag.contains("primary_rebel_side") ? tag.getString("primary_rebel_side") : null);
      WarId linkedWarId = tag.hasUUID("linked_war")
         ? new WarId(tag.getUUID("linked_war"))
         : WarId.tryParse(tag.contains("linked_war") ? tag.getString("linked_war") : null);

      conflict.loadState(
         type,
         ConflictStage.tryParse(tag.contains("stage") ? tag.getString("stage") : null),
         tag.contains("last_updated") ? tag.getLong("last_updated") : 0L,
         tag.contains("ended") ? tag.getLong("ended") : 0L,
         governmentSideId,
         primaryRebelSideId,
         FactionId.tryParse(tag.contains("rebel_faction") ? tag.getString("rebel_faction") : null),
         linkedWarId,
         ConflictResolution.tryParse(tag.contains("resolution") ? tag.getString("resolution") : null),
         tag.contains("cause") ? tag.getString("cause") : null,
         tag.contains("negotiating") && tag.getBoolean("negotiating"),
         !tag.contains("active") || tag.getBoolean("active"),
         relatedUnrest,
         modifiers,
         tag.contains("recovery_until") ? tag.getLong("recovery_until") : 0L,
         history,
         sides
      );
      return conflict;
   }

   @Nullable
   private static ConflictSide readSide(CompoundTag tag, ConflictId fallbackConflictId) {
      ConflictSideId sideId = tag.hasUUID("id")
         ? new ConflictSideId(tag.getUUID("id"))
         : ConflictSideId.tryParse(tag.contains("id") ? tag.getString("id") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (sideId == null || factionId == null) {
         return null;
      }

      ConflictId conflictId = tag.hasUUID("conflict")
         ? new ConflictId(tag.getUUID("conflict"))
         : ConflictId.tryParse(tag.contains("conflict") ? tag.getString("conflict") : null);
      if (conflictId == null) {
         conflictId = fallbackConflictId;
      }

      ConflictSide side = new ConflictSide(
         sideId,
         conflictId,
         factionId,
         tag.contains("government") && tag.getBoolean("government")
      );

      Set<PoliticalGroupId> supporters = new LinkedHashSet<>();
      if (tag.contains("supporters")) {
         ListTag list = tag.getList("supporters", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            if (g.hasUUID("id")) {
               supporters.add(new PoliticalGroupId(g.getUUID("id")));
            }
         }
      }

      Set<ArmyId> armies = new LinkedHashSet<>();
      if (tag.contains("armies")) {
         ListTag list = tag.getList("armies", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag a = list.getCompound(i);
            if (a.hasUUID("id")) {
               armies.add(new ArmyId(a.getUUID("id")));
            }
         }
      }

      Set<TerritoryId> territories = new LinkedHashSet<>();
      if (tag.contains("territories")) {
         ListTag list = tag.getList("territories", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("id")) {
               territories.add(new TerritoryId(t.getUUID("id")));
            }
         }
      }

      Set<VillageId> settlements = new LinkedHashSet<>();
      if (tag.contains("settlements")) {
         ListTag list = tag.getList("settlements", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            if (s.hasUUID("id")) {
               settlements.add(new VillageId(s.getUUID("id")));
            }
         }
      }

      side.loadState(
         tag.contains("government") && tag.getBoolean("government"),
         tag.hasUUID("leader") ? new LeaderId(tag.getUUID("leader")) : null,
         tag.contains("display_name") ? tag.getString("display_name") : null,
         tag.contains("conflict_score") ? tag.getInt("conflict_score") : 0,
         tag.contains("casualties") ? tag.getInt("casualties") : 0,
         tag.contains("exhaustion") ? tag.getInt("exhaustion") : 0,
         tag.contains("support") ? tag.getInt("support") : 0,
         tag.contains("legitimacy") ? tag.getInt("legitimacy") : 0,
         RebelObjective.tryParse(tag.contains("objective") ? tag.getString("objective") : null),
         supporters,
         armies,
         territories,
         settlements,
         !tag.contains("active") || tag.getBoolean("active")
      );
      return side;
   }

   @Nullable
   private static ConflictHistoryEntry readHistory(CompoundTag tag, ConflictId fallbackConflictId) {
      ConflictEventType eventType = ConflictEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (eventType == null || factionId == null) {
         return null;
      }

      ConflictId conflictId = tag.hasUUID("conflict")
         ? new ConflictId(tag.getUUID("conflict"))
         : ConflictId.tryParse(tag.contains("conflict") ? tag.getString("conflict") : null);
      if (conflictId == null) {
         conflictId = fallbackConflictId;
      }

      return new ConflictHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         conflictId,
         factionId,
         tag.hasUUID("side") ? new ConflictSideId(tag.getUUID("side")) : ConflictSideId.tryParse(tag.contains("side") ? tag.getString("side") : null),
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : WarId.tryParse(tag.contains("war") ? tag.getString("war") : null),
         tag.hasUUID("battle") ? new BattleId(tag.getUUID("battle")) : BattleId.tryParse(tag.contains("battle") ? tag.getString("battle") : null),
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.contains("quantity") ? tag.getInt("quantity") : 0,
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }

   @Nullable
   private static Unrest readUnrest(CompoundTag tag) {
      UnrestId unrestId = tag.hasUUID("id")
         ? new UnrestId(tag.getUUID("id"))
         : UnrestId.tryParse(tag.contains("id") ? tag.getString("id") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (unrestId == null || factionId == null) {
         return null;
      }

      Unrest unrest = new Unrest(
         unrestId,
         factionId,
         UnrestCause.tryParse(tag.contains("cause") ? tag.getString("cause") : null),
         UnrestScope.tryParse(tag.contains("scope") ? tag.getString("scope") : null),
         tag.contains("severity") ? tag.getInt("severity") : 0,
         tag.contains("started") ? tag.getLong("started") : 0L
      );
      unrest.loadState(
         tag.contains("severity") ? tag.getInt("severity") : 0,
         tag.contains("escalation") ? tag.getInt("escalation") : 0,
         tag.hasUUID("group") ? new PoliticalGroupId(tag.getUUID("group")) : null,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.hasUUID("territory") ? new TerritoryId(tag.getUUID("territory")) : null,
         tag.contains("last_updated") ? tag.getLong("last_updated") : 0L,
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("resolved") ? tag.getLong("resolved") : 0L,
         tag.contains("detail") ? tag.getString("detail") : null
      );
      return unrest;
   }

   @Nullable
   private static PostConflictRecovery readRecovery(CompoundTag tag) {
      ConflictId conflictId = tag.hasUUID("conflict")
         ? new ConflictId(tag.getUUID("conflict"))
         : ConflictId.tryParse(tag.contains("conflict") ? tag.getString("conflict") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (conflictId == null || factionId == null) {
         return null;
      }

      long started = tag.contains("started") ? tag.getLong("started") : 0L;
      long ends = tag.contains("ends") ? tag.getLong("ends") : started;
      PostConflictRecovery recovery = new PostConflictRecovery(conflictId, factionId, started, Math.max(0L, ends - started));

      Set<PostConflictModifier> modifiers = new LinkedHashSet<>();
      if (tag.contains("modifiers")) {
         ListTag list = tag.getList("modifiers", 8);
         for (int i = 0; i < list.size(); i++) {
            PostConflictModifier modifier = PostConflictModifier.tryParse(list.getString(i));
            if (modifier != null) {
               modifiers.add(modifier);
            }
         }
      }

      recovery.loadState(
         ends,
         tag.contains("stability_penalty") ? tag.getInt("stability_penalty") : 0,
         tag.contains("pressure_bonus") ? tag.getInt("pressure_bonus") : 0,
         modifiers,
         !tag.contains("active") || tag.getBoolean("active"),
         ConflictResolution.tryParse(tag.contains("resolved_as") ? tag.getString("resolved_as") : null)
      );
      return recovery;
   }

   @Nullable
   private static NegotiatedSettlement readSettlement(CompoundTag tag) {
      ConflictId conflictId = tag.hasUUID("conflict")
         ? new ConflictId(tag.getUUID("conflict"))
         : ConflictId.tryParse(tag.contains("conflict") ? tag.getString("conflict") : null);
      FactionId origin = FactionId.tryParse(tag.contains("origin") ? tag.getString("origin") : null);
      if (conflictId == null || origin == null) {
         return null;
      }

      NegotiatedSettlement settlement = new NegotiatedSettlement(
         conflictId,
         origin,
         ConflictResolution.tryParse(tag.contains("outcome") ? tag.getString("outcome") : null),
         tag.contains("settled") ? tag.getLong("settled") : 0L
      );

      List<String> terms = new ArrayList<>();
      if (tag.contains("terms")) {
         ListTag list = tag.getList("terms", 8);
         for (int i = 0; i < list.size(); i++) {
            terms.add(list.getString(i));
         }
      }

      settlement.loadState(
         terms,
         tag.contains("autonomy") ? tag.getInt("autonomy") : 0,
         tag.contains("leadership_changed") && tag.getBoolean("leadership_changed"),
         tag.contains("government_reformed") && tag.getBoolean("government_reformed"),
         tag.contains("territory_ceded") && tag.getBoolean("territory_ceded"),
         tag.contains("detail") ? tag.getString("detail") : null
      );
      return settlement;
   }
}
