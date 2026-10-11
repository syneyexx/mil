package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.civilization.presentation.CivilizationActionId;
import org.millenaire.civilization.presentation.CivilizationActionService;
import org.millenaire.civilization.presentation.CivilizationPresentationService;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.item.ModItems;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record CityAdminActionPayload(String villageId, String actionId, String targetId, int tabIndex)
   implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX = 128;
   public static final Type<CityAdminActionPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "city_admin_action"));
   public static final StreamCodec<ByteBuf, CityAdminActionPayload> STREAM_CODEC =
      StreamCodec.of(CityAdminActionPayload::encode, CityAdminActionPayload::decode);

   private static void encode(ByteBuf buf, CityAdminActionPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.villageId == null ? "" : p.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.actionId == null ? "" : p.actionId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.targetId == null ? "" : p.targetId);
      ByteBufCodecs.VAR_INT.encode(buf, p.tabIndex);
   }

   private static CityAdminActionPayload decode(ByteBuf buf) {
      return new CityAdminActionPayload(
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         ByteBufCodecs.VAR_INT.decode(buf)
      );
   }

   private static String truncate(String v) {
      if (v == null) {
         return "";
      }
      return v.length() > MAX ? v.substring(0, MAX) : v;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(CityAdminActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
         }
         if (!holdsWand(player)) {
            return;
         }
         UUID uuid;
         try {
            uuid = UUID.fromString(payload.villageId);
         } catch (IllegalArgumentException e) {
            LOGGER.warn("City admin action: bad village id {}", payload.villageId);
            return;
         }
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(uuid));
         if (village == null) {
            return;
         }
         CivilizationActionId actionId;
         try {
            actionId = CivilizationActionId.valueOf(payload.actionId);
         } catch (IllegalArgumentException e) {
            return;
         }
         CivilizationActionService.ActionResult result =
            CivilizationActionService.execute(level, player, village, actionId, payload.targetId, null, false);
         if (actionId == CivilizationActionId.OPEN_CONTROLLED_PROJECTS) {
            // ControlledProjectsPayload already opens the projects UI; do not re-open City Admin over it.
            return;
         }
         CivilizationVillageSnapshot snap = CivilizationPresentationService.villageSnapshot(level, village, player);
         String feedback = result == null || result.messageKey() == null ? "" : result.messageKey();
         PacketDistributor.sendToPlayer(player, CityAdminPayload.fromSnapshot(snap, payload.tabIndex, feedback));
      });
   }

   private static boolean holdsWand(ServerPlayer player) {
      return player.getMainHandItem().is((Item) ModItems.CITY_ADMIN_WAND.get())
         || player.getOffhandItem().is((Item) ModItems.CITY_ADMIN_WAND.get());
   }
}
