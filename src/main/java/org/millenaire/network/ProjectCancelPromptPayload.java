package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ProjectCancelPromptPayload(String villageUuid, int queueIndex, String planSetId, int plannedX, int plannedY, int plannedZ, String buildingName)
   implements CustomPacketPayload {
   private static final int MAX_STRING_LENGTH = 256;
   public static final Type<ProjectCancelPromptPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "project_cancel_prompt"));
   public static final StreamCodec<ByteBuf, ProjectCancelPromptPayload> STREAM_CODEC = StreamCodec.of(
      ProjectCancelPromptPayload::encode, ProjectCancelPromptPayload::decode
   );

   private static void encode(ByteBuf buf, ProjectCancelPromptPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.VAR_INT.encode(buf, payload.queueIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.planSetId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedX);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedY);
      ByteBufCodecs.VAR_INT.encode(buf, payload.plannedZ);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.buildingName);
   }

   private static ProjectCancelPromptPayload decode(ByteBuf buf) {
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
      String buildingName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (buildingName.length() > 256) {
         buildingName = buildingName.substring(0, 256);
      }

      return new ProjectCancelPromptPayload(villageUuid, queueIndex, planSetId, plannedX, plannedY, plannedZ, buildingName);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
