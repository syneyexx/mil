package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.chat.NpcChatRateLimiter;
import org.millenaire.chat.NpcChatService;

public record NpcChatOpenRequestPayload(int entityId) implements CustomPacketPayload {
   public static final Type<NpcChatOpenRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_open"));
   public static final StreamCodec<ByteBuf, NpcChatOpenRequestPayload> STREAM_CODEC = StreamCodec.of(
      NpcChatOpenRequestPayload::encode, NpcChatOpenRequestPayload::decode
   );
   private static final NpcChatRateLimiter RATE = new NpcChatRateLimiter(500L);

   private static void encode(ByteBuf buf, NpcChatOpenRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
   }

   private static NpcChatOpenRequestPayload decode(ByteBuf buf) {
      return new NpcChatOpenRequestPayload(ByteBufCodecs.VAR_INT.decode(buf));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(NpcChatOpenRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (!RATE.acquire(player.getUUID())) {
               // Avoid opening storms; closed payload clears any client wait without a session.
               net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                  player,
                  new org.millenaire.chat.NpcChatClosedPayload(payload.entityId(), "gui.millenaire.npcchat.rate_limited"),
                  new CustomPacketPayload[0]
               );
               return;
            }
            NpcChatService.handleOpen(player, payload.entityId());
         }
      });
   }
}
