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
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.village.DesignatedBuildingService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record UndesignateBuildingRequestPayload(String villageUuid, String buildingId) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<UndesignateBuildingRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "undesignate_building_request")
   );
   public static final StreamCodec<ByteBuf, UndesignateBuildingRequestPayload> STREAM_CODEC = StreamCodec.of(
      UndesignateBuildingRequestPayload::encode, UndesignateBuildingRequestPayload::decode
   );

   private static void encode(ByteBuf buf, UndesignateBuildingRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.buildingId);
   }

   private static UndesignateBuildingRequestPayload decode(ByteBuf buf) {
      String villageUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String buildingId = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (buildingId.length() > 64) {
         buildingId = buildingId.substring(0, 64);
      }

      return new UndesignateBuildingRequestPayload(villageUuid, buildingId);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(UndesignateBuildingRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (!(context.player() instanceof ServerPlayer player)) {
               return;
            }

            if (!(player.level() instanceof ServerLevel serverLevel)) {
               return;
            }

            ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
               return;
            }

            UUID villageUuid;
            UUID buildingUuid;
            try {
               villageUuid = UUID.fromString(payload.villageUuid);
               buildingUuid = UUID.fromString(payload.buildingId);
            } catch (IllegalArgumentException e) {
               return;
            }

            VillageSavedData savedData = VillageSavedData.get(overworld);
            Village village = savedData.getVillageManager().getVillage(new VillageId(villageUuid));
            if (village == null) {
               LOGGER.warn("[Millenaire] UndesignateBuilding: village {} not found", payload.villageUuid);
               return;
            }

            if (!village.isControlledBy(player.getUUID()) && !player.hasPermissions(2)) {
               player.sendSystemMessage(Component.translatable(DesignatedBuildingService.errorKey(DesignatedBuildingService.FailureReason.NOT_OWNER)));
               return;
            }

            if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
               player.sendSystemMessage(Component.translatable(DesignatedBuildingService.errorKey(DesignatedBuildingService.FailureReason.TOO_FAR)));
               return;
            }

            if (!RATE_LIMITER.acquire(player.getUUID())) {
               return;
            }

            BuildingInstance building = village.getBuilding(new BuildingId(buildingUuid));
            if (building == null || !building.isPlayerDesignated()) {
               player.sendSystemMessage(Component.translatable("gui.millenaire.designate.undesignate_missing"));
               return;
            }

            if (DesignatedBuildingService.undesignate(overworld, village, building)) {
               player.sendSystemMessage(Component.translatable("gui.millenaire.designate.undesignate_success"));
            } else {
               player.sendSystemMessage(Component.translatable("gui.millenaire.designate.undesignate_failed"));
            }
         }
      );
   }
}
