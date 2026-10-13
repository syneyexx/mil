package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.ControlledCityService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record WorldLedgerCityRequestPayload(String villageUuid) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<WorldLedgerCityRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "world_ledger_city_request")
   );
   public static final StreamCodec<ByteBuf, WorldLedgerCityRequestPayload> STREAM_CODEC = StreamCodec.of(
      (buf, p) -> ByteBufCodecs.STRING_UTF8.encode(buf, p.villageUuid == null ? "" : p.villageUuid),
      buf -> new WorldLedgerCityRequestPayload(ByteBufCodecs.STRING_UTF8.decode(buf))
   );
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(WorldLedgerCityRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel level = player.server.getLevel(Level.OVERWORLD);
         if (level == null || !RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }
         UUID villageId;
         try {
            villageId = UUID.fromString(payload.villageUuid());
         } catch (Exception e) {
            return;
         }
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageId));
         if (village == null || !village.isControlledBy(player.getUUID())) {
            LOGGER.debug("[Millenaire] WorldLedger city request denied for {}", player.getName().getString());
            return;
         }
         ControlledCityService.sendRefresh(player, level, village);
      });
   }
}
