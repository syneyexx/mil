package org.millenaire.civilization.interaction;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.village.Village;

/**
 * Limited cross-culture immigration when OPEN + standing + GUEST_QUARTER present.
 */
public final class DiasporaService {
   private static final int MIN_STANDING = 20;

   private DiasporaService() {
   }

   public static boolean allowsCrossCulture(
      ServerLevel level,
      Village destination,
      Faction destFaction,
      ResourceLocation sourceCultureId
   ) {
      if (level == null || destination == null || destFaction == null || sourceCultureId == null) {
         return false;
      }
      if (destFaction.getImmigrationPolicy() != ImmigrationPolicy.OPEN) {
         return false;
      }
      if (destination.getCultureId() != null && destination.getCultureId().equals(sourceCultureId)) {
         return true;
      }
      if (!hasGuestQuarter(destination)) {
         return false;
      }
      FactionId sourceFaction = findCultureFaction(level, sourceCultureId);
      if (sourceFaction == null) {
         return false;
      }
      int score = RelationshipSavedData.get(level)
         .getRelationshipManager()
         .getRelationshipScore(destFaction.getId(), sourceFaction);
      return score >= MIN_STANDING;
   }

   public static void recordAssimilationProgress(ServerLevel level, FactionId destFaction, ResourceLocation sourceCulture) {
      if (level == null || destFaction == null || sourceCulture == null) {
         return;
      }
      InteractionSavedData data = InteractionSavedData.get(level);
      String key = destFaction.asString() + "|" + sourceCulture;
      int next = data.getDiasporaAssimilation(key) + 1;
      data.setDiasporaAssimilation(key, next);
      if (next >= InteractionConfig.DIASPORA_ASSIMILATION_THRESHOLD && next % InteractionConfig.DIASPORA_ASSIMILATION_THRESHOLD == 0) {
         data.addTheaterStory(new TheaterStory(
            "diaspora",
            "civilization.millenaire.theater.diaspora_assimilating",
            destFaction.asString(),
            sourceCulture.toString(),
            1,
            level.getGameTime()
         ));
      }
   }

   public static boolean hasGuestQuarter(Village village) {
      if (village == null) {
         return false;
      }
      for (BuildingInstance building : village.getBuildings()) {
         if (building != null
            && building.isOperational()
            && building.getPurpose() == BuildingPurpose.GUEST_QUARTER) {
            return true;
         }
      }
      return false;
   }

   @Nullable
   private static FactionId findCultureFaction(ServerLevel level, ResourceLocation cultureId) {
      for (Faction faction : org.millenaire.faction.FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (faction.getType() == org.millenaire.faction.FactionType.CULTURE
            && cultureId.equals(faction.getCultureId())) {
            return faction.getId();
         }
      }
      return null;
   }
}
