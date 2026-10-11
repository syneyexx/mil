package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
import org.millenaire.building.BuildingPurpose;
import org.millenaire.village.DesignatedBuildingService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record DesignateBuildingRequestPayload(String villageUuid, String purposeId, BlockPos cornerA, BlockPos cornerB)
   implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<DesignateBuildingRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "designate_building_request")
   );
   public static final StreamCodec<ByteBuf, DesignateBuildingRequestPayload> STREAM_CODEC = StreamCodec.of(
      DesignateBuildingRequestPayload::encode, DesignateBuildingRequestPayload::decode
   );

   private static void encode(ByteBuf buf, DesignateBuildingRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.purposeId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerA.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerA.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerA.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerB.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerB.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.cornerB.getZ());
   }

   private static DesignateBuildingRequestPayload decode(ByteBuf buf) {
      String villageUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String purposeId = ByteBufCodecs.STRING_UTF8.decode(buf);
      if (purposeId.length() > 32) {
         purposeId = purposeId.substring(0, 32);
      }

      int ax = ByteBufCodecs.VAR_INT.decode(buf);
      int ay = ByteBufCodecs.VAR_INT.decode(buf);
      int az = ByteBufCodecs.VAR_INT.decode(buf);
      int bx = ByteBufCodecs.VAR_INT.decode(buf);
      int by = ByteBufCodecs.VAR_INT.decode(buf);
      int bz = ByteBufCodecs.VAR_INT.decode(buf);
      return new DesignateBuildingRequestPayload(villageUuid, purposeId, new BlockPos(ax, ay, az), new BlockPos(bx, by, bz));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(DesignateBuildingRequestPayload payload, IPayloadContext context) {
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

            UUID uuid;
            try {
               uuid = UUID.fromString(payload.villageUuid);
            } catch (IllegalArgumentException e) {
               return;
            }

            VillageSavedData savedData = VillageSavedData.get(overworld);
            Village village = savedData.getVillageManager().getVillage(new VillageId(uuid));
            if (village == null) {
               LOGGER.warn("[Millenaire] DesignateBuilding: village {} not found", payload.villageUuid);
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

            BuildingPurpose purpose = BuildingPurpose.fromId(payload.purposeId);
            if (purpose == null) {
               player.sendSystemMessage(Component.translatable(DesignatedBuildingService.errorKey(DesignatedBuildingService.FailureReason.INVALID_PURPOSE)));
               return;
            }

            DesignatedBuildingService.designate(overworld, village, player, purpose, payload.cornerA, payload.cornerB);
         }
      );
   }
}
