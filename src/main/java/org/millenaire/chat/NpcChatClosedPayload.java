package org.millenaire.chat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NpcChatClosedPayload(int entityId, String reasonKey) implements CustomPacketPayload {
   public static final Type<NpcChatClosedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_closed"));
   public static final StreamCodec<ByteBuf, NpcChatClosedPayload> STREAM_CODEC = StreamCodec.of(NpcChatClosedPayload::encode, NpcChatClosedPayload::decode);

   private static void encode(ByteBuf buf, NpcChatClosedPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.reasonKey);
   }

   private static NpcChatClosedPayload decode(ByteBuf buf) {
      return new NpcChatClosedPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
