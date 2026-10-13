package org.millenaire.faith;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;

/**
 * Assigns default faiths and queues a shrine/church project for player-founded faiths.
 */
public final class FaithService {
   private FaithService() {
   }

   public static void applyDefaultFaith(Village village) {
      if (village.getFaithId() != null) {
         return;
      }
      ResourceLocation faithId = FaithRegistry.defaultFaithIdForCulture(village.getCultureId());
      if (faithId != null) {
         village.setFaithId(faithId);
      }
   }

   public static void startPlayerFaith(Village village, String customName) {
      village.setFaithId(FaithRegistry.playerCustomId());
      village.setCustomFaithName(customName != null && !customName.isBlank() ? customName.trim() : "Faith");
      enqueuePreferredShrine(village);
   }

   public static String resolveDisplayName(Village village) {
      if (village.getCustomFaithName() != null && !village.getCustomFaithName().isBlank()) {
         return village.getCustomFaithName().trim();
      }
      FaithDefinition def = FaithRegistry.get(village.getFaithId());
      if (def != null) {
         return def.displayName();
      }
      return "Faith";
   }

   @Nullable
   public static FaithDefinition resolve(Village village) {
      FaithDefinition def = FaithRegistry.get(village.getFaithId());
      if (def != null) {
         return def;
      }
      return FaithRegistry.getDefaultForCulture(village.getCultureId());
   }

   public static void enqueuePreferredShrine(Village village) {
      if (village.getPendingProject() != null) {
         return;
      }
      ResourceLocation planId = findShrinePlan(village);
      if (planId == null) {
         return;
      }
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
      if (planSet == null) {
         return;
      }
      String variant = planSet.variants().isEmpty() ? "a" : planSet.variants().keySet().iterator().next();
      village.setPendingProject(new Village.PendingProject(planId, variant, 0, false, null, null));
   }

   @Nullable
   private static ResourceLocation findShrinePlan(Village village) {
      FaithDefinition def = resolve(village);
      if (def != null && def.preferredShrinePlan() != null) {
         return def.preferredShrinePlan();
      }
      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      if (type == null) {
         return null;
      }
      for (VillageType.LayoutSlot slot : type.layout()) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
         if (planSet == null) {
            continue;
         }
         if (hasReligiousTag(planSet, def)) {
            return slot.plan();
         }
      }
      return null;
   }

   private static boolean hasReligiousTag(BuildingPlanSet planSet, @Nullable FaithDefinition def) {
      for (String tag : planSet.tags()) {
         if (def != null && def.churchBuildingTags().contains(tag)) {
            return true;
         }
         if ("praying".equals(tag) || "mosque".equals(tag) || "pujas".equals(tag)
            || "sacrifices".equals(tag) || "religious".equals(tag)) {
            return true;
         }
      }
      return false;
   }

   /** No-op level param kept for call-site clarity at spawn. */
   public static void onVillageSpawned(ServerLevel level, Village village) {
      applyDefaultFaith(village);
   }
}
