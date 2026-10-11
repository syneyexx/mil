package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.millenaire.building.BuildingInstance;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.presentation.CivilizationActionId;
import org.millenaire.civilization.presentation.CivilizationActionService;
import org.millenaire.entity.VillagerInteraction;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;
import org.millenaire.item.PlayerInventoryHelper;
import org.millenaire.network.MapData;
import org.millenaire.network.PanelContentPayload;
import org.millenaire.network.ServerPayloadGuards;
import org.millenaire.network.VillageBookActionPayload;
import org.millenaire.network.VillageBookPayload;
import org.millenaire.network.VillageBookRefreshRequestPayload;
import org.millenaire.network.VillageScrollPurchasePayload;
import org.millenaire.village.panel.ConstructionPanelGenerator;
import org.millenaire.village.panel.MilitaryPanelGenerator;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelContentGenerator;
import org.millenaire.village.panel.VillageOverviewPanelGenerator;
import org.slf4j.Logger;

public final class VillageBookService {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int SCROLL_PRICE = 128;
   public static final int SCROLL_REPUTATION = 8192;
   public static final int REGEN_INTERVAL = 1000;
   private static final String TAG_VILLAGE_ID = "village_id";
   private static final String TAG_VILLAGE_NAME = "village_name";
   private static final double MAX_PURCHASE_DISTANCE_SQ = 4096.0;
   /** ~5 seconds at 20 TPS — short TTL for book-action double-click dedupe. */
   private static final long BOOK_ACTION_DEDUPE_TTL_TICKS = 100L;
   /** playerUuid|villageUuid -> last heavy refresh gameTime */
   private static final Map<String, Long> REFRESH_LAST_TICK = new HashMap<>();

   private VillageBookService() {
   }

   public static ItemStack createScrollForVillage(Village village) {
      ItemStack stack = new ItemStack((ItemLike)ModItems.VILLAGE_SCROLL.get());
      CompoundTag tag = new CompoundTag();
      tag.putString("village_id", village.getId().uuid().toString());
      tag.putString("village_name", village.getVillageName());
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      return stack;
   }

   public static List<PanelContent> generateBookContent(Village village, @Nullable ServerLevel level, @Nullable ServerPlayer player) {
      ServerLevel effectiveLevel = level != null && isDegraded(village, level) ? null : level;
      BuildingInstance townhall = village.getTownhall();
      List<PanelContent> sections = new ArrayList<>(8);
      sections.add(VillageOverviewPanelGenerator.generateSummary(village, townhall, effectiveLevel));
      sections.add(VillageOverviewPanelGenerator.generatePopulation(village, effectiveLevel));
      sections.add(ConstructionPanelGenerator.generateConstructions(village, player));
      sections.add(ConstructionPanelGenerator.generateProjects(village, player));
      sections.add(ConstructionPanelGenerator.generateResources(village, effectiveLevel));
      sections.add(MilitaryPanelGenerator.generateMilitary(village, effectiveLevel));
      sections.add(VillageOverviewPanelGenerator.generateCivilization(village, effectiveLevel, player));
      if (effectiveLevel != null) {
         sections.add(
            org.millenaire.religion.ReligionService.get().presentation().villageBookSection(effectiveLevel, village, player)
         );
      }
      sections.add(VillageOverviewPanelGenerator.generateChronicle(village));
      return sections;
   }

   public static boolean isDegraded(Village village, ServerLevel level) {
      BuildingInstance townhall = village.getTownhall();
      return townhall == null ? true : !level.isLoaded(townhall.getOrigin());
   }

   /**
    * Build and send a village book payload, optionally restoring nav and attaching feedback.
    */
   public static void openOrRefreshBook(
      ServerPlayer player,
      Village village,
      int section,
      int page,
      @Nullable String selectedTarget
   ) {
      openOrRefreshBook(player, village, section, page, selectedTarget, false, false, "", "");
   }

   public static void openOrRefreshBook(
      ServerPlayer player,
      Village village,
      int section,
      int page,
      @Nullable String selectedTarget,
      boolean hasFeedback,
      boolean feedbackSuccess,
      @Nullable String feedbackKey,
      @Nullable String feedbackDetail
   ) {
      if (player == null || village == null) {
         return;
      }
      ServerLevel serverLevel = player.serverLevel();
      try {
         boolean degraded = isDegraded(village, serverLevel);
         ServerLevel effectiveLevel = degraded ? null : serverLevel;
         List<PanelContent> sections = generateBookContent(village, effectiveLevel, player);
         List<MapData.MapBuilding> mapBuildings = List.of();
         List<MapData.MapVillager> mapVillagers = List.of();
         int mapPlayerX = 0;
         int mapPlayerZ = 0;
         int mapCenterX = 0;
         int mapCenterZ = 0;
         MapData.MapTerrain mapTerrain = MapData.MapTerrain.EMPTY;
         List<MapData.MapPath> mapPaths = List.of();
         boolean hasMapData = false;
         if (!degraded) {
            PanelContent dummyContent = sections.get(0);
            PanelContentPayload mapPayload = PanelContentGenerator.createMapPayload(dummyContent, village, serverLevel, player);
            mapBuildings = mapPayload.mapBuildings();
            mapVillagers = mapPayload.mapVillagers();
            mapPlayerX = mapPayload.mapPlayerX();
            mapPlayerZ = mapPayload.mapPlayerZ();
            mapCenterX = mapPayload.mapCenterX();
            mapCenterZ = mapPayload.mapCenterZ();
            mapTerrain = mapPayload.mapTerrain();
            mapPaths = mapPayload.mapPaths();
            hasMapData = mapPayload.hasMapData();
         }

         String villageId = village.getId().uuid().toString();
         VillageBookPayload payload = VillageBookPayload.open(
            village.getVillageName(),
            village.getCultureId().toString(),
            sections,
            mapBuildings,
            mapVillagers,
            mapPlayerX,
            mapPlayerZ,
            mapCenterX,
            mapCenterZ,
            mapTerrain,
            mapPaths,
            hasMapData,
            degraded,
            villageId
         );
         payload = payload.withNavRestore(Math.max(0, section), Math.max(0, page), selectedTarget == null ? "" : selectedTarget);
         if (hasFeedback) {
            payload = payload.withFeedback(
               feedbackSuccess,
               feedbackKey == null ? "" : feedbackKey,
               feedbackDetail == null ? "" : feedbackDetail,
               Math.max(0, section),
               Math.max(0, page),
               selectedTarget == null ? "" : selectedTarget
            );
         }
         PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
      } catch (Exception e) {
         LOGGER.error("Error generating village book for {}", village.getVillageName(), e);
         player.sendSystemMessage(Component.translatable("millenaire.scroll.error.generation_failed"));
      }
   }

   public static void handleActionPacket(VillageBookActionPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel level = player.serverLevel();
         UUID villageUuid;
         try {
            villageUuid = UUID.fromString(payload.villageId());
         } catch (IllegalArgumentException e) {
            LOGGER.warn("Village book action: invalid village UUID '{}'", payload.villageId());
            return;
         }

         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
         if (village == null) {
            player.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return;
         }
         int restoreSection = Math.max(0, payload.sectionIndex());
         int restorePage = Math.max(0, payload.pageIndex());
         if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
            openOrRefreshBook(
               player,
               village,
               restoreSection,
               restorePage,
               payload.targetId(),
               true,
               false,
               "civilization.millenaire.action.error.too_far",
               ""
            );
            return;
         }

         String requestId = payload.requestId() == null ? "" : payload.requestId().trim();
         if (requestId.isEmpty()) {
            return;
         }
         String dedupeKey = "book-action:" + player.getUUID() + ":" + requestId;
         if (!CivilizationSavedData.get(level).tryMarkPublished(dedupeKey, level.getGameTime(), BOOK_ACTION_DEDUPE_TTL_TICKS)) {
            openOrRefreshBook(
               player,
               village,
               restoreSection,
               restorePage,
               payload.targetId(),
               true,
               false,
               "civilization.millenaire.action.error.duplicate",
               ""
            );
            return;
         }

         CivilizationActionId actionId;
         try {
            actionId = CivilizationActionId.valueOf(payload.actionId());
         } catch (IllegalArgumentException e) {
            openOrRefreshBook(
               player,
               village,
               restoreSection,
               restorePage,
               payload.targetId(),
               true,
               false,
               "civilization.millenaire.action.error.invalid",
               ""
            );
            return;
         }

         String targetId = payload.targetId() == null || payload.targetId().isBlank() ? null : payload.targetId();
         CivilizationActionService.ActionResult result = CivilizationActionService.execute(
            level, player, village, actionId, targetId, null, false
         );
         openOrRefreshBook(
            player,
            village,
            restoreSection,
            restorePage,
            targetId == null ? "" : targetId,
            true,
            result.success(),
            result.messageKey(),
            result.detail() == null ? "" : result.detail()
         );
      });
   }

   public static void handleRefreshPacket(VillageBookRefreshRequestPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         ServerLevel level = player.serverLevel();
         UUID villageUuid;
         try {
            villageUuid = UUID.fromString(payload.villageId());
         } catch (IllegalArgumentException e) {
            LOGGER.warn("Village book refresh: invalid village UUID '{}'", payload.villageId());
            return;
         }

         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
         if (village == null) {
            player.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
            return;
         }
         if (!ServerPayloadGuards.isWithinVillageReach(player, village)) {
            openOrRefreshBook(
               player,
               village,
               payload.sectionIndex(),
               payload.pageIndex(),
               payload.selectedTargetId(),
               true,
               false,
               "civilization.millenaire.action.error.too_far",
               ""
            );
            return;
         }

         if (!allowHeavyRefresh(player, village, level.getGameTime())) {
            return;
         }

         openOrRefreshBook(
            player,
            village,
            payload.sectionIndex(),
            payload.pageIndex(),
            payload.selectedTargetId()
         );
      });
   }

   public static void handleScrollPurchasePacket(VillageScrollPurchasePayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            UUID villageUuid;
            try {
               villageUuid = UUID.fromString(payload.villageId());
            } catch (IllegalArgumentException e) {
               LOGGER.warn("Scroll purchase payload: invalid UUID '{}'", payload.villageId());
               return;
            }

            handleScrollPurchase(serverPlayer, villageUuid);
            VillagerInteraction.sendChiefRefresh(serverPlayer, serverPlayer.serverLevel(), payload.villageId());
         }
      });
   }

   public static void handleScrollPurchase(ServerPlayer player, UUID villageUuid) {
      ServerLevel level = player.serverLevel();
      VillageSavedData savedData = VillageSavedData.get(level);
      Village village = savedData.getVillageManager().getVillage(new VillageId(villageUuid));
      if (village == null) {
         player.sendSystemMessage(Component.translatable("millenaire.scroll.error.village_not_found"));
         LOGGER.warn("Scroll purchase failed: village {} not found", villageUuid);
      } else {
         double distSq = player.blockPosition().distSqr(village.getCenter());
         if (distSq > 4096.0) {
            LOGGER.warn("Scroll purchase denied: player {} too far from village {}", player.getName().getString(), villageUuid);
         } else {
            int rep = village.getCombinedReputation(level, player.getUUID());
            if (rep < 8192) {
               player.sendSystemMessage(Component.translatable("millenaire.scroll.error.reputation"));
            } else if (!MoneyHelper.removeDeniers(player.getInventory(), 128)) {
               player.sendSystemMessage(Component.translatable("millenaire.scroll.error.money"));
            } else {
               ItemStack scroll = createScrollForVillage(village);
               PlayerInventoryHelper.giveOrDrop(player, scroll);
               player.sendSystemMessage(Component.translatable("millenaire.scroll.purchased", new Object[]{village.getVillageName()}));
               LOGGER.debug("Scroll purchased by {} for village {}", player.getName().getString(), village.getVillageName());
            }
         }
      }
   }

   /**
    * Rate-limits heavy book rebuilds from client refresh spam. Opening via item use still
    * goes through {@link #openOrRefreshBook} without this gate.
    */
   private static boolean allowHeavyRefresh(ServerPlayer player, Village village, long gameTime) {
      int minInterval = 20;
      try {
         minInterval = MillenaireServerConfig.SERVER.villageBookRefreshMinIntervalTicks.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      if (minInterval <= 0) {
         return true;
      }
      String key = player.getUUID() + "|" + village.getId().uuid();
      Long last = REFRESH_LAST_TICK.get(key);
      if (last != null && gameTime - last < minInterval) {
         return false;
      }
      REFRESH_LAST_TICK.put(key, gameTime);
      if (REFRESH_LAST_TICK.size() > 512) {
         long cutoff = gameTime - Math.max(200L, minInterval * 10L);
         Iterator<Map.Entry<String, Long>> it = REFRESH_LAST_TICK.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == null || e.getValue() < cutoff) {
               it.remove();
            }
         }
      }
      return true;
   }
}
