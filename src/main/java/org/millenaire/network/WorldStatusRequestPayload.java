package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.civilization.presentation.WorldStatusPresentationService;
import org.millenaire.civilization.presentation.WorldStatusSnapshot;
import org.slf4j.Logger;

/** Client → server: open or refresh the world status command center. */
public record WorldStatusRequestPayload(int tabIndex, String selectedFactionId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 128;
   public static final Type<WorldStatusRequestPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "world_status_request"));
   public static final StreamCodec<ByteBuf, WorldStatusRequestPayload> STREAM_CODEC =
      StreamCodec.of(WorldStatusRequestPayload::encode, WorldStatusRequestPayload::decode);
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(250L);

   public WorldStatusRequestPayload() {
      this(0, "");
   }

   private static void encode(ByteBuf buf, WorldStatusRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.tabIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.selectedFactionId == null ? "" : payload.selectedFactionId);
   }

   private static WorldStatusRequestPayload decode(ByteBuf buf) {
      return new WorldStatusRequestPayload(
         ByteBufCodecs.VAR_INT.decode(buf),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf))
      );
   }

   private static String truncate(String value) {
      if (value == null) {
         return "";
      }
      return value.length() > MAX_STRING_LENGTH ? value.substring(0, MAX_STRING_LENGTH) : value;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(WorldStatusRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
         }
         if (!(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
         }
         if (!RATE_LIMITER.acquire(serverPlayer.getUUID())) {
            return;
         }
         WorldStatusSnapshot snap = WorldStatusPresentationService.build(
            serverLevel,
            serverPlayer,
            payload.tabIndex(),
            payload.selectedFactionId()
         );
         PacketDistributor.sendToPlayer(
            serverPlayer,
            WorldStatusContentPayload.fromSnapshot(snap),
            new CustomPacketPayload[0]
         );
         LOGGER.debug(
            "WorldStatus payload sent to {} factions={} wars={}",
            serverPlayer.getName().getString(),
            snap.factions().size(),
            snap.wars().size()
         );
      });
   }
}
