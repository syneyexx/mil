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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.item.VillageCreateWandItem;
import org.millenaire.village.PlayerBuildingDesignationService;
import org.millenaire.village.PlayerBuildingRole;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record DesignateBuildingPayload(String villageUuid, String roleId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<DesignateBuildingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "designate_building"));
   public static final StreamCodec<ByteBuf, DesignateBuildingPayload> STREAM_CODEC = StreamCodec.of(
      DesignateBuildingPayload::encode,
      DesignateBuildingPayload::decode
   );
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(250L);
   private static final int MAX_STRING = 64;

   private static void encode(ByteBuf buf, DesignateBuildingPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.roleId);
   }

   private static DesignateBuildingPayload decode(ByteBuf buf) {
      String villageUuid = truncate(ByteBufCodecs.STRING_UTF8.decode(buf));
      String roleId = truncate(ByteBufCodecs.STRING_UTF8.decode(buf));
      return new DesignateBuildingPayload(villageUuid, roleId);
   }

   private static String truncate(String s) {
      return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(DesignateBuildingPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel serverLevel = player.server.getLevel(Level.OVERWORLD);
         if (serverLevel == null) {
            return;
         }
         if (!RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }

         UUID villageId;
         try {
            villageId = UUID.fromString(payload.villageUuid);
         } catch (IllegalArgumentException e) {
            return;
         }

         Village village = VillageSavedData.get(serverLevel).getVillageManager().getVillage(new VillageId(villageId));
         if (village == null || !village.isControlledBy(player.getUUID())) {
            LOGGER.warn("[Designate] invalid village or owner for {}", player.getName().getString());
            return;
         }

         Village owned = VillageCreateWandItem.findOwnedVillage(serverLevel, player.getUUID());
         if (owned == null || !owned.getId().equals(village.getId())) {
            return;
         }

         if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
            return;
         }

         PlayerBuildingRole role = PlayerBuildingRole.fromId(payload.roleId);
         if (role == null) {
            LOGGER.warn("[Designate] unknown role {}", payload.roleId);
            return;
         }

         PlayerBuildingDesignationService.designate(player, village, role);
      });
   }
}
