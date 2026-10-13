package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record DiplomacyEventPopupPayload(
   String eventId, String typeName, String cultureName, String secondaryCultureName, int amount, String itemKey
) implements CustomPacketPayload {
   public static final Type<DiplomacyEventPopupPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "diplomacy_event_popup")
   );
   public static final StreamCodec<ByteBuf, DiplomacyEventPopupPayload> STREAM_CODEC = StreamCodec.of(
      DiplomacyEventPopupPayload::encode, DiplomacyEventPopupPayload::decode
   );

   private static void encode(ByteBuf buf, DiplomacyEventPopupPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.eventId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.typeName);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureName);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.secondaryCultureName);
      ByteBufCodecs.VAR_INT.encode(buf, p.amount);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.itemKey);
   }

   private static DiplomacyEventPopupPayload decode(ByteBuf buf) {
      return new DiplomacyEventPopupPayload(
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.VAR_INT.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf)
      );
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
