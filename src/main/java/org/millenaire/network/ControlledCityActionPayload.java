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
import org.millenaire.village.BuildingPurpose;
import org.millenaire.village.ControlledCityService;
import org.millenaire.village.ImmigrantService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record ControlledCityActionPayload(String villageUuid, int action, String buildingUuid, String purposeOrRole) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int ACTION_CALL_IMMIGRANTS = 0;
   public static final int ACTION_SET_PURPOSE = 1;
   public static final int ACTION_SET_JOB_PRIORITY = 2;
   public static final Type<ControlledCityActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_city_action"));
   public static final StreamCodec<ByteBuf, ControlledCityActionPayload> STREAM_CODEC = StreamCodec.of(
      ControlledCityActionPayload::encode, ControlledCityActionPayload::decode
   );
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   private static final int MAX_STRING = 128;

   private static void encode(ByteBuf buf, ControlledCityActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.action);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.buildingUuid != null ? payload.buildingUuid : "");
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.purposeOrRole != null ? payload.purposeOrRole : "");
   }

   private static ControlledCityActionPayload decode(ByteBuf buf) {
      String villageUuid = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      int action = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String buildingUuid = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String purposeOrRole = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      return new ControlledCityActionPayload(
         villageUuid, action, buildingUuid.isEmpty() ? null : buildingUuid, purposeOrRole.isEmpty() ? null : purposeOrRole
      );
   }

   private static String truncate(String s) {
      return s.length() > 128 ? s.substring(0, 128) : s;
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(ControlledCityActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }

         ServerLevel serverLevel = player.server.getLevel(Level.OVERWORLD);
         if (serverLevel == null) {
            return;
         }

         UUID villageId;
         try {
            villageId = UUID.fromString(payload.villageUuid);
         } catch (IllegalArgumentException e) {
            return;
         }

         Village village = VillageSavedData.get(serverLevel).getVillageManager().getVillage(new VillageId(villageId));
         if (village == null) {
            LOGGER.warn("[Millenaire] ControlledCity: village {} not found", payload.villageUuid);
            return;
         }

         if (!village.isControlledBy(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("millenaire.combat.raid_not_owner"));
            return;
         }

         if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
            LOGGER.warn("[Millenaire] ControlledCity: player {} too far from village {}", player.getName().getString(), payload.villageUuid);
            return;
         }

         if (!RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }

         switch (payload.action) {
            case ACTION_CALL_IMMIGRANTS -> {
               ImmigrantService.callImmigrants(serverLevel, village, player);
               ControlledCityService.sendRefresh(player, serverLevel, village);
            }
            case ACTION_SET_PURPOSE -> {
               if (payload.buildingUuid == null || payload.purposeOrRole == null) {
                  return;
               }

               UUID buildingUuid;
               try {
                  buildingUuid = UUID.fromString(payload.buildingUuid);
               } catch (IllegalArgumentException e) {
                  return;
               }

               BuildingInstance building = village.findBuildingById(new BuildingId(buildingUuid));
               if (building == null) {
                  return;
               }

               building.setPurpose(BuildingPurpose.fromName(payload.purposeOrRole));
               village.markCityStatsDirty();
               village.markDirty();
               VillageSavedData.get(serverLevel).setDirty();
               ControlledCityService.sendRefresh(player, serverLevel, village);
            }
            case ACTION_SET_JOB_PRIORITY -> {
               String role = payload.purposeOrRole;
               village.setJobPriorityRole(role == null || role.isBlank() ? null : role.trim());
               VillageSavedData.get(serverLevel).setDirty();
               ControlledCityService.sendRefresh(player, serverLevel, village);
            }
            default -> LOGGER.warn("[Millenaire] ControlledCity: unknown action {}", payload.action);
         }
      });
   }
}
