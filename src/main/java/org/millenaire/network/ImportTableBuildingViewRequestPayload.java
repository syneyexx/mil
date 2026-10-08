package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
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
import org.millenaire.building.ExportHydrator;
import org.millenaire.building.ImportBuildingResolver;
import org.millenaire.building.ImportTableIdentifierValidator;
import org.millenaire.building.ImportTablePlanResolver;
import org.slf4j.Logger;

public record ImportTableBuildingViewRequestPayload(BlockPos blockPos, String cultureKey, String buildingId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_KEY_LENGTH = 128;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<ImportTableBuildingViewRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_building_view_request")
   );
   public static final StreamCodec<ByteBuf, ImportTableBuildingViewRequestPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableBuildingViewRequestPayload::encode, ImportTableBuildingViewRequestPayload::decode
   );

   private static void encode(ByteBuf buf, ImportTableBuildingViewRequestPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.blockPos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.buildingId);
   }

   private static ImportTableBuildingViewRequestPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String cultureKey = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String buildingId = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      return new ImportTableBuildingViewRequestPayload(new BlockPos(x, y, z), cultureKey, buildingId);
   }

   private static String truncate(String s) {
      return s.length() > 128 ? s.substring(0, 128) : s;
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(ImportTableBuildingViewRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  if (ServerPayloadGuards.isOperatorOrSingleplayerOwner(player)) {
                     if (!RATE_LIMITER.acquire(player.getUUID())) {
                        LOGGER.debug("ImportTable building-view rate-limited for {}", player.getName().getString());
                     } else if (ServerPayloadGuards.isWithinDistance(player, payload.blockPos, 64.0)) {
                        if (ImportTableIdentifierValidator.isValidCultureKey(payload.cultureKey)
                           && ImportTableIdentifierValidator.isValidIdentifier(payload.buildingId)) {
                           ExportHydrator.Outcome outcome = ExportHydrator.select(serverLevel, payload.cultureKey, payload.buildingId);
                           ImportBuildingResolver.ResolvedBuilding resolved = outcome.resolved();
                           if (resolved != null) {
                              ImportTablePlanResolver.CultureListingEntry entry = ImportTablePlanResolver.toListingEntry(resolved);
                              PacketDistributor.sendToPlayer(
                                 player,
                                 new ImportTableBuildingViewPayload(
                                    payload.cultureKey,
                                    ImportTableCultureListPayload.entryOf(payload.cultureKey, entry),
                                    outcome.hydratedLevels(),
                                    outcome.migratedLegacy()
                                 ),
                                 new CustomPacketPayload[0]
                              );
                           }
                        } else {
                           LOGGER.warn("ImportTable: rejected building-view request with invalid identifiers from {}", player.getName().getString());
                        }
                     }
                  }
               }
            }
         }
      );
   }
}
