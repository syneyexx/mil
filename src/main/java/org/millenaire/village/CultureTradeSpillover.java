package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.culture.ModCultures;

/**
 * When a player trades with culture X, enemies-of-X lose standing with the player
 * and allies-of-X gain a little. Extra penalty if the player's own city culture is at war with X.
 */
public final class CultureTradeSpillover {
   private static final double ENEMY_FACTOR = 0.15;
   private static final double ALLY_FACTOR = 0.05;
   private static final double OWN_CITY_ENEMY_EXTRA = 0.25;
   private static final int MIN_ENEMY_HIT = 8;
   private static final int MIN_ALLY_GAIN = 2;

   private CultureTradeSpillover() {
   }

   public static void onPlayerTrade(ServerPlayer player, ServerLevel level, ResourceLocation tradedCulture, int tradeValue) {
      if (tradeValue <= 0 || tradedCulture == null) {
         return;
      }

      CultureDiplomacySimulator.ensureUpToDate(level);
      CultureStandingSavedData standing = CultureStandingSavedData.get(level);
      PlayerCultureReputation rep = PlayerCultureReputation.get(level);
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      ResourceLocation playerCityCulture = CultureWarService.playerCultureId(player, level);

      List<SpillMessage> messages = new ArrayList<>();
      for (ResourceLocation other : CultureDiplomacySimulator.collectNonLoneCultures(vm)) {
         if (other.equals(tradedCulture)) {
            continue;
         }

         if (standing.isAtWar(other, tradedCulture)) {
            int hit = -Math.max(MIN_ENEMY_HIT, (int)Math.round(tradeValue * ENEMY_FACTOR));
            if (playerCityCulture != null && playerCityCulture.equals(other)) {
               hit -= Math.max(MIN_ENEMY_HIT, (int)Math.round(tradeValue * OWN_CITY_ENEMY_EXTRA));
            }
            rep.add(player.getUUID(), other, hit);
            messages.add(new SpillMessage(other, tradedCulture, hit, true));
         } else if (standing.isAllied(other, tradedCulture)) {
            int gain = Math.max(MIN_ALLY_GAIN, (int)Math.round(tradeValue * ALLY_FACTOR));
            rep.add(player.getUUID(), other, gain);
            messages.add(new SpillMessage(other, tradedCulture, gain, false));
         }
      }

      if (playerCityCulture != null
         && !playerCityCulture.equals(tradedCulture)
         && standing.isAtWar(playerCityCulture, tradedCulture)) {
         // Already applied above when other == playerCityCulture; reinforce village relation chill
         Village owned = findOwned(vm, player.getUUID());
         if (owned != null) {
            for (Village enemy : vm.getAllVillages()) {
               if (!enemy.isLoneBuilding() && tradedCulture.equals(enemy.getCultureId())) {
                  owned.adjustRelationSymmetric(level, enemy.getId(), -5, false);
               }
            }
         }
      }

      for (SpillMessage msg : messages) {
         String otherName = cultureName(msg.other());
         String tradedName = cultureName(msg.traded());
         if (msg.enemy()) {
            player.sendSystemMessage(
               Component.translatable("message.millenaire.diplomacy.spillover_enemy", otherName, tradedName, msg.delta())
            );
         } else {
            player.sendSystemMessage(
               Component.translatable("message.millenaire.diplomacy.spillover_ally", otherName, tradedName, msg.delta())
            );
         }
      }
   }

   private static Village findOwned(VillageManager vm, UUID playerId) {
      for (Village v : vm.getAllVillages()) {
         if (v.isPlayerControlled() && playerId.equals(v.getOwnerUUID())) {
            return v;
         }
      }
      return null;
   }

   private static String cultureName(ResourceLocation id) {
      var c = ModCultures.getCulture(id);
      return c != null ? c.displayName() : id.getPath();
   }

   private record SpillMessage(ResourceLocation other, ResourceLocation traded, int delta, boolean enemy) {
   }
}
