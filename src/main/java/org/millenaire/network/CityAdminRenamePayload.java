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
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.civilization.presentation.CivilizationPresentationService;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.item.ModItems;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageRenameService;
import org.millenaire.village.VillageSavedData;

public record CityAdminRenamePayload(String villageId, String newName, int tabIndex) implements CustomPacketPayload {
   private static final int MAX = 128;
   public static final Type<CityAdminRenamePayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "city_admin_rename"));
   public static final StreamCodec<ByteBuf, CityAdminRenamePayload> STREAM_CODEC =
      StreamCodec.of(CityAdminRenamePayload::encode, CityAdminRenamePayload::decode);

   private static void encode(ByteBuf buf, CityAdminRenamePayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.villageId == null ? "" : p.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.newName == null ? "" : p.newName);
      ByteBufCodecs.VAR_INT.encode(buf, p.tabIndex);
   }

   private static CityAdminRenamePayload decode(ByteBuf buf) {
      String id = ByteBufCodecs.STRING_UTF8.decode(buf);
      String name = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (id.length() > MAX) {
         id = id.substring(0, MAX);
      }
      if (name.length() > 40) {
         name = name.substring(0, 40);
      }
      return new CityAdminRenamePayload(id, name, ByteBufCodecs.VAR_INT.decode(buf));
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(CityAdminRenamePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
         }
         if (!player.getMainHandItem().is((Item) ModItems.CITY_ADMIN_WAND.get())
            && !player.getOffhandItem().is((Item) ModItems.CITY_ADMIN_WAND.get())) {
            return;
         }
         UUID uuid;
         try {
            uuid = UUID.fromString(payload.villageId);
         } catch (IllegalArgumentException e) {
            return;
         }
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(uuid));
         if (village == null) {
            return;
         }
         VillageRenameService.RenameResult result = VillageRenameService.rename(level, player, village, payload.newName);
         player.sendSystemMessage(result.message());
         CivilizationVillageSnapshot snap = CivilizationPresentationService.villageSnapshot(level, village, player);
         PacketDistributor.sendToPlayer(player, CityAdminPayload.fromSnapshot(snap, payload.tabIndex));
      });
   }
}
