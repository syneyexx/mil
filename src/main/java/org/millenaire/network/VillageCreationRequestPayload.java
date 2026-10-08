package org.millenaire.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
import org.millenaire.item.ModItems;
import org.millenaire.item.SummoningWandItem;
import org.millenaire.village.PlayerCultureReputation;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.VillageSpawner;
import org.slf4j.Logger;

public record VillageCreationRequestPayload(BlockPos pos, String cultureKey, String typeKey, boolean randomSpawn) implements CustomPacketPayload {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Type<VillageCreationRequestPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "village_creation_request"));
   public static final StreamCodec<ByteBuf, VillageCreationRequestPayload> STREAM_CODEC = StreamCodec.of(
      VillageCreationRequestPayload::encode, VillageCreationRequestPayload::decode
   );
   private static final int MAX_KEY_LENGTH = 128;
   private static final double MAX_REQUEST_DISTANCE = 16.0;
   private static final PayloadRateLimiter RATE_LIMITER = new PayloadRateLimiter(1000L);

   private static void encode(ByteBuf buf, VillageCreationRequestPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getX());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getY());
      ByteBufCodecs.VAR_INT.encode(buf, payload.pos.getZ());
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.cultureKey);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.typeKey);
      buf.writeBoolean(payload.randomSpawn);
   }

   private static VillageCreationRequestPayload decode(ByteBuf buf) {
      int x = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int y = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int z = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      String cultureKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (cultureKey.length() > 128) {
         cultureKey = cultureKey.substring(0, 128);
      }

      String typeKey = (String)ByteBufCodecs.STRING_UTF8.decode(buf);
      if (typeKey.length() > 128) {
         typeKey = typeKey.substring(0, 128);
      }

      boolean randomSpawn = buf.readBoolean();
      return new VillageCreationRequestPayload(new BlockPos(x, y, z), cultureKey, typeKey, randomSpawn);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(VillageCreationRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(
         () -> {
            if (context.player() instanceof ServerPlayer player) {
               if (player.level() instanceof ServerLevel serverLevel) {
                  if (RATE_LIMITER.acquire(player.getUUID())) {
                     if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
                        player.sendSystemMessage(Component.translatable("ui.wand.overworld_only"));
                     } else {
                        boolean holdsWand = player.getMainHandItem().is((Item)ModItems.SUMMONING_WAND.get())
                           || player.getOffhandItem().is((Item)ModItems.SUMMONING_WAND.get());
                        if (!holdsWand) {
                           LOGGER.warn("[Millenaire] Player {} sent village creation request without holding the summoning wand", player.getName().getString());
                        } else if (!ServerPayloadGuards.isWithinDistance(player, payload.pos, 16.0)) {
                           LOGGER.warn(
                              "[Millenaire] Player {} sent village creation request for a position too far away ({})",
                              player.getName().getString(),
                              payload.pos.toShortString()
                           );
                        } else {
                           VillageManager villageManager = VillageSavedData.get(serverLevel).getVillageManager();
                           int minDistance = MillenaireServerConfig.SERVER.minVillageDistance.getAsInt();
                           if (villageManager.isWithinMinDistance(payload.pos, minDistance)) {
                              player.sendSystemMessage(Component.translatable("millenaire.error.village_too_close", new Object[]{minDistance}));
                           } else {
                              VillageType villageType;
                              if (payload.randomSpawn) {
                                 List<VillageType> compatible = SummoningWandItem.getCompatibleVillageTypes(serverLevel, payload.pos);
                                 if (compatible.isEmpty()) {
                                    player.sendSystemMessage(Component.translatable("ui.wand.no_compatible_village_type"));
                                    return;
                                 }

                                 int totalWeight = 0;

                                 for (VillageType vt : compatible) {
                                    totalWeight += vt.weight();
                                 }

                                 if (totalWeight <= 0) {
                                    player.sendSystemMessage(Component.translatable("ui.wand.no_weighted_village_type"));
                                    return;
                                 }

                                 int roll = ThreadLocalRandom.current().nextInt(totalWeight);
                                 villageType = compatible.getLast();
                                 int cumulative = 0;

                                 for (VillageType vt : compatible) {
                                    cumulative += vt.weight();
                                    if (roll < cumulative) {
                                       villageType = vt;
                                       break;
                                    }
                                 }
                              } else {
                                 ResourceLocation villageTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", payload.typeKey);
                                 villageType = ModCultures.getVillageType(villageTypeId);
                                 if (villageType == null) {
                                    player.sendSystemMessage(Component.translatable("ui.wand.village_type_not_found", new Object[]{payload.typeKey}));
                                    return;
                                 }

                                 if (villageType.playerControlled()) {
                                    PlayerCultureReputation cultureRep = PlayerCultureReputation.get(serverLevel);
                                    if (!cultureRep.hasCultureControl(player.getUUID(), villageType.culture())) {
                                       player.sendSystemMessage(Component.translatable("millenaire.error.no_culture_control", new Object[]{villageType.name()}));
                                       return;
                                    }
                                 }
                              }

                              Component failure = VillageSpawner.spawnVillage(
                                 serverLevel, payload.pos, villageType, 0, null, null, villageType.playerControlled() ? player : null
                              );
                              if (failure == null) {
                                 player.sendSystemMessage(
                                    Component.translatable(
                                       "ui.wand.village_created", new Object[]{villageType.name(), villageType.id().getPath(), payload.pos.toShortString()}
                                    )
                                 );
                                 MillAdvancements.grant(player, MillAdvancements.SUMMONING_WAND);
                              } else {
                                 player.sendSystemMessage(failure);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      );
   }
}
