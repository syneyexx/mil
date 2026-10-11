package org.millenaire.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class CompanionWorldSensor {
   private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();
   private static final long TTL_MS = 1500L;

   private CompanionWorldSensor() {
   }

   public static List<String> sense(ServerLevel level, ServerPlayer player, MillVillager companion, @Nullable Village village) {
      long now = System.currentTimeMillis();
      Cached cached = CACHE.get(companion.getUUID());
      if (cached != null && now - cached.atMs < TTL_MS) {
         return cached.lines;
      }
      List<String> lines = new ArrayList<>();
      lines.add("time=" + (level.isNight() ? "night" : "day") + " raining=" + level.isRaining());
      lines.add("dim=" + level.dimension().location());
      lines.add(
         "biome="
            + level.getBiome(companion.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("unknown")
      );
      lines.add("hp=" + (int)companion.getHealth() + "/" + (int)companion.getMaxHealth());
      lines.add("playerHp=" + (int)player.getHealth() + "/" + (int)player.getMaxHealth());
      lines.add("distPlayer=" + String.format("%.1f", Math.sqrt(companion.distanceToSqr(player))));
      if (village != null) {
         lines.add("village=" + village.getVillageName() + " culture=" + village.getCultureId());
      } else {
         lines.add("village=(none nearby resolved)");
      }
      String goal = companion.getGoalLabel();
      if (goal != null && !goal.isBlank()) {
         lines.add("goal=" + goal);
      }
      lines.add("invKinds=" + companion.getInventory().getAll().size());
      AABB box = companion.getBoundingBox().inflate(10.0);
      int hostiles = level.getEntitiesOfClass(Monster.class, box).size();
      int items = level.getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(6.0)).size();
      lines.add("nearbyHostiles=" + hostiles + " nearbyItems=" + items);
      if (!companion.getMainHandItem().isEmpty()) {
         lines.add("mainHand=" + BuiltInRegistries.ITEM.getKey(companion.getMainHandItem().getItem()));
      }
      List<String> frozen = List.copyOf(lines);
      CACHE.put(companion.getUUID(), new Cached(now, frozen));
      return frozen;
   }

   public static void clear(UUID companionId) {
      CACHE.remove(companionId);
   }

   private record Cached(long atMs, List<String> lines) {
   }
}
