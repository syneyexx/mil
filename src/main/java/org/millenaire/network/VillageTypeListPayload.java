package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record VillageTypeListPayload(BlockPos targetPos, List<VillageTypeListPayload.VillageTypeEntry> entries, boolean sovereignFoundingAvailable)
   implements CustomPacketPayload {
   public VillageTypeListPayload(BlockPos targetPos, List<VillageTypeListPayload.VillageTypeEntry> entries) {
      this(targetPos, entries, true);
   }
   private static final int MAX_ENTRIES = 256;
   public static final Type<VillageTypeListPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_type_list"));
   public static final StreamCodec<ByteBuf, VillageTypeListPayload> STREAM_CODEC = StreamCodec.of(
      VillageTypeListPayload::encode, VillageTypeListPayload::decode
   );

   private static void encode(ByteBuf buf, VillageTypeListPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.targetPos.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, payload.entries.size());

      for (VillageTypeListPayload.VillageTypeEntry entry : payload.entries) {
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.cultureKey);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.cultureName);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.typeKey);
         ByteBufCodecs.STRING_UTF8.encode(buf, entry.displayName);
         ByteBufCodecs.VAR_INT.encode(buf, entry.weight);
         ByteBufCodecs.BOOL.encode(buf, entry.requiresControl);
         ByteBufCodecs.BOOL.encode(buf, entry.hasControl);
         ByteBufCodecs.BOOL.encode(buf, entry.hasCultureControl);
         ByteBufCodecs.BOOL.encode(buf, entry.canFound);
      }
      ByteBufCodecs.BOOL.encode(buf, payload.sovereignFoundingAvailable);
   }

   private static VillageTypeListPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      BlockPos pos = new BlockPos(x, y, z);
      int rawCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int count = Math.min(rawCount, 256);
      List<VillageTypeListPayload.VillageTypeEntry> entries = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         entries.add(
            new VillageTypeListPayload.VillageTypeEntry(
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (String)ByteBufCodecs.STRING_UTF8.decode(buf),
               (Integer)ByteBufCodecs.VAR_INT.decode(buf),
               (Boolean)ByteBufCodecs.BOOL.decode(buf),
               (Boolean)ByteBufCodecs.BOOL.decode(buf),
               (Boolean)ByteBufCodecs.BOOL.decode(buf),
               (Boolean)ByteBufCodecs.BOOL.decode(buf)
            )
         );
      }

      for (int i = count; i < rawCount; i++) {
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.STRING_UTF8.decode(buf);
         ByteBufCodecs.VAR_INT.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
         ByteBufCodecs.BOOL.decode(buf);
      }

      boolean sovereignFoundingAvailable = ByteBufCodecs.BOOL.decode(buf);
      return new VillageTypeListPayload(pos, entries, sovereignFoundingAvailable);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record VillageTypeEntry(
      String cultureKey,
      String cultureName,
      String typeKey,
      String displayName,
      int weight,
      boolean requiresControl,
      boolean hasControl,
      boolean hasCultureControl,
      boolean canFound
   ) {
      public VillageTypeEntry(
         String cultureKey, String cultureName, String typeKey, String displayName, int weight, boolean requiresControl, boolean hasControl
      ) {
         this(cultureKey, cultureName, typeKey, displayName, weight, requiresControl, hasControl, hasControl, true);
      }

      public VillageTypeEntry(
         String cultureKey,
         String cultureName,
         String typeKey,
         String displayName,
         int weight,
         boolean requiresControl,
         boolean hasControl,
         boolean hasCultureControl
      ) {
         this(cultureKey, cultureName, typeKey, displayName, weight, requiresControl, hasControl, hasCultureControl, true);
      }
   }
}
