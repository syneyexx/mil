package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.List;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.PlacementSignHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public record BuildingProjectCancelRequestPayload(
   String villageUuid, int queueIndex, String planSetId, int plannedX, int plannedY, int plannedZ, boolean fromScreen
) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<BuildingProjectCancelRequestPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_cancel_request")
   );
   public static final StreamCodec<ByteBuf, BuildingProjectCancelRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectCancelRequestPayload::encode, BuildingProjectCancelRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectCancelRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.queueIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedX);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedY);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedZ);
      ByteBufCodecs.BOOL.encode(buf, payload.fromScreen);
   }

   private static BuildingProjectCancelRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      int queueIndex = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      int plannedX = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int plannedY = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int plannedZ = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      boolean fromScreen = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      return new BuildingProjectCancelRequestPayload(villageUuid, queueIndex, planSetId, plannedX, plannedY, plannedZ, fromScreen);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingProjectCancelRequestPayload payload, IPayloadContext context) {
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
                              "[Millenaire] BuildingProjectCancel: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid
                           );
                        } else if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
                           LOGGER.warn(
                              "[Millenaire] BuildingProjectCancel: player {} too far from village {}", player.getName().getString(), payload.villageUuid
                           );
                        } else if (RATE_LIMITER.acquire(player.getUUID())) {
                           List<ControlledQueuedProject> queue = village.getControlledQueue();
                           if (payload.queueIndex >= 0 && payload.queueIndex < queue.size()) {
                              ControlledQueuedProject project = queue.get(payload.queueIndex);
                              if (!project.matchesEcho(payload.planSetId, payload.plannedX, payload.plannedY, payload.plannedZ)) {
                                 notifyQueueChanged(player, village, overworld, payload.fromScreen);
                              } else {
                                 if (project.plannedLocation() != null) {
                                    BuildingPlanSet set = ModCultures.getBuildingPlanSet(project.planSetId());
                                    if (set != null) {
                                       BuildingPlanSet.LevelDef levelDef = set.getLevel(project.variant(), project.level());
                                       BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
                                       if (plan != null) {
                                          PlacementSignHelper.removeCornerSigns(overworld, plan, project.plannedLocation());
                                       }
                                    }
                                 }

                                 village.removeControlledProject(payload.queueIndex);
                                 savedData.setDirty();
                                 BuildingPlanSet set = ModCultures.getBuildingPlanSet(project.planSetId());
                                 String name = set != null ? set.nativeName() : project.planSetId().getPath();
                                 player.sendSystemMessage(Component.translatable("gui.millenaire.building_project.cancelled", new Object[]{name}));
                                 if (payload.fromScreen) {
                                    PacketDistributor.sendToPlayer(
                                       player, ControlledProjectsService.buildPayload(village, overworld), new CustomPacketPayload[0]
                                    );
                                 }
                              }
                           } else {
                              notifyQueueChanged(player, village, overworld, payload.fromScreen);
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }

   static void notifyQueueChanged(ServerPlayer player, Village village, ServerLevel overworld) {
      notifyQueueChanged(player, village, overworld, true);
   }

   static void notifyQueueChanged(ServerPlayer player, Village village, ServerLevel overworld, boolean refreshScreen) {
      player.sendSystemMessage(
         Component.translatable(refreshScreen ? "gui.millenaire.controlled_projects.queue_changed" : "gui.millenaire.controlled_projects.queue_changed_world")
      );
      if (refreshScreen) {
         PacketDistributor.sendToPlayer(player, ControlledProjectsService.buildPayload(village, overworld), new CustomPacketPayload[0]);
      }
   }
}
