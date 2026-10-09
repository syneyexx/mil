package org.millenaire.civilization;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.civilization.population.MigrationOperation;
import org.slf4j.Logger;

/**
 * Orchestration progress, migration operation log, and published-event dedupe keys.
 * Does not own faction/village authoritative records.
 */
public class CivilizationSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_civilization";

   private final EnumMap<CivilizationStage, Long> lastStageGameTime = new EnumMap<>(CivilizationStage.class);
   private final Map<String, Long> publishedEventKeys = new HashMap<>();
   private final Map<String, String> stateMarkers = new HashMap<>();
   private final Map<UUID, MigrationOperation> migrationOps = new HashMap<>();
   private final ArrayDeque<UUID> migrationOrder = new ArrayDeque<>();
   private final Map<UUID, String> settlementDevelopmentLabels = new HashMap<>();
   /** Keyed by {@link org.millenaire.faction.FactionId#asString()}, not UUID. */
   private final Map<String, CompoundTag> populationSnapshots = new HashMap<>();
   private final Set<String> pendingHistoryKeys = new HashSet<>();
   private final Map<String, Long> factionOffscreenGameTime = new HashMap<>();
   private final Map<String, Long> factionEconomyGameTime = new HashMap<>();
   private int membershipCursor;
   private int populationCursor;
   private int developmentCursor;
   private int offscreenCursor;
   private int tradeCursor;
   private int migrationCursor;
   private int colonizationCursor;
   private long lastOffscreenAttemptGameTime;
   private long lastOffscreenSuccessGameTime;
   private int eventsBudgetRemaining;
   private int schemaVersion = CivilizationConfig.SCHEMA_VERSION;

   private CivilizationSavedData() {
   }

   public static Factory<CivilizationSavedData> factory() {
      return new Factory<>(CivilizationSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static CivilizationSavedData get(ServerLevel level) {
      net.minecraft.server.MinecraftServer server = level.getServer();
      ServerLevel overworld = server != null ? server.getLevel(net.minecraft.world.level.Level.OVERWORLD) : null;
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   /**
    * Returns true when a previously observed marker value changed.
    * First observation after load seeds the marker without emitting (avoids chronicle spam on reload).
    * Callers that need "just created" detection should use {@link #updateStateMarkerAllowFirst}.
    */
   public boolean updateStateMarker(String key, String value) {
      return updateStateMarker(key, value, false);
   }

   /**
    * @param emitOnFirst when true, a first-seen key also returns true (for newly created entities
    *                    whose creation time is already known to be recent)
    */
   public boolean updateStateMarker(String key, String value, boolean emitOnFirst) {
      if (key == null || key.isBlank()) {
         return false;
      }
      String normalized = value == null ? "" : value;
      String prev = this.stateMarkers.get(key);
      if (Objects.equals(prev, normalized)) {
         return false;
      }
      this.stateMarkers.put(key, normalized);
      this.setDirty();
      if (prev == null && !emitOnFirst) {
         return false;
      }
      return true;
   }

   /** Convenience alias for first-seen emission. */
   public boolean updateStateMarkerAllowFirst(String key, String value) {
      return updateStateMarker(key, value, true);
   }

   public void resetTickBudgets(int eventsBudget) {
      this.eventsBudgetRemaining = Math.max(0, eventsBudget);
   }

   public boolean consumeEventBudget() {
      if (this.eventsBudgetRemaining <= 0) {
         return false;
      }
      this.eventsBudgetRemaining--;
      return true;
   }

   public long lastStageGameTime(CivilizationStage stage) {
      return this.lastStageGameTime.getOrDefault(stage, 0L);
   }

   public void setLastStageGameTime(CivilizationStage stage, long gameTime) {
      this.lastStageGameTime.put(stage, gameTime);
   }

   public int membershipCursor() {
      return this.membershipCursor;
   }

   public void setMembershipCursor(int cursor) {
      this.membershipCursor = Math.max(0, cursor);
   }

   public int populationCursor() {
      return this.populationCursor;
   }

   public void setPopulationCursor(int cursor) {
      this.populationCursor = Math.max(0, cursor);
   }

   public int developmentCursor() {
      return this.developmentCursor;
   }

   public void setDevelopmentCursor(int cursor) {
      this.developmentCursor = Math.max(0, cursor);
   }

   public int offscreenCursor() {
      return this.offscreenCursor;
   }

   public void setOffscreenCursor(int cursor) {
      this.offscreenCursor = Math.max(0, cursor);
   }

   public int tradeCursor() {
      return this.tradeCursor;
   }

   public void setTradeCursor(int cursor) {
      this.tradeCursor = Math.max(0, cursor);
   }

   public int migrationCursor() {
      return this.migrationCursor;
   }

   public void setMigrationCursor(int cursor) {
      this.migrationCursor = Math.max(0, cursor);
   }

   public int colonizationCursor() {
      return this.colonizationCursor;
   }

   public void setColonizationCursor(int cursor) {
      this.colonizationCursor = Math.max(0, cursor);
   }

   public long lastOffscreenAttemptGameTime() {
      return this.lastOffscreenAttemptGameTime;
   }

   public void setLastOffscreenAttemptGameTime(long gameTime) {
      this.lastOffscreenAttemptGameTime = Math.max(0L, gameTime);
   }

   public long lastOffscreenSuccessGameTime() {
      return this.lastOffscreenSuccessGameTime;
   }

   public void setLastOffscreenSuccessGameTime(long gameTime) {
      this.lastOffscreenSuccessGameTime = Math.max(0L, gameTime);
   }

   public long lastFactionOffscreenGameTime(String factionId) {
      return factionId == null ? 0L : this.factionOffscreenGameTime.getOrDefault(factionId, 0L);
   }

   public void setLastFactionOffscreenGameTime(String factionId, long gameTime) {
      if (factionId != null && !factionId.isBlank()) {
         this.factionOffscreenGameTime.put(factionId, gameTime);
         this.setDirty();
      }
   }

   public long lastFactionEconomyGameTime(String factionId) {
      return factionId == null ? 0L : this.factionEconomyGameTime.getOrDefault(factionId, 0L);
   }

   public void setLastFactionEconomyGameTime(String factionId, long gameTime) {
      if (factionId != null && !factionId.isBlank()) {
         this.factionEconomyGameTime.put(factionId, gameTime);
         this.setDirty();
      }
   }

   public boolean tryMarkPublished(String dedupeKey, long gameTime, long ttlTicks) {
      if (dedupeKey == null || dedupeKey.isBlank()) {
         return true;
      }
      Long prev = this.publishedEventKeys.get(dedupeKey);
      if (prev != null && gameTime - prev < ttlTicks) {
         return false;
      }
      this.publishedEventKeys.put(dedupeKey, gameTime);
      if (this.publishedEventKeys.size() > 4096) {
         long cutoff = gameTime - ttlTicks * 4L;
         this.publishedEventKeys.entrySet().removeIf(e -> e.getValue() < cutoff);
      }
      this.setDirty();
      return true;
   }

   /** True when a success key is still within its TTL (without writing). */
   public boolean isPublished(String dedupeKey, long gameTime, long ttlTicks) {
      if (dedupeKey == null || dedupeKey.isBlank()) {
         return false;
      }
      Long prev = this.publishedEventKeys.get(dedupeKey);
      return prev != null && gameTime - prev < ttlTicks;
   }

   public void queueHistoryKey(String key) {
      if (key != null && !key.isBlank()) {
         this.pendingHistoryKeys.add(key);
         this.setDirty();
      }
   }

   public Set<String> drainHistoryKeys() {
      Set<String> out = new HashSet<>(this.pendingHistoryKeys);
      this.pendingHistoryKeys.clear();
      this.setDirty();
      return out;
   }

   public void putMigration(MigrationOperation op) {
      if (op == null) {
         return;
      }
      UUID id = op.operationId();
      boolean alreadyQueued = this.migrationOps.containsKey(id);
      this.migrationOps.put(id, op);
      if (!alreadyQueued) {
         this.migrationOrder.addLast(id);
      }
      // Retention must not drop active or recovery-needed operations.
      while (this.migrationOrder.size() > CivilizationConfig.MIGRATION_OPERATION_RETENTION) {
         UUID old = this.migrationOrder.peekFirst();
         if (old == null) {
            break;
         }
         MigrationOperation existing = this.migrationOps.get(old);
         if (existing != null && existing.isActive()) {
            // Rotate active op to the end instead of deleting it.
            this.migrationOrder.pollFirst();
            this.migrationOrder.addLast(old);
            break;
         }
         this.migrationOrder.pollFirst();
         if (old != null) {
            this.migrationOps.remove(old);
         }
      }
      this.setDirty();
   }

   @Nullable
   public MigrationOperation getMigration(UUID operationId) {
      return operationId == null ? null : this.migrationOps.get(operationId);
   }

   /** Read-only view for recovery consumers. */
   public java.util.Collection<MigrationOperation> migrationOpsView() {
      return java.util.Collections.unmodifiableCollection(this.migrationOps.values());
   }

   public void putDevelopmentLabel(UUID villageId, String label) {
      if (villageId != null && label != null) {
         this.settlementDevelopmentLabels.put(villageId, label);
         this.setDirty();
      }
   }

   @Nullable
   public String getDevelopmentLabel(UUID villageId) {
      return villageId == null ? null : this.settlementDevelopmentLabels.get(villageId);
   }

   public void putPopulationSnapshot(String factionId, CompoundTag tag) {
      if (factionId != null && !factionId.isBlank() && tag != null) {
         this.populationSnapshots.put(factionId, tag);
         this.setDirty();
      }
   }

   @Nullable
   public CompoundTag getPopulationSnapshot(String factionId) {
      return factionId == null || factionId.isBlank() ? null : this.populationSnapshots.get(factionId);
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("schema", this.schemaVersion);
      root.putInt("membershipCursor", this.membershipCursor);
      root.putInt("populationCursor", this.populationCursor);
      root.putInt("developmentCursor", this.developmentCursor);
      root.putInt("offscreenCursor", this.offscreenCursor);
      root.putInt("tradeCursor", this.tradeCursor);
      root.putInt("migrationCursor", this.migrationCursor);
      root.putInt("colonizationCursor", this.colonizationCursor);
      root.putLong("offscreenAttempt", this.lastOffscreenAttemptGameTime);
      root.putLong("offscreenSuccess", this.lastOffscreenSuccessGameTime);
      CompoundTag stages = new CompoundTag();
      for (Map.Entry<CivilizationStage, Long> e : this.lastStageGameTime.entrySet()) {
         stages.putLong(e.getKey().name(), e.getValue());
      }
      root.put("stages", stages);
      CompoundTag offFaction = new CompoundTag();
      for (Map.Entry<String, Long> e : this.factionOffscreenGameTime.entrySet()) {
         offFaction.putLong(e.getKey(), e.getValue());
      }
      root.put("factionOffscreen", offFaction);
      CompoundTag econFaction = new CompoundTag();
      for (Map.Entry<String, Long> e : this.factionEconomyGameTime.entrySet()) {
         econFaction.putLong(e.getKey(), e.getValue());
      }
      root.put("factionEconomy", econFaction);

      CompoundTag published = new CompoundTag();
      for (Map.Entry<String, Long> e : this.publishedEventKeys.entrySet()) {
         published.putLong(e.getKey(), e.getValue());
      }
      root.put("published", published);

      CompoundTag markers = new CompoundTag();
      for (Map.Entry<String, String> e : this.stateMarkers.entrySet()) {
         markers.putString(e.getKey(), e.getValue());
      }
      root.put("markers", markers);

      ListTag migrations = new ListTag();
      for (UUID id : this.migrationOrder) {
         MigrationOperation op = this.migrationOps.get(id);
         if (op != null) {
            migrations.add(op.save());
         }
      }
      root.put("migrations", migrations);

      CompoundTag labels = new CompoundTag();
      for (Map.Entry<UUID, String> e : this.settlementDevelopmentLabels.entrySet()) {
         labels.putString(e.getKey().toString(), e.getValue());
      }
      root.put("devLabels", labels);

      CompoundTag pops = new CompoundTag();
      for (Map.Entry<String, CompoundTag> e : this.populationSnapshots.entrySet()) {
         pops.put(e.getKey(), e.getValue());
      }
      root.put("populations", pops);

      ListTag pending = new ListTag();
      for (String key : this.pendingHistoryKeys) {
         pending.add(StringTag.valueOf(key));
      }
      root.put("pendingHistory", pending);
      return root;
   }

   private static CivilizationSavedData load(CompoundTag tag) {
      CivilizationSavedData data = new CivilizationSavedData();
      data.schemaVersion = tag.contains("schema") ? tag.getInt("schema") : CivilizationConfig.SCHEMA_VERSION;
      data.membershipCursor = tag.getInt("membershipCursor");
      data.populationCursor = tag.getInt("populationCursor");
      data.developmentCursor = tag.getInt("developmentCursor");
      data.offscreenCursor = tag.getInt("offscreenCursor");
      data.tradeCursor = tag.getInt("tradeCursor");
      data.migrationCursor = tag.getInt("migrationCursor");
      data.colonizationCursor = tag.getInt("colonizationCursor");
      data.lastOffscreenAttemptGameTime = tag.getLong("offscreenAttempt");
      data.lastOffscreenSuccessGameTime = tag.getLong("offscreenSuccess");
      CompoundTag stages = tag.getCompound("stages");
      for (String key : stages.getAllKeys()) {
         try {
            data.lastStageGameTime.put(CivilizationStage.valueOf(key), stages.getLong(key));
         } catch (IllegalArgumentException ignored) {
         }
      }
      CompoundTag offFaction = tag.getCompound("factionOffscreen");
      for (String key : offFaction.getAllKeys()) {
         data.factionOffscreenGameTime.put(key, offFaction.getLong(key));
      }
      CompoundTag econFaction = tag.getCompound("factionEconomy");
      for (String key : econFaction.getAllKeys()) {
         data.factionEconomyGameTime.put(key, econFaction.getLong(key));
      }
      // Schema bump for multi-cursor + per-faction offscreen clocks.
      if (data.schemaVersion < 2) {
         data.schemaVersion = 2;
      }
      CompoundTag published = tag.getCompound("published");
      for (String key : published.getAllKeys()) {
         data.publishedEventKeys.put(key, published.getLong(key));
      }
      CompoundTag markers = tag.getCompound("markers");
      for (String key : markers.getAllKeys()) {
         data.stateMarkers.put(key, markers.getString(key));
      }
      ListTag migrations = tag.getList("migrations", Tag.TAG_COMPOUND);
      for (int i = 0; i < migrations.size(); i++) {
         MigrationOperation op = MigrationOperation.load(migrations.getCompound(i));
         if (op != null) {
            data.migrationOps.put(op.operationId(), op);
            data.migrationOrder.addLast(op.operationId());
         }
      }
      CompoundTag labels = tag.getCompound("devLabels");
      for (String key : labels.getAllKeys()) {
         try {
            data.settlementDevelopmentLabels.put(UUID.fromString(key), labels.getString(key));
         } catch (IllegalArgumentException e) {
            LOGGER.warn("[Millenaire] Skipping corrupt development label key {}", key);
         }
      }
      CompoundTag pops = tag.getCompound("populations");
      for (String key : pops.getAllKeys()) {
         if (key == null || key.isBlank()) {
            LOGGER.warn("[Millenaire] Skipping blank population snapshot key");
            continue;
         }
         data.populationSnapshots.put(key, pops.getCompound(key));
      }
      ListTag pending = tag.getList("pendingHistory", Tag.TAG_STRING);
      for (int i = 0; i < pending.size(); i++) {
         data.pendingHistoryKeys.add(pending.getString(i));
      }
      return data;
   }
}
