package org.millenaire.living.requests;

import java.util.Locale;

public enum VillageRequestType {
   CONSTRUCTION_MATERIAL_DELIVERY,
   FOOD_OR_PRODUCTION_SUPPLY,
   WORK_RESOURCE_DELIVERY,
   VILLAGE_SUPPLY_ASSISTANCE,
   EXISTING_QUEST_REFERRAL,
   SECURITY_ASSISTANCE_INFO;

   public String i18nKey() {
      return "living.millenaire.request.type." + this.name().toLowerCase(Locale.ROOT);
   }
}
