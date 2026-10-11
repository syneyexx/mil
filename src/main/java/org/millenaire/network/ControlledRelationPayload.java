package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.civilization.settlement.SovereignDiplomacyBridge;
import org.millenaire.village.ControlledMilitaryService;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;

public record ControlledRelationPayload(String villageId, String targetVillageId, int value) implements CustomPacketPayload {
   public static final int VALUE_GOOD = 100;
   public static final int VALUE_NEUTRAL = 0;
   public static final int VALUE_BAD = -100;
   public static final Type<ControlledRelationPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "controlled_relation"));
   public static final StreamCodec<ByteBuf, ControlledRelationPayload> STREAM_CODEC = StreamCodec.of(
      ControlledRelationPayload::encode, ControlledRelationPayload::decode
   );
   private static final int MAX_ID_LENGTH = 128;

   private static void encode(ByteBuf buf, ControlledRelationPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.villageId);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.targetVillageId);
      ByteBufCodecs.VAR_INT.encode(buf, payload.value);
   }

   private static ControlledRelationPayload decode(ByteBuf buf) {
      String villageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (villageId.length() > 128) {
         villageId = villageId.substring(0, 128);
      }

      String targetVillageId = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (targetVillageId.length() > 128) {
         targetVillageId = targetVillageId.substring(0, 128);
      }

      int value = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      return new ControlledRelationPayload(villageId, targetVillageId, value);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(ControlledRelationPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
               int value = payload.value;
               if (value == 100 || value == 0 || value == -100) {
                  UUID actingUuid;
                  UUID targetUuid;
                  try {
                     actingUuid = UUID.fromString(payload.villageId);
                     targetUuid = UUID.fromString(payload.targetVillageId);
                  } catch (IllegalArgumentException e) {
                     return;
                  }

                  VillageManager vm = VillageSavedData.get(serverLevel).getVillageManager();
                  Village village = vm.getVillage(new VillageId(actingUuid));
                  if (village != null) {
                     if (village.getOwnerUUID() != null && village.getOwnerUUID().equals(player.getUUID())) {
                        VillageId targetId = new VillageId(targetUuid);
                        Village target = vm.getVillage(targetId);
                        if (target != null) {
                           village.adjustRelationSymmetric(serverLevel, targetId, value, true);
                           VillageSavedData.get(serverLevel).setDirty();
                           SovereignDiplomacyBridge.onControlledRelationChanged(serverLevel, village, target, value);
                           ControlledMilitaryService.sendRefresh(player, serverLevel, village);
                        }
                     } else {
                        player.sendSystemMessage(Component.translatable("millenaire.combat.raid_not_owner"));
                     }
                  }
               }
            }
         }
      });
   }
}
