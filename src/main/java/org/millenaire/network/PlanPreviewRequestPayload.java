package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
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
import org.millenaire.building.PlacementStep;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.ControlledProjectsService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public record PlanPreviewRequestPayload(String villageUuid, String planSetId, int variantIndex) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 256;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(200L);
   private static final Map<UUID, PlanPreviewRequestPayload.PrePicked> PRE_PICKED = new ConcurrentHashMap<>();
   public static final Type<PlanPreviewRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "plan_preview_request"));
   public static final StreamCodec<ByteBuf, PlanPreviewRequestPayload> STREAM_CODEC = StreamCodec.of(
      PlanPreviewRequestPayload::encode, PlanPreviewRequestPayload::decode
   );

   @Nullable
   public static String consumePrePickedVariant(UUID playerId, ResourceLocation planSetLoc) {
      PlanPreviewRequestPayload.PrePicked picked = PRE_PICKED.get(playerId);
      if (picked != null && picked.planSet().equals(planSetLoc)) {
         PRE_PICKED.remove(playerId);
         return picked.variant();
      } else {
         return null;
      }
   }

   public static void clearPrePicked(UUID playerId) {
      PRE_PICKED.remove(playerId);
   }

   private static void encode(ByteBuf buf, PlanPreviewRequestPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.variantIndex);
   }

   private static PlanPreviewRequestPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageUuid.length() > 256) {
         villageUuid = villageUuid.substring(0, 256);
      }

      String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (planSetId.length() > 256) {
         planSetId = planSetId.substring(0, 256);
      }

      int variantIndex = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      if (variantIndex < -1) {
         variantIndex = -1;
      }

      return new PlanPreviewRequestPayload(villageUuid, planSetId, variantIndex);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(PlanPreviewRequestPayload payload, IPayloadContext context) {
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
                     if (village != null && village.isControlledBy(player.getUUID())) {
                        if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
                           LOGGER.warn("[Millenaire] PlanPreviewRequest: player {} too far from village {}", player.getName().getString(), payload.villageUuid);
                        } else if (RATE_LIMITER.acquire(player.getUUID())) {
                           ResourceLocation planSetLoc = ResourceLocation.tryParse(payload.planSetId);
                           if (planSetLoc != null) {
                              BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetLoc);
                              if (planSet != null) {
                                 VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
                                 if (ControlledProjectsService.findOfferedSlot(vt, planSetLoc, planSet) == null) {
                                    LOGGER.warn(
                                       "[Millenaire] PlanPreviewRequest: plan set {} is not offered for village {}", payload.planSetId, payload.villageUuid
                                    );
                                 } else {
                                    List<String> variantKeys = planSet.variants().keySet().stream().sorted().toList();
                                    String variant;
                                    if (payload.variantIndex >= 0 && payload.variantIndex < variantKeys.size()) {
                                       variant = variantKeys.get(payload.variantIndex);
                                    } else {
                                       variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
                                    }

                                    int variantIndex = variantKeys.indexOf(variant);
                                    BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
                                    BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
                                    if (plan == null) {
                                       LOGGER.warn("[Millenaire] PlanPreviewRequest: plan set {} has no level 0", payload.planSetId);
                                    } else {
                                       PRE_PICKED.put(player.getUUID(), new PlanPreviewRequestPayload.PrePicked(planSetLoc, variant));
                                       List<PlacementStep> steps = BuildingPlacer.compilePreviewSteps(overworld, plan);
                                       PlanPreviewPayload preview = PlanPreviewPayload.fromSteps(
                                          payload.planSetId, variant, variantIndex, plan.width(), plan.depth(), plan.groundLevel(), steps
                                       );
                                       PacketDistributor.sendToPlayer(player, preview, new CustomPacketPayload[0]);
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

   private record PrePicked(ResourceLocation planSet, String variant) {
   }
}
