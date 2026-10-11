package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.millenaire.combat.CombatHelper;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.faction.crime.CultureBountySavedData;
import org.millenaire.faction.crime.WantedTier;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;

/**
 * Guards ({@code helpInAttacks}) pursue players wanted by their culture on sight.
 */
public class PursueWantedPlayerGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "pursue_wanted");
   private static final double ACQUIRE_RADIUS = 40.0;
   private static final double WALK_SPEED = 0.75;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 9500;
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
      if (!isOnSightEnabled()) {
         return false;
      }
      MillVillager v = context.villager();
      if (v.isRaiderEntity() || v.getHiredBy() != null) {
         return false;
      }
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (type == null || !type.isHelpInAttacks()) {
         return false;
      }
      Village village = context.village();
      if (village == null || village.getCultureId() == null) {
         return false;
      }
      return findWantedPlayer(v, village) != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new PursueWantedTask();
   }

   @Nullable
   static ServerPlayer findWantedPlayer(MillVillager villager, Village village) {
      if (!(villager.level() instanceof ServerLevel level)) {
         return null;
      }
      ResourceLocation cultureId = village.getCultureId();
      if (cultureId == null) {
         return null;
      }
      WantedTier minTier = onSightTier();
      AABB box = villager.getBoundingBox().inflate(ACQUIRE_RADIUS);
      ServerPlayer best = null;
      double bestSq = ACQUIRE_RADIUS * ACQUIRE_RADIUS;
      for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
         if (!CombatHelper.isPlayerTargetable(player)) {
            continue;
         }
         if (village.isControlledBy(player.getUUID())) {
            continue;
         }
         WantedTier tier = CultureBountySavedData.get(level).getManager().getTier(player.getUUID(), cultureId);
         if (!tier.isAtLeast(minTier)) {
            continue;
         }
         double d = villager.distanceToSqr(player);
         if (d < bestSq) {
            bestSq = d;
            best = player;
         }
      }
      return best;
   }

   static boolean isOnSightEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyEnabled.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyOnSightEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   static WantedTier onSightTier() {
      try {
         return WantedTier.tryParse(MillenaireServerConfig.SERVER.cultureBountyOnSightTier.get());
      } catch (IllegalStateException e) {
         return WantedTier.WANTED;
      }
   }

   static class PursueWantedTask extends AbstractCombatTask {
      private boolean finished;

      @Override
      public ResourceLocation goalId() {
         return PursueWantedPlayerGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         Village village = ctx.village();
         if (village == null || !isOnSightEnabled()) {
            this.finished = true;
            v.getNavManager().stop(v);
            return;
         }
         LivingEntity current = v.getAttackTarget();
         ServerPlayer wanted = null;
         if (current instanceof ServerPlayer sp
            && CombatHelper.isPlayerTargetable(sp)
            && CultureBountySavedData.get((ServerLevel) v.level())
               .getManager()
               .getTier(sp.getUUID(), village.getCultureId())
               .isAtLeast(onSightTier())) {
            wanted = sp;
         } else {
            wanted = findWantedPlayer(v, village);
         }
         if (wanted == null) {
            this.finished = true;
            if (current instanceof ServerPlayer) {
               v.setAttackTarget(null);
            }
            v.getNavManager().stop(v);
            return;
         }
         if (v.getAttackTarget() != wanted) {
            v.setAttackTarget(wanted);
            CombatHelper.callForHelp(v, wanted);
         }
         if (CombatGoalSupport.pursueAndAttack(v, wanted, WALK_SPEED, 4.0)) {
            this.reportProgress();
         }
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }
   }
}
