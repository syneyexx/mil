package org.millenaire.perf.sense;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.millenaire.combat.CombatHelper;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.PerVillageTickCache;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

/**
 * Shared per-village sense caches so combat goals do not each run large AABB scans.
 */
public final class CombatSenseCache {
   private static final PerVillageTickCache<List<Monster>> MONSTERS = new PerVillageTickCache<>(10);
   private static final PerVillageTickCache<List<LivingEntity>> HOSTILES = new PerVillageTickCache<>(10);
   private static final PerVillageTickCache<List<MillVillager>> ALLIES = new PerVillageTickCache<>(10);
   private static int refreshCount;
   private static int cacheHits;

   private CombatSenseCache() {
   }

   public static List<Monster> monsters(ServerLevel level, Village village) {
      UUID id = village.getId().uuid();
      return MONSTERS.get(id, level.getGameTime(), () -> {
         refreshCount++;
         VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
         int radius = (vType != null ? vType.radius() : 90) + 16;
         BlockPos c = village.getCenter();
         AABB box = new AABB(c.getX() - radius, c.getY() - 24, c.getZ() - radius, c.getX() + radius, c.getY() + 64, c.getZ() + radius);
         return List.copyOf(level.getEntitiesOfClass(Monster.class, box));
      });
   }

   public static List<LivingEntity> hostiles(ServerLevel level, Village village, MillVillager self) {
      UUID id = village.getId().uuid();
      List<LivingEntity> cached = HOSTILES.get(id, level.getGameTime(), () -> {
         refreshCount++;
         VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
         int radius = (vType != null ? vType.radius() : 90) + 16;
         BlockPos c = village.getCenter();
         AABB box = new AABB(c.getX() - radius, c.getY() - 24, c.getZ() - radius, c.getX() + radius, c.getY() + 64, c.getZ() + radius);
         List<LivingEntity> found = new ArrayList<>();
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, CombatSenseCache::isHostileCandidate)) {
            found.add(e);
         }
         return List.copyOf(found);
      });
      cacheHits++;
      return cached;
   }

   public static List<MillVillager> nearbyVillagers(ServerLevel level, Village village) {
      UUID id = village.getId().uuid();
      return ALLIES.get(id, level.getGameTime(), () -> {
         refreshCount++;
         VillageType vType = ModCultures.getVillageType(village.getVillageTypeId());
         int radius = Math.max(80, (vType != null ? vType.radius() : 90) + 16);
         BlockPos c = village.getCenter();
         AABB box = new AABB(c.getX() - radius, c.getY() - 24, c.getZ() - radius, c.getX() + radius, c.getY() + 64, c.getZ() + radius);
         return List.copyOf(level.getEntitiesOfClass(MillVillager.class, box));
      });
   }

   @Nullable
   public static LivingEntity findClosestHostile(MillVillager v, double range) {
      if (!(v.level() instanceof ServerLevel level)) {
         return null;
      }
      VillageId villageId = v.getVillageId();
      Village village = villageId != null ? Village.resolve(level, villageId) : null;
      double rangeSq = range * range;
      double bestSq = rangeSq + 1.0;
      LivingEntity best = null;
      Iterable<? extends LivingEntity> candidates;
      if (village != null) {
         candidates = hostiles(level, village, v);
      } else {
         AABB box = v.getBoundingBox().inflate(range);
         candidates = level.getEntitiesOfClass(LivingEntity.class, box, CombatSenseCache::isHostileCandidate);
      }
      for (LivingEntity e : candidates) {
         if (e == v || !e.isAlive()) {
            continue;
         }
         double d = v.distanceToSqr(e);
         if (d < bestSq && d <= rangeSq) {
            bestSq = d;
            best = e;
         }
      }
      return best;
   }

   @Nullable
   public static MillVillager findClosestVillager(MillVillager v, double radius, Predicate<MillVillager> isOpponent) {
      if (!(v.level() instanceof ServerLevel level)) {
         return null;
      }
      VillageId villageId = v.getVillageId();
      Village village = villageId != null ? Village.resolve(level, villageId) : null;
      double bestSq = radius * radius;
      MillVillager best = null;
      Iterable<MillVillager> candidates = village != null
         ? nearbyVillagers(level, village)
         : level.getEntitiesOfClass(MillVillager.class, v.getBoundingBox().inflate(radius));
      for (MillVillager other : candidates) {
         if (other != v && other.isAlive() && isOpponent.test(other)) {
            double d = v.distanceToSqr(other);
            if (d < bestSq) {
               bestSq = d;
               best = other;
            }
         }
      }
      return best;
   }

   private static boolean isHostileCandidate(LivingEntity e) {
      if (!e.isAlive()) {
         return false;
      }
      if (e instanceof Player p) {
         return p instanceof ServerPlayer && CombatHelper.isPlayerTargetable(p);
      }
      if (e instanceof MillVillager mv) {
         VillagerType t = ModCultures.getVillagerType(mv.getVillagerTypeId());
         return t != null && !t.isHostile();
      }
      return false;
   }

   public static String diagnostic() {
      return "sense={refresh=" + refreshCount + ",hits=" + cacheHits + '}';
   }

   public static void resetForTest() {
      refreshCount = 0;
      cacheHits = 0;
   }
}
