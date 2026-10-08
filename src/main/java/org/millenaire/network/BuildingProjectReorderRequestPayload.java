package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record BuildingProjectReorderRequestPayload(String villageUuid, int from, int to, String planSetId, int plannedX, int plannedY, int plannedZ)
   implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<BuildingProjectReorderRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_reorder_request")
   );
   public static final StreamCodec<ByteBuf, BuildingProjectReorderRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectReorderRequestPayload::encode, BuildingProjectReorderRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectReorderRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.from);
      ByteBufCodecs.VAR_INT.encode(buf, payload.to);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedX);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedY);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedZ);
   }

   private static BuildingProjectReorderRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      int from = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int to = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      int plannedX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int plannedY = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int plannedZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      return new BuildingProjectReorderRequestPayload(villageUuid, from, to, planSetId, plannedX, plannedY, plannedZ);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingProjectReorderRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
                  if (overworld != null) {
                     UUID uuid;
                     try {
                        uuid = UUID.fromString(payload.villageUuid);
                     } catch (IllegalArgumentException e) {
                        return;
                     }

                     VillageSavedData savedData = VillageSavedData.get(overworld);
                     Village village = savedData.getVillageManager().getVillage(new VillageId(uuid));
                     if (village != null) {
                        if (!village.isControlledBy(player.getUUID())) {
                           LOGGER.warn(
                              "[Millenaire] BuildingProjectReorder: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid
                           );
                        } else if (ServerPayloadGuards.isWithinVillageReach(player, village)) {
                           if (RATE_LIMITER.acquire(player.getUUID())) {
                              List<ControlledQueuedProject> queue = village.getControlledQueue();
                              if (payload.from >= 0
                                 && payload.from < queue.size()
                                 && queue.get(payload.from).matchesEcho(payload.planSetId, payload.plannedX, payload.plannedY, payload.plannedZ)) {
                                 village.reorderControlledProject(payload.from, payload.to);
                                 savedData.setDirty();
                                 PacketDistributor.sendToPlayer(player, ControlledProjectsService.buildPayload(village, overworld), new CustomPacketPayload[0]);
                              } else {
                                 BuildingProjectCancelRequestPayload.notifyQueueChanged(player, village, overworld);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }
}
