package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingExporter;
import org.millenaire.building.ImportTableIdentifierValidator;
import org.millenaire.building.ImportTablePlanResolver;
import org.slf4j.Logger;

public record ImportTableCultureListRequestPayload(BlockPos blockPos, String cultureKey) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_KEY_LENGTH = 128;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   private static final long OVERLAY_REFRESH_MIN_INTERVAL_MS = 2000L;
   private static volatile long lastOverlayRefreshMs = 0L;
   public static final Type<ImportTableCultureListRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_culture_list_request")
   );
   public static final StreamCodec<ByteBuf, ImportTableCultureListRequestPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableCultureListRequestPayload::encode, ImportTableCultureListRequestPayload::decode
   );

   private static void encode(ByteBuf buf, ImportTableCultureListRequestPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
   }

   private static ImportTableCultureListRequestPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (cultureKey.length() > 128) {
         cultureKey = cultureKey.substring(0, 128);
      }

      return new ImportTableCultureListRequestPayload(new BlockPos(x, y, z), cultureKey);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(ImportTableCultureListRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel) {
               if (ServerPayloadGuards.isOperatorOrSingleplayerOwner(player)) {
                  if (!RATE_LIMITER.acquire(player.getUUID())) {
                     LOGGER.debug("ImportTable culture-list rate-limited for {}", player.getName().getString());
                  } else if (ServerPayloadGuards.isWithinDistance(player, payload.blockPos, 64.0)) {
                     if (!ImportTableIdentifierValidator.isValidCultureKey(payload.cultureKey)) {
                        LOGGER.warn("ImportTable: rejected culture-list request with invalid cultureKey from {}", player.getName().getString());
                     } else {
                        long now = System.currentTimeMillis();
                        if (now - lastOverlayRefreshMs >= 2000L) {
                           lastOverlayRefreshMs = now;
                           BuildingExporter.refreshOverlay();
                        }

                        List<ImportTablePlanResolver.CultureListingEntry> entries = ImportTablePlanResolver.listCulture(payload.cultureKey);
                        PacketDistributor.sendToPlayer(player, ImportTableCultureListPayload.of(payload.cultureKey, entries), new CustomPacketPayload[0]);
                     }
                  }
               }
            }
         }
      });
   }
}
