package org.millenaire.commerce;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;

/**
 * Rewrites vanilla villager / wandering trader emerald costs and results to deniers.
 */
public final class VanillaDenierTradeHelper {
   private VanillaDenierTradeHelper() {
   }

   public static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.vanillaVillagersUseDeniers.getAsBoolean();
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int emeraldToDenierRate() {
      try {
         return MillenaireServerConfig.SERVER.emeraldToDenierRate.getAsInt();
      } catch (IllegalStateException e) {
         return MoneyHelper.ARGENT_VALUE;
      }
   }

   public static void convertVillagerOffers(AbstractVillager villager) {
      if (!enabled() || villager.level().isClientSide()) {
         return;
      }
      MerchantOffers offers = villager.getOffers();
      if (offers == null || offers.isEmpty()) {
         return;
      }
      boolean changed = false;
      List<MerchantOffer> rewritten = new ArrayList<>(offers.size());
      for (MerchantOffer offer : offers) {
         MerchantOffer next = convertOffer(offer);
         rewritten.add(next);
         if (next != offer) {
            changed = true;
         }
      }
      if (changed) {
         offers.clear();
         offers.addAll(rewritten);
      }
   }

   private static MerchantOffer convertOffer(MerchantOffer offer) {
      ItemStack costAStack = convertStack(offer.getCostA());
      ItemStack costBStack = convertStack(offer.getCostB());
      ItemStack result = convertStack(offer.getResult());
      if (costAStack == offer.getCostA() && costBStack == offer.getCostB() && result == offer.getResult()) {
         return offer;
      }

      ItemCost costA = toItemCost(costAStack);
      Optional<ItemCost> costB = costBStack.isEmpty() ? Optional.empty() : Optional.of(toItemCost(costBStack));
      return new MerchantOffer(
         costA,
         costB,
         result,
         offer.getUses(),
         offer.getMaxUses(),
         offer.getXp(),
         offer.getPriceMultiplier(),
         offer.getDemand()
      );
   }

   private static ItemCost toItemCost(ItemStack stack) {
      return new ItemCost(stack.getItem(), Math.max(1, stack.getCount()));
   }

   private static ItemStack convertStack(ItemStack stack) {
      if (stack.isEmpty() || !stack.is(Items.EMERALD)) {
         return stack;
      }
      int deniers = Math.max(1, stack.getCount() * emeraldToDenierRate());
      return deniersToStack(deniers);
   }

   /**
    * Prefer argent/or for compact merchant slots when the value divides evenly.
    */
   private static ItemStack deniersToStack(int deniers) {
      Item or = ModItems.DENIER_OR.get();
      Item argent = ModItems.DENIER_ARGENT.get();
      Item bronze = ModItems.DENIER.get();
      if (deniers % MoneyHelper.OR_VALUE == 0 && deniers / MoneyHelper.OR_VALUE <= 64) {
         return new ItemStack(or, deniers / MoneyHelper.OR_VALUE);
      }
      if (deniers % MoneyHelper.ARGENT_VALUE == 0 && deniers / MoneyHelper.ARGENT_VALUE <= 64) {
         return new ItemStack(argent, deniers / MoneyHelper.ARGENT_VALUE);
      }
      if (deniers <= 64) {
         return new ItemStack(bronze, deniers);
      }
      int argentCount = Math.min(64, Math.max(1, deniers / MoneyHelper.ARGENT_VALUE));
      return new ItemStack(argent, argentCount);
   }
}
