package org.millenaire.worldboss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * Avoids always picking the same player when several are eligible.
 */
public final class WorldBossPlayerSelection {
   private WorldBossPlayerSelection() {
   }

   public record Candidate(UUID id, int recentPenalty) {
   }

   @Nullable
   public static UUID select(Collection<Candidate> candidates, RandomSource random) {
      if (candidates == null || candidates.isEmpty()) {
         return null;
      }
      List<Candidate> list = new ArrayList<>(candidates);
      int total = 0;
      int[] weights = new int[list.size()];
      for (int i = 0; i < list.size(); i++) {
         int w = Math.max(1, 100 - list.get(i).recentPenalty());
         weights[i] = w;
         total += w;
      }
      int roll = random.nextInt(Math.max(1, total));
      int cursor = 0;
      for (int i = 0; i < list.size(); i++) {
         cursor += weights[i];
         if (roll < cursor) {
            return list.get(i).id();
         }
      }
      return list.getLast().id();
   }
}
