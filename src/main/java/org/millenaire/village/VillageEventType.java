package org.millenaire.village;

import java.util.Locale;

public enum VillageEventType {
   FOUNDED,
   FOUNDED_CONTROLLED,
   BUILDING_STARTED,
   BUILDING_COMPLETED,
   UPGRADE_STARTED,
   VILLAGER_SPAWNED,
   BIRTH,
   DEATH,
   CAME_OF_AGE,
   MERCHANT_ARRIVED,
   MIGRATION,
   FESTIVAL,
   RITUAL,
   ORDER_EXPOSED,
   STORY,
   TREATY,
   FOUNDING;

   public String i18nKey() {
      return "chronicle.millenaire." + this.name().toLowerCase(Locale.ROOT);
   }
}
