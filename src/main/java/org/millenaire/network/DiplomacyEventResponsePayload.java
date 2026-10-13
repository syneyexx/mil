package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.diplomacy.DiplomacyEventService;

public record DiplomacyEventResponsePayload(String eventId, boolean accept) implements CustomPacketPayload {
   public static final Type<DiplomacyEventResponsePayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "diplomacy_event_response")
   );
   public static final StreamCodec<ByteBuf, DiplomacyEventResponsePayload> STREAM_CODEC = StreamCodec.of(
      DiplomacyEventResponsePayload::encode, DiplomacyEventResponsePayload::decode
   );
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(300L);

   private static void encode(ByteBuf buf, DiplomacyEventResponsePayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.eventId);
      buf.writeBoolean(p.accept);
   }

   private static DiplomacyEventResponsePayload decode(ByteBuf buf) {
      return new DiplomacyEventResponsePayload(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean());
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(DiplomacyEventResponsePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         if (!RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }
         ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
         if (overworld == null) {
            return;
         }
         try {
            UUID id = UUID.fromString(payload.eventId);
            DiplomacyEventService.respond(player, overworld, id, payload.accept);
         } catch (IllegalArgumentException ignored) {
         }
      });
   }
}
