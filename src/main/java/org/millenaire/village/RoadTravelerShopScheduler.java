package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.civilization.interaction.MarketDayService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.item.ModItems;

/**
 * Spawns short-lived road travelers that visit player SHOP buildings.
 */
public final class RoadTravelerShopScheduler {
   private static final int INTERVAL_TICKS = 6000;
   private static long lastTickGameTime = Long.MIN_VALUE;

   private RoadTravelerShopScheduler() {
   }

   public static void tick(ServerLevel level) {
      if (!enabled() || level == null) {
         return;
      }
      // Runs after VillageManager (still in AI_SUPPORT phase accounting); do not re-enter an earlier phase.
      if (!org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
         org.millenaire.perf.tick.ServerTickGovernor.noteDefer(org.millenaire.perf.tick.TickPhase.AI_SUPPORT);
         return;
      }
      long now = level.getGameTime();
      if (lastTickGameTime != Long.MIN_VALUE && now - lastTickGameTime < INTERVAL_TICKS) {
         return;
      }
      lastTickGameTime = now;
      int budget = 2;
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (budget <= 0 || !org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
            break;
         }
         if (!org.millenaire.civilization.settlement.PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
            continue;
         }
         boolean marketDay = MarketDayService.isMarketDay(level, village);
         // Market days raise visit chance: skip fewer villages and allow a higher per-village traveler cap.
         if (!marketDay && Math.floorMod(village.getId().uuid().hashCode() + (int) now, 3) != 0) {
            continue;
         }
         BuildingInstance shop = PlayerShopService.findShop(village);
         BlockPos shopPos = PlayerShopService.shopPos(shop);
         if (shop == null || shopPos == null || village.getCenter() == null) {
            continue;
         }
         if (!level.isLoaded(shopPos)) {
            continue;
         }
         AABB box = new AABB(shopPos).inflate(48.0);
         int existing = level.getEntitiesOfClass(MillVillager.class, box, MillVillager::isRoadShopTraveler).size();
         int cap = marketDay ? 4 : 2;
         if (existing >= cap) {
            continue;
         }
         ResourceLocation typeId = pickCivilianType(village.getCultureId());
         if (typeId == null) {
            continue;
         }
         BlockPos spawn = findPathSpawn(level, village.getCenter());
         if (spawn == null) {
            continue;
         }
         MillVillager traveler = VillagerSpawnFactory.spawnTransient(level, typeId, spawn);
         if (traveler == null) {
            continue;
         }
         traveler.setVillageId(village.getId());
         traveler.setRoadShopTraveler(true);
         int purse = marketDay ? 40 : 24;
         traveler.getInventory().add(ModItems.DENIER.get(), purse);
         budget--;
         if (marketDay && budget > 0 && existing + 1 < cap) {
            // Extra market-day visit attempt within the same budget pass.
            budget = Math.max(budget, 1);
         }
      }
   }

   private static BlockPos findPathSpawn(ServerLevel level, BlockPos center) {
      int[][] offsets = {{24, 0}, {-24, 0}, {0, 24}, {0, -24}, {32, 16}, {-32, -16}};
      for (int[] oz : offsets) {
         int x = center.getX() + oz[0];
         int z = center.getZ() + oz[1];
         int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos pos = new BlockPos(x, y, z);
         if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isSolid()) {
            return pos;
         }
      }
      return null;
   }

   private static ResourceLocation pickCivilianType(ResourceLocation cultureId) {
      if (cultureId == null) {
         return null;
      }
      List<ResourceLocation> candidates = new ArrayList<>();
      for (VillagerType type : ModCultures.getAllVillagerTypes().values()) {
         if (!cultureId.equals(type.culture()) || type.isHostile() || type.isChild() || type.isHelpInAttacks()) {
            continue;
         }
         candidates.add(type.id());
      }
      if (candidates.isEmpty()) {
         return null;
      }
      return candidates.get(Math.floorMod((int) System.nanoTime(), candidates.size()));
   }

   private static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.roadTravelerShopVisits.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.playerShopEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
