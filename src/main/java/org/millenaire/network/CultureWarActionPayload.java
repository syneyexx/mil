package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.ControlledMilitaryService;
import org.millenaire.village.CultureWarService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public record CultureWarActionPayload(String villageUuid, int action, String otherCultureId) implements CustomPacketPayload {
   public static final int ACTION_DECLARE_WAR = 0;
   public static final int ACTION_END_WAR = 1;
   public static final Type<CultureWarActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "culture_war_action"));
   public static final StreamCodec<ByteBuf, CultureWarActionPayload> STREAM_CODEC = StreamCodec.of(
      CultureWarActionPayload::encode, CultureWarActionPayload::decode
   );

   private static void encode(ByteBuf buf, CultureWarActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.action);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.otherCultureId);
   }

   private static CultureWarActionPayload decode(ByteBuf buf) {
      return new CultureWarActionPayload(
         (String)ByteBufCodecs.STRING_UTF8.decode(buf), (Integer)ByteBufCodecs.VAR_INT.decode(buf), (String)ByteBufCodecs.STRING_UTF8.decode(buf)
      );
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(CultureWarActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }

         if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
         }

         VillageId villageId;
         ResourceLocation otherCulture;
         try {
            villageId = new VillageId(java.util.UUID.fromString(payload.villageUuid));
            otherCulture = ResourceLocation.parse(payload.otherCultureId);
         } catch (IllegalArgumentException e) {
            return;
         }

         VillageManager vm = VillageSavedData.get(serverLevel).getVillageManager();
         Village village = vm.getVillage(villageId);
         if (village == null || village.getOwnerUUID() == null || !village.getOwnerUUID().equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("millenaire.combat.raid_not_owner"));
            return;
         }

         ResourceLocation ownCulture = village.getCultureId();
         if (!CultureWarService.canPlayerDeclareFor(player, ownCulture, serverLevel)) {
            player.sendSystemMessage(Component.translatable("millenaire.culturewar.not_authorized"));
            return;
         }

         if (ownCulture.equals(otherCulture)) {
            return;
         }

         if (payload.action == ACTION_DECLARE_WAR) {
            CultureWarService.playerDeclareWar(serverLevel, ownCulture, otherCulture);
         } else if (payload.action == ACTION_END_WAR) {
            CultureWarService.playerEndWar(serverLevel, ownCulture, otherCulture);
         } else {
            return;
         }

         VillageSavedData.get(serverLevel).setDirty();
         ControlledMilitaryService.sendRefresh(player, serverLevel, village);
      });
   }
}
