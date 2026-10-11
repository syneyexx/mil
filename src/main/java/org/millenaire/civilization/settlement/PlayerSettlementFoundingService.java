package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.slf4j.Logger;

/**
 * Sole mutation path for player MANUAL founding: claim site → name → PLAYER faction → empty shell village.
 */
public final class PlayerSettlementFoundingService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_NAME_LENGTH = 40;

   private PlayerSettlementFoundingService() {
   }

   public record FoundResult(boolean success, @Nullable Component message, @Nullable Village village, @Nullable Faction faction) {
      public static FoundResult fail(Component message) {
         return new FoundResult(false, message, null, null);
      }

      public static FoundResult ok(Village village, Faction faction, Component message) {
         return new FoundResult(true, message, village, faction);
      }
   }

   public static FoundResult claimAndFound(
      ServerLevel level, ServerPlayer player, BlockPos pos, VillageType villageType, String rawName
   ) {
      if (level == null || player == null || pos == null || villageType == null) {
         return FoundResult.fail(Component.translatable("ui.settlement.found.error.invalid"));
      }
      if (!level.dimension().equals(Level.OVERWORLD)) {
         return FoundResult.fail(Component.translatable("ui.wand.overworld_only"));
      }

      String name = sanitizeName(rawName);
      if (name == null) {
         return FoundResult.fail(Component.translatable("ui.settlement.found.error.bad_name"));
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      Faction existing = factions.findSovereignFaction(player.getUUID());
      if (existing != null && !existing.getSettlementIds().isEmpty()) {
         return FoundResult.fail(Component.translatable("ui.settlement.found.error.already_sovereign"));
      }

      VillageManager villageManager = VillageSavedData.get(level).getVillageManager();

      if (villageType.loneBuilding() || villageType.isHamlet()) {
         return FoundResult.fail(Component.translatable("ui.settlement.found.error.bad_type"));
      }

      // Sovereign founding does not require culture control; summoning controlled villages still does.

      Component siteError = VillageSpawner.validateSite(level, pos, villageType);
      if (siteError != null) {
         return FoundResult.fail(siteError);
      }

      Faction faction = existing;
      if (faction == null) {
         faction = factions.createPlayerFaction(name, villageType.culture(), level.getGameTime(), player.getUUID());
         if (faction == null) {
            return FoundResult.fail(Component.translatable("ui.settlement.found.error.faction"));
         }
      } else {
         factions.setDisplayName(faction.getId(), name);
         if (faction.getSovereignOwnerUuid() == null) {
            faction.setSovereignOwnerUuid(player.getUUID());
         }
      }

      Component spawnFailure = VillageSpawner.spawnSovereignShell(level, pos, villageType, player, name, faction.getId());
      if (spawnFailure != null) {
         if (faction.getSettlementIds().isEmpty() && faction.getType() == FactionType.PLAYER) {
            // Leave empty faction for retry; do not delete (may have been reused).
            LOGGER.warn("[Millenaire] Sovereign spawn failed for {}: {}", player.getName().getString(), spawnFailure.getString());
         }
         return FoundResult.fail(spawnFailure);
      }

      Village founded = findOwnedVillageAt(villageManager, player.getUUID(), pos);
      if (founded != null) {
         founded.setVillageName(name);
         // Light player brand: keep custom name; seed banner from culture when shell has none.
         if (founded.getBannerNbt() == null || founded.getBannerNbt().isBlank()) {
            var culture = founded.getCultureId() != null ? ModCultures.getCulture(founded.getCultureId()) : null;
            if (culture != null && culture.cultureBannerNbt() != null) {
               founded.setBannerNbt(culture.cultureBannerNbt());
               faction.setBannerNbt(culture.cultureBannerNbt());
            }
         } else {
            faction.setBannerNbt(founded.getBannerNbt());
         }
         VillageSavedData.get(level).setDirty();
         FactionSavedData.get(level).setDirty();
      }

      LOGGER.info(
         "[Millenaire] Player {} founded sovereign settlement '{}' ({}) faction={}",
         player.getName().getString(),
         name,
         villageType.id(),
         faction.getId().asString()
      );
      return FoundResult.ok(
         founded,
         faction,
         Component.translatable("ui.settlement.found.success", name)
      );
   }

   public static boolean isSovereignPlayerSettlement(ServerLevel level, Village village) {
      if (level == null || village == null || village.getFactionId() == null) {
         return false;
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
      return faction != null && faction.getType() == FactionType.PLAYER;
   }

   @Nullable
   private static Village findOwnedVillageAt(VillageManager manager, UUID owner, BlockPos pos) {
      Village nearest = null;
      double best = Double.MAX_VALUE;
      for (Village village : manager.getAllVillages()) {
         if (!owner.equals(village.getOwnerUUID())) {
            continue;
         }
         double dist = village.getCenter().distSqr(pos);
         if (dist < best) {
            best = dist;
            nearest = village;
         }
      }
      return nearest;
   }

   @Nullable
   private static String sanitizeName(String raw) {
      if (raw == null) {
         return null;
      }
      String trimmed = raw.trim().replaceAll("\\s+", " ");
      if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
         return null;
      }
      for (int i = 0; i < trimmed.length(); i++) {
         char c = trimmed.charAt(i);
         if (c < 32 || c == '§' || c == '&') {
            return null;
         }
      }
      return trimmed;
   }

   /** Resolve a village type for founding from network keys. */
   @Nullable
   public static VillageType resolveType(String cultureKey, String typeKey) {
      if (typeKey == null || typeKey.isBlank()) {
         return null;
      }
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("millenaire", typeKey);
      VillageType type = ModCultures.getVillageType(id);
      if (type == null) {
         return null;
      }
      if (cultureKey != null && !cultureKey.isBlank()) {
         String expected = cultureKey.toLowerCase(Locale.ROOT);
         if (type.culture() != null && !type.culture().getPath().equalsIgnoreCase(expected)) {
            return null;
         }
      }
      return type;
   }

   /** Marker unused by worldgen plans today; reserved for future MANUAL plan linkage. */
   public static SettlementPlan.FoundingMode foundingMode() {
      return SettlementPlan.FoundingMode.MANUAL;
   }
}
