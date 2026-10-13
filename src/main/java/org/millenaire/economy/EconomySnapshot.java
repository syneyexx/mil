package org.millenaire.economy;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Immutable economy planning input. Built on the server thread; consumed only by workers.
 */
public record EconomySnapshot(
   UUID villageId,
   long worldSessionId,
   long gameTime,
   long dayNumber,
   ResourceLocation cultureId,
   int population,
   int freeLaborEstimate,
   boolean hasPendingConstruction,
   boolean playerControlled,
   List<StockLine> stocks,
   List<NeedLine> needs,
   List<PartnerLine> tradePartners
) {
   public record StockLine(Item item, int count, int target, int constructionReserved) {
   }

   public record NeedLine(
      UUID needId,
      NeedCategory category,
      Item item,
      int remaining,
      int priority,
      String sourceRef,
      String blockedReason
   ) {
   }

   public record PartnerLine(UUID villageId, Item surplusItem, int surplusAmount, Item wantItem, int wantAmount, int relation) {
   }
}
