package org.millenaire.living.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.living.actions.NpcActionRegistry;
import org.millenaire.living.actions.NpcActionResult;
import org.millenaire.living.actions.NpcActionType;

public record LivingActionRequestPayload(int entityId, String actionType, String targetId) implements CustomPacketPayload {
   public static final Type<LivingActionRequestPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "living_action_request"));
   public static final StreamCodec<ByteBuf, LivingActionRequestPayload> STREAM_CODEC =
      StreamCodec.of(LivingActionRequestPayload::encode, LivingActionRequestPayload::decode);

   private static void encode(ByteBuf buf, LivingActionRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.actionType == null ? "" : payload.actionType);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.targetId == null ? "" : payload.targetId);
   }

   private static LivingActionRequestPayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      String actionType = ByteBufCodecs.STRING_UTF8.decode(buf);
      String targetId = ByteBufCodecs.STRING_UTF8.decode(buf);
      return new LivingActionRequestPayload(entityId, actionType, targetId);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(LivingActionRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         NpcActionType type;
         try {
            type = NpcActionType.valueOf(payload.actionType());
         } catch (Exception e) {
            // Single feedback owner for living actions.
            LivingActionFeedbackPayload.send(
               player,
               payload.entityId(),
               NpcActionResult.fail("living.millenaire.action.error.unknown_type")
            );
            return;
         }
         String target = payload.targetId() == null || payload.targetId().isBlank() ? null : payload.targetId();
         NpcActionResult result = NpcActionRegistry.executeDirect(player.serverLevel(), player, payload.entityId(), type, target);
         // Single player-facing feedback channel for living actions.
         LivingActionFeedbackPayload.send(player, payload.entityId(), result);
         org.millenaire.living.context.LivingDialogueSupport.sendActionChoices(player, payload.entityId());
      });
   }
}
