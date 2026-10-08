package org.millenaire.faction;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

/**
 * Internal/debug accessors for the faction foundation. Not a player-facing GUI.
 */
public final class FactionQueries {
   private FactionQueries() {
   }

   public static FactionManager manager(ServerLevel level) {
      return FactionSavedData.get(level).getFactionManager();
   }

   public static boolean factionExists(ServerLevel level, FactionId factionId) {
      return manager(level).exists(factionId);
   }

   @Nullable
   public static Faction getFaction(ServerLevel level, FactionId factionId) {
      return manager(level).getFaction(factionId);
   }

   @Nullable
   public static FactionId getFactionId(Village village) {
      return village == null ? null : village.getFactionId();
   }

   @Nullable
   public static Faction getFactionForVillage(ServerLevel level, Village village) {
      return manager(level).getFactionForVillage(village);
   }

   public static Collection<VillageId> getSettlements(ServerLevel level, FactionId factionId) {
      return manager(level).getSettlements(factionId);
   }

   public static Collection<Faction> getAllFactions(ServerLevel level) {
      return manager(level).getAllFactions();
   }

   @Nullable
   public static ResourceLocation getCulture(ServerLevel level, FactionId factionId) {
      Faction faction = getFaction(level, factionId);
      return faction == null ? null : faction.getCultureId();
   }

   @Nullable
   public static FactionType getType(ServerLevel level, FactionId factionId) {
      Faction faction = getFaction(level, factionId);
      return faction == null ? null : faction.getType();
   }

   public static List<Faction> getFactionsUsingCulture(ServerLevel level, ResourceLocation cultureId) {
      return manager(level).getFactionsUsingCulture(cultureId);
   }
}
