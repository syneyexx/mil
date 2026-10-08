package org.millenaire.chat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NpcChatLinePayload(int entityId, byte speaker, String text, boolean pending) implements CustomPacketPayload {
   public static final Type<NpcChatLinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_line"));
   public static final StreamCodec<ByteBuf, NpcChatLinePayload> STREAM_CODEC = StreamCodec.of(NpcChatLinePayload::encode, NpcChatLinePayload::decode);

   private static void encode(ByteBuf buf, NpcChatLinePayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      buf.writeByte(payload.speaker);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.text);
      buf.writeBoolean(payload.pending);
   }

   private static NpcChatLinePayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      byte speaker = buf.readByte();
      String text = ByteBufCodecs.STRING_UTF8.decode(buf);
      boolean pending = buf.readBoolean();
      return new NpcChatLinePayload(entityId, speaker, text, pending);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
