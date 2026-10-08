package org.millenaire.goal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalScheduler;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.visit.VisitGoalSchema;

public class PlayGoal implements VillagerGoal {
   private final ResourceLocation id;
   private final boolean withFriends;
   private final long reoccurDelay;

   public PlayGoal(boolean withFriends) {
      this.withFriends = withFriends;
      this.id = ResourceLocation.fromNamespaceAndPath("millenaire", withFriends ? "play_with_friends" : "play");
      this.reoccurDelay = withFriends ? 1200L : 600L;
   }

   public static PlayGoal fromSchema(VisitGoalSchema.Play s) {
      return new PlayGoal(s.withFriends());
   }

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public ResourceLocation id() {
      return this.id;
   }

   @Override
   public boolean isLeisure() {
      return true;
   }

   @Override
   public long reoccurDelayTicks() {
      return this.reoccurDelay;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 5 + ThreadLocalRandom.current().nextInt(10);
   }

   @Override
   public boolean canStart(GoalContext context) {
      return this.withFriends ? this.findPlayingFriend(context) != null : this.findLeisurePos(context) != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      if (this.withFriends) {
         MillVillager friend = this.findPlayingFriend(context);
         return new PlayGoal.PlayWithFriendTask(friend);
      } else {
         BlockPos target = this.findLeisurePos(context);
         return new PlayGoal.PlayTask(target);
      }
   }

   @Nullable
   private MillVillager findPlayingFriend(GoalContext ctx) {
      List<MillVillager> candidates = new ArrayList<>();

      for (UUID uuid : ctx.village().getVillagerUuids()) {
         if (ctx.level().getEntity(uuid) instanceof MillVillager other && other != ctx.villager() && isStillPlaying(other)) {
            candidates.add(other);
         }
      }

      return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   static boolean isStillPlaying(MillVillager other) {
      if (!other.isAlive()) {
         return false;
      }

      GoalScheduler scheduler = other.getGoalScheduler();
      if (scheduler == null) {
         return false;
      }

      ResourceLocation otherGoalId = scheduler.getCurrentGoalId();
      if (otherGoalId == null) {
         return false;
      }

      String path = otherGoalId.getPath();
      return "play".equals(path) || "play_with_friends".equals(path);
   }

   @Nullable
   private BlockPos findLeisurePos(GoalContext ctx) {
      List<BuildingInstance> leisureBuildings = ctx.village().getOperationalBuildingsWithTag("leisure");
      if (leisureBuildings.isEmpty()) {
         return null;
      }

      BuildingInstance chosen = leisureBuildings.get(ThreadLocalRandom.current().nextInt(leisureBuildings.size()));
      BlockPos basePos = chosen.resolveNavigationTarget("leisurePos", "sellingPos", "sleepingPos");
      return SocialiseGoal.findRandomSafePosition(ctx.level(), basePos);
   }

   static class PlayTask extends WalkAndWaitTask {
      private static final int MAX_WAIT_TICKS = 200;

      PlayTask(@Nullable BlockPos target) {
         super(target, 200);
      }

      @Override
      protected boolean allowRandomMoves() {
         return true;
      }

      @Override
      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", "play");
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return List.of(new ItemStack(Items.STICK));
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "play");
      }
   }

   static class PlayWithFriendTask implements VillagerTask {
      private static final double WALK_SPEED = 0.5;
      private static final int MAX_WAIT_TICKS = 200;
      @Nullable
      private final MillVillager friend;
      private boolean arrived;
      private int waitTicks;

      PlayWithFriendTask(@Nullable MillVillager friend) {
         this.friend = friend;
         if (friend == null) {
            this.arrived = true;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return ResourceLocation.fromNamespaceAndPath("millenaire", "play_with_friends");
      }

      static boolean shouldAbort(boolean hasFriend, boolean friendStillPlaying) {
         return hasFriend && !friendStillPlaying;
      }

      @Override
      public void tick(GoalContext ctx) {
         if (shouldAbort(this.friend != null, this.friend != null && PlayGoal.isStillPlaying(this.friend))) {
            this.arrived = true;
            this.waitTicks = 200;
         } else {
            if (!this.arrived && this.friend != null) {
               VillagerNavDriver nav = ctx.villager().getNavManager();
               nav.navigateTo(ctx.villager(), this.friend.blockPosition(), 0.5);
               if (nav.isArrived(ctx.villager(), 4.0)) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
                  ctx.villager().getLookControl().setLookAt(this.friend);
               } else if (nav.isAbandoned()) {
                  this.arrived = true;
                  nav.stop(ctx.villager());
               }
            }

            if (this.arrived) {
               this.waitTicks++;
               if (this.friend != null && this.friend.isAlive()) {
                  ctx.villager().getLookControl().setLookAt(this.friend);
               }
            }
         }
      }

      @Override
      public boolean isFinished() {
         return this.arrived && this.waitTicks >= 200;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return List.of(new ItemStack(Items.STICK));
      }

      @Override
      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "play");
      }
   }
}
