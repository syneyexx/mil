package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public record ToggleStancePayload(boolean aggressive) implements CustomPacketPayload {
   private static final double RADIUS_XZ = 16.0;
   private static final double RADIUS_Y = 8.0;
   public static final Type<ToggleStancePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "toggle_stance"));
   public static final StreamCodec<ByteBuf, ToggleStancePayload> STREAM_CODEC = StreamCodec.of(
      (buf, p) -> ByteBufCodecs.BOOL.encode(buf, p.aggressive), buf -> new ToggleStancePayload((Boolean)ByteBufCodecs.BOOL.decode(buf))
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(ToggleStancePayload p, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer player) {
            ServerLevel var9 = player.serverLevel();
            AABB box = player.getBoundingBox().inflate(16.0, 8.0, 16.0);

            for (MillVillager v : var9.getEntitiesOfClass(MillVillager.class, box)) {
               if (player.getUUID().equals(v.getHiredBy())) {
                  v.setAggressiveStance(p.aggressive());
                  if (v.getVillageId() != null) {
                     Village village = Village.resolve(var9, v.getVillageId());
                     if (village != null) {
                        VillagerRecord rec = village.getVillagerRecord(v.getUUID());
                        if (rec != null) {
                           rec.setAggressiveStance(p.aggressive());
                           village.markDirty();
                        }
                     }
                  }
               }
            }
         }
      });
   }
}
