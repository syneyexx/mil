package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.culture.VillageType;
import org.millenaire.item.ModItems;
import org.slf4j.Logger;

/**
 * Player claim → name → sovereign PLAYER faction settlement (not instant NPC spawn).
 */
public record SettlementFoundingRequestPayload(BlockPos pos, String cultureKey, String typeKey, String settlementName)
   implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<SettlementFoundingRequestPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "settlement_founding_request"));
   public static final StreamCodec<ByteBuf, SettlementFoundingRequestPayload> STREAM_CODEC =
      StreamCodec.of(SettlementFoundingRequestPayload::encode, SettlementFoundingRequestPayload::decode);
   private static final int MAX_KEY_LENGTH = 128;
   private static final int MAX_NAME_LENGTH = 40;
   private static final double MAX_REQUEST_DISTANCE = 16.0;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(1000L);

   private static void encode(ByteBuf buf, SettlementFoundingRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.typeKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.settlementName);
   }

   private static SettlementFoundingRequestPayload decode(ByteBuf buf) {
      int x = ByteBufCodecs.VAR_INT.decode(buf);
      int y = ByteBufCodecs.VAR_INT.decode(buf);
      int z = ByteBufCodecs.VAR_INT.decode(buf);
      String cultureKey = truncate(ByteBufCodecs.STRING_UTF8.decode(buf), MAX_KEY_LENGTH);
      String typeKey = truncate(ByteBufCodecs.STRING_UTF8.decode(buf), MAX_KEY_LENGTH);
      String settlementName = truncate(ByteBufCodecs.STRING_UTF8.decode(buf), MAX_NAME_LENGTH);
      return new SettlementFoundingRequestPayload(new BlockPos(x, y, z), cultureKey, typeKey, settlementName);
   }

   private static String truncate(String value, int max) {
      if (value == null) {
         return "";
      }
      return value.length() > max ? value.substring(0, max) : value;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(SettlementFoundingRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
         }
         if (!RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }
         boolean holdsWand = player.getMainHandItem().is((Item) ModItems.SUMMONING_WAND.get())
            || player.getOffhandItem().is((Item) ModItems.SUMMONING_WAND.get());
         if (!holdsWand) {
            LOGGER.warn("[Millenaire] Player {} sent settlement founding without wand", player.getName().getString());
            return;
         }
         if (!ServerPayloadGuards.isWithinDistance(player, payload.pos, MAX_REQUEST_DISTANCE)) {
            LOGGER.warn("[Millenaire] Settlement founding position too far for {}", player.getName().getString());
            return;
         }

         VillageType villageType = PlayerSettlementFoundingService.resolveType(payload.cultureKey, payload.typeKey);
         if (villageType == null) {
            player.sendSystemMessage(Component.translatable("ui.wand.village_type_not_found", payload.typeKey));
            return;
         }

         PlayerSettlementFoundingService.FoundResult result =
            PlayerSettlementFoundingService.claimAndFound(level, player, payload.pos, villageType, payload.settlementName);
         if (result.message() != null) {
            player.sendSystemMessage(result.message());
         }
         if (result.success()) {
            MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
         }
      });
   }
}
