package org.millenaire.living.needs;

import java.util.Locale;

public enum VillageNeedType {
   CONSTRUCTION_MATERIAL_SHORTAGE,
   VILLAGE_FOOD_SHORTAGE,
   WORK_RESOURCE_SHORTAGE,
   TRADE_DISRUPTION,
   VILLAGE_SECURITY_THREAT,
   CONSTRUCTION_STALLED,
   RESIDENT_LOSS,
   MERCHANT_OR_SUPPLY_UNAVAILABLE;

   public String i18nKey() {
      return "living.millenaire.need." + this.name().toLowerCase(Locale.ROOT);
   }
}
