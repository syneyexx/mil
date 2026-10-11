package org.millenaire.religion.faith;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.religion.content.ReligionContentRegistry;
import org.millenaire.religion.content.ReligionDefinition;
import org.millenaire.religion.model.FaithAffiliation;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.millenaire.religion.persist.ReligionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

/**
 * Initializes NPC faith once from stable identity + content rules. Never re-rolls on reload.
 */
public final class NpcFaithService {
   private NpcFaithService() {
   }

   public static NpcFaithProfile ensureInitialized(ServerLevel level, Village village, VillagerRecord record) {
      ReligionSavedData data = ReligionSavedData.get(level);
      NpcFaithProfile profile = data.profileFor(record.getUuid());
      if (profile.initialized()) {
         return profile;
      }
      List<ResourceLocation> traditions = ReligionContentRegistry.traditionsForCulture(cultureKey(village));
      if (traditions.isEmpty() && ReligionContentRegistry.cultureHasExplicitNone(cultureKey(village))) {
         profile.markInitialized();
         data.setDirty();
         return profile;
      }
      if (traditions.isEmpty()) {
         VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
         if (type != null && type.culture() != null) {
            traditions = ReligionContentRegistry.traditionsForCulture(type.culture().getPath());
         }
      }
      long seed = record.getUuid().getMostSignificantBits() ^ record.getUuid().getLeastSignificantBits();
      if (!traditions.isEmpty()) {
         // Deterministic primary tradition; optional secondary when tradition allows multi.
         ResourceLocation primary = traditions.get((int) Math.floorMod(seed, traditions.size()));
         ReligionDefinition def = ReligionContentRegistry.traditionOrNull(primary);
         int devotionBase = def != null ? def.defaultDevotion() : 40;
         int devotion = clamp(devotionBase + (int) (seed % 21) - 10);
         // Clergy tend to be more devoted.
         if (isClergy(record, def)) {
            devotion = clamp(devotion + 25);
         }
         // Some NPCs are less active (low devotion) without leaving the tradition.
         if ((seed & 7L) == 0L) {
            devotion = Math.min(devotion, 15);
         }
         profile.addAffiliation(new FaithAffiliation(primary, devotion, level.getGameTime()));
         if (def != null && def.allowsMultipleAffiliations() && traditions.size() > 1) {
            ResourceLocation secondary = traditions.get((int) Math.floorMod(seed >>> 8, traditions.size()));
            if (!secondary.equals(primary)) {
               profile.addAffiliation(new FaithAffiliation(secondary, Math.max(10, devotion / 2), level.getGameTime()));
            }
         }
      }
      profile.markInitialized();
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      for (FaithAffiliation a : profile.affiliations()) {
         community.addTradition(a.traditionId());
      }
      data.setDirty();
      return profile;
   }

   public static void ensureVillageCommunity(ServerLevel level, Village village) {
      ReligionSavedData data = ReligionSavedData.get(level);
      VillageFaithCommunity community = data.communityFor(village.getId().uuid());
      if (!community.traditionIds().isEmpty() || ReligionContentRegistry.cultureHasExplicitNone(cultureKey(village))) {
         return;
      }
      for (ResourceLocation id : ReligionContentRegistry.traditionsForCulture(cultureKey(village))) {
         community.addTradition(id);
      }
      data.setDirty();
   }

   private static boolean isClergy(VillagerRecord record, @Nullable ReligionDefinition def) {
      if (def == null) {
         return false;
      }
      VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
      List<String> tags = type != null ? type.tags() : List.of();
      for (var role : def.roles()) {
         if (role.clergy() && role.matches(record.getVillagerTypeId(), tags, record.getRoleName())) {
            return true;
         }
      }
      return false;
   }

   private static String cultureKey(Village village) {
      ResourceLocation id = village.getCultureId();
      return id != null ? id.getPath() : "";
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(100, v));
   }
}
