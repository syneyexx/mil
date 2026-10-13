package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BountyHudPayload(List<BountyHudPayload.Entry> entries) implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 32;
   public static final Type<BountyHudPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "bounty_hud"));
   public static final StreamCodec<ByteBuf, BountyHudPayload> STREAM_CODEC = StreamCodec.of(
      BountyHudPayload::encode, BountyHudPayload::decode
   );

   private static void encode(ByteBuf buf, BountyHudPayload p) {
      int count = Math.min(p.entries.size(), MAX_ENTRIES);
      ByteBufCodecs.VAR_INT.encode(buf, count);
      for (int i = 0; i < count; i++) {
         Entry e = p.entries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, e.culturePath());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.displayName());
         ByteBufCodecs.VAR_INT.encode(buf, e.amount());
      }
   }

   private static BountyHudPayload decode(ByteBuf buf) {
      int count = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_ENTRIES);
      List<Entry> entries = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
         entries.add(
            new Entry(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf)
            )
         );
      }
      return new BountyHudPayload(entries);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record Entry(String culturePath, String displayName, int amount) {
   }
}
