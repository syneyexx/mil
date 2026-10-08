package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.Millenaire;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalRegistry;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageEventType;
import org.slf4j.Logger;

public class ChildBecomeAdultGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "child_become_adult");

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 100;
   }

   @Override
   public boolean canStart(GoalContext context) {
      MillVillager villager = context.villager();
      VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
      if (vType != null && vType.isChild()) {
         return villager.getChildSize() < 20 ? false : this.findTargetBuilding(context, vType.gender()) != null;
      } else {
         return false;
      }
   }

   @Override
   public VillagerTask start(GoalContext context) {
      MillVillager villager = context.villager();
      VillagerType vType = ModCultures.getVillagerType(villager.getVillagerTypeId());
      Gender gender = vType != null ? vType.gender() : Gender.MALE;
      ChildBecomeAdultGoal.TargetInfo target = this.findTargetBuilding(context, gender);
      if (target == null) {
         return new ChildBecomeAdultGoal.FailedTask();
      }

      String adultType = context.village().reserveSlot(target.buildingId, gender, villager.getUUID());
      if (adultType == null) {
         return new ChildBecomeAdultGoal.FailedTask();
      }

      LOGGER.debug("[Millenaire] Teenager {} reserves a slot {} in {}", new Object[]{villager.getVillagerDisplayName(), adultType, target.buildingId});
      return new ChildBecomeAdultGoal.BecomeAdultTask(target.buildingId, target.planSetId, gender, adultType);
   }

   @Nullable
   private ChildBecomeAdultGoal.TargetInfo findTargetBuilding(GoalContext ctx, Gender gender) {
      Village village = ctx.village();
      MillVillager villager = ctx.villager();
      String familyName = villager.getFamilyName();

      record Candidate(BuildingId buildingId, ResourceLocation planSetId, int priority, boolean completesCouple) {
      }

      List<Candidate> candidates = new ArrayList<>();

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isOperational() && b.getPlanSetId() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
            if (planSet != null
               && village.hasFreeSlot(b.getId(), gender)
               && !this.hasRelativeOfOppositeGender(village, ctx.level(), b.getId(), gender, familyName)) {
               Gender oppositeGender = gender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
               boolean completesCouple = this.buildingHasAdultOfGender(ctx, b.getId(), oppositeGender);
               candidates.add(new Candidate(b.getId(), b.getPlanSetId(), planSet.priorityMoveIn(), completesCouple));
            }
         }
      }

      if (candidates.isEmpty()) {
         return null;
      }

      List<Candidate> preferred = candidates.stream().filter(Candidate::completesCouple).toList();
      List<Candidate> pool = preferred.isEmpty() ? candidates : preferred;
      int maxPriority = pool.stream().mapToInt(Candidate::priority).max().getAsInt();
      List<Candidate> best = new ArrayList<>();

      for (Candidate c : pool) {
         if (c.priority() == maxPriority) {
            best.add(c);
         }
      }

      Candidate chosen = best.get(ctx.villager().getRandom().nextInt(best.size()));
      return new ChildBecomeAdultGoal.TargetInfo(chosen.buildingId(), chosen.planSetId());
   }

   private boolean hasRelativeOfOppositeGender(Village village, ServerLevel level, BuildingId buildingId, Gender teenagerGender, String familyName) {
      if (familyName != null && !familyName.isEmpty()) {
         Gender oppositeGender = teenagerGender == Gender.MALE ? Gender.FEMALE : Gender.MALE;

         for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
            BuildingId home = village.getVillagerHome(entry.getKey());
            if (home != null && home.equals(buildingId)) {
               VillagerType vType = ModCultures.getVillagerType(entry.getValue());
               if (vType != null
                  && !vType.isChild()
                  && vType.gender() == oppositeGender
                  && level.getEntity(entry.getKey()) instanceof MillVillager mv
                  && mv.isAlive()
                  && familyName.equals(mv.getFamilyName())) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean buildingHasAdultOfGender(GoalContext ctx, BuildingId buildingId, Gender targetGender) {
      Village village = ctx.village();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         BuildingId home = village.getVillagerHome(entry.getKey());
         if (home != null && home.equals(buildingId)) {
            VillagerType vType = ModCultures.getVillagerType(entry.getValue());
            if (vType != null
               && !vType.isChild()
               && vType.gender() == targetGender
               && ctx.level().getEntity(entry.getKey()) instanceof MillVillager mv
               && mv.isAlive()) {
               return true;
            }
         }
      }

      return false;
   }

   static class BecomeAdultTask implements VillagerTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private final BuildingId targetBuildingId;
      private final ResourceLocation planSetId;
      private final Gender gender;
      private final String adultTypeSuffix;
      private boolean arrived;
      private boolean finished;
      private int tickCount;

      BecomeAdultTask(BuildingId targetBuildingId, ResourceLocation planSetId, Gender gender, String adultTypeSuffix) {
         this.targetBuildingId = targetBuildingId;
         this.planSetId = planSetId;
         this.gender = gender;
         this.adultTypeSuffix = adultTypeSuffix;
      }

      @Override
      public ResourceLocation goalId() {
         return ChildBecomeAdultGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         this.tickCount++;
         if (this.arrived) {
            this.performTransformation(ctx);
            this.finished = true;
         } else {
            BuildingInstance target = ctx.village().getBuilding(this.targetBuildingId);
            if (target == null) {
               this.finished = true;
            } else {
               BlockPos targetPos = target.getOrigin();
               int surfaceY = ctx.level().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
               BlockPos walkTarget = new BlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
               VillagerNavDriver nav = ctx.villager().getNavManager();
               if (nav.getDestination() == null) {
                  nav.navigateTo(ctx.villager(), walkTarget, 0.5);
               }

               if (nav.isArrived(ctx.villager(), 3.0)) {
                  nav.stop(ctx.villager());
                  this.arrived = true;
               } else if (nav.isAbandoned()) {
                  nav.stop(ctx.villager());
                  this.arrived = true;
               }
            }
         }
      }

      private void performTransformation(GoalContext ctx) {
         MillVillager villager = ctx.villager();
         Village village = ctx.village();
         String culturePath = village.getCultureId().getPath();
         ResourceLocation adultTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + this.adultTypeSuffix);
         VillagerType adultType = ModCultures.getVillagerType(adultTypeId);
         if (adultType == null) {
            ChildBecomeAdultGoal.LOGGER.warn("[Millenaire] Adult type not found: {}", adultTypeId);
         } else {
            String firstName = villager.getFirstName();
            String familyName = villager.getFamilyName();
            String fathersName = villager.getFathersName();
            String mothersName = villager.getMothersName();
            this.handleMarriage(ctx, villager, adultType);
            villager.setVillagerTypeId(adultTypeId);
            villager.setChildSize(-1);
            villager.setHomeBuilding(this.targetBuildingId);
            GoalRegistry registry = Millenaire.getGoalRegistry();
            if (registry != null) {
               villager.initGoals(registry, adultType);
            }

            VillagerAppearanceFactory.randomizeAppearance(villager, adultType);
            villager.setFirstName(firstName);
            villager.setFathersName(fathersName);
            villager.setMothersName(mothersName);

            for (Entry<ResourceLocation, Integer> entry : adultType.initialInventory().entrySet()) {
               Item item = ItemHelper.resolve(entry.getKey());
               if (item != null) {
                  villager.getInventory().add(item, entry.getValue());
               }
            }

            villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
            villager.setHealth(villager.getMaxHealth());
            village.updateVillagerType(villager.getUUID(), adultTypeId);
            village.releaseAllSlots(villager.getUUID());
            ChildBecomeAdultGoal.LOGGER
               .info(
                  "[Millenaire] Teenager {} {} became adult: {} in {}",
                  new Object[]{villager.getFirstName(), villager.getFamilyName(), adultTypeId.getPath(), this.targetBuildingId}
               );
            village.recordEvent(
               ctx.level(),
               Component.translatable(
                     "event.millenaire.teenager_became_adult", new Object[]{villager.getFirstName() + " " + villager.getFamilyName(), adultTypeId.getPath()}
                  )
                  .getString()
            );
            village.recordChronicleEvent(
               ctx.level(), VillageEventType.CAME_OF_AGE, villager.getFirstName() + " " + villager.getFamilyName(), adultTypeId.getPath()
            );
         }
      }

      private void handleMarriage(GoalContext ctx, MillVillager teenager, VillagerType adultType) {
         Village village = ctx.village();
         Gender oppositeGender = this.gender == Gender.MALE ? Gender.FEMALE : Gender.MALE;
         MillVillager spouse = null;

         for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
            BuildingId home = village.getVillagerHome(entry.getKey());
            if (home != null && home.equals(this.targetBuildingId)) {
               VillagerType vType = ModCultures.getVillagerType(entry.getValue());
               if (vType != null
                  && !vType.isChild()
                  && vType.gender() == oppositeGender
                  && ctx.level().getEntity(entry.getKey()) instanceof MillVillager mv
                  && mv.isAlive()) {
                  spouse = mv;
                  break;
               }
            }
         }

         if (spouse != null) {
            if (this.gender == Gender.FEMALE) {
               teenager.setMaidenName(teenager.getFamilyName());
               teenager.setFamilyName(spouse.getFamilyName());
               teenager.setSpousesName(spouse.getFirstName() + " " + spouse.getFamilyName());
               spouse.setSpousesName(teenager.getFirstName() + " " + teenager.getMaidenName());
            } else {
               spouse.setMaidenName(spouse.getFamilyName());
               spouse.setFamilyName(teenager.getFamilyName());
               spouse.setSpousesName(teenager.getFirstName() + " " + teenager.getFamilyName());
               teenager.setSpousesName(spouse.getFirstName() + " " + spouse.getMaidenName());
            }

            ChildBecomeAdultGoal.LOGGER.info("[Millenaire] Marriage: {} & {}", teenager.getVillagerDisplayName(), spouse.getVillagerDisplayName());
         }
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
            if (reason != StopReason.COMPLETED) {
               ctx.village().releaseAllSlots(ctx.villager().getUUID());
            }
         }
      }

      @Override
      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "become_adult");
      }
   }

   private static class FailedTask implements VillagerTask {
      @Override
      public ResourceLocation goalId() {
         return ChildBecomeAdultGoal.ID;
      }

      @Override
      public void tick(GoalContext context) {
      }

      @Override
      public boolean isFinished() {
         return true;
      }

      @Override
      public void stop(GoalContext context, StopReason reason) {
      }
   }

   private record TargetInfo(BuildingId buildingId, ResourceLocation planSetId) {
   }
}
