package org.millenaire.crime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.network.BountyHudPayload;

public final class BountyHudSync {
   private BountyHudSync() {
   }

   public static void syncToPlayer(ServerPlayer player) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      PacketDistributor.sendToPlayer(player, build(level, player), new CustomPacketPayload[0]);
   }

   public static BountyHudPayload build(ServerLevel level, ServerPlayer player) {
      Map<ResourceLocation, Integer> all = PlayerBountySavedData.get(level).getAll(player.getUUID());
      List<BountyHudPayload.Entry> entries = new ArrayList<>();
      for (Entry<ResourceLocation, Integer> e : all.entrySet()) {
         int amount = e.getValue();
         if (amount <= 0) {
            continue;
         }
         ResourceLocation cultureId = e.getKey();
         entries.add(new BountyHudPayload.Entry(cultureId.getPath(), resolveDisplayName(cultureId), amount));
      }
      entries.sort(Comparator.comparingInt(BountyHudPayload.Entry::amount).reversed());
      return new BountyHudPayload(entries);
   }

   private static String resolveDisplayName(ResourceLocation cultureId) {
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
         return culture.displayName();
      }
      return cultureId.getPath();
   }
}
