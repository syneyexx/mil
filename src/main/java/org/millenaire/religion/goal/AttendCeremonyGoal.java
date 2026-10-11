package org.millenaire.religion.goal;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.goal.impl.WalkAndWaitTask;
import org.millenaire.religion.ReligionService;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.faith.NpcFaithService;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.CeremonyState;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.village.VillagerRecord;

/**
 * Walk to an active ceremony site and participate. Priority is elevated during GATHERING/ACTIVE.
 */
public final class AttendCeremonyGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "attend_ceremony");

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public boolean isLeisure() {
      return true;
   }

   @Override
   public int computePriority(GoalContext context) {
      CeremonyInstance ceremony = active(context);
      if (ceremony == null) {
         return Integer.MIN_VALUE;
      }
      RitualDefinition ritual = ReligionContentRegistry.ritualOrNull(ceremony.ritualId());
      int base = ritual != null ? ritual.priority() : 50;
      if (ceremony.state() == CeremonyState.GATHERING || ceremony.state() == CeremonyState.ACTIVE) {
         return base + 80;
      }
      return base;
   }

   @Override
   public boolean canStart(GoalContext context) {
      CeremonyInstance ceremony = active(context);
      if (ceremony == null) {
         return false;
      }
      if (ceremony.state() != CeremonyState.GATHERING && ceremony.state() != CeremonyState.ACTIVE) {
         return false;
      }
      VillagerRecord record = context.village().getVillagerRecords().get(context.villager().getUUID());
      if (record == null || record.isKilled()) {
         return false;
      }
      NpcFaithProfile profile = NpcFaithService.ensureInitialized(context.level(), context.village(), record);
      if (!profile.hasTradition(ceremony.traditionId())) {
         return false;
      }
      BuildingInstance building = resolveBuilding(context, ceremony);
      return building != null && building.isOperational();
   }

   @Override
   public VillagerTask start(GoalContext context) {
      CeremonyInstance ceremony = active(context);
      BuildingInstance building = resolveBuilding(context, ceremony);
      BlockPos target = offsetSlot(context, building, ceremony);
      return new AttendTask(target, building, ceremony != null ? ceremony.ceremonyId().toString() : "ceremony");
   }

   @Nullable
   private static CeremonyInstance active(GoalContext context) {
      return ReligionService.get().ceremonies().activeCeremony(context.level(), context.village());
   }

   @Nullable
   private static BuildingInstance resolveBuilding(GoalContext context, @Nullable CeremonyInstance ceremony) {
      if (ceremony == null || ceremony.buildingId() == null) {
         return null;
      }
      return context.village().getBuilding(ceremony.buildingId());
   }

   /**
    * Spread participants around the building so they do not stack on one block.
    */
   @Nullable
   private static BlockPos offsetSlot(GoalContext context, @Nullable BuildingInstance building, @Nullable CeremonyInstance ceremony) {
      if (building == null) {
         return null;
      }
      BlockPos base = building.getFirstPointPos("craftingPos");
      if (base == null) {
         base = building.getOrigin();
      }
      int slot = Math.floorMod(context.villager().getUUID().hashCode(), 9);
      int dx = (slot % 3) - 1;
      int dz = (slot / 3) - 1;
      if (dx == 0 && dz == 0) {
         dx = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
      }
      return base.offset(dx, 0, dz);
   }

   static final class AttendTask extends WalkAndWaitTask {
      private final String ceremonyKey;

      AttendTask(@Nullable BlockPos target, @Nullable BuildingInstance building, String ceremonyKey) {
         super(target, 800);
         this.ceremonyKey = ceremonyKey;
      }

      @Override
      public ResourceLocation goalId() {
         return AttendCeremonyGoal.ID;
      }

      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "attend_ceremony");
      }

      @Override
      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }
   }
}
