package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

/**
 * Periodic market-day windows that boost road-shop traveler visits.
 */
public final class MarketDayService {
   private MarketDayService() {
   }

   public static boolean isMarketDay(@Nullable Village village, long gameTime) {
      if (village == null || village.getId() == null) {
         return false;
      }
      // Deterministic calendar: every Nth Minecraft day is market day for a village hash slot.
      long day = gameTime / InteractionConfig.MARKET_DAY_INTERVAL_TICKS;
      int slot = Math.floorMod(village.getId().uuid().hashCode(), 5);
      return Math.floorMod(day, 5) == slot;
   }

   public static boolean isMarketDay(ServerLevel level, Village village) {
      if (level == null || village == null) {
         return false;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      String id = village.getId().uuid().toString();
      if (data.marketDayFlags().containsKey(id)) {
         return data.isMarketDay(id);
      }
      return isMarketDay(village, level.getGameTime());
   }

   public static void tickFlags(ServerLevel level) {
      if (level == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      data.clearMarketDays();
      long now = level.getGameTime();
      for (Village village : org.millenaire.village.VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (isMarketDay(village, now)) {
            data.setMarketDay(village.getId().uuid().toString(), true);
         }
      }
   }

   /** Visit chance / budget multiplier for road travelers. */
   public static double visitBoost(ServerLevel level, Village village) {
      return isMarketDay(level, village) ? 2.0 : 1.0;
   }

   public static boolean isMarketDayVillageId(@Nullable VillageId villageId, long gameTime) {
      if (villageId == null) {
         return false;
      }
      long day = gameTime / InteractionConfig.MARKET_DAY_INTERVAL_TICKS;
      int slot = Math.floorMod(villageId.uuid().hashCode(), 5);
      return Math.floorMod(day, 5) == slot;
   }
}
