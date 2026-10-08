package org.millenaire.faction.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for Phase 6 warfare state.
 */
public final class WarSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_war";

   private final WarManager warManager = new WarManager(this::setDirty);

   private WarSavedData() {
   }

   public static Factory<WarSavedData> factory() {
      return new Factory<>(WarSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static WarSavedData get(ServerLevel level) {
      return (WarSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public WarManager getWarManager() {
      return this.warManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_update", this.warManager.getLastUpdateGameTime());

      ListTag wars = new ListTag();
      for (War war : this.warManager.getAllWars()) {
         try {
            wars.add(saveWar(war));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize war {}", war.getId(), e);
         }
      }
      root.put("wars", wars);

      ListTag battles = new ListTag();
      for (Battle battle : this.warManager.getAllBattles()) {
         try {
            battles.add(saveBattle(battle));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize battle {}", battle.getId(), e);
         }
      }
      root.put("battles", battles);

      ListTag armyStates = new ListTag();
      for (ArmyWartimeState state : this.warManager.getAllArmyStates()) {
         try {
            armyStates.add(saveArmyState(state));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize army wartime state {}", state.getArmyId(), e);
         }
      }
      root.put("army_states", armyStates);

      LOGGER.debug("[Millenaire] Saved warfare data: {} war(s), {} battle(s), {} army state(s)", wars.size(), battles.size(), armyStates.size());
      return root;
   }

   private static CompoundTag saveWar(War war) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", war.getId().uuid());
      tag.putString("initiator", war.getInitiatorFactionId().asString());
      tag.putString("target", war.getPrimaryTargetFactionId().asString());
      tag.putString("state", war.getState().name());
      tag.putInt("war_score", war.getWarScore());
      tag.putLong("started", war.getStartedGameTime());
      tag.putLong("mobilization_ends", war.getMobilizationEndsGameTime());
      tag.putLong("ended", war.getEndedGameTime());
      if (war.getOutcome() != null) {
         tag.putString("outcome", war.getOutcome().name());
      }

      ListTag participants = new ListTag();
      for (WarParticipant participant : war.getParticipants().values()) {
         CompoundTag p = new CompoundTag();
         p.putString("faction", participant.getFactionId().asString());
         p.putString("side", participant.getSide().name());
         p.putString("join_reason", participant.getJoinReason().name());
         p.putLong("joined", participant.getJoinedGameTime());
         p.putBoolean("active", participant.isActive());
         p.putLong("left", participant.getLeftGameTime());
         p.putInt("casualties_suffered", participant.getCasualtiesSuffered());
         p.putInt("casualties_inflicted", participant.getCasualtiesInflicted());
         p.putInt("exhaustion", participant.getWarExhaustion());
         participants.add(p);
      }
      tag.put("participants", participants);

      ListTag goals = new ListTag();
      for (WarGoal goal : war.getGoals().values()) {
         CompoundTag g = new CompoundTag();
         g.putUUID("id", goal.getId().uuid());
         g.putString("type", goal.getType().name());
         g.putString("initiator", goal.getInitiatingFactionId().asString());
         if (goal.getTargetFactionId() != null) {
            g.putString("target_faction", goal.getTargetFactionId().asString());
         }
         if (goal.getTargetArmyId() != null) {
            g.putUUID("target_army", goal.getTargetArmyId().uuid());
         }
         if (goal.getTargetSettlementId() != null) {
            g.putUUID("target_settlement", goal.getTargetSettlementId().uuid());
         }
         g.putInt("progress", goal.getProgressPercent());
         g.putString("status", goal.getStatus().name());
         g.putLong("created", goal.getCreatedGameTime());
         g.putLong("completed", goal.getCompletedGameTime());
         goals.add(g);
      }
      tag.put("goals", goals);

      tag.put("battles", saveIdList(war.getBattleIds(), BattleId::uuid));
      tag.put("armies", saveIdList(war.getParticipatingArmyIds(), ArmyId::uuid));

      if (war.getCeasefire() != null) {
         CompoundTag c = new CompoundTag();
         c.putLong("start", war.getCeasefire().getStartGameTime());
         c.putLong("expiration", war.getCeasefire().getExpirationGameTime());
         c.putBoolean("active", war.getCeasefire().isActive());
         c.putLong("ended", war.getCeasefire().getEndedGameTime());
         tag.put("ceasefire", c);
      }

      if (war.getPeaceSettlement() != null) {
         PeaceSettlement settlement = war.getPeaceSettlement();
         CompoundTag p = new CompoundTag();
         p.putString("outcome", settlement.getOutcome().name());
         p.putInt("final_score", settlement.getFinalWarScore());
         p.putLong("settled", settlement.getSettledGameTime());
         if (settlement.getTermsNote() != null) {
            p.putString("terms", settlement.getTermsNote());
         }
         if (settlement.getReason() != null) {
            p.putString("reason", settlement.getReason());
         }
         tag.put("peace", p);
      }

      ListTag history = new ListTag();
      for (WarHistoryEntry entry : war.getHistory()) {
         history.add(saveHistoryEntry(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveBattle(Battle battle) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", battle.getId().uuid());
      tag.putUUID("war", battle.getWarId().uuid());
      tag.putString("dimension", battle.getDimensionId().toString());
      tag.putLong("pos", battle.getLocation().asLong());
      if (battle.getSettlementId() != null) {
         tag.putUUID("settlement", battle.getSettlementId().uuid());
      }
      tag.putString("state", battle.getState().name());
      tag.put("attacker_armies", saveIdList(battle.getAttackerArmyIds(), ArmyId::uuid));
      tag.put("defender_armies", saveIdList(battle.getDefenderArmyIds(), ArmyId::uuid));
      tag.put("attacker_factions", saveFactionList(battle.getAttackerFactionIds()));
      tag.put("defender_factions", saveFactionList(battle.getDefenderFactionIds()));
      tag.putInt("attacker_casualties", battle.getAttackerCasualties());
      tag.putInt("defender_casualties", battle.getDefenderCasualties());
      tag.putInt("attacker_morale", battle.getAttackerMorale());
      tag.putInt("defender_morale", battle.getDefenderMorale());
      tag.putInt("round", battle.getRoundIndex());
      tag.putString("terrain", battle.getTerrain().name());
      if (battle.getVictorSide() != null) {
         tag.putString("victor", battle.getVictorSide().name());
      }
      tag.putLong("started", battle.getStartedGameTime());
      tag.putLong("ended", battle.getEndedGameTime());
      tag.putLong("last_round", battle.getLastRoundGameTime());
      ListTag history = new ListTag();
      for (WarHistoryEntry entry : battle.getHistory()) {
         history.add(saveHistoryEntry(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveArmyState(ArmyWartimeState state) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("army", state.getArmyId().uuid());
      tag.putString("faction", state.getFactionId().asString());
      if (state.getWarId() != null) {
         tag.putUUID("war", state.getWarId().uuid());
      }
      if (state.getBattleId() != null) {
         tag.putUUID("battle", state.getBattleId().uuid());
      }
      tag.putString("dimension", state.getDimensionId().toString());
      tag.putLong("pos", state.getPosition().asLong());
      if (state.getDestination() != null) {
         tag.putLong("destination", state.getDestination().asLong());
      }
      tag.put("order", saveOrder(state.getOrder()));
      if (state.getObjective() != null) {
         tag.put("objective", saveObjective(state.getObjective()));
      }
      tag.putInt("morale", state.getMorale());
      tag.putInt("supply", state.getSupplyPercent());
      tag.putLong("last_moved", state.getLastMovedGameTime());
      tag.putLong("last_ai", state.getLastAiEvaluationGameTime());
      tag.putLong("last_reinforce", state.getLastReinforcementGameTime());
      tag.putLong("retreat_immunity", state.getRetreatImmunityUntil());
      tag.putBoolean("destroyed", state.isDestroyed());
      return tag;
   }

   private static CompoundTag saveOrder(ArmyOrder order) {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", order.getType().name());
      tag.putLong("issued", order.getIssuedGameTime());
      if (order.getTargetPos() != null) {
         tag.putLong("pos", order.getTargetPos().asLong());
      }
      if (order.getTargetArmyId() != null) {
         tag.putUUID("army", order.getTargetArmyId().uuid());
      }
      if (order.getTargetSettlementId() != null) {
         tag.putUUID("settlement", order.getTargetSettlementId().uuid());
      }
      if (order.getTargetBattleId() != null) {
         tag.putUUID("battle", order.getTargetBattleId().uuid());
      }
      if (order.getNote() != null) {
         tag.putString("note", order.getNote());
      }
      return tag;
   }

   private static CompoundTag saveObjective(MilitaryObjective objective) {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", objective.getType().name());
      tag.putInt("priority", objective.getPriority());
      tag.putLong("assigned", objective.getAssignedGameTime());
      if (objective.getTargetPos() != null) {
         tag.putLong("pos", objective.getTargetPos().asLong());
      }
      if (objective.getTargetArmyId() != null) {
         tag.putUUID("army", objective.getTargetArmyId().uuid());
      }
      if (objective.getTargetSettlementId() != null) {
         tag.putUUID("settlement", objective.getTargetSettlementId().uuid());
      }
      if (objective.getTargetBattleId() != null) {
         tag.putUUID("battle", objective.getTargetBattleId().uuid());
      }
      return tag;
   }

   private static CompoundTag saveHistoryEntry(WarHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      if (entry.getFactionA() != null) {
         tag.putString("faction_a", entry.getFactionA().asString());
      }
      if (entry.getFactionB() != null) {
         tag.putString("faction_b", entry.getFactionB().asString());
      }
      if (entry.getArmyId() != null) {
         tag.putUUID("army", entry.getArmyId().uuid());
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

   private static <T> ListTag saveIdList(Set<T> ids, java.util.function.Function<T, UUID> uuidFn) {
      ListTag list = new ListTag();
      for (T id : ids) {
         CompoundTag tag = new CompoundTag();
         tag.putUUID("id", uuidFn.apply(id));
         list.add(tag);
      }
      return list;
   }

   private static ListTag saveFactionList(Set<FactionId> ids) {
      ListTag list = new ListTag();
      for (FactionId id : ids) {
         CompoundTag tag = new CompoundTag();
         tag.putString("id", id.asString());
         list.add(tag);
      }
      return list;
   }

   private static WarSavedData load(CompoundTag root) {
      WarSavedData data = new WarSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] WarSavedData version {} newer than supported {} — best-effort load", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading WarSavedData version {}", version);
      }

      if (root.contains("last_update")) {
         data.warManager.setLastUpdateGameTime(root.getLong("last_update"));
      }

      if (root.contains("wars")) {
         ListTag wars = root.getList("wars", 10);
         for (int i = 0; i < wars.size(); i++) {
            try {
               War war = readWar(wars.getCompound(i));
               if (war != null) {
                  data.warManager.loadWar(war);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed war at index {}", i, e);
            }
         }
      }

      if (root.contains("battles")) {
         ListTag battles = root.getList("battles", 10);
         for (int i = 0; i < battles.size(); i++) {
            try {
               Battle battle = readBattle(battles.getCompound(i));
               if (battle != null) {
                  data.warManager.loadBattle(battle);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed battle at index {}", i, e);
            }
         }
      }

      if (root.contains("army_states")) {
         ListTag states = root.getList("army_states", 10);
         for (int i = 0; i < states.size(); i++) {
            try {
               ArmyWartimeState state = readArmyState(states.getCompound(i));
               if (state != null) {
                  data.warManager.loadArmyState(state);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed army wartime state at index {}", i, e);
            }
         }
      }

      return data;
   }

   @Nullable
   private static War readWar(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId initiator = FactionId.tryParse(tag.contains("initiator") ? tag.getString("initiator") : null);
      FactionId target = FactionId.tryParse(tag.contains("target") ? tag.getString("target") : null);
      if (initiator == null || target == null || initiator.equals(target)) {
         LOGGER.error("[Millenaire] Invalid war identity — skipped");
         return null;
      }

      War war = new War(new WarId(tag.getUUID("id")), initiator, target, tag.contains("started") ? tag.getLong("started") : 0L);
      Ceasefire ceasefire = null;
      if (tag.contains("ceasefire")) {
         CompoundTag c = tag.getCompound("ceasefire");
         ceasefire = new Ceasefire(c.getLong("start"), c.getLong("expiration"));
         ceasefire.loadState(c.getBoolean("active"), c.getLong("ended"));
      }

      PeaceSettlement settlement = null;
      if (tag.contains("peace")) {
         CompoundTag p = tag.getCompound("peace");
         settlement = new PeaceSettlement(
            war.getId(),
            PeaceOutcome.tryParse(p.contains("outcome") ? p.getString("outcome") : null),
            p.contains("final_score") ? p.getInt("final_score") : 0,
            p.contains("settled") ? p.getLong("settled") : 0L,
            p.contains("terms") ? p.getString("terms") : null,
            p.contains("reason") ? p.getString("reason") : null
         );
      }

      Set<BattleId> battleIds = readUuidSet(tag, "battles", BattleId::new);
      Set<ArmyId> armyIds = readUuidSet(tag, "armies", ArmyId::new);
      war.loadState(
         WarState.tryParse(tag.contains("state") ? tag.getString("state") : null),
         tag.contains("war_score") ? tag.getInt("war_score") : 0,
         tag.contains("mobilization_ends") ? tag.getLong("mobilization_ends") : 0L,
         tag.contains("ended") ? tag.getLong("ended") : 0L,
         ceasefire,
         settlement,
         PeaceOutcome.tryParse(tag.contains("outcome") ? tag.getString("outcome") : null),
         battleIds,
         armyIds,
         readHistoryList(tag)
      );

      // Replace constructor participants with persisted ones.
      if (tag.contains("participants")) {
         ListTag participants = tag.getList("participants", 10);
         for (int i = 0; i < participants.size(); i++) {
            CompoundTag p = participants.getCompound(i);
            FactionId factionId = FactionId.tryParse(p.contains("faction") ? p.getString("faction") : null);
            WarSideId side = WarSideId.tryParse(p.contains("side") ? p.getString("side") : null);
            if (factionId == null || side == null) {
               continue;
            }

            WarParticipant participant = new WarParticipant(
               factionId,
               side,
               JoinWarReason.tryParse(p.contains("join_reason") ? p.getString("join_reason") : null),
               p.contains("joined") ? p.getLong("joined") : 0L
            );
            participant.loadState(
               !p.contains("active") || p.getBoolean("active"),
               p.contains("left") ? p.getLong("left") : 0L,
               p.contains("casualties_suffered") ? p.getInt("casualties_suffered") : 0,
               p.contains("casualties_inflicted") ? p.getInt("casualties_inflicted") : 0,
               p.contains("exhaustion") ? p.getInt("exhaustion") : 0
            );
            war.putLoadedParticipant(participant);
         }
      }

      if (tag.contains("goals")) {
         ListTag goals = tag.getList("goals", 10);
         for (int i = 0; i < goals.size(); i++) {
            CompoundTag g = goals.getCompound(i);
            if (!g.hasUUID("id")) {
               continue;
            }

            WarGoalType type = WarGoalType.tryParse(g.contains("type") ? g.getString("type") : null);
            FactionId goalInitiator = FactionId.tryParse(g.contains("initiator") ? g.getString("initiator") : null);
            if (type == null || goalInitiator == null) {
               continue;
            }

            WarGoal goal = new WarGoal(
               new WarGoalId(g.getUUID("id")),
               war.getId(),
               type,
               goalInitiator,
               g.contains("created") ? g.getLong("created") : 0L,
               FactionId.tryParse(g.contains("target_faction") ? g.getString("target_faction") : null),
               g.hasUUID("target_army") ? new ArmyId(g.getUUID("target_army")) : null,
               g.hasUUID("target_settlement") ? new VillageId(g.getUUID("target_settlement")) : null
            );
            goal.loadState(
               g.contains("progress") ? g.getInt("progress") : 0,
               WarGoalStatus.tryParse(g.contains("status") ? g.getString("status") : null),
               g.contains("completed") ? g.getLong("completed") : 0L
            );
            war.putLoadedGoal(goal);
         }
      }

      return war;
   }

   @Nullable
   private static Battle readBattle(CompoundTag tag) {
      if (!tag.hasUUID("id") || !tag.hasUUID("war")) {
         return null;
      }

      ResourceLocation dimension;
      try {
         dimension = ResourceLocation.parse(tag.contains("dimension") ? tag.getString("dimension") : "minecraft:overworld");
      } catch (RuntimeException e) {
         dimension = ResourceLocation.withDefaultNamespace("overworld");
      }

      Battle battle = new Battle(
         new BattleId(tag.getUUID("id")),
         new WarId(tag.getUUID("war")),
         dimension,
         BlockPos.of(tag.contains("pos") ? tag.getLong("pos") : 0L),
         tag.contains("started") ? tag.getLong("started") : 0L,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null
      );
      battle.loadState(
         BattleState.tryParse(tag.contains("state") ? tag.getString("state") : null),
         readUuidSet(tag, "attacker_armies", ArmyId::new),
         readUuidSet(tag, "defender_armies", ArmyId::new),
         readFactionSet(tag, "attacker_factions"),
         readFactionSet(tag, "defender_factions"),
         tag.contains("attacker_casualties") ? tag.getInt("attacker_casualties") : 0,
         tag.contains("defender_casualties") ? tag.getInt("defender_casualties") : 0,
         tag.contains("attacker_morale") ? tag.getInt("attacker_morale") : WarConfig.MORALE_BASE,
         tag.contains("defender_morale") ? tag.getInt("defender_morale") : WarConfig.MORALE_BASE,
         tag.contains("round") ? tag.getInt("round") : 0,
         TerrainType.tryParse(tag.contains("terrain") ? tag.getString("terrain") : null),
         WarSideId.tryParse(tag.contains("victor") ? tag.getString("victor") : null),
         tag.contains("ended") ? tag.getLong("ended") : 0L,
         tag.contains("last_round") ? tag.getLong("last_round") : 0L,
         readHistoryList(tag)
      );
      return battle;
   }

   @Nullable
   private static ArmyWartimeState readArmyState(CompoundTag tag) {
      if (!tag.hasUUID("army")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      ResourceLocation dimension;
      try {
         dimension = ResourceLocation.parse(tag.contains("dimension") ? tag.getString("dimension") : "minecraft:overworld");
      } catch (RuntimeException e) {
         dimension = ResourceLocation.withDefaultNamespace("overworld");
      }

      ArmyWartimeState state = new ArmyWartimeState(
         new ArmyId(tag.getUUID("army")),
         factionId,
         dimension,
         BlockPos.of(tag.contains("pos") ? tag.getLong("pos") : 0L),
         tag.contains("last_moved") ? tag.getLong("last_moved") : 0L
      );
      state.loadState(
         tag.hasUUID("war") ? new WarId(tag.getUUID("war")) : null,
         tag.hasUUID("battle") ? new BattleId(tag.getUUID("battle")) : null,
         dimension,
         BlockPos.of(tag.contains("pos") ? tag.getLong("pos") : 0L),
         tag.contains("destination") ? BlockPos.of(tag.getLong("destination")) : null,
         tag.contains("order") ? readOrder(tag.getCompound("order")) : ArmyOrder.none(0L),
         tag.contains("objective") ? readObjective(tag.getCompound("objective")) : null,
         tag.contains("morale") ? tag.getInt("morale") : WarConfig.MORALE_BASE,
         tag.contains("supply") ? tag.getInt("supply") : WarConfig.SUPPLY_BASE,
         tag.contains("last_moved") ? tag.getLong("last_moved") : 0L,
         tag.contains("last_ai") ? tag.getLong("last_ai") : 0L,
         tag.contains("last_reinforce") ? tag.getLong("last_reinforce") : 0L,
         tag.contains("retreat_immunity") ? tag.getLong("retreat_immunity") : 0L,
         tag.contains("destroyed") && tag.getBoolean("destroyed")
      );
      return state;
   }

   private static ArmyOrder readOrder(CompoundTag tag) {
      return new ArmyOrder(
         ArmyOrderType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.contains("issued") ? tag.getLong("issued") : 0L,
         tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null,
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.hasUUID("battle") ? new BattleId(tag.getUUID("battle")) : null,
         tag.contains("note") ? tag.getString("note") : null
      );
   }

   @Nullable
   private static MilitaryObjective readObjective(CompoundTag tag) {
      MilitaryObjectiveType type = MilitaryObjectiveType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      if (type == null) {
         return null;
      }

      return new MilitaryObjective(
         type,
         tag.contains("priority") ? tag.getInt("priority") : type.basePriority(),
         tag.contains("assigned") ? tag.getLong("assigned") : 0L,
         tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null,
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.hasUUID("battle") ? new BattleId(tag.getUUID("battle")) : null
      );
   }

   private static List<WarHistoryEntry> readHistoryList(CompoundTag tag) {
      List<WarHistoryEntry> history = new ArrayList<>();
      if (!tag.contains("history")) {
         return history;
      }

      ListTag list = tag.getList("history", 10);
      for (int i = 0; i < list.size(); i++) {
         WarHistoryEntry entry = readHistoryEntry(list.getCompound(i));
         if (entry != null) {
            history.add(entry);
         }
      }

      return history;
   }

   @Nullable
   private static WarHistoryEntry readHistoryEntry(CompoundTag tag) {
      WarEventType eventType = WarEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      if (eventType == null) {
         return null;
      }

      return new WarHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         FactionId.tryParse(tag.contains("faction_a") ? tag.getString("faction_a") : null),
         FactionId.tryParse(tag.contains("faction_b") ? tag.getString("faction_b") : null),
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.hasUUID("battle") ? new BattleId(tag.getUUID("battle")) : null,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.contains("quantity") ? tag.getInt("quantity") : 0,
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }

   private static <T> Set<T> readUuidSet(CompoundTag tag, String key, java.util.function.Function<UUID, T> factory) {
      Set<T> result = new LinkedHashSet<>();
      if (!tag.contains(key)) {
         return result;
      }

      ListTag list = tag.getList(key, 10);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         if (entry.hasUUID("id")) {
            result.add(factory.apply(entry.getUUID("id")));
         }
      }

      return result;
   }

   private static Set<FactionId> readFactionSet(CompoundTag tag, String key) {
      Set<FactionId> result = new LinkedHashSet<>();
      if (!tag.contains(key)) {
         return result;
      }

      ListTag list = tag.getList(key, 10);
      for (int i = 0; i < list.size(); i++) {
         FactionId id = FactionId.tryParse(list.getCompound(i).contains("id") ? list.getCompound(i).getString("id") : null);
         if (id != null) {
            result.add(id);
         }
      }

      return result;
   }
}
