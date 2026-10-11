package org.millenaire.village;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;

/**
 * Shared rename path for commands and City Admin Wand.
 */
public final class VillageRenameService {
   private static final int MAX_NAME_LENGTH = 40;

   private VillageRenameService() {
   }

   public record RenameResult(boolean success, Component message) {
      public static RenameResult fail(Component message) {
         return new RenameResult(false, message);
      }

      public static RenameResult ok(Component message) {
         return new RenameResult(true, message);
      }
   }

   public static RenameResult rename(ServerLevel level, ServerPlayer player, Village village, String rawName) {
      if (level == null || player == null || village == null) {
         return RenameResult.fail(Component.translatable("ui.city_admin.rename.error"));
      }
      boolean sovereignOwner = PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
         && village.getOwnerUUID() != null
         && village.getOwnerUUID().equals(player.getUUID());
      if (!village.isControlledBy(player.getUUID()) && !sovereignOwner) {
         return RenameResult.fail(Component.translatable("ui.city_admin.rename.not_owner"));
      }
      String name = sanitize(rawName);
      if (name == null) {
         return RenameResult.fail(Component.translatable("ui.settlement.found.error.bad_name"));
      }
      String oldName = village.getVillageName();
      village.setVillageName(name);
      village.markDirty();
      org.millenaire.map.sync.SettlementMapSyncService.markDirty();
      if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village) && village.getFactionId() != null) {
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
         if (faction != null && faction.getType() == FactionType.PLAYER) {
            FactionSavedData.get(level).getFactionManager().setDisplayName(faction.getId(), name);
         }
      }
      return RenameResult.ok(Component.translatable("command.millenaire.renamevillage.success", oldName, name));
   }

   @Nullable
   private static String sanitize(String raw) {
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
}
