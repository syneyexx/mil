package org.millenaire.companion;

import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

/**
 * Server tick entry for bonded companions. Opt-in: no work without bonds.
 */
public final class CompanionLifecycle {
   private CompanionLifecycle() {
   }

   public static void tick(MinecraftServer server) {
      if (!CompanionService.enabled()) {
         return;
      }
      CompanionTickBudget.beginSlice();
      try {
         ServerLevel overworld = server.getLevel(Level.OVERWORLD);
         if (overworld == null) {
            return;
         }
         CompanionBondSavedData data = CompanionBondSavedData.get(overworld);
         for (Map.Entry<UUID, CompanionBond> entry : data.all().entrySet()) {
            if (!CompanionTickBudget.hasTimeForCompanion()) {
               break;
            }
            CompanionBond bond = entry.getValue();
            if (bond.dismissed() || bond.companionEntityId() == null) {
               continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
               continue;
            }
            MillVillager companion = resolveCompanion(player.serverLevel(), overworld, bond.companionEntityId());
            if (companion == null) {
               continue;
            }
            if (!companion.isCompanion()) {
               companion.setCompanionState(player.getUUID(), true);
               companion.setHireState(player.getUUID(), CompanionService.COMPANION_HIRE_UNTIL);
            }
            ServerLevel level = (ServerLevel)companion.level();
            Village village = companion.getVillageId() != null ? Village.resolve(level, companion.getVillageId()) : null;
            CompanionBrain.tickCompanion(level, player, companion, bond, data, village);
         }
      } finally {
         CompanionTickBudget.endSlice();
      }
   }

   private static MillVillager resolveCompanion(ServerLevel playerLevel, ServerLevel overworld, UUID entityId) {
      Entity entity = playerLevel.getEntity(entityId);
      if (entity instanceof MillVillager villager && villager.isAlive()) {
         return villager;
      }
      if (playerLevel != overworld) {
         Entity other = overworld.getEntity(entityId);
         if (other instanceof MillVillager villager && villager.isAlive()) {
            return villager;
         }
      }
      return null;
   }

   public static void onServerStopping() {
      // Bonds persist via SavedData; clear runtime caches only.
   }
}
