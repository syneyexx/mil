package org.millenaire.chat;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record NpcChatOpenPayload(int entityId, String villagerName, String roleLine, String reputationLabel, List<HistoryLine> history)
   implements CustomPacketPayload {
   public static final Type<NpcChatOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_open"));
   public static final StreamCodec<ByteBuf, NpcChatOpenPayload> STREAM_CODEC = StreamCodec.of(NpcChatOpenPayload::encode, NpcChatOpenPayload::decode);

   private static void encode(ByteBuf buf, NpcChatOpenPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villagerName);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.roleLine);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.reputationLabel);
      ByteBufCodecs.VAR_INT.encode(buf, payload.history.size());
      for (HistoryLine line : payload.history) {
         buf.writeByte(line.speaker());
         ByteBufCodecs.STRING_UTF8.encode(buf, line.text());
      }
   }

   private static NpcChatOpenPayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      String name = ByteBufCodecs.STRING_UTF8.decode(buf);
      String role = ByteBufCodecs.STRING_UTF8.decode(buf);
      String rep = ByteBufCodecs.STRING_UTF8.decode(buf);
      int count = ByteBufCodecs.VAR_INT.decode(buf);
      List<HistoryLine> history = new ArrayList<>();
      int max = Math.min(count, 12);
      for (int i = 0; i < count; i++) {
         byte speaker = buf.readByte();
         String text = ByteBufCodecs.STRING_UTF8.decode(buf);
         if (i < max) {
            history.add(new HistoryLine(speaker, text));
         }
      }
      return new NpcChatOpenPayload(entityId, name, role, rep, history);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record HistoryLine(byte speaker, String text) {
   }
}
