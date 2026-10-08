package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.chat.NpcChatRateLimiter;
import org.millenaire.chat.NpcChatSanitize;
import org.millenaire.chat.NpcChatService;
import org.millenaire.config.MillenaireServerConfig;

public record NpcChatSendPayload(int entityId, String text, String languageCode) implements CustomPacketPayload {
   public static final Type<NpcChatSendPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "npc_chat_send"));
   public static final StreamCodec<ByteBuf, NpcChatSendPayload> STREAM_CODEC = StreamCodec.of(NpcChatSendPayload::encode, NpcChatSendPayload::decode);
   private static NpcChatRateLimiter rateLimiter = new NpcChatRateLimiter(1500L);
   private static int lastConfigured = -1;

   private static void encode(ByteBuf buf, NpcChatSendPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.text);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.languageCode);
   }

   private static NpcChatSendPayload decode(ByteBuf buf) {
      int entityId = ByteBufCodecs.VAR_INT.decode(buf);
      String text = ByteBufCodecs.STRING_UTF8.decode(buf);
      String languageCode = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (text.length() > 400 || languageCode.length() > 16) {
         return new NpcChatSendPayload(entityId, "", "");
      }
      return new NpcChatSendPayload(entityId, text, languageCode);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(NpcChatSendPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         int configured = MillenaireServerConfig.SERVER.npcChatMinMillisBetweenMessages.getAsInt();
         if (configured != lastConfigured) {
            rateLimiter = new NpcChatRateLimiter(configured);
            lastConfigured = configured;
         }
         if (!rateLimiter.acquire(player.getUUID())) {
            return;
         }
         if (payload.text().isEmpty() || !NpcChatSanitize.isValidLanguageCode(payload.languageCode())) {
            return;
         }
         NpcChatService.handleSend(player, payload.entityId(), payload.text(), payload.languageCode());
      });
   }
}
