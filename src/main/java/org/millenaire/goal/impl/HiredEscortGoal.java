package org.millenaire.goal.impl;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.NavigationHelperUtils;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.hire.HiringHelper;
import org.millenaire.village.Village;

public class HiredEscortGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "hired_escort");

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext c) {
      return 10000;
   }

   @Override
   public boolean isCombatUrgent() {
      return true;
   }

   @Override
   public boolean canBeDoneAtNight() {
      return true;
   }

   @Override
   public boolean canStart(GoalContext c) {
      return c.villager().isHired();
   }

   @Override
   public VillagerTask start(GoalContext c) {
      return new HiredEscortGoal.HiredEscortTask();
   }

   static boolean isOpponent(boolean targetsOwner, boolean isHostileMonster, boolean isHostileVillager, boolean isCreeper, boolean isOwner, boolean aggressive) {
      if (isOwner || isCreeper) {
         return false;
      } else {
         return targetsOwner ? true : aggressive && (isHostileMonster || isHostileVillager);
      }
   }

   private static void followOwner(MillVillager v, ServerPlayer owner) {
      double dist = Math.sqrt(v.distanceToSqr(owner));
      if (dist > 16.0) {
         NavigationHelperUtils.teleportToSafe(v, owner.blockPosition());
      } else if (dist > 4.0) {
         v.getNavManager().navigateTo(v, owner.blockPosition(), 0.5);
      } else {
         v.getNavManager().stop(v);
      }
   }

   private static void selfHeal(MillVillager v) {
      if (v.getHealth() < v.getMaxHealth() && v.getRandom().nextInt(1600) == 0) {
         v.heal(1.0F);
      }
   }

   static void endHire(MillVillager v, ServerLevel level, String msgKey) {
      UUID owner = v.getHiredBy();
      Village village = v.getVillageId() == null ? null : Village.resolve(level, v.getVillageId());
      if (village != null) {
         village.setVillagerHired(level, v.getUUID(), null, 0L);
      } else {
         v.setHireState(null, 0L);
      }

      if (owner != null) {
         ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
         if (p != null) {
            p.sendSystemMessage(Component.translatable(msgKey, new Object[]{v.getName()}));
         }
      }
   }

   @Nullable
   private static LivingEntity acquireOpponent(MillVillager v, ServerPlayer owner, boolean aggressive) {
      AABB box = new AABB(owner.blockPosition()).inflate(16.0, 8.0, 16.0);
      LivingEntity best = null;
      double bestSq = 6400.0;

      for (LivingEntity e : v.level().getEntitiesOfClass(LivingEntity.class, box)) {
         if (e != v && e != owner && e.isAlive()) {
            boolean targetsOwner = e instanceof Mob m && m.getTarget() == owner;
            boolean monster = e instanceof Monster && !(e instanceof Creeper);
            boolean hostileVillager = e instanceof MillVillager mv && isHostileVillager(mv);
            if (isOpponent(targetsOwner, monster, hostileVillager, e instanceof Creeper, false, aggressive)) {
               double d = v.distanceToSqr(e);
               if (d < bestSq) {
                  bestSq = d;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   private static boolean isHostileVillager(MillVillager mv) {
      VillagerType type = ModCultures.getVillagerType(mv.getVillagerTypeId());
      return type != null && type.isHostile();
   }

   @Nullable
   private static LivingEntity retainValidTarget(MillVillager v, ServerPlayer owner, ServerLevel level) {
      LivingEntity cur = v.getAttackTarget();
      if (cur == null || !cur.isAlive()) {
         return null;
      }

      if (cur != owner && !(cur instanceof Creeper)) {
         if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return null;
         } else {
            return v.distanceToSqr(cur) > 6400.0 ? null : cur;
         }
      } else {
         return null;
      }
   }

   static class HiredEscortTask extends ProgressAwareTask {
      private boolean finished;

      @Override
      public ResourceLocation goalId() {
         return HiredEscortGoal.ID;
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return List.of();
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         if (v.level() instanceof ServerLevel level) {
            if (!v.isHired()) {
               this.finished = true;
            } else {
               long now = level.getGameTime();
               if (HiringHelper.isExpired(v.getHiredUntil(), now)) {
                  HiredEscortGoal.endHire(v, level, "message.millenaire.hire.hireover");
                  this.finished = true;
               } else {
                  ServerPlayer owner = level.getServer().getPlayerList().getPlayer(v.getHiredBy());
                  if (owner != null && owner.level() == level) {
                     HiredEscortGoal.selfHeal(v);
                     v.setHireHoursLeft(HiringHelper.hoursLeft(v.getHiredUntil(), now));
                     LivingEntity target = HiredEscortGoal.retainValidTarget(v, owner, level);
                     if (target == null) {
                        target = HiredEscortGoal.acquireOpponent(v, owner, v.isAggressiveStance());
                     }

                     if (target != null) {
                        v.setAttackTarget(target);
                        if (CombatGoalSupport.pursueAndAttack(v, target, 0.5, 4.0)) {
                           this.reportProgress();
                        }
                     } else {
                        v.setAttackTarget(null);
                        HiredEscortGoal.followOwner(v, owner);
                     }
                  } else {
                     v.getNavManager().stop(v);
                  }
               }
            }
         }
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            ctx.villager().setAttackTarget(null);
         }
      }
   }
}
