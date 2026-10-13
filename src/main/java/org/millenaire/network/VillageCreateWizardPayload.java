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

public record VillageCreateWizardPayload(
   BlockPos targetPos,
   int warDistance,
   int closestDistance,
   boolean territoryConflict,
   boolean overlapsRadius,
   List<NearbyVillage> nearby,
   List<CultureOption> cultures,
   List<TypeOption> types
) implements CustomPacketPayload {
   public static final Type<VillageCreateWizardPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "village_create_wizard")
   );
   public static final StreamCodec<ByteBuf, VillageCreateWizardPayload> STREAM_CODEC = StreamCodec.of(
      VillageCreateWizardPayload::encode, VillageCreateWizardPayload::decode
   );

   private static void encode(ByteBuf buf, VillageCreateWizardPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.targetPos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.targetPos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.targetPos.getZ());
      ByteBufCodecs.VAR_INT.encode(buf, p.warDistance);
      ByteBufCodecs.VAR_INT.encode(buf, p.closestDistance);
      buf.writeBoolean(p.territoryConflict);
      buf.writeBoolean(p.overlapsRadius);
      ByteBufCodecs.VAR_INT.encode(buf, p.nearby.size());
      for (NearbyVillage n : p.nearby) {
         ByteBufCodecs.STRING_UTF8.encode(buf, n.name());
         ByteBufCodecs.STRING_UTF8.encode(buf, n.culturePath());
         ByteBufCodecs.VAR_INT.encode(buf, n.distance());
         ByteBufCodecs.VAR_INT.encode(buf, n.radius());
         buf.writeBoolean(n.conflict());
      }
      ByteBufCodecs.VAR_INT.encode(buf, p.cultures.size());
      for (CultureOption c : p.cultures) {
         ByteBufCodecs.STRING_UTF8.encode(buf, c.cultureKey());
         ByteBufCodecs.STRING_UTF8.encode(buf, c.displayName());
      }
      ByteBufCodecs.VAR_INT.encode(buf, p.types.size());
      for (TypeOption t : p.types) {
         ByteBufCodecs.STRING_UTF8.encode(buf, t.cultureKey());
         ByteBufCodecs.STRING_UTF8.encode(buf, t.typeKey());
         ByteBufCodecs.STRING_UTF8.encode(buf, t.displayName());
      }
   }

   private static VillageCreateWizardPayload decode(ByteBuf buf) {
      BlockPos pos = new BlockPos(
         ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)
      );
      int warDistance = ByteBufCodecs.VAR_INT.decode(buf);
      int closest = ByteBufCodecs.VAR_INT.decode(buf);
      boolean conflict = buf.readBoolean();
      boolean overlaps = buf.readBoolean();
      int nearbyCount = ByteBufCodecs.VAR_INT.decode(buf);
      List<NearbyVillage> nearby = new ArrayList<>(nearbyCount);
      for (int i = 0; i < nearbyCount; i++) {
         nearby.add(
            new NearbyVillage(
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.STRING_UTF8.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               ByteBufCodecs.VAR_INT.decode(buf),
               buf.readBoolean()
            )
         );
      }
      int cultureCount = ByteBufCodecs.VAR_INT.decode(buf);
      List<CultureOption> cultures = new ArrayList<>(cultureCount);
      for (int i = 0; i < cultureCount; i++) {
         cultures.add(new CultureOption(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));
      }
      int typeCount = ByteBufCodecs.VAR_INT.decode(buf);
      List<TypeOption> types = new ArrayList<>(typeCount);
      for (int i = 0; i < typeCount; i++) {
         types.add(
            new TypeOption(
               ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)
            )
         );
      }
      return new VillageCreateWizardPayload(pos, warDistance, closest, conflict, overlaps, nearby, cultures, types);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public record NearbyVillage(String name, String culturePath, int distance, int radius, boolean conflict) {
   }

   public record CultureOption(String cultureKey, String displayName) {
   }

   public record TypeOption(String cultureKey, String typeKey, String displayName) {
   }
}
