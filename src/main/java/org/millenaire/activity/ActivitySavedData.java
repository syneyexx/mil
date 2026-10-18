package org.millenaire.activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/**
 * Per-dimension persistent ledger for regional activity. Runtime leases are rebuilt on load.
 */
public final class ActivitySavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_regional_activity";

   private final Map<UUID, ActivityOperation> operations = new HashMap<>();
   private final Map<UUID, ActivityGroup> groups = new HashMap<>();
   private final PriorityQueue<UUID> dueQueue = new PriorityQueue<>(Comparator.comparingLong(id -> {
      ActivityOperation op = this.operations.get(id);
      return op == null ? Long.MAX_VALUE : op.nextDueGameTime();
   }));
   private final Map<String, Long> sourceCooldowns = new HashMap<>();
   private final Map<String, Long> targetCooldowns = new HashMap<>();
   private final Map<String, Long> areaCooldowns = new HashMap<>();
   private final Map<UUID, Long> settlementGraceUntil = new HashMap<>();
   private final Map<String, Integer> rivalryScores = new HashMap<>();
   private final List<ActivityOutcomeSummary> recentOutcomes = new ArrayList<>();
   private final Map<String, Long> pendingProposals = new HashMap<>();
   private boolean featureGraceAssigned;
   private long warmupUntilGameTime;
   private int fairnessCursor;

   public static ActivitySavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static Factory<ActivitySavedData> factory() {
      return new Factory<>(ActivitySavedData::new, ActivitySavedData::load, null);
   }

   public List<ActivityOperation> nonTerminalOperations() {
      List<ActivityOperation> out = new ArrayList<>();
      for (ActivityOperation op : this.operations.values()) {
         if (!op.state().isTerminal()) {
            out.add(op);
         }
      }
      return out;
   }

   @Nullable
   public ActivityOperation getOperation(UUID id) {
      return this.operations.get(id);
   }

   @Nullable
   public ActivityGroup getGroup(UUID id) {
      return this.groups.get(id);
   }

   public void putOperation(ActivityOperation op) {
      this.operations.put(op.operationId(), op);
      this.dueQueue.remove(op.operationId());
      if (!op.state().isTerminal()) {
         this.dueQueue.offer(op.operationId());
      }
      this.setDirty();
   }

   public void putGroup(ActivityGroup group) {
      this.groups.put(group.groupId(), group);
      this.setDirty();
   }

   public void removeGroup(UUID id) {
      this.groups.remove(id);
      this.setDirty();
   }

   public void reschedule(ActivityOperation op) {
      this.dueQueue.remove(op.operationId());
      if (!op.state().isTerminal()) {
         this.dueQueue.offer(op.operationId());
      }
      this.setDirty();
   }

   public List<ActivityOperation> pollDue(long gameTime, int max) {
      List<ActivityOperation> out = new ArrayList<>();
      while (out.size() < max && !this.dueQueue.isEmpty()) {
         UUID id = this.dueQueue.peek();
         ActivityOperation op = this.operations.get(id);
         if (op == null || op.state().isTerminal()) {
            this.dueQueue.poll();
            continue;
         }
         if (op.nextDueGameTime() > gameTime) {
            break;
         }
         this.dueQueue.poll();
         out.add(op);
      }
      return out;
   }

   public void compactHistory(int maxOps, int maxOutcomes) {
      List<ActivityOperation> terminals = new ArrayList<>();
      for (ActivityOperation op : this.operations.values()) {
         if (op.state().isTerminal()) {
            terminals.add(op);
         }
      }
      int nonTerminal = this.operations.size() - terminals.size();
      if (nonTerminal > maxOps) {
         // Prefer cancelling oldest PROPOSED
         List<ActivityOperation> proposed = this.operations.values().stream()
            .filter(o -> o.state() == ActivityState.PROPOSED)
            .sorted(Comparator.comparingLong(ActivityOperation::creationGameTime))
            .toList();
         int excess = nonTerminal - maxOps;
         for (int i = 0; i < proposed.size() && excess > 0; i++) {
            ActivityOperation op = proposed.get(i);
            op.enterState(ActivityState.CANCELLED, op.nextDueGameTime());
            op.setDeferReason(ActivityReasonCode.QUEUE_FULL);
            excess--;
            this.setDirty();
         }
      }
      terminals.sort(Comparator.comparingLong(ActivityOperation::creationGameTime));
      while (terminals.size() > maxOutcomes) {
         ActivityOperation old = terminals.removeFirst();
         this.operations.remove(old.operationId());
         this.setDirty();
      }
      while (this.recentOutcomes.size() > maxOutcomes) {
         this.recentOutcomes.removeFirst();
         this.setDirty();
      }
   }

   public boolean tryCoalesceProposal(String key, long gameTime) {
      if (this.pendingProposals.containsKey(key)) {
         return false;
      }
      this.pendingProposals.put(key, gameTime);
      this.setDirty();
      return true;
   }

   public void clearProposal(String key) {
      this.pendingProposals.remove(key);
      this.setDirty();
   }

   public int pendingProposalCount() {
      return this.pendingProposals.size();
   }

   public Map<String, Long> pendingProposals() {
      return this.pendingProposals;
   }

   public void setSourceCooldown(String key, long until) {
      this.sourceCooldowns.put(key, until);
      this.setDirty();
   }

   public void setTargetCooldown(String key, long until) {
      this.targetCooldowns.put(key, until);
      this.setDirty();
   }

   public void setAreaCooldown(String key, long until) {
      this.areaCooldowns.put(key, until);
      this.setDirty();
   }

   public boolean onCooldown(Map<String, Long> map, String key, long gameTime) {
      Long until = map.get(key);
      return until != null && until > gameTime;
   }

   public boolean sourceOnCooldown(String key, long gameTime) {
      return onCooldown(this.sourceCooldowns, key, gameTime);
   }

   public boolean targetOnCooldown(String key, long gameTime) {
      return onCooldown(this.targetCooldowns, key, gameTime);
   }

   public boolean areaOnCooldown(String key, long gameTime) {
      return onCooldown(this.areaCooldowns, key, gameTime);
   }

   public void assignGraceIfNeeded(UUID settlementId, long until) {
      this.settlementGraceUntil.putIfAbsent(settlementId, until);
      this.setDirty();
   }

   public boolean inGrace(UUID settlementId, long gameTime) {
      Long until = this.settlementGraceUntil.get(settlementId);
      return until != null && until > gameTime;
   }

   public void addRivalry(String a, String b, int delta) {
      String key = rivalryKey(a, b);
      this.rivalryScores.merge(key, delta, Integer::sum);
      this.setDirty();
   }

   public int rivalry(String a, String b) {
      return this.rivalryScores.getOrDefault(rivalryKey(a, b), 0);
   }

   private static String rivalryKey(String a, String b) {
      if (a.compareTo(b) <= 0) {
         return a + "|" + b;
      }
      return b + "|" + a;
   }

   public void recordOutcome(ActivityOutcomeSummary summary, int cap) {
      this.recentOutcomes.add(summary);
      while (this.recentOutcomes.size() > cap) {
         this.recentOutcomes.removeFirst();
      }
      this.setDirty();
   }

   public List<ActivityOutcomeSummary> recentOutcomes() {
      return List.copyOf(this.recentOutcomes);
   }

   public boolean featureGraceAssigned() {
      return this.featureGraceAssigned;
   }

   public void setFeatureGraceAssigned(boolean v) {
      this.featureGraceAssigned = v;
      this.setDirty();
   }

   public long warmupUntilGameTime() {
      return this.warmupUntilGameTime;
   }

   public void setWarmupUntilGameTime(long t) {
      this.warmupUntilGameTime = t;
      this.setDirty();
   }

   public int fairnessCursor() {
      return this.fairnessCursor;
   }

   public void setFairnessCursor(int c) {
      this.fairnessCursor = c;
   }

   public int operationCount() {
      return this.operations.size();
   }

   public CollectionView operationsView() {
      return new CollectionView(this.operations.values());
   }

   public static final class CollectionView implements Iterable<ActivityOperation> {
      private final Iterable<ActivityOperation> inner;

      CollectionView(Iterable<ActivityOperation> inner) {
         this.inner = inner;
      }

      @Override
      public Iterator<ActivityOperation> iterator() {
         return this.inner.iterator();
      }
   }

   public static ActivitySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      ActivitySavedData data = new ActivitySavedData();
      int version = tag.getInt("version");
      if (version > SCHEMA_VERSION) {
         // Forward-compat: load what we can.
      }
      ListTag ops = tag.getList("ops", Tag.TAG_COMPOUND);
      for (int i = 0; i < ops.size(); i++) {
         ActivityOperation op = ActivityOperation.load(ops.getCompound(i), registries);
         data.operations.put(op.operationId(), op);
         if (!op.state().isTerminal()) {
            data.dueQueue.offer(op.operationId());
         }
      }
      ListTag groups = tag.getList("groups", Tag.TAG_COMPOUND);
      for (int i = 0; i < groups.size(); i++) {
         ActivityGroup g = ActivityGroup.load(groups.getCompound(i));
         data.groups.put(g.groupId(), g);
      }
      readLongMap(tag.getList("sourceCd", Tag.TAG_COMPOUND), data.sourceCooldowns);
      readLongMap(tag.getList("targetCd", Tag.TAG_COMPOUND), data.targetCooldowns);
      readLongMap(tag.getList("areaCd", Tag.TAG_COMPOUND), data.areaCooldowns);
      ListTag grace = tag.getList("grace", Tag.TAG_COMPOUND);
      for (int i = 0; i < grace.size(); i++) {
         CompoundTag e = grace.getCompound(i);
         data.settlementGraceUntil.put(e.getUUID("id"), e.getLong("t"));
      }
      ListTag riv = tag.getList("rivalry", Tag.TAG_COMPOUND);
      for (int i = 0; i < riv.size(); i++) {
         CompoundTag e = riv.getCompound(i);
         data.rivalryScores.put(e.getString("k"), e.getInt("v"));
      }
      ListTag outcomes = tag.getList("outcomes", Tag.TAG_COMPOUND);
      for (int i = 0; i < outcomes.size(); i++) {
         data.recentOutcomes.add(ActivityOutcomeSummary.load(outcomes.getCompound(i)));
      }
      readLongMap(tag.getList("proposals", Tag.TAG_COMPOUND), data.pendingProposals);
      data.featureGraceAssigned = tag.getBoolean("featureGrace");
      data.warmupUntilGameTime = tag.getLong("warmupUntil");
      data.fairnessCursor = tag.getInt("fairness");
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      ListTag ops = new ListTag();
      for (ActivityOperation op : this.operations.values()) {
         ops.add(op.save(registries));
      }
      tag.put("ops", ops);
      ListTag groups = new ListTag();
      for (ActivityGroup g : this.groups.values()) {
         groups.add(g.save());
      }
      tag.put("groups", groups);
      tag.put("sourceCd", writeLongMap(this.sourceCooldowns));
      tag.put("targetCd", writeLongMap(this.targetCooldowns));
      tag.put("areaCd", writeLongMap(this.areaCooldowns));
      ListTag grace = new ListTag();
      for (var e : this.settlementGraceUntil.entrySet()) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", e.getKey());
         t.putLong("t", e.getValue());
         grace.add(t);
      }
      tag.put("grace", grace);
      ListTag riv = new ListTag();
      for (var e : this.rivalryScores.entrySet()) {
         CompoundTag t = new CompoundTag();
         t.putString("k", e.getKey());
         t.putInt("v", e.getValue());
         riv.add(t);
      }
      tag.put("rivalry", riv);
      ListTag outcomes = new ListTag();
      for (ActivityOutcomeSummary s : this.recentOutcomes) {
         outcomes.add(s.save());
      }
      tag.put("outcomes", outcomes);
      tag.put("proposals", writeLongMap(this.pendingProposals));
      tag.putBoolean("featureGrace", this.featureGraceAssigned);
      tag.putLong("warmupUntil", this.warmupUntilGameTime);
      tag.putInt("fairness", this.fairnessCursor);
      return tag;
   }

   private static void readLongMap(ListTag list, Map<String, Long> map) {
      map.clear();
      for (int i = 0; i < list.size(); i++) {
         CompoundTag e = list.getCompound(i);
         map.put(e.getString("k"), e.getLong("v"));
      }
   }

   private static ListTag writeLongMap(Map<String, Long> map) {
      ListTag list = new ListTag();
      for (var e : map.entrySet()) {
         CompoundTag t = new CompoundTag();
         t.putString("k", e.getKey());
         t.putLong("v", e.getValue());
         list.add(t);
      }
      return list;
   }
}
