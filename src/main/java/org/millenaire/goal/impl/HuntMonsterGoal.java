package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.PerVillageTickCache;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;
import org.millenaire.world.TerrainPreparer;

public class HuntMonsterGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "hunt_monster");
   private static final int PRIORITY = 50;
   private static final double SCAN_RADIUS = 50.0;
   private static final double SCAN_VERTICAL = 10.0;
   private static final double ATTACK_RANGE_SQ = 16.0;
   private static final int UNDERGROUND_MARGIN = 2;
   private static final int SCAN_REFRESH_TICKS = 10;
   private static final PerVillageTickCache<List<LivingEntity>> SCAN_CACHE = new PerVillageTickCache<>(10);

   public static boolean isHuntableEntity(LivingEntity entity) {
      if (entity == null || !entity.isAlive()) {
         return false;
      } else if (entity instanceof Player) {
         return false;
      } else if (entity instanceof Creeper) {
         return false;
      } else if (!(entity instanceof MillVillager villager)) {
         return entity instanceof Monster;
      } else {
         VillagerType type = ModCultures.getVillagerType(villager.getVillagerTypeId());
         return type != null && type.isHostile();
      }
   }

   public static boolean isHuntableBy(LivingEntity target, MillVillager hunter) {
      if (target == hunter) {
         return false;
      }

      if (!isHuntableEntity(target)) {
         return false;
      }

      if (!(target instanceof MillVillager)) {
         return true;
      }

      VillagerType hunterType = ModCultures.getVillagerType(hunter.getVillagerTypeId());
      return hunterType == null || !hunterType.isHostile();
   }

   static boolean isAtOrAboveSurface(double entityY, int surfaceY) {
      return entityY >= surfaceY - 2;
   }

   static boolean isAboveGround(LivingEntity e) {
      if (e.level() instanceof ServerLevel level) {
         int var3 = TerrainPreparer.getGroundHeight(level, e.getBlockX(), e.getBlockZ());
         return isAtOrAboveSurface(e.getY(), var3);
      } else {
         return true;
      }
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 50;
   }

   @Override
   public boolean canBeDoneAtNight() {
      return true;
   }

   @Override
   public boolean isCombatUrgent() {
      return true;
   }

   @Override
   public boolean canStart(GoalContext context) {
      MillVillager v = context.villager();
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (type == null || !type.isHelpInAttacks()) {
         return false;
      } else {
         return context.village() == null ? false : findClosestHuntable(context) != null;
      }
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new HuntMonsterGoal.HuntMonsterTask(findClosestHuntable(context));
   }

   @Nullable
   static LivingEntity findClosestHuntable(GoalContext context) {
      Village village = context.village();
      long now = context.villager().level().getGameTime();
      BuildingInstance townhall = village.getTownhall();
      List<LivingEntity> candidates;
      if (townhall != null) {
         BlockPos center = townhall.getOrigin();
         candidates = SCAN_CACHE.get(village.getId().uuid(), now, () -> scanHuntables(context.villager().level(), center));
      } else {
         candidates = scanHuntables(context.villager().level(), context.villager().blockPosition());
      }

      LivingEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (LivingEntity e : candidates) {
         if (isHuntableBy(e, context.villager()) && !village.isHuntUnreachable(e.blockPosition(), now)) {
            double d = context.villager().distanceToSqr(e);
            if (d < bestSq) {
               bestSq = d;
               best = e;
            }
         }
      }

      return best;
   }

   private static List<LivingEntity> scanHuntables(Level level, BlockPos center) {
      AABB box = new AABB(center).inflate(50.0, 10.0, 50.0);
      List<LivingEntity> result = new ArrayList<>();

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, HuntMonsterGoal::isHuntableEntity)) {
         if (isAboveGround(e)) {
            result.add(e);
         }
      }

      return result;
   }

   static class HuntMonsterTask implements VillagerTask {
      private static final double WALK_SPEED = 0.65;
      private static final int MAX_TICKS = 600;
      @Nullable
      private LivingEntity target;
      @Nullable
      private MillVillager ctxVillager;
      private int ticks;

      HuntMonsterTask(@Nullable LivingEntity initial) {
         this.target = initial;
      }

      @Override
      public ResourceLocation goalId() {
         return HuntMonsterGoal.ID;
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         MillVillager v = this.ctxVillager;
         return v == null ? List.of() : List.of(v.getCombatWeapon());
      }

      @Override
      public void tick(GoalContext ctx) {
         this.ticks++;
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         if (this.target == null || !HuntMonsterGoal.isHuntableBy(this.target, v) || this.ticks % 10 == 0) {
            this.target = HuntMonsterGoal.findClosestHuntable(ctx);
            if (this.target == null) {
               return;
            }
         }

         v.setAttackTarget(this.target);
         CombatGoalSupport.pursueAndAttack(v, this.target, 0.65, 16.0);
      }

      @Override
      public boolean isFinished() {
         return this.ticks > 600 || this.target == null || !this.target.isAlive();
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            if (reason != StopReason.INTERRUPTED) {
               ctx.villager().setAttackTarget(null);
            }
         }
      }
   }
}
