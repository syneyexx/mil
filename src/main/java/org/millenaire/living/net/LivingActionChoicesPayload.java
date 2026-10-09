package org.millenaire.living.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record LivingActionChoicesPayload(int entityId, List<String> actionTypes, String activeRequestId) implements CustomPacketPayload {
   public static final Type<LivingActionChoicesPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "living_action_choices"));
   public static final StreamCodec<ByteBuf, LivingActionChoicesPayload> STREAM_CODEC =
      StreamCodec.of(LivingActionChoicesPayload::encode, LivingActionChoicesPayload::decode);

   private static void encode(ByteBuf buf, LivingActionChoicesPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.actionTypes.size());
      for (String action : payload.actionTypes) {
         ByteBufCodecs.STRING_UTF8.encode(buf, action);
      }
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.activeRequestId == null ? "" : payload.activeRequestId);
   }

   private static LivingActionChoicesPayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      int count = ByteBufCodecs.VAR_INT.decode(buf);
      List<String> actions = new ArrayList<>();
      int max = Math.min(count, 16);
      for (int i = 0; i < count; i++) {
         String action = ByteBufCodecs.STRING_UTF8.decode(buf);
         if (i < max) {
            actions.add(action);
         }
      }
      String requestId = ByteBufCodecs.STRING_UTF8.decode(buf);
      return new LivingActionChoicesPayload(entityId, actions, requestId);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
