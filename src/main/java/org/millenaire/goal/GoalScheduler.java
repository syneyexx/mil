package org.millenaire.goal;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.diagnostics.NavEvent;
import org.millenaire.diagnostics.NavigationCounters;
import org.millenaire.entity.MillVillager;
import org.millenaire.perf.sense.CombatSenseCache;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class GoalScheduler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_TASK_TICKS = 6000;
   private static final int HARD_MAX_TASK_TICKS = 24000;
   private static final int MAX_IDLE_BACKOFF = 20;
   private static final int LEISURE_RECHECK_INTERVAL = 10;
   private final List<VillagerGoal> goals;
   private final Map<ResourceLocation, Integer> goalIndex = new HashMap<>();
   /** Parallel to {@link #goals}; {@link Long#MIN_VALUE} means never started. */
   private final long[] lastGoalTick;
   @Nullable
   private VillagerGoal currentGoal;
   @Nullable
   private VillagerTask currentTask;
   private int taskTicks;
   private int taskAgeTicks;
   @Nullable
   private VillagerTask lastTickedTask;
   private int idleBackoff = 0;
   private int idleBackoffDelay = 1;
   private int leisureRecheckCooldown = 0;

   public int getTaskTicks() {
      return this.taskTicks;
   }

   public int getMaxTaskTicks() {
      return 6000;
   }

   public GoalScheduler(List<VillagerGoal> goals) {
      this.goals = goals;
      this.lastGoalTick = new long[goals.size()];
      Arrays.fill(this.lastGoalTick, Long.MIN_VALUE);
      for (int i = 0; i < goals.size(); i++) {
         this.goalIndex.put(goals.get(i).id(), i);
      }
   }

   public void tick(@Nullable GoalContext ctx) {
      this.tick(ctx, true, true);
   }

   /**
    * @param allowGoalPick when false, skip idle goal selection and leisure rechecks;
    *        current task still runs every tick.
    */
   public void tick(@Nullable GoalContext ctx, boolean allowGoalPick) {
      this.tick(ctx, allowGoalPick, true);
   }

   /**
    * @param allowCombatScan when false, skip combat-urgent canStart preemption scans.
    */
   public void tick(@Nullable GoalContext ctx, boolean allowGoalPick, boolean allowCombatScan) {
      try {
         this.tickInternal(ctx, allowGoalPick, allowCombatScan);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Exception in GoalScheduler.tick() — goal={}, task={} — forced reset",
            new Object[]{this.currentGoal != null ? this.currentGoal.id() : "null", this.currentTask != null ? this.currentTask.goalId() : "null", e}
         );

         try {
            if (this.currentTask != null) {
               this.currentTask.stop(ctx, StopReason.INTERRUPTED);
            }

            if (ctx != null) {
               ctx.villager().getNavManager().stop(ctx.villager());
            }
         } catch (Exception cleanupError) {
            LOGGER.error("[Millenaire] Exception during forced task cleanup", cleanupError);
         }

         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
         this.taskAgeTicks = 0;
         this.lastTickedTask = null;
      }
   }

   private void tickInternal(@Nullable GoalContext ctx, boolean allowGoalPick, boolean allowCombatScan) {
      if (this.currentTask != null && this.currentTask.isFinished()) {
         this.currentTask.stop(ctx, StopReason.COMPLETED);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
         this.resetIdleBackoff();
      }

      if (this.currentTask != null && this.currentGoal != null && !isTimeAllowed(this.currentGoal, ctx)) {
         this.currentTask.stop(ctx, StopReason.INTERRUPTED);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
         this.resetIdleBackoff();
      }

      if (allowGoalPick) {
         if (this.currentTask != null && this.currentGoal != null && this.currentGoal.isLeisure() && this.leisureRecheckCooldown > 0) {
            this.leisureRecheckCooldown--;
         } else if (this.currentTask != null && this.currentGoal != null && this.currentGoal.isLeisure()) {
            this.leisureRecheckCooldown = 9;

            for (int i = 0; i < this.goals.size(); i++) {
               VillagerGoal goal = this.goals.get(i);
               if (!goal.isLeisure()
                  && isTimeAllowed(goal, ctx)
                  && this.isCooldownExpired(i, goal.reoccurDelayTicks(), ctx != null ? ctx.gameTime() : 0L)
                  && goal.canStart(ctx)) {
                  this.currentTask.stop(ctx, StopReason.INTERRUPTED);
                  if (ctx != null) {
                     ctx.villager().getNavManager().stop(ctx.villager());
                  }

                  this.startGoal(goal, i, ctx);
                  break;
               }
            }
         }
      } else if (this.currentTask != null && this.currentGoal != null && this.currentGoal.isLeisure() && this.leisureRecheckCooldown > 0) {
         this.leisureRecheckCooldown--;
      }

      boolean doCombatScan = allowCombatScan && this.currentTask != null && this.currentGoal != null;
      if (doCombatScan && this.idleBackoff > 0 && !combatPreemptRequired(ctx)) {
         // No nearby hostiles and not recently hurt — reuse idle backoff for combat-urgent scans.
         doCombatScan = false;
      }
      if (doCombatScan) {
         boolean currentIsCombat = this.currentGoal.isCombatUrgent();
         int currentPriority = currentIsCombat ? this.currentGoal.computePriority(ctx) : Integer.MIN_VALUE;

         for (int i = 0; i < this.goals.size(); i++) {
            VillagerGoal goal = this.goals.get(i);
            if (goal.isCombatUrgent()
               && goal != this.currentGoal
               && (!currentIsCombat || goal.computePriority(ctx) > currentPriority)
               && isTimeAllowed(goal, ctx)
               && this.isCooldownExpired(i, goal.reoccurDelayTicks(), ctx != null ? ctx.gameTime() : 0L)
               && goal.canStart(ctx)) {
               this.currentTask.stop(ctx, StopReason.INTERRUPTED);
               if (ctx != null) {
                  ctx.villager().getNavManager().stop(ctx.villager());
               }

               this.startGoal(goal, i, ctx);
               break;
            }
         }
      }

      if (this.currentTask == null) {
         if (this.idleBackoff > 0) {
            this.idleBackoff--;
         } else if (allowGoalPick) {
            GoalScheduler.BestGoal best = this.pickBest(ctx);
            if (best != null) {
               this.startGoal(best.goal(), best.index(), ctx);
            } else {
               this.idleBackoff = this.idleBackoffDelay;
               this.idleBackoffDelay = Math.min(this.idleBackoffDelay * 2, MAX_IDLE_BACKOFF);
            }
         }
      }

      if (this.currentTask != null) {
         if (this.currentTask != this.lastTickedTask) {
            this.taskAgeTicks = 0;
            this.lastTickedTask = this.currentTask;
         }

         if (this.currentTask.consumeProgress()) {
            this.taskTicks = 0;
         }

         this.taskTicks++;
         this.taskAgeTicks++;
         boolean noProgressTimeout = this.taskTicks > 6000;
         boolean hardCeiling = this.taskAgeTicks > 24000;
         if (!noProgressTimeout && !hardCeiling) {
            this.currentTask.tick(ctx);
         } else {
            LOGGER.warn(
               "Watchdog: {} (taskTicks={}, taskAgeTicks={}), goal={} — forced stop",
               new Object[]{
                  hardCeiling ? "task exceeded absolute run ceiling" : "task without progress",
                  this.taskTicks,
                  this.taskAgeTicks,
                  this.currentGoal != null ? this.currentGoal.id() : "null"
               }
            );
            this.recordAbandon(ctx, hardCeiling ? "hard-ceiling age=" + this.taskAgeTicks : "watchdog ticks=" + this.taskTicks);
            this.currentTask.stop(ctx, StopReason.IMPOSSIBLE);
            if (ctx != null) {
               ctx.villager().getNavManager().stop(ctx.villager());
            }

            this.currentGoal = null;
            this.currentTask = null;
            this.taskTicks = 0;
            this.lastTickedTask = null;
            this.taskAgeTicks = 0;
         }
      }
   }

   private void startGoal(VillagerGoal goal, int index, @Nullable GoalContext ctx) {
      this.currentGoal = goal;
      this.currentTask = goal.start(ctx);
      if (index >= 0 && index < this.lastGoalTick.length) {
         this.lastGoalTick[index] = ctx != null ? ctx.gameTime() : 0L;
      }
      this.taskTicks = 0;
      this.resetIdleBackoff();
   }

   private void resetIdleBackoff() {
      this.idleBackoff = 0;
      this.idleBackoffDelay = 1;
   }

   private boolean isCooldownExpired(int goalIdx, long cooldownTicks, long currentGameTime) {
      if (cooldownTicks <= 0L || goalIdx < 0 || goalIdx >= this.lastGoalTick.length) {
         return true;
      }
      long last = this.lastGoalTick[goalIdx];
      return last == Long.MIN_VALUE || currentGameTime >= last + cooldownTicks;
   }

   /**
    * Combat preemption must not wait on idle backoff when the villager is hurt, already targeting,
    * or the village sense cache reports hostiles.
    */
   private static boolean combatPreemptRequired(@Nullable GoalContext ctx) {
      if (ctx == null) {
         return true;
      }
      MillVillager v = ctx.villager();
      if (v.getAttackTarget() != null || v.hurtTime > 0) {
         return true;
      }
      Village village = ctx.village();
      ServerLevel level = ctx.level();
      if (village == null || level == null) {
         return true;
      }
      if (village.isUnderAttack() || village.isRaidActiveOrSuffering()) {
         return true;
      }
      List<?> hostiles = CombatSenseCache.hostiles(level, village, v);
      return hostiles != null && !hostiles.isEmpty();
   }

   @Nullable
   private GoalScheduler.BestGoal pickBest(@Nullable GoalContext ctx) {
      VillagerGoal best = null;
      int bestIdx = -1;
      int bestPriority = Integer.MIN_VALUE;
      boolean bestIsLeisure = true;

      for (int i = 0; i < this.goals.size(); i++) {
         VillagerGoal goal = this.goals.get(i);
         if (isTimeAllowed(goal, ctx)
            && this.isCooldownExpired(i, goal.reoccurDelayTicks(), ctx != null ? ctx.gameTime() : 0L)
            && goal.canStart(ctx)) {
            int priority = goal.computePriority(ctx);
            boolean isLeisure = goal.isLeisure();
            if (!isLeisure && bestIsLeisure || isLeisure == bestIsLeisure && priority > bestPriority) {
               bestPriority = priority;
               best = goal;
               bestIdx = i;
               bestIsLeisure = isLeisure;
            }
         }
      }

      return best != null ? new GoalScheduler.BestGoal(best, bestIdx, bestPriority) : null;
   }

   private static boolean isTimeAllowed(VillagerGoal goal, @Nullable GoalContext ctx) {
      if (ctx == null) {
         return true;
      }

      boolean isNight = org.millenaire.perf.tick.LevelTickCache.isNight(ctx.level());
      return isNight && !goal.canBeDoneAtNight() ? false : isNight || goal.canBeDoneInDayTime();
   }

   public void forceTask(VillagerTask task, @Nullable GoalContext ctx) {
      if (this.currentTask != null) {
         try {
            this.currentTask.stop(ctx, StopReason.INTERRUPTED);
            if (ctx != null) {
               ctx.villager().getNavManager().stop(ctx.villager());
            }
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Exception in stop() during forceTask — goal={}", this.currentGoal != null ? this.currentGoal.id() : "null", e);
         }
      }

      this.currentGoal = null;
      this.currentTask = task;
      this.taskTicks = 0;
      this.resetIdleBackoff();
   }

   public void forceStop(@Nullable GoalContext ctx) {
      if (this.currentTask != null) {
         this.recordAbandon(ctx, "force-stop");
         this.currentTask.stop(ctx, StopReason.IMPOSSIBLE);
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }

         LOGGER.info("forceStop: forced stop of goal {}", this.currentGoal != null ? this.currentGoal.id() : "null");
         this.currentGoal = null;
         this.currentTask = null;
         this.taskTicks = 0;
      }
   }

   private void recordAbandon(@Nullable GoalContext ctx, String detail) {
      String goalSuffix = " goal=" + (this.currentGoal != null ? this.currentGoal.id() : "forced");
      if (ctx != null) {
         ctx.villager().getNavEventLog().record(ctx.gameTime(), NavEvent.Layer.SCHEDULER, NavEvent.Type.GOAL_ABANDONED, detail + goalSuffix);
      }

      NavigationCounters.incGoalAbandoned();
   }

   @Nullable
   public VillagerGoal getCurrentGoal() {
      return this.currentGoal;
   }

   @Nullable
   public VillagerTask getCurrentTask() {
      return this.currentTask;
   }

   @Nullable
   public ResourceLocation getCurrentGoalId() {
      if (this.currentGoal != null) {
         return this.currentGoal.id();
      } else {
         return this.currentTask != null ? this.currentTask.goalId() : null;
      }
   }

   private record BestGoal(VillagerGoal goal, int index, int priority) {
   }
}
