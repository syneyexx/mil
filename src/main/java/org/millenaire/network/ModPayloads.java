package org.millenaire.network;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.millenaire.village.BuildingPurchaseService;
import org.millenaire.village.VillageBookService;
import org.slf4j.Logger;

public final class ModPayloads {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final List<ModPayloads.ClientboundDef<?>> CLIENTBOUND_DEFS = List.of(
      new ModPayloads.ClientboundDef(VillagerInfoPayload.TYPE, VillagerInfoPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(VillageChiefPayload.TYPE, VillageChiefPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(VillageChiefUpdatePayload.TYPE, VillageChiefUpdatePayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(TradePayload.TYPE, TradePayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(TradeStockUpdatePayload.TYPE, TradeStockUpdatePayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(PanelContentPayload.TYPE, PanelContentPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(VillageBookPayload.TYPE, VillageBookPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ImportTableSyncPayload.TYPE, ImportTableSyncPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ImportTableCostsPayload.TYPE, ImportTableCostsPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ImportTableCultureListPayload.TYPE, ImportTableCultureListPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ImportTableBuildingViewPayload.TYPE, ImportTableBuildingViewPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(TravelBookContentPayload.TYPE, TravelBookContentPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(FireplacePositionsPayload.TYPE, FireplacePositionsPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(SpeechChatPayload.TYPE, SpeechChatPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(VillageTypeListPayload.TYPE, VillageTypeListPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(InfoPanelContentPayload.TYPE, InfoPanelContentPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(QuestInstanceSyncPayload.TYPE, QuestInstanceSyncPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(QuestInstanceDestroyPayload.TYPE, QuestInstanceDestroyPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(QuestResultTextPayload.TYPE, QuestResultTextPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(QuestOpenPayload.TYPE, QuestOpenPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(WandDebugMenuPayload.TYPE, WandDebugMenuPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(NegationWandPayload.TYPE, NegationWandPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(BuildingProjectListPayload.TYPE, BuildingProjectListPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ControlledProjectsPayload.TYPE, ControlledProjectsPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ControlledMilitaryPayload.TYPE, ControlledMilitaryPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(OpenHireScreenPayload.TYPE, OpenHireScreenPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(UnlockingToastPayload.TYPE, UnlockingToastPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(PlanPreviewPayload.TYPE, PlanPreviewPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(ProjectCancelPromptPayload.TYPE, ProjectCancelPromptPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(org.millenaire.chat.NpcChatOpenPayload.TYPE, org.millenaire.chat.NpcChatOpenPayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(org.millenaire.chat.NpcChatLinePayload.TYPE, org.millenaire.chat.NpcChatLinePayload.STREAM_CODEC),
      new ModPayloads.ClientboundDef(org.millenaire.chat.NpcChatClosedPayload.TYPE, org.millenaire.chat.NpcChatClosedPayload.STREAM_CODEC)
   );

   private ModPayloads() {
   }

   static List<ModPayloads.ClientboundDef<?>> clientboundDefs() {
      return CLIENTBOUND_DEFS;
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("3");
      if (FMLEnvironment.dist.isClient()) {
         Map<Type<?>, IPayloadHandler<?>> handlers = ModPayloads.ClientHandlers.handlers();
         Set<Type<?>> orphans = new HashSet<>(handlers.keySet());

         for (ModPayloads.ClientboundDef<?> def : CLIENTBOUND_DEFS) {
            IPayloadHandler<?> handler = handlers.get(def.type());
            if (handler == null) {
               throw new IllegalStateException("No client handler mapped for clientbound payload " + def.type().id());
            }

            orphans.remove(def.type());
            registerClientbound(registrar, def, handler);
         }

         if (!orphans.isEmpty()) {
            LOGGER.warn(
               "Client payload handlers mapped for types missing from CLIENTBOUND_DEFS: {}",
               orphans.stream().map(type -> type.id().toString()).sorted().toList()
            );
         }
      } else {
         for (ModPayloads.ClientboundDef<?> def : CLIENTBOUND_DEFS) {
            registerNoop(registrar, def);
         }
      }

      registrar.playToServer(VillageScrollPurchasePayload.TYPE, VillageScrollPurchasePayload.STREAM_CODEC, VillageBookService::handleScrollPurchasePacket);
      registrar.playToServer(BuildingPurchasePayload.TYPE, BuildingPurchasePayload.STREAM_CODEC, BuildingPurchaseService::handlePurchasePacket);
      registrar.playToServer(ImportTableActionPayload.TYPE, ImportTableActionPayload.STREAM_CODEC, ImportTableActionHandler::handleAction);
      registrar.playToServer(
         ImportTableCultureListRequestPayload.TYPE, ImportTableCultureListRequestPayload.STREAM_CODEC, ImportTableCultureListRequestPayload::handleOnServer
      );
      registrar.playToServer(
         ImportTableBuildingViewRequestPayload.TYPE, ImportTableBuildingViewRequestPayload.STREAM_CODEC, ImportTableBuildingViewRequestPayload::handleOnServer
      );
      registrar.playToServer(TravelBookRequestPayload.TYPE, TravelBookRequestPayload.STREAM_CODEC, TravelBookRequestPayload::handleOnServer);
      registrar.playToServer(DiplomacyActionPayload.TYPE, DiplomacyActionPayload.STREAM_CODEC, DiplomacyActionPayload::handleOnServer);
      registrar.playToServer(VillageCreationRequestPayload.TYPE, VillageCreationRequestPayload.STREAM_CODEC, VillageCreationRequestPayload::handleOnServer);
      registrar.playToServer(CropLearningPayload.TYPE, CropLearningPayload.STREAM_CODEC, CropLearningPayload::handleOnServer);
      registrar.playToServer(HuntingLearningPayload.TYPE, HuntingLearningPayload.STREAM_CODEC, HuntingLearningPayload::handleOnServer);
      registrar.playToServer(CultureControlPurchasePayload.TYPE, CultureControlPurchasePayload.STREAM_CODEC, CultureControlPurchasePayload::handleOnServer);
      registrar.playToServer(InfoPanelRequestPayload.TYPE, InfoPanelRequestPayload.STREAM_CODEC, InfoPanelRequestPayload::handleOnServer);
      registrar.playToServer(QuestCompleteStepPayload.TYPE, QuestCompleteStepPayload.STREAM_CODEC, QuestCompleteStepPayload::handle);
      registrar.playToServer(QuestRefusePayload.TYPE, QuestRefusePayload.STREAM_CODEC, QuestRefusePayload::handleOnServer);
      registrar.playToServer(WandDebugActionPayload.TYPE, WandDebugActionPayload.STREAM_CODEC, WandDebugActionPayload::handleOnServer);
      registrar.playToServer(NegationWandConfirmPayload.TYPE, NegationWandConfirmPayload.STREAM_CODEC, NegationWandConfirmPayload::handleOnServer);
      registrar.playToServer(BuildingProjectRequestPayload.TYPE, BuildingProjectRequestPayload.STREAM_CODEC, BuildingProjectRequestPayload::handleOnServer);
      registrar.playToServer(
         BuildingUpgradeToggleRequestPayload.TYPE, BuildingUpgradeToggleRequestPayload.STREAM_CODEC, BuildingUpgradeToggleRequestPayload::handleOnServer
      );
      registrar.playToServer(
         BuildingProjectCancelRequestPayload.TYPE, BuildingProjectCancelRequestPayload.STREAM_CODEC, BuildingProjectCancelRequestPayload::handleOnServer
      );
      registrar.playToServer(
         BuildingProjectReorderRequestPayload.TYPE, BuildingProjectReorderRequestPayload.STREAM_CODEC, BuildingProjectReorderRequestPayload::handleOnServer
      );
      registrar.playToServer(BuildingForgetRequestPayload.TYPE, BuildingForgetRequestPayload.STREAM_CODEC, BuildingForgetRequestPayload::handleOnServer);
      registrar.playToServer(RaidActionPayload.TYPE, RaidActionPayload.STREAM_CODEC, RaidActionPayload::handleOnServer);
      registrar.playToServer(ControlledRelationPayload.TYPE, ControlledRelationPayload.STREAM_CODEC, ControlledRelationPayload::handleOnServer);
      registrar.playToServer(HireActionPayload.TYPE, HireActionPayload.STREAM_CODEC, HireActionPayload::handleOnServer);
      registrar.playToServer(ToggleStancePayload.TYPE, ToggleStancePayload.STREAM_CODEC, ToggleStancePayload::handleOnServer);
      registrar.playToServer(PlanPreviewRequestPayload.TYPE, PlanPreviewRequestPayload.STREAM_CODEC, PlanPreviewRequestPayload::handleOnServer);
      registrar.playToServer(NpcChatOpenRequestPayload.TYPE, NpcChatOpenRequestPayload.STREAM_CODEC, NpcChatOpenRequestPayload::handleOnServer);
      registrar.playToServer(NpcChatSendPayload.TYPE, NpcChatSendPayload.STREAM_CODEC, NpcChatSendPayload::handleOnServer);
      registrar.playToServer(NpcChatCloseRequestPayload.TYPE, NpcChatCloseRequestPayload.STREAM_CODEC, NpcChatCloseRequestPayload::handleOnServer);
   }

   private static <T extends CustomPacketPayload> void registerNoop(PayloadRegistrar registrar, ModPayloads.ClientboundDef<T> def) {
      registrar.playToClient(def.type(), def.codec(), (payload, context) -> {});
   }

   @SuppressWarnings("unchecked")
   private static <T extends CustomPacketPayload> void registerClientbound(
      PayloadRegistrar registrar, ModPayloads.ClientboundDef<T> def, IPayloadHandler<?> handler
   ) {
      registrar.playToClient(def.type(), def.codec(), (IPayloadHandler<T>) handler);
   }

   static final class ClientHandlers {
      private ClientHandlers() {
      }

      static Map<Type<?>, IPayloadHandler<?>> handlers() {
         Map<Type<?>, IPayloadHandler<?>> map = new HashMap<>();
         put(map, VillagerInfoPayload.TYPE, ClientPayloadHandler::handleVillagerInfo);
         put(map, VillageChiefPayload.TYPE, ClientPayloadHandler::handleVillageChief);
         put(map, VillageChiefUpdatePayload.TYPE, ClientPayloadHandler::handleVillageChiefUpdate);
         put(map, TradePayload.TYPE, ClientPayloadHandler::handleTrade);
         put(map, TradeStockUpdatePayload.TYPE, ClientPayloadHandler::handleTradeStockUpdate);
         put(map, PanelContentPayload.TYPE, ClientPayloadHandler::handlePanelContent);
         put(map, VillageBookPayload.TYPE, ClientPayloadHandler::handleVillageBook);
         put(map, ImportTableSyncPayload.TYPE, ClientPayloadHandler::handleImportTableSync);
         put(map, ImportTableCostsPayload.TYPE, ClientPayloadHandler::handleImportTableCosts);
         put(map, ImportTableCultureListPayload.TYPE, ClientPayloadHandler::handleImportTableCultureList);
         put(map, ImportTableBuildingViewPayload.TYPE, ClientPayloadHandler::handleImportTableBuildingView);
         put(map, TravelBookContentPayload.TYPE, ClientPayloadHandler::handleTravelBook);
         put(map, FireplacePositionsPayload.TYPE, ClientPayloadHandler::handleFireplacePositions);
         put(map, SpeechChatPayload.TYPE, ClientPayloadHandler::handleSpeechChat);
         put(map, VillageTypeListPayload.TYPE, ClientPayloadHandler::handleVillageTypeList);
         put(map, InfoPanelContentPayload.TYPE, ClientPayloadHandler::handleInfoPanel);
         put(map, QuestInstanceSyncPayload.TYPE, ClientPayloadHandler::handleQuestSync);
         put(map, QuestInstanceDestroyPayload.TYPE, ClientPayloadHandler::handleQuestDestroy);
         put(map, QuestResultTextPayload.TYPE, ClientPayloadHandler::handleQuestResult);
         put(map, QuestOpenPayload.TYPE, ClientPayloadHandler::handleQuestOpen);
         put(map, WandDebugMenuPayload.TYPE, ClientPayloadHandler::handleWandDebugMenu);
         put(map, NegationWandPayload.TYPE, ClientPayloadHandler::handleNegationWand);
         put(map, BuildingProjectListPayload.TYPE, ClientPayloadHandler::handleBuildingProjectList);
         put(map, ControlledProjectsPayload.TYPE, ClientPayloadHandler::handleControlledProjects);
         put(map, ControlledMilitaryPayload.TYPE, ClientPayloadHandler::handleControlledMilitary);
         put(map, OpenHireScreenPayload.TYPE, ClientPayloadHandler::handleOpenHire);
         put(map, UnlockingToastPayload.TYPE, ClientPayloadHandler::handleUnlockingToast);
         put(map, PlanPreviewPayload.TYPE, ClientPayloadHandler::handlePlanPreview);
         put(map, ProjectCancelPromptPayload.TYPE, ClientPayloadHandler::handleProjectCancelPrompt);
         put(map, org.millenaire.chat.NpcChatOpenPayload.TYPE, ClientPayloadHandler::handleNpcChatOpen);
         put(map, org.millenaire.chat.NpcChatLinePayload.TYPE, ClientPayloadHandler::handleNpcChatLine);
         put(map, org.millenaire.chat.NpcChatClosedPayload.TYPE, ClientPayloadHandler::handleNpcChatClosed);
         return map;
      }

      private static <T extends CustomPacketPayload> void put(Map<Type<?>, IPayloadHandler<?>> map, Type<T> type, IPayloadHandler<T> handler) {
         map.put(type, handler);
      }
   }

   record ClientboundDef<T extends CustomPacketPayload>(Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
   }
}
