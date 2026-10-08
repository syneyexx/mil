package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record OpenHireScreenPayload(
   UUID villagerId,
   String name,
   String occupation,
   int health,
   int maxHealth,
   int attackStrength,
   int hireCost,
   boolean hiredByMe,
   int hoursLeft,
   boolean hasReputation,
   boolean canAfford
) implements CustomPacketPayload {
   public static final Type<OpenHireScreenPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "open_hire"));
   public static final StreamCodec<ByteBuf, OpenHireScreenPayload> STREAM_CODEC = StreamCodec.of(OpenHireScreenPayload::encode, OpenHireScreenPayload::decode);

   private static void encode(ByteBuf buf, OpenHireScreenPayload p) {
      FriendlyByteBuf f = new FriendlyByteBuf(buf);
      f.writeUUID(p.villagerId);
      f.writeUtf(p.name);
      f.writeUtf(p.occupation);
      f.writeVarInt(p.health);
      f.writeVarInt(p.maxHealth);
      f.writeVarInt(p.attackStrength);
      f.writeVarInt(p.hireCost);
      f.writeBoolean(p.hiredByMe);
      f.writeVarInt(p.hoursLeft);
      f.writeBoolean(p.hasReputation);
      f.writeBoolean(p.canAfford);
   }

   private static OpenHireScreenPayload decode(ByteBuf buf) {
      FriendlyByteBuf f = new FriendlyByteBuf(buf);
      return new OpenHireScreenPayload(
         f.readUUID(),
         f.readUtf(),
         f.readUtf(),
         f.readVarInt(),
         f.readVarInt(),
         f.readVarInt(),
         f.readVarInt(),
         f.readBoolean(),
         f.readVarInt(),
         f.readBoolean(),
         f.readBoolean()
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
