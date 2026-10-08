package org.millenaire.compat;

import net.minecraft.resources.ResourceLocation;
import org.millenaire.block.ModBlocks;
import org.millenaire.item.ModItems;

public final class LegacyRegistryAliases {
   static final ResourceLocation BYZANTINE_FRESCO_OLD_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantine_mosaic_blue");
   static final ResourceLocation BYZANTINE_FRESCO_NEW_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "byzantine_fresco");

   private LegacyRegistryAliases() {
   }

   public static void register() {
      ModBlocks.BLOCKS.addAlias(BYZANTINE_FRESCO_OLD_ID, BYZANTINE_FRESCO_NEW_ID);
      ModItems.ITEMS.addAlias(BYZANTINE_FRESCO_OLD_ID, BYZANTINE_FRESCO_NEW_ID);
   }
}
