package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record UnlockingToastPayload(String category, String title, String cultureName, int nbUnlocked, int nbTotal, String iconId)
   implements CustomPacketPayload {
   public static final Type<UnlockingToastPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "unlocking_toast"));
   public static final StreamCodec<ByteBuf, UnlockingToastPayload> STREAM_CODEC = StreamCodec.of(UnlockingToastPayload::encode, UnlockingToastPayload::decode);
   private static final int MAX_STR = 256;

   private static void encode(ByteBuf buf, UnlockingToastPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.category);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.title);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureName);
      ByteBufCodecs.VAR_INT.encode(buf, p.nbUnlocked);
      ByteBufCodecs.VAR_INT.encode(buf, p.nbTotal);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.iconId);
   }

   private static UnlockingToastPayload decode(ByteBuf buf) {
      String category = clamp((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String title = clamp((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String cultureName = clamp((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      int nbUnlocked = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int nbTotal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String iconId = clamp((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      return new UnlockingToastPayload(category, title, cultureName, nbUnlocked, nbTotal, iconId);
   }

   private static String clamp(String s) {
      return s.length() > 256 ? s.substring(0, 256) : s;
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
