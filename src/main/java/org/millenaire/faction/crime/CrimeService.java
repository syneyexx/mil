package org.millenaire.faction.crime;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.civilization.interaction.InteractionTicker;
import org.millenaire.culture.VillagerType;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.village.Village;

/**
 * Public entry points for recording player crimes against cultures.
 */
public final class CrimeService {
   private CrimeService() {
   }

   public static void onVillagerAssault(ServerLevel level, ServerPlayer player, MillVillager villager, Village village) {
      if (level == null || player == null || villager == null) {
         return;
      }
      VillagerType type = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (type != null && type.isHostile()) {
         return;
      }
      ResourceLocation cultureId = resolveCulture(village, type);
      if (cultureId == null) {
         return;
      }
      var record = CultureBountySavedData.get(level).getManager().recordCrime(level, player, cultureId, CrimeType.ASSAULT, village);
      applyFestivalGrudge(level, player, cultureId, village, record);
   }

   public static void onVillagerMurder(ServerLevel level, ServerPlayer player, MillVillager villager, Village village) {
      if (level == null || player == null || villager == null) {
         return;
      }
      VillagerType type = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (type != null && type.isHostile()) {
         return;
      }
      ResourceLocation cultureId = resolveCulture(village, type);
      if (cultureId == null) {
         return;
      }
      var record = CultureBountySavedData.get(level).getManager().recordCrime(level, player, cultureId, CrimeType.MURDER, village);
      applyFestivalGrudge(level, player, cultureId, village, record);
   }

   public static void onTheft(ServerLevel level, ServerPlayer player, Village village) {
      if (level == null || player == null || village == null || village.getCultureId() == null) {
         return;
      }
      if (village.isControlledBy(player.getUUID())) {
         return;
      }
      var record = CultureBountySavedData.get(level).getManager().recordCrime(level, player, village.getCultureId(), CrimeType.THEFT, village);
      applyFestivalGrudge(level, player, village.getCultureId(), village, record);
   }

   public static void onPickpocket(ServerLevel level, ServerPlayer player, MillVillager villager, Village village) {
      if (level == null || player == null || villager == null) {
         return;
      }
      VillagerType type = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (type != null && type.isHostile()) {
         return;
      }
      ResourceLocation cultureId = resolveCulture(village, type);
      if (cultureId == null) {
         return;
      }
      var record = CultureBountySavedData.get(level).getManager().recordCrime(level, player, cultureId, CrimeType.PICKPOCKET, village);
      applyFestivalGrudge(level, player, cultureId, village, record);
   }

   public static void onRaidAgainstCulture(ServerLevel level, ServerPlayer player, Village targetVillage) {
      if (level == null || player == null || targetVillage == null || targetVillage.getCultureId() == null) {
         return;
      }
      CultureBountySavedData.get(level)
         .getManager()
         .recordCrime(level, player, targetVillage.getCultureId(), CrimeType.RAID_AGAINST_CULTURE, targetVillage);
   }

   public static void onPeaceWithCulture(ServerLevel level, java.util.UUID playerId, ResourceLocation cultureId) {
      if (level == null || playerId == null || cultureId == null) {
         return;
      }
      CultureBountyManager manager = CultureBountySavedData.get(level).getManager();
      CultureBountyRecord record = manager.get(playerId, cultureId);
      if (record == null) {
         return;
      }
      record.reduceBounty(200);
      record.clearCampaign();
      CultureBountySavedData.get(level).setDirty();
      ServerPlayer online = level.getServer().getPlayerList().getPlayer(playerId);
      if (online != null) {
         BountyHudSync.sync(level, online);
      }
   }

   @Nullable
   private static ResourceLocation resolveCulture(@Nullable Village village, @Nullable VillagerType type) {
      if (village != null && village.getCultureId() != null) {
         return village.getCultureId();
      }
      return type != null ? type.culture() : null;
   }

   /** Festival seasons amplify relationship impact of crimes (grudge memory). */
   private static void applyFestivalGrudge(
      ServerLevel level,
      ServerPlayer player,
      ResourceLocation cultureId,
      @Nullable Village village,
      @Nullable CultureBountyRecord record
   ) {
      if (level == null || cultureId == null || record == null) {
         return;
      }
      if (!InteractionTicker.isFestivalActive(level, cultureId.toString())) {
         return;
      }
      if (village != null) {
         village.adjustReputation(level, player.getUUID(), -8);
      }
      if (village != null && village.getFactionId() != null) {
         FactionId cultureFaction = village.getFactionId();
         // Extra insult during festivals — uses existing relationship track only.
         for (var faction : org.millenaire.faction.FactionSavedData.get(level).getFactionManager().getAllFactions()) {
            if (faction.getType() == org.millenaire.faction.FactionType.PLAYER
               && faction.isSovereignOwnedBy(player.getUUID())) {
               RelationshipSavedData.get(level)
                  .getRelationshipManager()
                  .modifyRelationship(level, cultureFaction, faction.getId(), -6, RelationshipChangeReason.INSULT);
               break;
            }
         }
      }
   }
}
