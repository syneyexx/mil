package org.millenaire.goal.impl;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.war.WarBuildingDamageService;
import org.millenaire.combat.war.WarCampaign;
import org.millenaire.combat.war.WarCampaignService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;

/** Raiders walk to a sabotage target building and request budgeted war damage. */
public class WarSabotageGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "war_sabotage");
   private static final double ARRIVE_SQ = 9.0;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 10020;
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
   public boolean canStart(GoalContext context) {
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return false;
      }
      if (!MillenaireServerConfig.SERVER.warBuildingDamageEnabled.getAsBoolean()) {
         return false;
      }
      MillVillager v = context.villager();
      if (!v.isRaiderEntity()) {
         return false;
      }
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (type == null || !type.isRaider()) {
         return false;
      }
      Village village = context.village();
      if (village == null || !village.isUnderAttack()) {
         return false;
      }
      if (!(v.level() instanceof ServerLevel level)) {
         return false;
      }
      WarCampaign campaign = WarCampaignService.activeCampaignForVillage(level, village);
      if (campaign == null || campaign.phase() != WarCampaign.Phase.WAVE_FIGHT) {
         return false;
      }
      return WarCampaignService.resolveSabotageTarget(village, campaign) != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new WarSabotageTask();
   }

   static class WarSabotageTask extends ProgressAwareTask {
      @Nullable
      private BlockPos targetPos;
      @Nullable
      private BuildingInstance targetBuilding;
      private boolean finished;
      private int damageCooldown;

      @Override
      public ResourceLocation goalId() {
         return WarSabotageGoal.ID;
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
         if (!(v.level() instanceof ServerLevel level)) {
            this.finished = true;
            return;
         }
         Village village = ctx.village();
         if (village == null || !village.isUnderAttack()) {
            this.finished = true;
            v.getNavManager().stop(v);
            return;
         }
         WarCampaign campaign = WarCampaignService.activeCampaignForVillage(level, village);
         if (campaign == null || campaign.phase() != WarCampaign.Phase.WAVE_FIGHT) {
            this.finished = true;
            v.getNavManager().stop(v);
            return;
         }
         if (this.targetBuilding == null || !this.targetBuilding.isOperational()) {
            this.targetBuilding = WarCampaignService.resolveSabotageTarget(village, campaign);
            if (this.targetBuilding == null) {
               this.finished = true;
               return;
            }
            BlockPos origin = this.targetBuilding.getOrigin();
            this.targetPos = origin != null ? origin : village.getCenter();
         }
         if (this.targetPos == null) {
            this.finished = true;
            return;
         }
         double distSq = v.blockPosition().distSqr(this.targetPos);
         if (distSq > ARRIVE_SQ) {
            v.getNavManager().navigateTo(v, this.targetPos, 0.55);
            return;
         }
         v.getNavManager().stop(v);
         if (this.damageCooldown > 0) {
            this.damageCooldown--;
            return;
         }
         WarBuildingDamageService.requestDamage(village, this.targetBuilding, campaign.id(), campaign.activeWaveId(), 4);
         this.reportProgress();
         this.damageCooldown = 40;
         // Move on after a successful request so multiple buildings get hit.
         campaign.sabotageTargets().remove(this.targetBuilding.getId().uuid());
         this.targetBuilding = null;
         this.targetPos = null;
         if (campaign.sabotageTargets().isEmpty()) {
            this.finished = true;
         }
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }
   }
}
