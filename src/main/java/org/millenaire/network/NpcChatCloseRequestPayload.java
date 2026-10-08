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

public record NpcChatCloseRequestPayload(int entityId) implements CustomPacketPayload {
   public static final Type<NpcChatCloseRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_close"));
   public static final StreamCodec<ByteBuf, NpcChatCloseRequestPayload> STREAM_CODEC = StreamCodec.of(
      NpcChatCloseRequestPayload::encode, NpcChatCloseRequestPayload::decode
   );
   private static final NpcChatRateLimiter RATE = new NpcChatRateLimiter(200L);

   private static void encode(ByteBuf buf, NpcChatCloseRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
   }

   private static NpcChatCloseRequestPayload decode(ByteBuf buf) {
      return new NpcChatCloseRequestPayload(ByteBufCodecs.VAR_INT.decode(buf));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(NpcChatCloseRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (!RATE.acquire(player.getUUID())) {
               return;
            }
            NpcChatService.handleClose(player, payload.entityId());
         }
      });
   }
}
