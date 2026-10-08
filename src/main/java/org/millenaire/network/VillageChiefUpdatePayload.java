package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record VillageChiefUpdatePayload(VillageChiefPayload.ChiefDynamic dynamic) implements CustomPacketPayload {
   public static final Type<VillageChiefUpdatePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_chief_update"));
   public static final StreamCodec<ByteBuf, VillageChiefUpdatePayload> STREAM_CODEC = StreamCodec.of(
      (buf, payload) -> VillageChiefPayload.encodeDynamic(buf, payload.dynamic), buf -> new VillageChiefUpdatePayload(VillageChiefPayload.decodeDynamic(buf))
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
