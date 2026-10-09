package org.millenaire.living.events;

import java.util.Locale;

public enum LivingEventType {
   VILLAGE_FOUNDED,
   BUILDING_STARTED,
   BUILDING_COMPLETED,
   BUILDING_UPGRADED,
   RESIDENT_BORN,
   RESIDENT_DIED,
   RESIDENT_CAME_OF_AGE,
   MERCHANT_ARRIVED,
   MIGRATION,
   ATTACK_STARTED,
   ATTACK_ENDED,
   MATERIAL_SHORTAGE_DETECTED,
   MATERIAL_SHORTAGE_RESOLVED,
   ASSISTANCE_REQUEST_OFFERED,
   ASSISTANCE_REQUEST_ACCEPTED,
   ASSISTANCE_REQUEST_DECLINED,
   ASSISTANCE_REQUEST_COMPLETED,
   PLAYER_DELIVERY_VERIFIED,
   REPUTATION_CHANGED,
   TRADE_CHANGED,
   SOCIAL_INTERACTION,
   KNOWLEDGE_SHARED,
   MOOD_REACTION,
   /** Phase 5 civilization chronicle events — verified faction outcomes only. */
   LEADERSHIP_CHANGED,
   TREATY_SIGNED,
   TREATY_ENDED,
   WAR_DECLARED,
   PEACE_SIGNED,
   TRADE_COMPLETED,
   SETTLEMENT_DEVELOPMENT;

   public String i18nKey() {
      return "living.millenaire.event." + this.name().toLowerCase(Locale.ROOT);
   }

   public boolean isSignificantForChronicle() {
      return switch (this) {
         case ASSISTANCE_REQUEST_COMPLETED,
            PLAYER_DELIVERY_VERIFIED,
            MATERIAL_SHORTAGE_RESOLVED,
            ATTACK_STARTED,
            ATTACK_ENDED,
            LEADERSHIP_CHANGED,
            TREATY_SIGNED,
            TREATY_ENDED,
            WAR_DECLARED,
            PEACE_SIGNED,
            TRADE_COMPLETED,
            VILLAGE_FOUNDED -> true;
         default -> false;
      };
   }
}
