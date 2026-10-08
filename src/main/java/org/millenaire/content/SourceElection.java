package org.millenaire.content;

import java.util.ArrayList;
import java.util.List;

public final class SourceElection {
   public static final int NO_CROSS_SOURCE_WINNER = -1;

   private SourceElection() {
   }

   public static int crossSourceWinnerIndex(List<SourceKind> kinds) {
      if (kinds != null && kinds.size() >= 2) {
         SourceKind first = kinds.get(0);

         for (int i = 1; i < kinds.size(); i++) {
            if (kinds.get(i) != first) {
               return kinds.size() - 1;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public static int crossSourceWinnerIndexOf(List<Resource> hits) {
      if (hits != null && hits.size() >= 2) {
         List<SourceKind> kinds = new ArrayList<>(hits.size());

         for (Resource r : hits) {
            kinds.add(r.kind());
         }

         return crossSourceWinnerIndex(kinds);
      } else {
         return -1;
      }
   }

   public static int servedIndexOf(List<Resource> hits) {
      int idx = crossSourceWinnerIndexOf(hits);
      return idx == -1 ? 0 : idx;
   }
}
