package org.millenaire.goal.impl;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.millenaire.combat.war.WarCampaign;
import org.millenaire.combat.war.WarCampaignService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;

/**
 * War-muster escort: hired fighters escort the player during an active war campaign.
 * Falls back to the same follow/fight behaviour as {@link HiredEscortGoal}.
 */
public class WarEscortGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "war_escort");

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public int computePriority(GoalContext c) {
      return 10050;
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
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean()) {
         return false;
      }
      MillVillager v = c.villager();
      if (!v.isHired()) {
         return false;
      }
      if (!(v.level() instanceof ServerLevel level)) {
         return false;
      }
      Village village = c.village();
      if (village == null) {
         return false;
      }
      WarCampaign campaign = WarCampaignService.activeCampaignForVillage(level, village);
      return campaign != null && campaign.isOpen() && campaign.phase() != WarCampaign.Phase.ENDED;
   }

   @Override
   public VillagerTask start(GoalContext c) {
      return new WarEscortTask();
   }

   static class WarEscortTask extends ProgressAwareTask {
      private final HiredEscortGoal.HiredEscortTask delegate = new HiredEscortGoal.HiredEscortTask();

      @Override
      public ResourceLocation goalId() {
         return WarEscortGoal.ID;
      }

      @Override
      public boolean isFinished() {
         return this.delegate.isFinished();
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return this.delegate.getHeldItems(phase);
      }

      @Override
      public void tick(GoalContext ctx) {
         this.delegate.tick(ctx);
         if (this.delegate.consumeProgress()) {
            this.reportProgress();
         }
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         this.delegate.stop(ctx, reason);
      }
   }
}
