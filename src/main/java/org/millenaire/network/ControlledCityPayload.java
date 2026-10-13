package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ControlledCityPayload(
   String villageUuid,
   String villageName,
   int population,
   int maxSlots,
   int openJobs,
   int openBeds,
   int buildings,
   int threatLevel,
   String threatLabel,
   String jobPriorityRole,
   int immigrantAutoToday,
   int immigrantManualToday,
   int autoCap,
   int manualCap,
   int denierCost,
   List<ControlledCityPayload.BuildingEntry> buildingEntries
) implements CustomPacketPayload {
   private static final int MAX_BUILDINGS = 128;
   public static final Type<ControlledCityPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_city"));
   public static final StreamCodec<ByteBuf, ControlledCityPayload> STREAM_CODEC = StreamCodec.of(
      ControlledCityPayload::encode, ControlledCityPayload::decode
   );

   private static void encode(ByteBuf buf, ControlledCityPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.VAR_INT.encode(buf, payload.population);
      ByteBufCodecs.VAR_INT.encode(buf, payload.maxSlots);
      ByteBufCodecs.VAR_INT.encode(buf, payload.openJobs);
      ByteBufCodecs.VAR_INT.encode(buf, payload.openBeds);
      ByteBufCodecs.VAR_INT.encode(buf, payload.buildings);
      ByteBufCodecs.VAR_INT.encode(buf, payload.threatLevel);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.threatLabel != null ? payload.threatLabel : "");
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.jobPriorityRole != null ? payload.jobPriorityRole : "");
      ByteBufCodecs.VAR_INT.encode(buf, payload.immigrantAutoToday);
      ByteBufCodecs.VAR_INT.encode(buf, payload.immigrantManualToday);
      ByteBufCodecs.VAR_INT.encode(buf, payload.autoCap);
      ByteBufCodecs.VAR_INT.encode(buf, payload.manualCap);
      ByteBufCodecs.VAR_INT.encode(buf, payload.denierCost);
      int count = Math.min(payload.buildingEntries.size(), 128);
      ByteBufCodecs.VAR_INT.encode(buf, count);

      for (int i = 0; i < count; i++) {
         ControlledCityPayload.BuildingEntry e = payload.buildingEntries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, e.buildingUuid());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.planName());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.purpose());
         ByteBufCodecs.VAR_INT.encode(buf, e.openSlots());
         ByteBufCodecs.VAR_INT.encode(buf, e.occupants());
      }
   }

   private static ControlledCityPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int population = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int maxSlots = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int openJobs = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int openBeds = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int buildings = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int threatLevel = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String threatLabel = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String jobPriorityRole = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int immigrantAutoToday = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int immigrantManualToday = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int autoCap = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int manualCap = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int denierCost = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 128);
      List<ControlledCityPayload.BuildingEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < rawCount; i++) {
         String buildingUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String planName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String purpose = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int openSlots = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int occupants = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         if (i < count) {
            entries.add(new ControlledCityPayload.BuildingEntry(buildingUuid, planName, purpose, openSlots, occupants));
         }
      }

      return new ControlledCityPayload(
         villageUuid,
         villageName,
         population,
         maxSlots,
         openJobs,
         openBeds,
         buildings,
         threatLevel,
         threatLabel.isEmpty() ? "none" : threatLabel,
         jobPriorityRole.isEmpty() ? null : jobPriorityRole,
         immigrantAutoToday,
         immigrantManualToday,
         autoCap,
         manualCap,
         denierCost,
         entries
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record BuildingEntry(String buildingUuid, String planName, String purpose, int openSlots, int occupants) {
   }
}
