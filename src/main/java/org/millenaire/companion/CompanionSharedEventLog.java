package org.millenaire.companion;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.entity.MillVillager;

public final class CompanionSharedEventLog {
   private CompanionSharedEventLog() {
   }

   public static void record(ServerLevel level, UUID playerId, String line) {
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      CompanionBond bond = data.get(playerId);
      if (bond == null || bond.dismissed()) {
         return;
      }
      bond.addSharedEvent(line);
      data.markChanged();
   }

   public static void onLowHealth(ServerPlayer player, MillVillager companion) {
      if (companion.getHealth() < companion.getMaxHealth() * 0.35f) {
         record(player.serverLevel(), player.getUUID(), "companion was badly hurt near the player");
      }
      if (player.getHealth() < player.getMaxHealth() * 0.35f) {
         record(player.serverLevel(), player.getUUID(), "player was badly hurt while companion was present");
      }
   }

   public static void onCombat(@Nullable ServerPlayer player, MillVillager companion) {
      if (player == null) {
         return;
      }
      if (companion.getAttackTarget() != null || player.getLastHurtMob() != null) {
         record(player.serverLevel(), player.getUUID(), "fought together near " + companion.blockPosition().toShortString());
      }
   }

   public static void onGift(ServerPlayer player, String itemDesc) {
      record(player.serverLevel(), player.getUUID(), "player gave companion " + itemDesc);
   }
}
