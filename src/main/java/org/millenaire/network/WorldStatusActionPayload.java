package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.presentation.CivilizationActionId;
import org.millenaire.civilization.presentation.CivilizationActionService;
import org.millenaire.civilization.presentation.WorldStatusPresentationService;
import org.millenaire.civilization.presentation.WorldStatusSnapshot;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/** Client → server: execute a civilization action from the world status UI, then refresh. */
public record WorldStatusActionPayload(
   String villageId,
   String actionId,
   String targetId,
   String requestId,
   int tabIndex,
   String selectedFactionId
) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_STRING_LENGTH = 128;
   private static final long ACTION_DEDUPE_TTL_TICKS = 40L;
   public static final Type<WorldStatusActionPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("millenaire", "world_status_action"));
   public static final StreamCodec<ByteBuf, WorldStatusActionPayload> STREAM_CODEC =
      StreamCodec.of(WorldStatusActionPayload::encode, WorldStatusActionPayload::decode);

   private static void encode(ByteBuf buf, WorldStatusActionPayload payload) {
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(payload.villageId));
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(payload.actionId));
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(payload.targetId));
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(payload.requestId));
      ByteBufCodecs.VAR_INT.encode(buf, payload.tabIndex);
      ByteBufCodecs.STRING_UTF8.encode(buf, nullToEmpty(payload.selectedFactionId));
   }

   private static WorldStatusActionPayload decode(ByteBuf buf) {
      return new WorldStatusActionPayload(
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf)),
         ByteBufCodecs.VAR_INT.decode(buf),
         truncate(ByteBufCodecs.STRING_UTF8.decode(buf))
      );
   }

   private static String nullToEmpty(String value) {
      return value == null ? "" : value;
   }

   private static String truncate(String value) {
      if (value == null) {
         return "";
      }
      return value.length() > MAX_STRING_LENGTH ? value.substring(0, MAX_STRING_LENGTH) : value;
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(WorldStatusActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel level = player.serverLevel();
         UUID villageUuid;
         try {
            villageUuid = UUID.fromString(payload.villageId());
         } catch (IllegalArgumentException e) {
            LOGGER.warn("World status action: invalid village UUID '{}'", payload.villageId());
            sendRefresh(player, level, payload.tabIndex(), payload.selectedFactionId());
            return;
         }

         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
         if (village == null) {
            sendRefresh(player, level, payload.tabIndex(), payload.selectedFactionId());
            return;
         }

         String requestId = payload.requestId() == null ? "" : payload.requestId().trim();
         if (requestId.isEmpty()) {
            return;
         }
         String dedupeKey = "world-status-action:" + player.getUUID() + ":" + requestId;
         if (!CivilizationSavedData.get(level).tryMarkPublished(dedupeKey, level.getGameTime(), ACTION_DEDUPE_TTL_TICKS)) {
            sendRefresh(player, level, payload.tabIndex(), payload.selectedFactionId());
            return;
         }

         CivilizationActionId actionId;
         try {
            actionId = CivilizationActionId.valueOf(payload.actionId());
         } catch (IllegalArgumentException e) {
            sendRefresh(player, level, payload.tabIndex(), payload.selectedFactionId());
            return;
         }

         String targetId = payload.targetId() == null || payload.targetId().isBlank() ? null : payload.targetId();
         CivilizationActionService.ActionResult result = CivilizationActionService.execute(
            level, player, village, actionId, targetId, null, true
         );
         result.notify(player);
         sendRefresh(player, level, payload.tabIndex(), payload.selectedFactionId());
      });
   }

   private static void sendRefresh(ServerPlayer player, ServerLevel level, int tabIndex, String selectedFactionId) {
      WorldStatusSnapshot snap = WorldStatusPresentationService.build(level, player, tabIndex, selectedFactionId);
      PacketDistributor.sendToPlayer(player, WorldStatusContentPayload.fromSnapshot(snap), new CustomPacketPayload[0]);
   }
}
