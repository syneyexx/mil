package org.millenaire.village.diplomacy;

public enum DiplomacyEventType {
   TRADE_BUY_OFFER,
   WAR_DECLARATION,
   RAID_WARNING,
   PEACE_OFFER,
   ALLIANCE_PROPOSAL,
   TRIBUTE_DEMAND,
   DIPLOMATIC_GIFT,
   TRADE_SELL_OFFER,
   BORDER_DISPUTE,
   TRADE_EMBARGO,
   FESTIVAL_INVITE,
   SPY_ACCUSATION,
   REFUGEE_REQUEST,
   MERCENARY_OFFER,
   GRAIN_LOAN_REQUEST,
   CARAVAN_BONUS,
   JUSTICE_DEMAND,
   JOINT_RAID_INVITE,
   TERRITORY_PROTEST,
   MERCHANT_RANSOM,
   CULTURE_PRAISE,
   SABOTAGE_THREAT;

   public String titleKey() {
      return "gui.millenaire.diplomacy.event." + this.name().toLowerCase() + ".title";
   }

   public String bodyKey() {
      return "gui.millenaire.diplomacy.event." + this.name().toLowerCase() + ".body";
   }
}
