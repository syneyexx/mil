package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.VillageBookService;

/**
 * Client → server: regenerate village book content, preserving section/page.
 */
public record VillageBookRefreshRequestPayload(String villageId, int sectionIndex, int pageIndex, String selectedTargetId)
   implements CustomPacketPayload {
   private static final int MAX_STRING_LENGTH = 128;
   public static final Type<VillageBookRefreshRequestPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "village_book_refresh"));
   public static final StreamCodec<ByteBuf, VillageBookRefreshRequestPayload> STREAM_CODEC =
      StreamCodec.of(VillageBookRefreshRequestPayload::encode, VillageBookRefreshRequestPayload::decode);

   private static void encode(ByteBuf buf, VillageBookRefreshRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId == null ? "" : payload.villageId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.sectionIndex);
      ByteBufCodecs.VAR_INT.encode(buf, payload.pageIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.selectedTargetId == null ? "" : payload.selectedTargetId);
   }

   private static VillageBookRefreshRequestPayload decode(ByteBuf buf) {
      String villageId = truncate(ByteBufCodecs.STRING_UTF8.decode(buf));
      int sectionIndex = ByteBufCodecs.VAR_INT.decode(buf);
      int pageIndex = ByteBufCodecs.VAR_INT.decode(buf);
      String selectedTargetId = truncate(ByteBufCodecs.STRING_UTF8.decode(buf));
      return new VillageBookRefreshRequestPayload(villageId, sectionIndex, pageIndex, selectedTargetId);
   }

   private static String truncate(String value) {
      if (value == null) {
         return "";
      }
      return value.length() > MAX_STRING_LENGTH ? value.substring(0, MAX_STRING_LENGTH) : value;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(VillageBookRefreshRequestPayload payload, IPayloadContext context) {
      VillageBookService.handleRefreshPacket(payload, context);
   }
}
