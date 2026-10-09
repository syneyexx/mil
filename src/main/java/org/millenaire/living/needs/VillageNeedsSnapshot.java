package org.millenaire.living.needs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable read-only cache of needs for one village at one evaluation time.
 */
public final class VillageNeedsSnapshot {
   private final UUID villageId;
   private final long gameTime;
   private final int stateVersion;
   private final List<VillageNeed> needs;

   public VillageNeedsSnapshot(UUID villageId, long gameTime, int stateVersion, List<VillageNeed> needs) {
      this.villageId = Objects.requireNonNull(villageId);
      this.gameTime = gameTime;
      this.stateVersion = stateVersion;
      this.needs = List.copyOf(needs);
   }

   public UUID villageId() {
      return this.villageId;
   }

   public long gameTime() {
      return this.gameTime;
   }

   public int stateVersion() {
      return this.stateVersion;
   }

   public List<VillageNeed> needs() {
      return this.needs;
   }

   public List<VillageNeed> needsUnmodifiable() {
      return Collections.unmodifiableList(this.needs);
   }

   public Optional<VillageNeed> mostSevere() {
      VillageNeed best = null;
      for (VillageNeed need : this.needs) {
         if (need.severity() == VillageNeedPriority.NONE) {
            continue;
         }
         if (best == null || need.severity().rank() > best.severity().rank()) {
            best = need;
         }
      }
      return Optional.ofNullable(best);
   }

   public Optional<VillageNeed> findById(String needId) {
      for (VillageNeed need : this.needs) {
         if (need.needId().equals(needId)) {
            return Optional.of(need);
         }
      }
      return Optional.empty();
   }

   public Optional<VillageNeed> firstOfType(VillageNeedType type) {
      for (VillageNeed need : this.needs) {
         if (need.type() == type && need.severity() != VillageNeedPriority.NONE) {
            return Optional.of(need);
         }
      }
      return Optional.empty();
   }

   public boolean isEmpty() {
      return this.needs.isEmpty() || this.needs.stream().allMatch(n -> n.severity() == VillageNeedPriority.NONE);
   }
}
