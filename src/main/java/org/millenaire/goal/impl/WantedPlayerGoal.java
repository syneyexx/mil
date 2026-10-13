package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.millenaire.combat.CombatHelper;
import org.millenaire.crime.PlayerBountySavedData;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class WantedPlayerGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "wanted_player");
   private static final double RANGE = 48.0;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 4500;
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
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public boolean canStart(GoalContext context) {
      MillVillager v = context.villager();
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (type == null || !type.isHelpInAttacks() || !PlayerBountySavedData.crimeEnabled()) {
         return false;
      }

      return findWantedPlayer(v) != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new WantedPlayerGoal.WantedPlayerTask();
   }

   @Nullable
   static ServerPlayer findWantedPlayer(MillVillager v) {
      if (!(v.level() instanceof ServerLevel level)) {
         return null;
      }

      ResourceLocation cultureId = v.getCultureId();
      if (cultureId == null) {
         return null;
      }

      PlayerBountySavedData bounty = PlayerBountySavedData.get(level);
      double rangeSq = RANGE * RANGE;
      double bestSq = rangeSq + 1.0;
      ServerPlayer best = null;
      AABB box = v.getBoundingBox().inflate(RANGE);

      for (Player player : level.getEntitiesOfClass(Player.class, box)) {
         if (!(player instanceof ServerPlayer sp) || !CombatHelper.isPlayerTargetable(sp.isCreative(), sp.isSpectator())) {
            continue;
         }

         if (!bounty.isHostile(sp.getUUID(), cultureId)) {
            continue;
         }

         double d = v.distanceToSqr(sp);
         if (d < bestSq) {
            bestSq = d;
            best = sp;
         }
      }

      return best;
   }

   static class WantedPlayerTask extends AbstractCombatTask {
      @Nullable
      private LivingEntity target;
      private boolean done;

      @Override
      public ResourceLocation goalId() {
         return WantedPlayerGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         if (!(this.target instanceof ServerPlayer sp) || !sp.isAlive() || !CombatHelper.isPlayerTargetable(sp.isCreative(), sp.isSpectator())) {
            this.target = WantedPlayerGoal.findWantedPlayer(v);
            if (this.target == null) {
               this.done = true;
               return;
            }
         }

         v.setAttackTarget(this.target);
         if (v.distanceToSqr(this.target) > RANGE * RANGE) {
            this.done = true;
         } else if (CombatGoalSupport.pursueAndAttack(v, this.target, 0.7, 4.0)) {
            this.reportProgress();
         }
      }

      @Override
      public boolean isFinished() {
         return this.done;
      }
   }
}
