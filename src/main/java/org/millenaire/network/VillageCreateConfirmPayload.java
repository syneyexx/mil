package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faith.FaithService;
import org.millenaire.item.ModItems;
import org.millenaire.item.VillageCreateWandItem;
import org.millenaire.village.CultureWarService;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

public record VillageCreateConfirmPayload(
   BlockPos pos, String cultureKey, String typeKey, String villageName, boolean startOwnFaith, String customFaithName
) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<VillageCreateConfirmPayload> TYPE = new Type(
      ResourceLocation.fromNamespaceAndPath("millenaire", "village_create_confirm")
   );
   public static final StreamCodec<ByteBuf, VillageCreateConfirmPayload> STREAM_CODEC = StreamCodec.of(
      VillageCreateConfirmPayload::encode, VillageCreateConfirmPayload::decode
   );
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(1000L);

   private static void encode(ByteBuf buf, VillageCreateConfirmPayload p) {
      ByteBufCodecs.VAR_INT.encode(buf, p.pos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, p.pos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, p.pos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, p.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.typeKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.villageName);
      buf.writeBoolean(p.startOwnFaith);
      ByteBufCodecs.STRING_UTF8.encode(buf, p.customFaithName != null ? p.customFaithName : "");
   }

   private static VillageCreateConfirmPayload decode(ByteBuf buf) {
      return new VillageCreateConfirmPayload(
         new BlockPos(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         ByteBufCodecs.STRING_UTF8.decode(buf),
         buf.readBoolean(),
         ByteBufCodecs.STRING_UTF8.decode(buf)
      );
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(VillageCreateConfirmPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         if (!(player.level() instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(Level.OVERWORLD)) {
            player.sendSystemMessage(Component.translatable("ui.village_create_wand.overworld_only"));
            return;
         }
         if (!RATE_LIMITER.acquire(player.getUUID())) {
            return;
         }
         boolean holdsWand = player.getMainHandItem().is((Item)ModItems.VILLAGE_CREATE_WAND.get())
            || player.getOffhandItem().is((Item)ModItems.VILLAGE_CREATE_WAND.get());
         if (!holdsWand) {
            LOGGER.warn("[Millenaire] Player {} sent village create confirm without holding VillageCreate wand", player.getName().getString());
            return;
         }
         if (!ServerPayloadGuards.isWithinDistance(player, payload.pos, 16.0)) {
            return;
         }
         if (VillageCreateWandItem.findOwnedVillage(serverLevel, player.getUUID()) != null) {
            player.sendSystemMessage(Component.translatable("ui.village_create_wand.already_owns"));
            return;
         }

         String sanitizedName = VillageCreateWandItem.sanitizeVillageName(payload.villageName);
         if (sanitizedName.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.millenaire.village_create.name_required"));
            return;
         }

         boolean customOutfit = "player_custom".equals(payload.cultureKey());
         VillageType villageType = resolveVillageType(payload, customOutfit);
         if (villageType == null || !villageType.playerControlled()) {
            player.sendSystemMessage(Component.translatable("ui.wand.village_type_not_found", payload.typeKey));
            return;
         }
         PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
         if (!customOutfit && !cultureRep.hasCultureControl(player.getUUID(), villageType.culture())) {
            player.sendSystemMessage(Component.translatable("millenaire.error.no_culture_control", villageType.name()));
            return;
         }

         VillageManager villageManager = VillageSavedData.get(serverLevel).getVillageManager();
         if (villageManager.overlapsExistingVillage(payload.pos, villageType.radius())) {
            player.sendSystemMessage(Component.translatable("ui.village_create_wand.overlaps"));
            return;
         }

         int warDistance = MillenaireServerConfig.SERVER.playerFoundTerritoryWarDistance.getAsInt();
         Set<ResourceLocation> conflictCultures = new HashSet<>();
         for (Village other : villageManager.getAllVillages()) {
            if (other.isLoneBuilding()) {
               continue;
            }
            double dist = Math.sqrt(other.getCenter().distSqr(payload.pos));
            if (dist < warDistance) {
               conflictCultures.add(other.getCultureId());
            }
         }

         Component failure = VillageSpawner.spawnVillage(serverLevel, payload.pos, villageType, 0, null, null, player);
         if (failure != null) {
            player.sendSystemMessage(failure);
            return;
         }

         Village founded = VillageCreateWandItem.findOwnedVillage(serverLevel, player.getUUID());
         if (founded != null) {
            founded.setVillageName(sanitizedName);
            VillageSavedData.get(serverLevel).setDirty();
            org.millenaire.compat.WaystonePlacer.syncNameForVillage(serverLevel, founded);
         }
         if (founded != null && customOutfit) {
            founded.setCustomOutfitEnabled(true);
            VillageSavedData.get(serverLevel).setDirty();
            player.sendSystemMessage(Component.translatable("gui.millenaire.village_create.custom_outfit_hint"));
         }
         if (founded != null && payload.startOwnFaith()) {
            String faithName = VillageCreateWandItem.sanitizeVillageName(payload.customFaithName());
            if (faithName.isEmpty()) {
               faithName = sanitizedName + " Faith";
            }
            FaithService.startPlayerFaith(serverLevel, founded, faithName);
            VillageSavedData.get(serverLevel).setDirty();
            player.sendSystemMessage(Component.translatable("gui.millenaire.village_create.faith_started", faithName));
         }
         if (founded != null && !conflictCultures.isEmpty()) {
            for (ResourceLocation enemyCulture : conflictCultures) {
               if (!enemyCulture.equals(founded.getCultureId())) {
                  CultureWarService.playerDeclareWar(serverLevel, founded.getCultureId(), enemyCulture);
               }
            }
            for (Village other : villageManager.getAllVillages()) {
               if (other == founded || other.isLoneBuilding()) {
                  continue;
               }
               double dist = Math.sqrt(other.getCenter().distSqr(founded.getCenter()));
               if (dist < warDistance) {
                  founded.setRelation(other.getId(), VillageRelations.OPEN_CONFLICT);
                  other.setRelation(founded.getId(), VillageRelations.OPEN_CONFLICT);
               }
            }
            VillageSavedData.get(serverLevel).setDirty();
            player.sendSystemMessage(Component.translatable("ui.village_create_wand.territory_war"));
         }

         String displayName = founded != null && founded.getVillageName() != null ? founded.getVillageName() : sanitizedName;
         player.sendSystemMessage(
            Component.translatable("ui.wand.village_created", displayName, villageType.id().getPath(), payload.pos.toShortString())
         );
         MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
      });
   }

   private static VillageType resolveVillageType(VillageCreateConfirmPayload payload, boolean customOutfit) {
      if (customOutfit) {
         ResourceLocation clientTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.typeKey);
         VillageType clientType = ModCultures.getVillageType(clientTypeId);
         if (clientType == null || !clientType.playerControlled()) {
            return null;
         }
         VillageType resolved = VillageCreateWandItem.resolveDefaultControlledType(clientType.culture());
         return resolved != null ? resolved : clientType;
      }
      ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.cultureKey);
      VillageType resolved = VillageCreateWandItem.resolveDefaultControlledType(cultureId);
      if (resolved != null) {
         return resolved;
      }
      ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.typeKey);
      return ModCultures.getVillageType(typeId);
   }
}
