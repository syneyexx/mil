package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.map.sync.SettlementMapEntry;

/** Server → client: full (possibly chunked) settlement map snapshot. */
public record SettlementMapSnapshotPayload(
   String worldToken,
   int revision,
   int partIndex,
   int partCount,
   List<SettlementMapEntry> entries
) implements CustomPacketPayload {
   public static final Type<SettlementMapSnapshotPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "settlement_map_snapshot"));
   public static final StreamCodec<ByteBuf, SettlementMapSnapshotPayload> STREAM_CODEC =
      StreamCodec.of(SettlementMapSnapshotPayload::encode, SettlementMapSnapshotPayload::decode);

   private static void encode(ByteBuf buf, SettlementMapSnapshotPayload payload) {
      writeString(buf, payload.worldToken);
      ByteBufCodecs.VAR_INT.encode(buf, payload.revision);
      ByteBufCodecs.VAR_INT.encode(buf, payload.partIndex);
      ByteBufCodecs.VAR_INT.encode(buf, payload.partCount);
      ByteBufCodecs.VAR_INT.encode(buf, payload.entries.size());
      for (SettlementMapEntry e : payload.entries) {
         writeEntry(buf, e);
      }
   }

   private static SettlementMapSnapshotPayload decode(ByteBuf buf) {
      String worldToken = readString(buf);
      int revision = ByteBufCodecs.VAR_INT.decode(buf);
      int partIndex = ByteBufCodecs.VAR_INT.decode(buf);
      int partCount = ByteBufCodecs.VAR_INT.decode(buf);
      int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 512);
      List<SettlementMapEntry> entries = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         entries.add(readEntry(buf));
      }
      return new SettlementMapSnapshotPayload(worldToken, revision, partIndex, partCount, entries);
   }

   static void writeEntry(ByteBuf buf, SettlementMapEntry e) {
      buf.writeLong(e.villageId().getMostSignificantBits());
      buf.writeLong(e.villageId().getLeastSignificantBits());
      writeString(buf, e.name());
      writeString(buf, e.cultureId().toString());
      writeString(buf, e.villageTypeId().toString());
      writeString(buf, e.developmentLabel());
      buf.writeInt(e.x());
      buf.writeInt(e.y());
      buf.writeInt(e.z());
      writeString(buf, e.dimension().toString());
      buf.writeInt(e.flags());
      writeString(buf, e.factionId() == null ? "" : e.factionId());
      ByteBufCodecs.VAR_INT.encode(buf, e.revision());
      SettlementMapEntry.ApproxBounds b = e.approxBounds();
      buf.writeBoolean(b != null);
      if (b != null) {
         buf.writeInt(b.minX());
         buf.writeInt(b.minY());
         buf.writeInt(b.minZ());
         buf.writeInt(b.maxX());
         buf.writeInt(b.maxY());
         buf.writeInt(b.maxZ());
      }
   }

   static SettlementMapEntry readEntry(ByteBuf buf) {
      UUID id = new UUID(buf.readLong(), buf.readLong());
      String name = readString(buf);
      ResourceLocation culture = parseRl(readString(buf), "unknown");
      ResourceLocation type = parseRl(readString(buf), "unknown");
      String label = readString(buf);
      int x = buf.readInt();
      int y = buf.readInt();
      int z = buf.readInt();
      ResourceLocation dim = parseRl(readString(buf), "overworld");
      int flags = buf.readInt();
      String faction = readString(buf);
      if (faction.isEmpty()) {
         faction = null;
      }
      int revision = ByteBufCodecs.VAR_INT.decode(buf);
      SettlementMapEntry.ApproxBounds bounds = null;
      if (buf.readBoolean()) {
         bounds = new SettlementMapEntry.ApproxBounds(
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()
         );
      }
      return new SettlementMapEntry(id, name, culture, type, label, x, y, z, dim, flags, faction, revision, bounds);
   }

   static void writeString(ByteBuf buf, @Nullable String s) {
      ByteBufCodecs.STRING_UTF8.encode(buf, s == null ? "" : s);
   }

   static String readString(ByteBuf buf) {
      return ByteBufCodecs.STRING_UTF8.decode(buf);
   }

   static ResourceLocation parseRl(String raw, String fallbackPath) {
      ResourceLocation rl = ResourceLocation.tryParse(raw);
      return rl != null ? rl : ResourceLocation.fromNamespaceAndPath("millenaire", fallbackPath);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
