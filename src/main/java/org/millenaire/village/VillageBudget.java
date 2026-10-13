package org.millenaire.village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Policy weights and budget reservations for controlled villages.
 * Physical coins stay in {@link VillageTreasuryService}; this tracks intent and caps.
 */
public final class VillageBudget {
   public static final int MAX_ACTIVE_PRIORITIES = 3;

   private int foodWeight = 30;
   private int buildWeight = 25;
   private int educationWeight = 10;
   private int religionWeight = 15;
   private int defenseWeight = 20;
   private int reservedDeniers;
   private String activePriority0 = "food";
   private String activePriority1 = "build";
   private String activePriority2 = "defense";
   private String lastCouncilAdvice = "";

   public int foodWeight() {
      return this.foodWeight;
   }

   public int buildWeight() {
      return this.buildWeight;
   }

   public int educationWeight() {
      return this.educationWeight;
   }

   public int religionWeight() {
      return this.religionWeight;
   }

   public int defenseWeight() {
      return this.defenseWeight;
   }

   public int reservedDeniers() {
      return this.reservedDeniers;
   }

   public void setReservedDeniers(int reservedDeniers) {
      this.reservedDeniers = Math.max(0, reservedDeniers);
   }

   public String lastCouncilAdvice() {
      return this.lastCouncilAdvice;
   }

   public void setLastCouncilAdvice(String advice) {
      this.lastCouncilAdvice = advice == null ? "" : advice;
   }

   public String[] activePriorities() {
      return new String[]{this.activePriority0, this.activePriority1, this.activePriority2};
   }

   /** Normalize weights to sum 100. */
   public void setWeights(int food, int build, int education, int religion, int defense) {
      int sum = Math.max(1, food + build + education + religion + defense);
      this.foodWeight = food * 100 / sum;
      this.buildWeight = build * 100 / sum;
      this.educationWeight = education * 100 / sum;
      this.religionWeight = religion * 100 / sum;
      this.defenseWeight = Math.max(0, 100 - this.foodWeight - this.buildWeight - this.educationWeight - this.religionWeight);
      refreshPriorities();
   }

   public void refreshPriorities() {
      // Pick top 3 categories by weight
      String[] cats = {"food", "build", "education", "religion", "defense"};
      int[] weights = {this.foodWeight, this.buildWeight, this.educationWeight, this.religionWeight, this.defenseWeight};
      for (int i = 0; i < cats.length; i++) {
         for (int j = i + 1; j < cats.length; j++) {
            if (weights[j] > weights[i]) {
               int tw = weights[i];
               weights[i] = weights[j];
               weights[j] = tw;
               String tc = cats[i];
               cats[i] = cats[j];
               cats[j] = tc;
            }
         }
      }
      this.activePriority0 = cats[0];
      this.activePriority1 = cats[1];
      this.activePriority2 = cats[2];
   }

   public String rankProjectsAdvice(boolean hasFoodNeed, boolean hasBuildPending, boolean shrineDamaged) {
      StringBuilder sb = new StringBuilder("Council: ");
      if (hasFoodNeed && this.foodWeight >= this.buildWeight) {
         sb.append("secure food reserves before new housing. ");
      } else if (hasBuildPending) {
         sb.append("continue the build queue within budget. ");
      }
      if (shrineDamaged && this.religionWeight >= 20) {
         sb.append("restore the shrine to ease tension. ");
      }
      if (this.defenseWeight >= 25) {
         sb.append("keep a defense reserve. ");
      }
      this.lastCouncilAdvice = sb.toString().trim();
      return this.lastCouncilAdvice;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("food", this.foodWeight);
      tag.putInt("build", this.buildWeight);
      tag.putInt("education", this.educationWeight);
      tag.putInt("religion", this.religionWeight);
      tag.putInt("defense", this.defenseWeight);
      tag.putInt("reserved", this.reservedDeniers);
      tag.putString("p0", this.activePriority0);
      tag.putString("p1", this.activePriority1);
      tag.putString("p2", this.activePriority2);
      tag.putString("advice", this.lastCouncilAdvice);
      return tag;
   }

   public void load(CompoundTag tag, HolderLookup.Provider registries) {
      if (tag.contains("food")) {
         this.foodWeight = tag.getInt("food");
         this.buildWeight = tag.getInt("build");
         this.educationWeight = tag.getInt("education");
         this.religionWeight = tag.getInt("religion");
         this.defenseWeight = tag.getInt("defense");
      }
      this.reservedDeniers = tag.getInt("reserved");
      if (tag.contains("p0")) {
         this.activePriority0 = tag.getString("p0");
         this.activePriority1 = tag.getString("p1");
         this.activePriority2 = tag.getString("p2");
      } else {
         refreshPriorities();
      }
      this.lastCouncilAdvice = tag.contains("advice") ? tag.getString("advice") : "";
   }
}
