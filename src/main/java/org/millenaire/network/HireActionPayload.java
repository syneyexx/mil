package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerInteraction;
import org.millenaire.hire.HireAction;
import org.millenaire.hire.HiringHelper;
import org.millenaire.item.MoneyHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillagerRecord;

public record HireActionPayload(UUID villagerId, HireAction action) implements CustomPacketPayload {
   public static final Type<HireActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "hire_action"));
   public static final StreamCodec<ByteBuf, HireActionPayload> STREAM_CODEC = StreamCodec.of(HireActionPayload::encode, HireActionPayload::decode);

   private static void encode(ByteBuf buf, HireActionPayload p) {
      FriendlyByteBuf f = new FriendlyByteBuf(buf);
      f.writeUUID(p.villagerId);
      f.writeVarInt(p.action.ordinal());
   }

   private static HireActionPayload decode(ByteBuf buf) {
      FriendlyByteBuf f = new FriendlyByteBuf(buf);
      UUID id = f.readUUID();
      int i = f.readVarInt();
      HireAction action = i >= 0 && i < HireAction.values().length ? HireAction.values()[i] : HireAction.RELEASE;
      return new HireActionPayload(id, action);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(HireActionPayload p, IPayloadContext ctx) {
      ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            MillVillager v = level.getEntity(p.villagerId()) instanceof MillVillager mv ? mv : null;
            if (v != null) {
               if (ServerPayloadGuards.isWithinDistance(player, v, 64.0)) {
                  Village village = v.getVillageId() == null ? null : Village.resolve(level, v.getVillageId());
                  if (village != null) {
                     VillagerType vtype = ModCultures.getVillagerType(v.getVillagerTypeId());
                     if (vtype != null && HiringHelper.isHireable(vtype.hiringCost())) {
                        boolean controlled = village.isControlledBy(player.getUUID());
                        int cost = HiringHelper.hireCost(vtype.hiringCost(), controlled);
                        long now = level.getGameTime();
                        switch (p.action()) {
                           case HIRE:
                              if (v.isHired()) {
                                 return;
                              }

                              VillagerRecord rec = village.getVillagerRecord(p.villagerId());
                              if (rec != null && (rec.isAwayRaiding() || rec.isRaidingVillage())) {
                                 return;
                              }

                              if (village.getCombinedReputation(level, player.getUUID()) < 4096) {
                                 return;
                              }

                              if (!MoneyHelper.removeDeniers(player.getInventory(), cost)) {
                                 return;
                              }

                              village.setVillagerHired(level, p.villagerId(), player.getUUID(), now + 24000L);
                              MillAdvancements.grant(player, MillAdvancements.HIRED);
                              break;
                           case EXTEND:
                              if (!player.getUUID().equals(v.getHiredBy())) {
                                 return;
                              }

                              if (village.getCombinedReputation(level, player.getUUID()) < 4096) {
                                 return;
                              }

                              if (!MoneyHelper.removeDeniers(player.getInventory(), cost)) {
                                 return;
                              }

                              village.setVillagerHired(level, p.villagerId(), player.getUUID(), v.getHiredUntil() + 24000L);
                              break;
                           case RELEASE:
                              if (!player.getUUID().equals(v.getHiredBy())) {
                                 return;
                              }

                              village.setVillagerHired(level, p.villagerId(), null, 0L);
                        }

                        VillagerInteraction.sendHirePayload(player, v);
                     }
                  }
               }
            }
         }
      });
   }
}
