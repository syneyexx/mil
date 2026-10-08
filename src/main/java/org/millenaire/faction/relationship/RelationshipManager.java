package org.millenaire.faction.relationship;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.slf4j.Logger;

/**
 * Authoritative API for directional faction-to-faction political relationships.
 * <p>
 * Semantics:
 * <ul>
 *   <li>No explicit record ⇒ implicit neutral score {@link RelationshipConfig#DEFAULT_SCORE}</li>
 *   <li>{@link #hasRelationship} is true only when an explicit persisted record exists</li>
 *   <li>Self-relationships are rejected and never persisted</li>
 *   <li>Scores are always clamped to [{@link RelationshipConfig#SCORE_MIN}, {@link RelationshipConfig#SCORE_MAX}]</li>
 * </ul>
 * Distinct from Millénaire village/player reputation.
 */
public final class RelationshipManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<RelationshipKey, FactionRelationship> relationships = new LinkedHashMap<>();
   private final Map<FactionId, Set<RelationshipKey>> bySource = new LinkedHashMap<>();
   private final Map<FactionId, Set<RelationshipKey>> byTarget = new LinkedHashMap<>();
   private final Runnable markDirty;
   private long lastDecayCheckGameTime;

   RelationshipManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   public boolean hasRelationship(FactionId source, FactionId target) {
      if (!isValidPair(source, target)) {
         return false;
      }

      return this.relationships.containsKey(RelationshipKey.of(source, target));
   }

   /**
    * Returns the explicit relationship record, or {@code null} when only implicit neutral applies.
    */
   @Nullable
   public FactionRelationship getRelationship(FactionId source, FactionId target) {
      if (!isValidPair(source, target)) {
         return null;
      }

      return this.relationships.get(RelationshipKey.of(source, target));
   }

   /**
    * Opinion of {@code source} toward {@code target}. Returns {@link RelationshipConfig#DEFAULT_SCORE}
    * when no explicit record exists or the pair is invalid/self.
    */
   public int getRelationshipScore(FactionId source, FactionId target) {
      if (!isValidPair(source, target)) {
         return RelationshipConfig.DEFAULT_SCORE;
      }

      FactionRelationship relationship = this.relationships.get(RelationshipKey.of(source, target));
      return relationship == null ? RelationshipConfig.DEFAULT_SCORE : relationship.getScore();
   }

   public RelationshipCategory getRelationshipCategory(FactionId source, FactionId target) {
      return RelationshipCategory.fromScore(this.getRelationshipScore(source, target));
   }

   public boolean isPositive(FactionId source, FactionId target) {
      return this.getRelationshipScore(source, target) >= RelationshipConfig.THRESHOLD_POSITIVE;
   }

   public boolean isNegative(FactionId source, FactionId target) {
      return this.getRelationshipScore(source, target) <= RelationshipConfig.THRESHOLD_NEGATIVE;
   }

   public boolean isFriendly(FactionId source, FactionId target) {
      return this.getRelationshipCategory(source, target).isFriendly();
   }

   public boolean isHostile(FactionId source, FactionId target) {
      return this.getRelationshipCategory(source, target).isHostile();
   }

   public boolean setRelationship(ServerLevel level, FactionId source, FactionId target, int value, RelationshipChangeReason reason) {
      return this.setRelationship(level, source, target, value, reason, level.getGameTime());
   }

   /**
    * Authoritative score assignment (admin, migration, initialization). Prefer
    * {@link #modifyRelationship} for normal gameplay changes.
    *
    * @return {@code true} when the relationship was created or updated
    */
   public boolean setRelationship(
      ServerLevel level, FactionId source, FactionId target, int value, RelationshipChangeReason reason, long gameTime
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(reason, "reason");
      if (!validateMutablePair(level, source, target)) {
         return false;
      }

      int clamped = RelationshipConfig.clampScore(value);
      RelationshipKey key = RelationshipKey.of(source, target);
      FactionRelationship relationship = this.relationships.get(key);
      int previous = relationship == null ? RelationshipConfig.DEFAULT_SCORE : relationship.getScore();
      if (relationship == null) {
         relationship = new FactionRelationship(source, target, clamped, gameTime);
         this.indexRelationship(relationship);
         LOGGER.debug("[Millenaire] Created relationship {} → {} = {}", source, target, clamped);
      } else if (previous == clamped) {
         relationship.applyScoreChange(clamped, gameTime, true);
         this.markDirty.run();
         return true;
      } else {
         relationship.applyScoreChange(clamped, gameTime, true);
      }

      int delta = clamped - previous;
      if (Math.abs(delta) >= RelationshipConfig.MIN_HISTORY_ABS_DELTA) {
         relationship.recordHistory(new RelationshipHistoryEntry(gameTime, reason, delta, previous, clamped));
      }

      this.markDirty.run();
      LOGGER.debug("[Millenaire] Set relationship {} → {} to {} (was {}, reason={})", source, target, clamped, previous, reason);
      return true;
   }

   public int modifyRelationship(ServerLevel level, FactionId source, FactionId target, int delta, RelationshipChangeReason reason) {
      return this.modifyRelationship(level, source, target, delta, reason, level.getGameTime());
   }

   /**
    * Applies a gameplay delta with reason and optional history.
    *
    * @return the resulting score (clamped), or {@link RelationshipConfig#DEFAULT_SCORE} when rejected
    */
   public int modifyRelationship(
      ServerLevel level, FactionId source, FactionId target, int delta, RelationshipChangeReason reason, long gameTime
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(reason, "reason");
      if (delta == 0) {
         return this.getRelationshipScore(source, target);
      }

      if (!validateMutablePair(level, source, target)) {
         return RelationshipConfig.DEFAULT_SCORE;
      }

      RelationshipKey key = RelationshipKey.of(source, target);
      FactionRelationship relationship = this.relationships.get(key);
      int previous = relationship == null ? RelationshipConfig.DEFAULT_SCORE : relationship.getScore();
      int next = RelationshipConfig.applyDelta(previous, delta);
      if (next == previous) {
         LOGGER.debug(
            "[Millenaire] Relationship modify {} → {} delta={} clamped with no score change (score={})",
            source,
            target,
            delta,
            previous
         );
         return previous;
      }

      if (relationship == null) {
         relationship = new FactionRelationship(source, target, next, gameTime);
         this.indexRelationship(relationship);
         LOGGER.debug("[Millenaire] Created relationship {} → {} = {} via modify", source, target, next);
      } else {
         relationship.applyScoreChange(next, gameTime, true);
      }

      int appliedDelta = next - previous;
      if (Math.abs(appliedDelta) >= RelationshipConfig.MIN_HISTORY_ABS_DELTA) {
         relationship.recordHistory(new RelationshipHistoryEntry(gameTime, reason, appliedDelta, previous, next));
      }

      this.markDirty.run();
      LOGGER.debug(
         "[Millenaire] Modified relationship {} → {} by {} to {} (reason={})",
         source,
         target,
         appliedDelta,
         next,
         reason
      );
      return next;
   }

   public int increaseRelationship(ServerLevel level, FactionId source, FactionId target, int amount, RelationshipChangeReason reason) {
      return this.increaseRelationship(level, source, target, amount, reason, level.getGameTime());
   }

   public int increaseRelationship(
      ServerLevel level, FactionId source, FactionId target, int amount, RelationshipChangeReason reason, long gameTime
   ) {
      if (amount < 0) {
         LOGGER.warn("[Millenaire] increaseRelationship called with negative amount {} — rejected", amount);
         return this.getRelationshipScore(source, target);
      }

      return this.modifyRelationship(level, source, target, amount, reason, gameTime);
   }

   public int decreaseRelationship(ServerLevel level, FactionId source, FactionId target, int amount, RelationshipChangeReason reason) {
      return this.decreaseRelationship(level, source, target, amount, reason, level.getGameTime());
   }

   public int decreaseRelationship(
      ServerLevel level, FactionId source, FactionId target, int amount, RelationshipChangeReason reason, long gameTime
   ) {
      if (amount < 0) {
         LOGGER.warn("[Millenaire] decreaseRelationship called with negative amount {} — rejected", amount);
         return this.getRelationshipScore(source, target);
      }

      return this.modifyRelationship(level, source, target, -amount, reason, gameTime);
   }

   public boolean removeRelationship(FactionId source, FactionId target) {
      if (!isValidPair(source, target)) {
         return false;
      }

      RelationshipKey key = RelationshipKey.of(source, target);
      FactionRelationship removed = this.relationships.remove(key);
      if (removed == null) {
         return false;
      }

      this.unindex(key);
      this.markDirty.run();
      LOGGER.debug("[Millenaire] Removed relationship {} → {}", source, target);
      return true;
   }

   /**
    * Removes every relationship that originates from or targets {@code factionId}.
    */
   public int removeAllInvolving(FactionId factionId) {
      if (factionId == null) {
         return 0;
      }

      Set<RelationshipKey> keys = new LinkedHashSet<>();
      Set<RelationshipKey> fromSource = this.bySource.get(factionId);
      if (fromSource != null) {
         keys.addAll(fromSource);
      }

      Set<RelationshipKey> fromTarget = this.byTarget.get(factionId);
      if (fromTarget != null) {
         keys.addAll(fromTarget);
      }

      int removed = 0;

      for (RelationshipKey key : keys) {
         if (this.relationships.remove(key) != null) {
            this.unindex(key);
            removed++;
         }
      }

      this.bySource.remove(factionId);
      this.byTarget.remove(factionId);
      if (removed > 0) {
         this.markDirty.run();
         LOGGER.info("[Millenaire] Removed {} relationship(s) involving faction {}", removed, factionId);
      }

      return removed;
   }

   public Collection<FactionRelationship> getAllRelationships() {
      return Collections.unmodifiableCollection(this.relationships.values());
   }

   /**
    * Explicit relationships originating from {@code faction} (A → *).
    */
   public Collection<FactionRelationship> getAllRelationships(FactionId faction) {
      return this.collectByIndex(this.bySource.get(faction));
   }

   /**
    * Explicit relationships where {@code faction} is source or target.
    */
   public Collection<FactionRelationship> getAllRelationshipsInvolving(FactionId faction) {
      if (faction == null) {
         return List.of();
      }

      Set<RelationshipKey> keys = new LinkedHashSet<>();
      Set<RelationshipKey> sources = this.bySource.get(faction);
      if (sources != null) {
         keys.addAll(sources);
      }

      Set<RelationshipKey> targets = this.byTarget.get(faction);
      if (targets != null) {
         keys.addAll(targets);
      }

      return this.collectByIndex(keys);
   }

   /**
    * Factions that {@code source} has an explicit outgoing relationship toward.
    */
   public Collection<FactionId> getRelatedTargets(FactionId source) {
      Set<RelationshipKey> keys = this.bySource.get(source);
      if (keys == null || keys.isEmpty()) {
         return List.of();
      }

      List<FactionId> result = new ArrayList<>(keys.size());

      for (RelationshipKey key : keys) {
         result.add(key.target());
      }

      return Collections.unmodifiableList(result);
   }

   /**
    * Factions that have an explicit relationship targeting {@code target}.
    */
   public Collection<FactionId> getRelatedSources(FactionId target) {
      Set<RelationshipKey> keys = this.byTarget.get(target);
      if (keys == null || keys.isEmpty()) {
         return List.of();
      }

      List<FactionId> result = new ArrayList<>(keys.size());

      for (RelationshipKey key : keys) {
         result.add(key.source());
      }

      return Collections.unmodifiableList(result);
   }

   @Nullable
   public FactionRelationship getMostPositiveRelationship(FactionId source) {
      return this.extremumFromSource(source, true);
   }

   @Nullable
   public FactionRelationship getMostNegativeRelationship(FactionId source) {
      return this.extremumFromSource(source, false);
   }

   /**
    * Applies passive decay toward neutral for existing records, driven by Minecraft game time.
    * Does not create history entries. Does not invent relationships.
    */
   public void tickDecay(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastDecayCheckGameTime > 0L && gameTime - this.lastDecayCheckGameTime < RelationshipConfig.DECAY_CHECK_INTERVAL_TICKS) {
         return;
      }

      this.lastDecayCheckGameTime = gameTime;
      if (this.relationships.isEmpty()) {
         return;
      }

      boolean dirty = false;
      List<RelationshipKey> toRemove = new ArrayList<>();

      for (FactionRelationship relationship : this.relationships.values()) {
         if (this.applyDecay(relationship, gameTime)) {
            dirty = true;
         }

         if (relationship.getScore() == RelationshipConfig.DEFAULT_SCORE && !relationship.hasHistory()) {
            toRemove.add(relationship.getKey());
         }
      }

      for (RelationshipKey key : toRemove) {
         if (this.relationships.remove(key) != null) {
            this.unindex(key);
            dirty = true;
            LOGGER.debug("[Millenaire] Pruned neutral relationship with empty history {}", key);
         }
      }

      if (dirty) {
         this.markDirty.run();
      }
   }

   /**
    * Drops relationship records that reference factions no longer present in the world.
    */
   public int purgeOrphans(FactionManager factionManager) {
      Objects.requireNonNull(factionManager, "factionManager");
      List<RelationshipKey> orphanKeys = new ArrayList<>();

      for (RelationshipKey key : this.relationships.keySet()) {
         if (!factionManager.exists(key.source()) || !factionManager.exists(key.target())) {
            orphanKeys.add(key);
         }
      }

      for (RelationshipKey key : orphanKeys) {
         this.relationships.remove(key);
         this.unindex(key);
         LOGGER.warn("[Millenaire] Purged orphan relationship {} → {}", key.source(), key.target());
      }

      if (!orphanKeys.isEmpty()) {
         this.markDirty.run();
      }

      return orphanKeys.size();
   }

   void loadRelationship(FactionRelationship relationship) {
      Objects.requireNonNull(relationship, "relationship");
      RelationshipKey key = relationship.getKey();
      if (key.isSelf()) {
         LOGGER.error("[Millenaire] Ignoring persisted self-relationship {}", key.source());
         return;
      }

      if (this.relationships.containsKey(key)) {
         LOGGER.error("[Millenaire] Ignoring duplicate persisted relationship {} → {}", key.source(), key.target());
         return;
      }

      this.indexRelationship(relationship);
   }

   long getLastDecayCheckGameTime() {
      return this.lastDecayCheckGameTime;
   }

   void setLastDecayCheckGameTime(long gameTime) {
      this.lastDecayCheckGameTime = gameTime;
   }

   void clear() {
      this.relationships.clear();
      this.bySource.clear();
      this.byTarget.clear();
      this.lastDecayCheckGameTime = 0L;
   }

   private boolean applyDecay(FactionRelationship relationship, long gameTime) {
      int score = relationship.getScore();
      if (Math.abs(score) < RelationshipConfig.MIN_ABS_SCORE_FOR_DECAY) {
         if (relationship.getLastDecayGameTime() < gameTime) {
            relationship.setDecayBookmark(gameTime);
         }

         return false;
      }

      long graceEnds = relationship.getLastMeaningfulChangeGameTime() + RelationshipConfig.DECAY_GRACE_AFTER_EVENT_TICKS;
      long decayOrigin = Math.max(relationship.getLastDecayGameTime(), graceEnds);
      if (gameTime <= decayOrigin) {
         return false;
      }

      long elapsed = gameTime - decayOrigin;
      int steps = (int)(elapsed / RelationshipConfig.DECAY_INTERVAL_TICKS);
      if (steps <= 0) {
         return false;
      }

      steps = Math.min(steps, RelationshipConfig.MAX_DECAY_STEPS_PER_PASS);
      int newScore = score;

      for (int i = 0; i < steps && Math.abs(newScore) >= RelationshipConfig.MIN_ABS_SCORE_FOR_DECAY; i++) {
         if (newScore > 0) {
            newScore = Math.max(0, newScore - RelationshipConfig.DECAY_AMOUNT);
         } else if (newScore < 0) {
            newScore = Math.min(0, newScore + RelationshipConfig.DECAY_AMOUNT);
         }
      }

      long appliedThrough = decayOrigin + (long)steps * RelationshipConfig.DECAY_INTERVAL_TICKS;
      relationship.setDecayBookmark(Math.min(appliedThrough, gameTime));
      if (newScore != score) {
         relationship.applyScoreChange(newScore, gameTime, false);
         LOGGER.debug(
            "[Millenaire] Decayed relationship {} → {} from {} to {} ({} step(s))",
            relationship.getSourceFactionId(),
            relationship.getTargetFactionId(),
            score,
            newScore,
            steps
         );
         return true;
      }

      return false;
   }

   @Nullable
   private FactionRelationship extremumFromSource(FactionId source, boolean mostPositive) {
      Set<RelationshipKey> keys = this.bySource.get(source);
      if (keys == null || keys.isEmpty()) {
         return null;
      }

      FactionRelationship best = null;

      for (RelationshipKey key : keys) {
         FactionRelationship relationship = this.relationships.get(key);
         if (relationship == null) {
            continue;
         }

         if (best == null) {
            best = relationship;
            continue;
         }

         if (mostPositive) {
            if (relationship.getScore() > best.getScore()) {
               best = relationship;
            }
         } else if (relationship.getScore() < best.getScore()) {
            best = relationship;
         }
      }

      return best;
   }

   private Collection<FactionRelationship> collectByIndex(@Nullable Set<RelationshipKey> keys) {
      if (keys == null || keys.isEmpty()) {
         return List.of();
      }

      List<FactionRelationship> result = new ArrayList<>(keys.size());

      for (RelationshipKey key : keys) {
         FactionRelationship relationship = this.relationships.get(key);
         if (relationship != null) {
            result.add(relationship);
         }
      }

      return Collections.unmodifiableList(result);
   }

   private void indexRelationship(FactionRelationship relationship) {
      RelationshipKey key = relationship.getKey();
      this.relationships.put(key, relationship);
      this.bySource.computeIfAbsent(key.source(), ignored -> new LinkedHashSet<>()).add(key);
      this.byTarget.computeIfAbsent(key.target(), ignored -> new LinkedHashSet<>()).add(key);
   }

   private void unindex(RelationshipKey key) {
      Set<RelationshipKey> sources = this.bySource.get(key.source());
      if (sources != null) {
         sources.remove(key);
         if (sources.isEmpty()) {
            this.bySource.remove(key.source());
         }
      }

      Set<RelationshipKey> targets = this.byTarget.get(key.target());
      if (targets != null) {
         targets.remove(key);
         if (targets.isEmpty()) {
            this.byTarget.remove(key.target());
         }
      }
   }

   private static boolean isValidPair(@Nullable FactionId source, @Nullable FactionId target) {
      return source != null && target != null && !source.equals(target);
   }

   private boolean validateMutablePair(ServerLevel level, @Nullable FactionId source, @Nullable FactionId target) {
      if (source == null || target == null) {
         LOGGER.warn("[Millenaire] Relationship mutation rejected — null faction id");
         return false;
      }

      if (source.equals(target)) {
         LOGGER.warn("[Millenaire] Relationship mutation rejected — self-relationship {}", source);
         return false;
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(source)) {
         LOGGER.warn("[Millenaire] Relationship mutation rejected — missing source faction {}", source);
         return false;
      }

      if (!factions.exists(target)) {
         LOGGER.warn("[Millenaire] Relationship mutation rejected — missing target faction {}", target);
         return false;
      }

      return true;
   }
}
