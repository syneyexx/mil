package org.millenaire.discovery;

import java.util.Set;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.network.UnlockingToastPayload;

public final class DiscoveryToasts {
   private DiscoveryToasts() {
   }

   public static void sendBuildingToast(ServerPlayer player, ServerLevel level, String cultureKey, BuildingPlanSet planSet) {
      int unlocked = countForCulture(DiscoveryTracker.get(level).getUnlockedBuildings(player.getUUID()), cultureKey);
      int total = (int)ModCultures.getAllBuildingPlanSets()
         .entrySet()
         .stream()
         .filter(e -> belongsToCulture(e.getKey(), cultureKey) && e.getValue().travelBookDisplay())
         .count();
      send(player, "building", planSet.nativeName(), cultureKey, unlocked, total, planSet.icon());
   }

   public static void sendVillageToast(ServerPlayer player, ServerLevel level, String cultureKey, VillageType villageType) {
      int unlocked = countForCulture(DiscoveryTracker.get(level).getUnlockedVillages(player.getUUID()), cultureKey);
      int total = (int)ModCultures.getAllVillageTypes()
         .entrySet()
         .stream()
         .filter(e -> belongsToCulture(e.getKey(), cultureKey) && e.getValue().travelBookDisplay())
         .count();
      send(player, "village", villageType.name(), cultureKey, unlocked, total, villageType.icon());
   }

   private static void send(ServerPlayer player, String category, String title, String cultureKey, int unlocked, int total, String iconId) {
      Culture culture = ModCultures.getCulture(ResourceLocation.fromNamespaceAndPath("millenaire", cultureKey));
      String cultureName = culture != null ? culture.displayName() : cultureKey;
      PacketDistributor.sendToPlayer(
         player,
         new UnlockingToastPayload(category, title != null ? title : "", cultureName, unlocked, total, iconId != null ? iconId : ""),
         new CustomPacketPayload[0]
      );
   }

   private static boolean belongsToCulture(ResourceLocation key, String cultureKey) {
      return key.getPath().startsWith(cultureKey + "/");
   }

   private static int countForCulture(Set<String> keys, String cultureKey) {
      String prefix = cultureKey + "_";
      int count = 0;

      for (String key : keys) {
         if (key.startsWith(prefix)) {
            count++;
         }
      }

      return count;
   }
}
