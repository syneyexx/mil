package org.millenaire.faction.crime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.network.BountyHudPayload;

/** Builds and sends bounty HUD snapshots to a player. */
public final class BountyHudSync {
   private BountyHudSync() {
   }

   public static void sync(ServerLevel level, ServerPlayer player) {
      if (level == null || player == null) {
         return;
      }
      if (!hudEnabled()) {
         PacketDistributor.sendToPlayer(player, new BountyHudPayload(List.of()), new CustomPacketPayload[0]);
         return;
      }
      // HUD shows every positive bounty; on-sight pursuit still uses cultureBountyOnSightTier.
      List<BountyHudPayload.Entry> entries = new ArrayList<>();
      UUID playerId = player.getUUID();
      for (CultureBountyRecord record : CultureBountySavedData.get(level).getManager().getRecordsForPlayer(playerId)) {
         ResourceLocation cultureId = record.cultureId();
         Culture culture = ModCultures.getCulture(cultureId);
         String display = culture != null && culture.displayName() != null && !culture.displayName().isBlank()
            ? culture.displayName()
            : cultureId.getPath();
         entries.add(new BountyHudPayload.Entry(cultureId.toString(), display, record.bountyScore(), record.tier().name()));
      }
      entries.sort(Comparator.comparingInt(BountyHudPayload.Entry::score).reversed());
      PacketDistributor.sendToPlayer(player, new BountyHudPayload(List.copyOf(entries)), new CustomPacketPayload[0]);
   }

   private static boolean hudEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.bountyHudEnabled.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
