package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

/** Server → client: active wanted bounty lines for HUD. */
public record BountyHudPayload(List<BountyHudPayload.Entry> entries) implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 16;
   private static final int MAX_STRING = 64;
   public static final Type<BountyHudPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "bounty_hud"));
   public static final StreamCodec<ByteBuf, BountyHudPayload> STREAM_CODEC =
      StreamCodec.of(BountyHudPayload::encode, BountyHudPayload::decode);

   public record Entry(String cultureId, String displayName, int score, String tier) {
   }

   private static void encode(ByteBuf buf, BountyHudPayload payload) {
      List<Entry> list = payload.entries() == null ? List.of() : payload.entries();
      int count = Math.min(MAX_ENTRIES, list.size());
      ByteBufCodecs.VAR_INT.encode(buf, count);
      for (int i = 0; i < count; i++) {
         Entry e = list.get(i);
         ByteBufCodecs.stringUtf8(MAX_STRING).encode(buf, e.cultureId() == null ? "" : e.cultureId());
         ByteBufCodecs.stringUtf8(MAX_STRING).encode(buf, e.displayName() == null ? "" : e.displayName());
         ByteBufCodecs.VAR_INT.encode(buf, e.score());
         ByteBufCodecs.stringUtf8(MAX_STRING).encode(buf, e.tier() == null ? "" : e.tier());
      }
   }

   private static BountyHudPayload decode(ByteBuf buf) {
      int count = ByteBufCodecs.VAR_INT.decode(buf);
      List<Entry> list = new ArrayList<>(Math.min(MAX_ENTRIES, Math.max(0, count)));
      for (int i = 0; i < count && i < MAX_ENTRIES; i++) {
         String cultureId = ByteBufCodecs.stringUtf8(MAX_STRING).decode(buf);
         String displayName = ByteBufCodecs.stringUtf8(MAX_STRING).decode(buf);
         int score = ByteBufCodecs.VAR_INT.decode(buf);
         String tier = ByteBufCodecs.stringUtf8(MAX_STRING).decode(buf);
         list.add(new Entry(cultureId, displayName, score, tier));
      }
      return new BountyHudPayload(List.copyOf(list));
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
