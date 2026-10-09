package org.millenaire.living.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.client.gui.NpcChatScreen;
import org.millenaire.living.actions.NpcActionResult;

public record LivingActionFeedbackPayload(int entityId, boolean success, String messageKey, String detail) implements CustomPacketPayload {
   public static final Type<LivingActionFeedbackPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "living_action_feedback"));
   public static final StreamCodec<ByteBuf, LivingActionFeedbackPayload> STREAM_CODEC =
      StreamCodec.of(LivingActionFeedbackPayload::encode, LivingActionFeedbackPayload::decode);

   private static void encode(ByteBuf buf, LivingActionFeedbackPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      buf.writeBoolean(payload.success);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.messageKey == null ? "" : payload.messageKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.detail == null ? "" : payload.detail);
   }

   private static LivingActionFeedbackPayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      boolean success = buf.readBoolean();
      String key = ByteBufCodecs.STRING_UTF8.decode(buf);
      String detail = ByteBufCodecs.STRING_UTF8.decode(buf);
      return new LivingActionFeedbackPayload(entityId, success, key, detail);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void send(ServerPlayer player, int entityId, NpcActionResult result) {
      PacketDistributor.sendToPlayer(
         player,
         new LivingActionFeedbackPayload(entityId, result.success(), result.messageKey(), result.detail() == null ? "" : result.detail()),
         new CustomPacketPayload[0]
      );
   }

   public static void handleOnClient(LivingActionFeedbackPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         NpcChatScreen.handleLivingFeedback(payload);
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && payload.detail() != null && !payload.detail().isBlank()) {
            mc.player.displayClientMessage(Component.literal(payload.detail()), false);
         }
      });
   }
}
