package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record BuildingProjectListPayload(
   String villageUuid,
   String villageName,
   BlockPos clickedPos,
   BlockPos villageCenter,
   int villageRadius,
   List<BuildingProjectListPayload.Box> occupied,
   List<BuildingProjectListPayload.BuildingEntry> entries
) implements CustomPacketPayload {
   private static final int MAX_ENTRIES = 64;
   private static final int MAX_BOXES = 256;
   public static final Type<BuildingProjectListPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "building_project_list"));
   public static final StreamCodec<ByteBuf, BuildingProjectListPayload> STREAM_CODEC = StreamCodec.of(
      BuildingProjectListPayload::encode, BuildingProjectListPayload::decode
   );

   private static void encode(ByteBuf buf, BuildingProjectListPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageUuid);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageName);
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.clickedPos.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageCenter.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, payload.villageRadius);
      int boxCount = Math.min(payload.occupied.size(), 256);
      ByteBufCodecs.VAR_INT.encode(buf, boxCount);

      for (int i = 0; i < boxCount; i++) {
         BuildingProjectListPayload.Box b = payload.occupied.get(i);
         ByteBufCodecs.VAR_INT.encode(buf, b.minX());
         ByteBufCodecs.VAR_INT.encode(buf, b.minZ());
         ByteBufCodecs.VAR_INT.encode(buf, b.maxX());
         ByteBufCodecs.VAR_INT.encode(buf, b.maxZ());
         ByteBufCodecs.VAR_INT.encode(buf, b.surfaceY());
         ByteBufCodecs.BOOL.encode(buf, b.planned());
      }

      int count = Math.min(payload.entries.size(), 64);
      ByteBufCodecs.VAR_INT.encode(buf, count);

      for (int i = 0; i < count; i++) {
         BuildingProjectListPayload.BuildingEntry entry = payload.entries.get(i);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.planSetId());
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.displayName());
         ByteBufCodecs.VAR_INT.encode(buf, entry.width());
         ByteBufCodecs.VAR_INT.encode(buf, entry.depth());
         ByteBufCodecs.VAR_INT.encode(buf, entry.buildingOrientation());
         ByteBufCodecs.VAR_INT.encode(buf, entry.levelCount());
         ByteBufCodecs.VAR_INT.encode(buf, entry.variantCount());
         ByteBufCodecs.VAR_INT.encode(buf, entry.marginLengthBefore());
         ByteBufCodecs.VAR_INT.encode(buf, entry.marginLengthAfter());
         ByteBufCodecs.VAR_INT.encode(buf, entry.marginWidthBefore());
         ByteBufCodecs.VAR_INT.encode(buf, entry.marginWidthAfter());
         ByteBufCodecs.VAR_INT.encode(buf, entry.fixedOrientation() != null ? entry.fixedOrientation() + 1 : 0);
      }
   }

   private static BuildingProjectListPayload decode(ByteBuf buf) {
      String villageUuid = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      String villageName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      int px = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int py = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int pz = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos clickedPos = new BlockPos(px, py, pz);
      int cx = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cy = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int cz = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos villageCenter = new BlockPos(cx, cy, cz);
      int villageRadius = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int boxCount = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 256);
      List<BuildingProjectListPayload.Box> occupied = new ArrayList<>(boxCount);

      for (int i = 0; i < boxCount; i++) {
         occupied.add(
            new BuildingProjectListPayload.Box(
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Boolean)ByteBufCodecs.BOOL.decode(buf)
            )
         );
      }

      int count = Math.min((Integer)ByteBufCodecs.VAR_INT.decode(buf), 64);
      List<BuildingProjectListPayload.BuildingEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         String planSetId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         String displayName = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
         int width = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int depth = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int buildingOrientation = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int levelCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int variantCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int marginLengthBefore = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int marginLengthAfter = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int marginWidthBefore = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int marginWidthAfter = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         int fixedOrientationShifted = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         entries.add(
            new BuildingProjectListPayload.BuildingEntry(
               planSetId,
               displayName,
               width,
               depth,
               buildingOrientation,
               levelCount,
               variantCount,
               marginLengthBefore,
               marginLengthAfter,
               marginWidthBefore,
               marginWidthAfter,
               fixedOrientationShifted <= 0 ? null : fixedOrientationShifted - 1
            )
         );
      }

      return new BuildingProjectListPayload(villageUuid, villageName, clickedPos, villageCenter, villageRadius, occupied, entries);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record Box(int minX, int minZ, int maxX, int maxZ, int surfaceY, boolean planned) {
   }

   public record BuildingEntry(
      String planSetId,
      String displayName,
      int width,
      int depth,
      int buildingOrientation,
      int levelCount,
      int variantCount,
      int marginLengthBefore,
      int marginLengthAfter,
      int marginWidthBefore,
      int marginWidthAfter,
      @Nullable Integer fixedOrientation
   ) {
   }
}
