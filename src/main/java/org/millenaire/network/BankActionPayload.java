package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillageTreasuryService;
import org.slf4j.Logger;

public record BankActionPayload(String villageUuid, int action, int amount) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int ACTION_DEPOSIT = 0;
   public static final int ACTION_WITHDRAW = 1;
   public static final Type<BankActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "bank_action"));
   public static final StreamCodec<ByteBuf, BankActionPayload> STREAM_CODEC = StreamCodec.of(BankActionPayload::encode, BankActionPayload::decode);
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);

   private static void encode(ByteBuf buf, BankActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.action);
      ByteBufCodecs.VAR_INT.encode(buf, payload.amount);
   }

   private static BankActionPayload decode(ByteBuf buf) {
      String villageUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
      int action = ByteBufCodecs.VAR_INT.decode(buf);
      int amount = ByteBufCodecs.VAR_INT.decode(buf);
      return new BankActionPayload(villageUuid, action, amount);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BankActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel level = player.server.getLevel(Level.OVERWORLD);
         if (level == null || !RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }

         UUID villageId;
         try {
            villageId = UUID.fromString(payload.villageUuid());
         } catch (IllegalArgumentException e) {
            return;
         }

         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageId));
         if (village == null || !village.isControlledBy(player.getUUID())) {
            return;
         }
         if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
            return;
         }

         int amount = Math.max(0, Math.min(payload.amount(), 1_000_000));
         if (amount <= 0) {
            return;
         }

         if (payload.action() == ACTION_DEPOSIT) {
            if (!MoneyHelper.removeDeniers(player.getInventory(), amount)) {
               player.sendSystemMessage(Component.translatable("message.millenaire.bank.not_enough_player"));
               return;
            }
            int deposited = VillageTreasuryService.deposit(level, village, amount, VillageTreasuryService.REASON_PLAYER_DEPOSIT);
            if (deposited < amount) {
               MoneyHelper.addDeniers(player.getInventory(), amount - deposited, player);
            }
            player.sendSystemMessage(
               Component.translatable("message.millenaire.bank.deposited", MoneyHelper.formatPrice(deposited))
            );
         } else if (payload.action() == ACTION_WITHDRAW) {
            if (!VillageTreasuryService.withdraw(level, village, amount, VillageTreasuryService.REASON_PLAYER_WITHDRAW)) {
               player.sendSystemMessage(Component.translatable("message.millenaire.bank.not_enough_treasury"));
               return;
            }
            MoneyHelper.addDeniers(player.getInventory(), amount, player);
            player.sendSystemMessage(
               Component.translatable("message.millenaire.bank.withdrawn", MoneyHelper.formatPrice(amount))
            );
         } else {
            LOGGER.warn("[Bank] unknown action {} from {}", payload.action(), player.getName().getString());
         }
      });
   }
}
