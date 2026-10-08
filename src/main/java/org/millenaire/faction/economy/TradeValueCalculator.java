package org.millenaire.faction.economy;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;

/**
 * Centralized relative trade-value estimation using Millénaire {@link TradeGood} denier prices.
 */
public final class TradeValueCalculator {
   private TradeValueCalculator() {
   }

   /**
    * Estimates denier value for {@code quantity} of {@code item} using the exporter culture catalog.
    * Falls back to quantity itself when no priced trade good matches.
    */
   public static int estimateValue(@Nullable ResourceLocation cultureId, Item item, int quantity) {
      if (item == null || quantity <= 0) {
         return 0;
      }

      int unit = unitPrice(cultureId, item);
      long total = (long)unit * (long)quantity;
      if (total > Integer.MAX_VALUE) {
         return Integer.MAX_VALUE;
      }

      return (int)total;
   }

   public static int estimateValue(@Nullable ResourceLocation cultureId, EconomicResourceId resourceId, int quantity) {
      if (resourceId == null) {
         return 0;
      }

      Item item = resourceId.resolveItem();
      return item == null ? Math.max(0, quantity) : estimateValue(cultureId, item, quantity);
   }

   public static boolean isMajorTrade(int value) {
      return value >= EconomicConfig.MIN_TRADE_VALUE_FOR_RELATIONSHIP * 4;
   }

   public static boolean isSignificantTrade(int value) {
      return value >= EconomicConfig.MIN_TRADE_VALUE_FOR_RELATIONSHIP;
   }

   private static int unitPrice(@Nullable ResourceLocation cultureId, Item item) {
      if (cultureId == null) {
         return 1;
      }

      int best = 0;

      for (TradeGood good : TradeGoodsLoader.getGoods(cultureId)) {
         Item resolved = good.resolveItem();
         if (resolved != null && resolved == item) {
            int price = Math.max(good.sellingPrice(), good.buyingPrice());
            if (price <= 0) {
               price = Math.max(good.foreignMerchantPrice(), 0);
            }

            if (price > best) {
               best = price;
            }
         }
      }

      return best > 0 ? best : 1;
   }
}
