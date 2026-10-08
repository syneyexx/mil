package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
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
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.PlacementSignHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BuildingLocationFinder;
import org.millenaire.world.PlacedLocation;
import org.slf4j.Logger;

public record BuildingProjectRequestPayload(String villageUuid, String planSetId, BlockPos clickedPos, int rotation) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   public static final Type<BuildingProjectRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_request"));
   public static final StreamCodec<ByteBuf, BuildingProjectRequestPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectRequestPayload::encode, BuildingProjectRequestPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, payload.rotation);
   }

   private static BuildingProjectRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      int px = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int py = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int pz = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rotation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (rotation < 0 || rotation > 3) {
         rotation = -1;
      }

      return new BuildingProjectRequestPayload(villageUuid, planSetId, new BlockPos(px, py, pz), rotation);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(BuildingProjectRequestPayload payload, IPayloadContext context) {
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
                     if (village == null) {
                        LOGGER.warn(
                           "[Millenaire] BuildingProjectRequest: village {} not found for player {}", payload.villageUuid, player.getName().getString()
                        );
                     } else if (!village.isControlledBy(player.getUUID())) {
                        LOGGER.warn(
                           "[Millenaire] BuildingProjectRequest: player {} is not owner of village {}", player.getName().getString(), payload.villageUuid
                        );
                     } else if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
                        LOGGER.warn("[Millenaire] BuildingProjectRequest: player {} too far from village {}", player.getName().getString(), payload.villageUuid);
                        player.sendSystemMessage(Component.translatable("gui.millenaire.building_project.too_far"));
                     } else if (RATE_LIMITER.acquire(player.getUUID())) {
                        ResourceLocation planSetLoc = ResourceLocation.tryParse(payload.planSetId);
                        if (planSetLoc != null) {
                           BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetLoc);
                           if (planSet == null) {
                              LOGGER.warn("[Millenaire] BuildingProjectRequest: plan set {} not found", payload.planSetId);
                           } else {
                              String variant = PlanPreviewRequestPayload.consumePrePickedVariant(player.getUUID(), planSetLoc);
                              if (variant == null || planSet.getLevel(variant, 0) == null) {
                                 variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
                              }

                              BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
                              BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
                              if (plan == null) {
                                 LOGGER.warn("[Millenaire] BuildingProjectRequest: plan {} has no level 0 / plan resource", payload.planSetId);
                                 player.sendSystemMessage(
                                    Component.translatable("gui.millenaire.building_project.no_location", new Object[]{planSet.nativeName()})
                                 );
                              } else {
                                 VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
                                 VillageType.LayoutSlot slot = ControlledProjectsService.findOfferedSlot(vt, planSetLoc, planSet);
                                 if (slot == null) {
                                    LOGGER.warn(
                                       "[Millenaire] BuildingProjectRequest: plan set {} is not offered for village {}", payload.planSetId, payload.villageUuid
                                    );
                                 } else {
                                    Integer playerRotation = payload.rotation >= 0 ? payload.rotation : null;
                                    BuildingLocationFinder.AnchorEvaluation evaluation = VillageGrowthManager.validateLocationAt(
                                       overworld, village, planSet, plan, slot, payload.clickedPos, playerRotation
                                    );
                                    if (!evaluation.isSuccess()) {
                                       String hint = evaluation.errorPos() != null
                                          ? ControlledProjectsService.formatDistanceDirection(payload.clickedPos, evaluation.errorPos())
                                          : "";
                                       player.sendSystemMessage(
                                          Component.translatable(errorKeyFor(evaluation.reason()), new Object[]{planSet.nativeName(), hint})
                                       );
                                    } else {
                                       PlacedLocation planned = evaluation.location();
                                       boolean enqueued = village.enqueueControlledProject(new ControlledQueuedProject(planSetLoc, variant, 0, planned));
                                       if (!enqueued) {
                                          player.sendSystemMessage(
                                             Component.translatable("gui.millenaire.building_project.queue_full", new Object[]{String.valueOf(5)})
                                          );
                                       } else {
                                          village.setNoProjectsLeftUntil(0L);
                                          PlacementSignHelper.placeCornerSigns(overworld, plan, planned, planSet.nativeName());
                                          player.sendSystemMessage(
                                             Component.translatable("gui.millenaire.building_project.queued", new Object[]{planSet.nativeName()})
                                          );
                                       }
                                    }
                                 }
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

   static String errorKeyFor(@Nullable BuildingLocationFinder.FailureReason reason) {
      if (reason == null) {
         return "gui.millenaire.building_project.no_location";
      }

      return switch (reason) {
         case CONSTRUCTION_FORBIDDEN -> "gui.millenaire.building_project.construction_forbidden";
         case LOCATION_CLASH -> "gui.millenaire.building_project.location_clash";
         case OUTSIDE_RADIUS -> "gui.millenaire.building_project.outside_radius";
         case WRONG_ALTITUDE -> "gui.millenaire.building_project.wrong_elevation";
         case DANGER -> "gui.millenaire.building_project.danger";
         case NOT_REACHABLE -> "gui.millenaire.building_project.not_reachable";
         case GENERIC -> "gui.millenaire.building_project.no_location";
      };
   }
}
