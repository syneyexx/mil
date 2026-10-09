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
 * Client → server: execute a civilization action from the village book.
 * Server re-authorizes; {@code requestId} dedupes double-clicks.
 * {@code sectionIndex}/{@code pageIndex} restore book navigation after refresh.
 */
public record VillageBookActionPayload(
   String villageId, String actionId, String targetId, String requestId, int sectionIndex, int pageIndex
) implements CustomPacketPayload {
   private static final int MAX_STRING_LENGTH = 128;
   public static final Type<VillageBookActionPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "village_book_action"));
   public static final StreamCodec<ByteBuf, VillageBookActionPayload> STREAM_CODEC =
      StreamCodec.of(VillageBookActionPayload::encode, VillageBookActionPayload::decode);

   public VillageBookActionPayload(String villageId, String actionId, String targetId, String requestId) {
      this(villageId, actionId, targetId, requestId, 6, 0);
   }

   private static void encode(ByteBuf buf, VillageBookActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId == null ? "" : payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.actionId == null ? "" : payload.actionId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.targetId == null ? "" : payload.targetId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.requestId == null ? "" : payload.requestId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.sectionIndex);
      ByteBufCodecs.VAR_INT.encode(buf, payload.pageIndex);
   }

   private static VillageBookActionPayload decode(ByteBuf buf) {
      return new VillageBookActionPayload(
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         ByteBufCodecs.VAR_INT.decode(buf),
         ByteBufCodecs.VAR_INT.decode(buf)
      );
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

   public static void handleOnServer(VillageBookActionPayload payload, IPayloadContext context) {
      VillageBookService.handleActionPacket(payload, context);
   }
}
