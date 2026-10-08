package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record ImportTableBuildingViewPayload(String cultureKey, ImportTableCultureListPayload.Entry entry, int hydratedLevels, boolean migratedLegacy)
   implements CustomPacketPayload {
   public static final Type<ImportTableBuildingViewPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "import_table_building_view"));
   public static final StreamCodec<ByteBuf, ImportTableBuildingViewPayload> STREAM_CODEC = StreamCodec.of(
      ImportTableBuildingViewPayload::encode, ImportTableBuildingViewPayload::decode
   );

   private static void encode(ByteBuf buf, ImportTableBuildingViewPayload p) {
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
      ImportTableCultureListPayload.encodeEntry(buf, p.entry);
      ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, p.hydratedLevels));
      ByteBufCodecs.BOOL.encode(buf, p.migratedLegacy);
   }

   private static ImportTableBuildingViewPayload decode(ByteBuf buf) {
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      ImportTableCultureListPayload.Entry entry = ImportTableCultureListPayload.decodeEntry(buf);
      int hydrated = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      boolean migrated = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      return new ImportTableBuildingViewPayload(cultureKey, entry, hydrated, migrated);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
