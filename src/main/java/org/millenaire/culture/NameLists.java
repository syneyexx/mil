package org.millenaire.culture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public record NameLists(Map<String, List<String>> lists) {
   public String randomFrom(String listKey) {
      List<String> names = this.lists.get(listKey);
      return names != null && !names.isEmpty() ? names.get(ThreadLocalRandom.current().nextInt(names.size())) : null;
   }

   public String randomFrom(String listKey, Set<String> namesTaken) {
      List<String> names = this.lists.get(listKey);
      if (names == null || names.isEmpty()) {
         return null;
      } else if (namesTaken != null && !namesTaken.isEmpty()) {
         List<String> available = new ArrayList<>(names);
         available.removeAll(namesTaken);
         return available.isEmpty()
            ? names.get(ThreadLocalRandom.current().nextInt(names.size()))
            : available.get(ThreadLocalRandom.current().nextInt(available.size()));
      } else {
         return names.get(ThreadLocalRandom.current().nextInt(names.size()));
      }
   }
}
