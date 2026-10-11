package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.map.sync.SettlementMapEntry;

/** Server → client: incremental settlement map upserts/removals. */
public record SettlementMapDeltaPayload(
   String worldToken,
   int revision,
   List<SettlementMapEntry> upserts,
   List<UUID> removed
) implements CustomPacketPayload {
   public static final Type<SettlementMapDeltaPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "settlement_map_delta"));
   public static final StreamCodec<ByteBuf, SettlementMapDeltaPayload> STREAM_CODEC =
      StreamCodec.of(SettlementMapDeltaPayload::encode, SettlementMapDeltaPayload::decode);

   private static void encode(ByteBuf buf, SettlementMapDeltaPayload payload) {
      SettlementMapSnapshotPayload.writeString(buf, payload.worldToken);
      ByteBufCodecs.VAR_INT.encode(buf, payload.revision);
      ByteBufCodecs.VAR_INT.encode(buf, payload.upserts.size());
      for (SettlementMapEntry e : payload.upserts) {
         SettlementMapSnapshotPayload.writeEntry(buf, e);
      }
      ByteBufCodecs.VAR_INT.encode(buf, payload.removed.size());
      for (UUID id : payload.removed) {
         buf.writeLong(id.getMostSignificantBits());
         buf.writeLong(id.getLeastSignificantBits());
      }
   }

   private static SettlementMapDeltaPayload decode(ByteBuf buf) {
      String worldToken = SettlementMapSnapshotPayload.readString(buf);
      int revision = ByteBufCodecs.VAR_INT.decode(buf);
      int nUp = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 512);
      List<SettlementMapEntry> upserts = new ArrayList<>(nUp);
      for (int i = 0; i < nUp; i++) {
         upserts.add(SettlementMapSnapshotPayload.readEntry(buf));
      }
      int nRm = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 512);
      List<UUID> removed = new ArrayList<>(nRm);
      for (int i = 0; i < nRm; i++) {
         removed.add(new UUID(buf.readLong(), buf.readLong()));
      }
      return new SettlementMapDeltaPayload(worldToken, revision, upserts, removed);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
