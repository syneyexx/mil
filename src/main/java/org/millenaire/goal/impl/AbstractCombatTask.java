package org.millenaire.goal.impl;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;

abstract class AbstractCombatTask extends ProgressAwareTask {
   @Nullable
   protected MillVillager ctxVillager;

   @Override
   public List<ItemStack> getHeldItems(TravelPhase phase) {
      MillVillager v = this.ctxVillager;
      return v == null ? List.of() : List.of(v.getCombatWeapon());
   }

   protected boolean raidStillValid(@Nullable GoalContext ctx) {
      return ctx != null && ctx.village() != null && ctx.village().isUnderAttack();
   }

   @Override
   public void stop(GoalContext ctx, StopReason reason) {
      if (ctx != null) {
         ctx.villager().getNavManager().stop(ctx.villager());
      }
   }
}
