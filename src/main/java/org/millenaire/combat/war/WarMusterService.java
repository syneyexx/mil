package org.millenaire.combat.war;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Player offense muster: hire up to {@code warMaxLevy} fighters as escorts for a campaign wave.
 * Reuses the hire/escort path ({@link org.millenaire.goal.impl.HiredEscortGoal} / {@link WarEscortGoal}).
 */
public final class WarMusterService {
   private WarMusterService() {
   }

   public static int countMustered(Village village, UUID playerId) {
      int n = 0;
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (playerId.equals(r.getHiredBy()) && r.isAwayHired()) {
            n++;
         }
      }
      return n;
   }

   /**
    * Muster eligible raiders/defenders under the player as hired escorts for the war duration.
    * @return number newly mustered
    */
   public static int musterForOffense(ServerLevel level, ServerPlayer player, Village village, @Nullable WarCampaign campaign) {
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return 0;
      }
      if (village == null || player == null) {
         return 0;
      }
      if (!village.isPlayerControlled() || !player.getUUID().equals(village.getOwnerUUID())) {
         return 0;
      }
      int maxLevy = MillenaireServerConfig.SERVER.warMaxLevy.getAsInt();
      int already = countMustered(village, player.getUUID());
      if (already >= maxLevy) {
         player.sendSystemMessage(Component.translatable("message.millenaire.war.muster_full", maxLevy));
         return 0;
      }

      List<VillagerRecord> candidates = new ArrayList<>();
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (r.isKilled() || r.isAwayRaiding() || r.isRaidingVillage() || r.isAwayHired()) {
            continue;
         }
         VillagerType type = ModCultures.getVillagerType(r.getVillagerTypeId());
         if (type == null || type.isChild()) {
            continue;
         }
         if (type.isRaider() || type.isHelpInAttacks()) {
            candidates.add(r);
         }
      }
      if (candidates.isEmpty()) {
         player.sendSystemMessage(Component.translatable("message.millenaire.war.muster_none"));
         return 0;
      }

      long until = level.getGameTime() + MillenaireServerConfig.SERVER.warWaveIntervalTicks.getAsInt() * 2L;
      int hired = 0;
      for (VillagerRecord r : candidates) {
         if (already + hired >= maxLevy) {
            break;
         }
         village.setVillagerHired(level, r.getUuid(), player.getUUID(), until);
         if (level.getEntity(r.getUuid()) instanceof MillVillager mv) {
            mv.setAggressiveStance(true);
         }
         hired++;
      }
      if (hired > 0) {
         village.markDirty();
         player.sendSystemMessage(Component.translatable("message.millenaire.war.muster_ok", hired, already + hired, maxLevy));
      }
      return hired;
   }

   public static void releaseMuster(ServerLevel level, Village village, UUID playerId) {
      if (village == null || playerId == null) {
         return;
      }
      for (VillagerRecord r : village.getVillagerRecords().values()) {
         if (playerId.equals(r.getHiredBy()) && r.isAwayHired()) {
            village.setVillagerHired(level, r.getUuid(), null, 0L);
         }
      }
   }
}
