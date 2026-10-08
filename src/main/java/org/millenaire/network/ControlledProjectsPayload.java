package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ControlledProjectsPayload(
   String villageUuid, String villageName, List<ControlledProjectsPayload.ProjectEntry> entries, List<ControlledProjectsPayload.QueueEntry> queue
) implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 128;
   private static final int MAX_QUEUE = 16;
   private static final int MAX_RESOURCES = 64;
   public static final Type<ControlledProjectsPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_projects"));
   public static final StreamCodec<ByteBuf, ControlledProjectsPayload> STREAM_CODEC = StreamCodec.of(
      ControlledProjectsPayload::encode, ControlledProjectsPayload::decode
   );

   private static void encodeResources(ByteBuf buf, List<ControlledProjectsPayload.ResourceLine> resources) {
      int resCount = Math.min(resources.size(), 64);
      ByteBufCodecs.VAR_INT.encode(buf, resCount);

      for (int i = 0; i < resCount; i++) {
         ControlledProjectsPayload.ResourceLine r = resources.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, r.displayKey());
         buf.writeBoolean(r.translatable());
         ByteBufCodecs.STRING_UTF8.encode(buf, r.itemId());
         ByteBufCodecs.VAR_INT.encode(buf, r.present());
         ByteBufCodecs.VAR_INT.encode(buf, r.required());
      }
   }

   private static List<ControlledProjectsPayload.ResourceLine> decodeResources(ByteBuf buf) {
      int resCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<ControlledProjectsPayload.ResourceLine> resources = new ArrayList<>(resCount);

      for (int i = 0; i < resCount; i++) {
         resources.add(
            new ControlledProjectsPayload.ResourceLine(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               buf.readBoolean(),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf)
            )
         );
      }

      return resources;
   }

   private static void encode(ByteBuf buf, ControlledProjectsPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      int count = Math.min(payload.entries.size(), 128);
      ByteBufCodecs.VAR_INT.encode(buf, count);

      for (int i = 0; i < count; i++) {
         ControlledProjectsPayload.ProjectEntry e = payload.entries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, e.buildingId());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.displayName());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.planSetId());
         ByteBufCodecs.VAR_INT.encode(buf, e.currentLevel());
         ByteBufCodecs.VAR_INT.encode(buf, e.maxLevel());
         buf.writeBoolean(e.upgradesAllowed());
         ByteBufCodecs.STRING_UTF8.encode(buf, e.distanceLabel());
         buf.writeBoolean(e.isTownHall());
      }

      int queueCount = Math.min(payload.queue.size(), 16);
      ByteBufCodecs.VAR_INT.encode(buf, queueCount);

      for (int i = 0; i < queueCount; i++) {
         ControlledProjectsPayload.QueueEntry q = payload.queue.get(i);
         ByteBufCodecs.VAR_INT.encode(buf, q.index());
         ByteBufCodecs.STRING_UTF8.encode(buf, q.displayName());
         ByteBufCodecs.STRING_UTF8.encode(buf, q.planSetId());
         ByteBufCodecs.VAR_INT.encode(buf, q.plannedX());
         ByteBufCodecs.VAR_INT.encode(buf, q.plannedY());
         ByteBufCodecs.VAR_INT.encode(buf, q.plannedZ());
         ByteBufCodecs.STRING_UTF8.encode(buf, q.distanceLabel());
         encodeResources(buf, q.resources());
      }
   }

   private static ControlledProjectsPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int count = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 128);
      List<ControlledProjectsPayload.ProjectEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add(
            new ControlledProjectsPayload.ProjectEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean(),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               buf.readBoolean()
            )
         );
      }

      int queueCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 16);
      List<ControlledProjectsPayload.QueueEntry> queue = new ArrayList<>(queueCount);

      for (int i = 0; i < queueCount; i++) {
         queue.add(
            new ControlledProjectsPayload.QueueEntry(
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               decodeResources(buf)
            )
         );
      }

      return new ControlledProjectsPayload(villageUuid, villageName, entries, queue);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record ProjectEntry(
      String buildingId,
      String displayName,
      String planSetId,
      int currentLevel,
      int maxLevel,
      boolean upgradesAllowed,
      String distanceLabel,
      boolean isTownHall
   ) {
   }

   public record QueueEntry(
      int index,
      String displayName,
      String planSetId,
      int plannedX,
      int plannedY,
      int plannedZ,
      String distanceLabel,
      List<ControlledProjectsPayload.ResourceLine> resources
   ) {
   }

   public record ResourceLine(String displayKey, boolean translatable, String itemId, int present, int required) {
   }
}
