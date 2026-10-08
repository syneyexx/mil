package org.millenaire.combat.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.advancement.MillAdvancements;

public final class RaidAdvancementResolver {
   private static final String SELJUK = "seljuk";
   private static final String BYZANTINES = "byzantines";

   private RaidAdvancementResolver() {
   }

   public static List<RaidAdvancementResolver.Grant> resolve(
      ResourceLocation attackerCulture, ResourceLocation targetCulture, @Nullable UUID attackerOwner, @Nullable UUID targetOwner, boolean attackersWon
   ) {
      List<RaidAdvancementResolver.Grant> grants = new ArrayList<>();
      String attackerKey = attackerCulture != null ? attackerCulture.getPath() : "";
      String targetKey = targetCulture != null ? targetCulture.getPath() : "";
      if (attackersWon) {
         if (attackerOwner != null) {
            grants.add(new RaidAdvancementResolver.Grant(attackerOwner, MillAdvancements.VIKING));
            if ("seljuk".equals(attackerKey) && "byzantines".equals(targetKey)) {
               grants.add(new RaidAdvancementResolver.Grant(attackerOwner, MillAdvancements.SELJUK_ISTANBUL));
            }

            if (targetOwner != null && !attackerOwner.equals(targetOwner)) {
               grants.add(new RaidAdvancementResolver.Grant(attackerOwner, MillAdvancements.MP_RAIDONPLAYER));
            }
         }
      } else if ("seljuk".equals(attackerKey) && "byzantines".equals(targetKey) && targetOwner != null) {
         grants.add(new RaidAdvancementResolver.Grant(targetOwner, MillAdvancements.BYZANTINES_NOTTODAY));
      }

      return grants;
   }

   public record Grant(UUID playerUuid, ResourceLocation advancement) {
   }
}
