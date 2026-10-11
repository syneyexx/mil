package org.millenaire.companion;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class CompanionActionCatalog {
   private static final Set<String> ALL = new LinkedHashSet<>();

   static {
      // Movement / bond
      add("FOLLOW_PLAYER", "WAIT_HERE", "HOLD_POSITION", "COME_CLOSER");
      add("SET_STANCE", "RETURN_TO_PLAYER", "RETURN_TO_VILLAGE", "GO_HOME");
      // Gear
      add("PICK_UP_NEARBY_ITEMS", "EQUIP_BEST_GEAR", "EQUIP_ITEM", "UNEQUIP", "DROP_ITEM", "ACCEPT_GIFT_FROM_PLAYER");
      // Work / trade
      add("GATHER_GOODS", "GET_TOOL", "DELIVER_TO_HOUSEHOLD", "STORE_IN_BUILDING", "HELP_BUILD");
      add("OPEN_TRADE_WITH_PLAYER", "SELL_WARES", "BUY_SUPPLIES", "WORK_FOR_DENIERS");
      // Social / play
      add("IDLE_PLAY", "SOCIALISE", "CHAT_WITH_VILLAGER", "VISIT_BUILDING", "OBSERVE_VILLAGER", "ATTEND_CEREMONY");
      // Combat
      add("DEFEND_PLAYER", "ATTACK_HOSTILE", "FLEE_TO_SAFETY");
      // Speech / generic player acts
      add("SAY_ALOUD", "USE_ITEM", "USE_BLOCK", "ATTACK", "PLACE_BLOCK");
      // Living villages (string names match NpcActionType where possible)
      add("VIEW_VILLAGE_NEEDS", "OFFER_ASSISTANCE", "VIEW_VILLAGE_STATUS");
      // Mod capabilities
      add("TACZ_FIRE", "USE_WAYSTONE", "CAST_SPELL", "AD_ASTRA_INTERACT", "CREATE_INTERACT");
   }

   private CompanionActionCatalog() {
   }

   private static void add(String... types) {
      for (String t : types) {
         ALL.add(t);
      }
   }

   public static boolean isKnown(String type) {
      if (type == null || type.isBlank()) {
         return false;
      }
      return ALL.contains(type.trim().toUpperCase(Locale.ROOT));
   }

   public static Set<String> all() {
      return Set.copyOf(ALL);
   }

   public static String promptList() {
      return String.join(", ", ALL);
   }
}
