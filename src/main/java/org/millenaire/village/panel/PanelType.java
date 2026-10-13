package org.millenaire.village.panel;

public enum PanelType {
   VILLAGE_SUMMARY,
   POPULATION,
   CONSTRUCTIONS,
   PROJECTS,
   PLAYER_BUILDINGS,
   CONTROLLED_PROJECTS,
   HOUSE,
   RESOURCES,
   ARCHIVES,
   VILLAGE_MAP,
   MILITARY,
   CONTROLLED_MILITARY,
   CONTROLLED_CITY,
   INN_TRADE_GOODS,
   INN_VISITORS,
   MARKET_MERCHANTS,
   VISITORS,
   WALLS,
   MARVEL_PROJECTS,
   MARVEL_DONATIONS,
   MARVEL_RESOURCES,
   HALL_OF_FAME,
   CHRONICLE,
   RELIGION,
   BANK,
   BUILDING_DEFAULT;

   public static PanelType fromName(String name) {
      try {
         return valueOf(name);
      } catch (IllegalArgumentException e) {
         return VILLAGE_SUMMARY;
      }
   }
}
